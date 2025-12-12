package iuh.fit.se.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import feign.FeignException;
import iuh.fit.event.dto.*;
import iuh.fit.se.dto.request.*;
import iuh.fit.se.dto.response.*;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.ProductElastic;
import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.OptionMediaGroup;
import iuh.fit.se.entity.records.Variant;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.mapper.ProductMapper;
import iuh.fit.se.repository.ProductElasticRepository;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.repository.httpclient.FileClient;
import iuh.fit.se.repository.httpclient.GeminiClient;
import iuh.fit.se.repository.httpclient.UserClient;
import iuh.fit.se.service.ProductService;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    ProductRepository productRepository;
    ProductElasticRepository productElasticRepository;
    ElasticsearchClient elasticsearchClient;
    ProductMapper productMapper;
    FileClient fileClient;
    MongoTemplate mongoTemplate;
    KafkaTemplate<String, Object> kafkaTemplate;
    UserClient userClient;
    GeminiClient geminiClient;
    private final ExecutorService geminiExecutor;

    @Override
    public ProductResponse findById(String id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toProductResponse(product);
    }
    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request, List<MultipartFile> images) {
        log.info("Creating product with {} images", images != null ? images.size() : 0);

        // 1. Validate input
        if (images == null || images.isEmpty() ||
                images.stream().allMatch(f -> f == null || f.isEmpty())) {
            throw new AppException(ErrorCode.FILE_NOT_EMPTY);
        }

        // Validate mediaByOption reference hợp lệ
        if (request.getMediaByOption() != null) {
            for (OptionMediaGroup media : request.getMediaByOption()) {
                int imageIndex = parseImageIndex(media.image());
                if (imageIndex < 0 || imageIndex >= images.size()) {
                    throw new AppException(ErrorCode.INVALID_IMAGE_INDEX);
                }
            }
        }

        List<String> uploadedUrls = null;

        try {
            // 2. Upload TẤT CẢ ảnh lên AWS
            log.info("Uploading {} image(s) to file service", images.size());
            FileClientResponse fileClientResponse;

            try {
                fileClientResponse = fileClient.uploadFile(images);
            } catch (feign.FeignException.BadRequest e) {
                String errorMsg = e.contentUTF8();
                log.error("File upload validation failed: {}", errorMsg);

                if (errorMsg.contains("inappropriate") || errorMsg.contains("not allowed")) {
                    throw new AppException(ErrorCode.IMAGE_CONTENT_NOT_ALLOWED);
                } else if (errorMsg.contains("not valid") || errorMsg.contains("format")) {
                    throw new AppException(ErrorCode.FILE_NOT_VALID);
                }
                throw new AppException(ErrorCode.UPLOAD_FILE_FAILED);
            } catch (feign.FeignException e) {
                log.error("File service error: {}", e.getMessage());
                throw new AppException(ErrorCode.FEIGN_CLIENT_ERROR);
            }

            if (fileClientResponse == null || fileClientResponse.getResult() == null) {
                throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
            }

            uploadedUrls = fileClientResponse.getResult();

            if (uploadedUrls.isEmpty()) {
                throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
            }

            log.info("Successfully uploaded {} image(s)", uploadedUrls.size());

            // 3. Tạo danh sách Image TỔNG (tất cả ảnh của sản phẩm)
            List<Image> allImages = new ArrayList<>();
            for (int i = 0; i < uploadedUrls.size(); i++) {
                allImages.add(Image.builder()
                        .url(uploadedUrls.get(i))
                        .position(i + 1)
                        .build());
            }

            // 4. Map mediaByOption: từ imageIndex -> URL thực tế
            List<OptionMediaGroup> mappedMediaByOption = new ArrayList<>();
            if (request.getMediaByOption() != null) {
                for (OptionMediaGroup mediaGroup : request.getMediaByOption()) {
                    int imageIndex = parseImageIndex(mediaGroup.image());

                    // Lấy URL từ uploadedUrls theo index
                    String imageUrl = uploadedUrls.get(imageIndex);

                    mappedMediaByOption.add(OptionMediaGroup.builder()
                            .optionName(mediaGroup.optionName())
                            .optionValue(mediaGroup.optionValue())
                            .image(imageUrl)  // URL đầy đủ
                            .build());
                }
            }

            // 5. Map DTO -> Entity
            Product product = productMapper.toProduct(request);
            product.setImages(allImages);  // TẤT CẢ ảnh
            product.setMediaByOption(mappedMediaByOption);  // Ánh xạ ảnh -> option
            product.setStatus(Status.AVAILABLE);
            product.setViewCount(0);
            product.setSoldCount(0);

            // 6. Lưu DB
            product = productRepository.save(product);
            log.info("Product saved to database with ID: {}", product.getId());

            // 7. Lưu elasticsearch
            ProductElastic productElastic = productElasticRepository.save(
                    productMapper.toProductElastic(product)
            );
            log.info("Product indexed in Elasticsearch with ID: {}", productElastic.getId());
            kafkaTemplate.send("product-index-request", ProductIndexEvent.builder()
                    .productId(product.getId())
                    .forceReindex(true)
                    .build());
            log.info("Sent index request for product {}", product.getId());
            return productMapper.toProductResponse(product);

        } catch (AppException e) {
            if (uploadedUrls != null && !uploadedUrls.isEmpty()) {
                log.warn("Rolling back uploaded files due to error: {}", e.getMessage());
                cleanupUploadedFiles(uploadedUrls);
            }
            throw e;
        } catch (Exception e) {
            if (uploadedUrls != null && !uploadedUrls.isEmpty()) {
                log.error("Unexpected error, rolling back uploaded files", e);
                cleanupUploadedFiles(uploadedUrls);
            }
            throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
        }
    }

    /**
     * Cleanup uploaded files when product creation fails
     */
    private void cleanupUploadedFiles(List<String> urls) {
        try {
            log.info("Attempting to delete {} uploaded file(s)", urls.size());
            FileClientResponse fileClientResponse = fileClient.deleteByUrl(DeleteRequest.builder()
                    .urls(urls)
                    .build());
            log.info("Deleted image from product:", fileClientResponse.getMessage());
            log.info("Successfully cleaned up uploaded files");
        } catch (Exception e) {
            log.error("Failed to cleanup uploaded files: {}", e.getMessage(), e);
            // Don't throw - this is cleanup, shouldn't fail the main operation
        }
    }

    private int parseImageIndex(String imageRef) {
        if (imageRef == null || imageRef.isEmpty()) {
            return -1;
        }

        try {
            // Nếu là số thuần (0, 1, 2...)
            return Integer.parseInt(imageRef);
        } catch (NumberFormatException e) {
            // Nếu đã là URL (trường hợp update), tìm trong uploadedUrls
            return -1;
        }
    }
    @Override
    @Transactional
    public ProductResponse updateProduct(ProductUpdateRequest request, List<MultipartFile> images) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // 0) Lọc file hợp lệ
        List<MultipartFile> validFiles = (images == null) ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty() && f.getSize() > 0).toList();

        log.info("Update product {}, incomingFiles={}, validFiles={}",
                request.getId(), images == null ? 0 : images.size(), validFiles.size());

        // 1) Normalize position 1..n
        List<Image> working = new ArrayList<>(product.getImages() != null ? product.getImages() : List.of());
        working.sort((a, b) -> {
            int pa = a.position() == null ? Integer.MAX_VALUE : a.position();
            int pb = b.position() == null ? Integer.MAX_VALUE : b.position();
            return Integer.compare(pa, pb);
        });
        List<Image> normalized = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            Image img = working.get(i);
            normalized.add(Image.builder().url(img.url()).position(i + 1).build());
        }
        working = normalized;

        // **LƯU LẠI MAPPING CŨ: position -> URL (trước khi xóa/thêm)**
        Map<Integer, String> oldPositionToUrl = new HashMap<>();
        for (Image img : working) {
            oldPositionToUrl.put(img.position(), img.url());
        }

        // 2) Xóa theo position
        Set<String> removedImageUrls = new HashSet<>();
        if (request.getRemoveImage() != null && !request.getRemoveImage().isEmpty()) {
            Set<Integer> removePositions = new HashSet<>(request.getRemoveImage());
            int size = working.size();
            for (Integer p : removePositions) {
                if (p == null || p < 1 || p > size) {
                    throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                }
            }
            List<Image> kept = new ArrayList<>();
            for (Image img : working) {
                if (!removePositions.contains(img.position())) {
                    kept.add(img);
                } else {
                    removedImageUrls.add(img.url());
                }
            }
            log.info("Removing {} image(s) from product {}, kept {} images",
                    removedImageUrls.size(), product.getId(), kept.size());

            // Xóa file vật lý
            if (!removedImageUrls.isEmpty()) {
                FileClientResponse delResp = fileClient.deleteByUrl(DeleteRequest.builder()
                        .urls(new ArrayList<>(removedImageUrls))
                        .build());
                log.info("Deleted images response: {}", delResp.getMessage());
            }

            working = kept;
        }

        // 3) Upload & append ảnh mới
        if (!validFiles.isEmpty()) {
            FileClientResponse up = fileClient.uploadFile(validFiles);
            List<String> urls = (up == null) ? null : up.getResult();
            if (urls == null || urls.isEmpty()) {
                throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
            }
            int nextPos = working.size() + 1;
            for (String url : urls) {
                working.add(Image.builder().url(url).position(nextPos++).build());
            }
        }

        // 4) Normalize lại position 1..n lần cuối
        List<Image> finalImages = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            finalImages.add(Image.builder().url(working.get(i).url()).position(i + 1).build());
        }
        product.setImages(finalImages);

        // **TẠO MAPPING MỚI: URL -> position mới**
        Map<String, Integer> newUrlToPosition = new HashMap<>();
        for (Image img : finalImages) {
            newUrlToPosition.put(img.url(), img.position());
        }

        // 5) Partial update các field khác
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getOptionDefs() != null) product.setOptionDefs(request.getOptionDefs());
        if (request.getVariants() != null) product.setVariants(request.getVariants());

        // 6) **XỬ LÝ mediaByOption THÔNG MINH**
        if (request.getMediaByOption() != null) {
            // 6.1) Request gửi mediaByOption mới -> replace hoàn toàn
            List<OptionMediaGroup> mapped = new ArrayList<>();
            for (OptionMediaGroup mg : request.getMediaByOption()) {
                String resolvedUrl = resolveImageRefToUrl(mg.image(), finalImages);
                if (resolvedUrl == null) {
                    throw new AppException(ErrorCode.INVALID_IMAGE_INDEX);
                }
                mapped.add(OptionMediaGroup.builder()
                        .optionName(mg.optionName())
                        .optionValue(mg.optionValue())
                        .image(resolvedUrl)
                        .build());
            }
            product.setMediaByOption(mapped);
        } else {
            // 6.2) **KHÔNG gửi mediaByOption -> TỰ ĐỘNG CẬP NHẬT**
            if (product.getMediaByOption() != null && !product.getMediaByOption().isEmpty()) {
                List<OptionMediaGroup> updatedMediaByOption = new ArrayList<>();

                for (OptionMediaGroup mg : product.getMediaByOption()) {
                    String currentImageUrl = mg.image();

                    // **Kiểm tra ảnh có bị xóa không**
                    if (removedImageUrls.contains(currentImageUrl)) {
                        log.info("Removing mediaByOption mapping for deleted image: {}", currentImageUrl);
                        continue; // Bỏ qua mapping này
                    }

                    // **Kiểm tra ảnh còn tồn tại không**
                    if (!newUrlToPosition.containsKey(currentImageUrl)) {
                        log.warn("Image URL {} no longer exists, removing mapping", currentImageUrl);
                        continue; // Bỏ qua mapping này
                    }

                    // **Giữ lại mapping (position có thể đã thay đổi nhưng URL vẫn hợp lệ)**
                    updatedMediaByOption.add(mg);
                }

                product.setMediaByOption(updatedMediaByOption);
                log.info("Auto-updated mediaByOption: {} -> {} mappings",
                        product.getMediaByOption().size(), updatedMediaByOption.size());
            }
        }

        // 7) Update ES & Mongo
        productElasticRepository.save(productMapper.toProductElastic(product));
        Query query = new Query(Criteria.where("_id").is(product.getId()));

        FindAndReplaceOptions options = FindAndReplaceOptions.options()
                .returnNew()   // Return the document after replacement
                .upsert();     // Only update, do not create a new document

        Product saved = mongoTemplate.findAndReplace(query, product, options, "products");
        kafkaTemplate.send("product-index-request", ProductIndexEvent.builder()
                .productId(saved.getId())
                .forceReindex(true)
                .build());
        log.info("Sent index update request for product {}", saved.getId());

        log.info("Updated product {}. images={}, optionDefs={}, mediaByOption={}, variants={}",
                saved.getId(),
                saved.getImages() == null ? 0 : saved.getImages().size(),
                saved.getOptionDefs() == null ? 0 : saved.getOptionDefs().size(),
                saved.getMediaByOption() == null ? 0 : saved.getMediaByOption().size(),
                saved.getVariants() == null ? 0 : saved.getVariants().size()
        );

        return productMapper.toProductResponse(saved);
    }

    /**
     * Resolve 'image' trong mediaByOption thành URL hợp lệ dựa trên danh sách ảnh hiện có.
     * Hỗ trợ cả:
     *  - Chỉ số dạng "0", "1", ... (0-based). Nếu out-of-range, thử 1-based.
     *  - URL tuyệt đối http(s)://
     */
    private String resolveImageRefToUrl(String ref, List<Image> images) {
        if (ref == null || ref.isBlank()) return null;

        // URL?
        String lower = ref.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return ref;
        }

        // Index?
        try {
            int idx = Integer.parseInt(ref.trim());
            // 0-based
            if (idx >= 0 && idx < images.size()) {
                return images.get(idx).url();
            }
            // 1-based
            int oneBased = idx - 1;
            if (oneBased >= 0 && oneBased < images.size()) {
                return images.get(oneBased).url();
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Override
    public void deleteProduct(ProductInvalid productInvalid) {
        Product product = productRepository.findById(productInvalid.getProductId()).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setStatus(Status.SUSPENDED);
        product.setReUpdate(true);
//        product.setReasonDelete(productInvalid.getReason());
//        product.setDeleteAt(Instant.now());
        productRepository.save(product);
        productElasticRepository.deleteById(productInvalid.getProductId());
        kafkaTemplate.send("product-remove-gemini-request", ProductRemoveGeminiEvent.builder()
                .productId(product.getId())
                .build());
        log.info("Sent remove Gemini request for product {}", product.getId());
        ApiResponse<SellerResponse> seller = userClient.searchBySellerId(product.getSellerId());
        kafkaTemplate.send("product-invalid-notify", ProductInvalidNotify.builder()
                .productId(product.getId())
                .productName(product.getName())
                .reason(productInvalid.getReason())
                .email(seller.getResult().getEmail())
                .build());
    }

    @Override
    public void deleteProductBySeller(ProductInvalid productInvalid) {
        Product product = productRepository.findById(productInvalid.getProductId()).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setStatus(Status.DISCONTINUED);
        product.setReasonDelete(productInvalid.getReason());
        product.setDeleteAt(Instant.now());
        productRepository.save(product);
        productElasticRepository.deleteById(productInvalid.getProductId());
        kafkaTemplate.send("product-remove-gemini-request", ProductRemoveGeminiEvent.builder()
                .productId(product.getId())
                .build());
        log.info("Sent remove Gemini request for product {}", product.getId());
    }

    @Override
    public OrderItemProductResponse findByIdAndSize(SearchSizeAndIDRequest request) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

//        Size selectedSize = product.getSizes().stream()
//                .filter(s -> s.size().equals(request.getSize()))
//                .findFirst()
//                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        Map<String, String> reqOptions = request.getOptions();
        if (reqOptions == null || reqOptions.isEmpty()) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND); // hoặc lỗi riêng: MISSING_OPTIONS
        }


        Variant selected = null;
        if (reqOptions != null && !reqOptions.isEmpty() && product.getVariants() != null) {
            for (Variant v : product.getVariants()) {
                if (variantMatches(v, reqOptions)) {
                    selected = v;
                    break;
                }
            }
        }
        if (selected == null) {
            // nên có ErrorCode.VARIANT_NOT_FOUND; nếu chưa có, tạm dùng PRODUCT_NOT_FOUND
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return OrderItemProductResponse.builder()
                .productId(product.getId())
                .sellerId(product.getSellerId())
                .name(product.getName())
                .image(product.getImages() != null && !product.getImages().isEmpty()
                        ? product.getImages().get(0).url() : null)
                // nếu tìm thấy variant thì map các thông tin giá/kho/avail
                .options(reqOptions)                   // NEW
                .optionsLabel(formatOptions(reqOptions))
                .price(selected != null ? selected.price() : null)
                .compareAtPrice(selected != null ? selected.compareAtPrice() : null)
                .available(selected != null ? Boolean.TRUE.equals(selected.available()) : null)
                .stock(selected != null ? selected.quantity() : null)
                .status(product.getStatus().name())
                .build();
    }
    private String formatOptions(Map<String, String> opts) {
        if (opts == null || opts.isEmpty()) return null;
        return opts.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.valueOf(e.getValue()))
                .reduce((a, b) -> a + " | " + b)
                .orElse(null);
    }

    private boolean variantMatches(Variant v, Map<String, String> reqOptions) {
        if (v == null || v.options() == null) return false;
        // yêu cầu: mọi cặp (k,v) trong reqOptions phải có trong v.options()
        for (Map.Entry<String, String> e : reqOptions.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (key == null) return false;
            String vv = v.options().get(key);
            if (vv == null) return false;
            if (!vv.equalsIgnoreCase(val == null ? "" : val)) return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void updateStockFromOrder(OrderCreatedEvent event) {
        log.info("Cập nhật kho cho đơn hàng: {}", event.getOrderId());

        for (OrderItemPayload item : event.getItems()) {
            Query query = new Query(Criteria.where("id").is(item.getProductId()));
            Product product = mongoTemplate.findOne(query, Product.class);
            if (product == null) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            // Chuẩn hoá options từ payload
            Map<String, String> reqOptions = item.getOptions();
            if (reqOptions == null || reqOptions.isEmpty()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND); // hoặc lỗi riêng: MISSING_OPTIONS
            }


            Variant selected = null;
            if (reqOptions != null && !reqOptions.isEmpty() && product.getVariants() != null) {
                for (Variant v : product.getVariants()) {
                    if (variantMatches(v, reqOptions)) {
                        selected = v;
                        break;
                    }
                }
            }
            if (product.getVariants() == null || product.getVariants().isEmpty()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            // Tìm variant cần trừ kho
            int idx = -1;
            for (int i = 0; i < product.getVariants().size(); i++) {
                if (variantMatches(product.getVariants().get(i), reqOptions)) {
                    idx = i; break;
                }
            }
            if (idx < 0) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            Variant target = product.getVariants().get(idx);
            int currentQty = target.quantity() == null ? 0 : target.quantity();
            int newQty = currentQty - item.getQuantity();
            if (newQty < 0) {
                throw new AppException(ErrorCode.QUANTITY_INVALID);
            }

            // build danh sách variant mới
            List<Variant> newVariants = new ArrayList<>(product.getVariants());
            newVariants.set(idx, Variant.builder()
                    .options(target.options())
                    .price(target.price())
                    .compareAtPrice(target.compareAtPrice())
                    .quantity(newQty)
                    .available(newQty > 0)
                    .build());

            Update update = new Update()
                    .set("variants", newVariants)
                    .inc("soldCount", item.getQuantity())
                    .set("version", product.getVersion() + 1);

            long updatedCount = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getProductId()).and("version").is(product.getVersion())),
                    update,
                    Product.class
            ).getModifiedCount();

            if (updatedCount == 0) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }

            log.info("Đã cập nhật kho cho sản phẩm {} (options: {}, giảm: {}, soldCount+={})",
                    item.getProductId(), reqOptions, item.getQuantity(), item.getQuantity());
        }
    }


    @Override
    @Transactional
    public void restoreStockFromOrder(OrderStatusChangedEvent event) {
        log.info("Xử lý hoàn kho cho đơn hàng: {}, trạng thái: {}", event.getOrderId(), event.getStatus());

        if (!"CANCELLED".equals(event.getStatus())) {
            log.info("Trạng thái đơn hàng {} không phải CANCELLED, bỏ qua hoàn kho", event.getOrderId());
            return;
        }

        for (OrderItemPayload item : event.getItems()) {
            Query query = new Query(Criteria.where("id").is(item.getProductId()));
            Product product = mongoTemplate.findOne(query, Product.class);
            if (product == null) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            Map<String, String> reqOptions = item.getOptions();
            if (reqOptions == null || reqOptions.isEmpty()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND); // hoặc lỗi riêng: MISSING_OPTIONS
            }


            Variant selected = null;
            if (reqOptions != null && !reqOptions.isEmpty() && product.getVariants() != null) {
                for (Variant v : product.getVariants()) {
                    if (variantMatches(v, reqOptions)) {
                        selected = v;
                        break;
                    }
                }
            }

            if (product.getVariants() == null || product.getVariants().isEmpty()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            int idx = -1;
            for (int i = 0; i < product.getVariants().size(); i++) {
                if (variantMatches(product.getVariants().get(i), reqOptions)) {
                    idx = i; break;
                }
            }
            if (idx < 0) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            Variant target = product.getVariants().get(idx);
            int currentQty = target.quantity() == null ? 0 : target.quantity();
            int newQty = currentQty + item.getQuantity();

            // giảm soldCount nhưng không âm
            int currentSoldCount = product.getSoldCount() != null ? product.getSoldCount() : 0;
            int newSoldCount = Math.max(0, currentSoldCount - item.getQuantity());

            List<Variant> newVariants = new ArrayList<>(product.getVariants());
            newVariants.set(idx, Variant.builder()
                    .options(target.options())
                    .price(target.price())
                    .compareAtPrice(target.compareAtPrice())
                    .quantity(newQty)
                    .available(true)
                    .build());

            Update update = new Update()
                    .set("variants", newVariants)
                    .set("soldCount", newSoldCount)
                    .set("version", product.getVersion() + 1);

            long updatedCount = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getProductId()).and("version").is(product.getVersion())),
                    update,
                    Product.class
            ).getModifiedCount();

            if (updatedCount == 0) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }

            log.info("Đã hoàn kho cho sản phẩm {} (options: {}, tăng: {}, soldCount-={})",
                    item.getProductId(), reqOptions, item.getQuantity(), item.getQuantity());
        }
    }


    @Override
    public List<ProductResponse> findAllByCategory(String category) {
        List<ProductElastic> products = productElasticRepository.findByCategoryId(category);
        return products.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findAllProducts() {
        Iterable<ProductElastic> products = productElasticRepository.findAllByOrderByCreatedAtDesc();
        return StreamSupport.stream(products.spliterator(), false)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findBestSellingProducts() {
        return productElasticRepository.findAllByOrderBySoldCountDesc()
                .stream().map(productMapper::toProductResponse).toList();
    }

    @Override
    public List<ProductResponse> findAllBySellerId(String sellerId) {
        return productRepository.findBySellerId(sellerId).stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> searchProducts(String query) {
        List<ProductElastic> products = productElasticRepository.searchByNameOrDescription(query);
        return products.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }
    public List<String> suggestProducts(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        String queryText = prefix.trim();

        try {
            SearchResponse<Void> resp = elasticsearchClient.search(s -> s
                            .index("products")
                            .suggest(sug -> sug
                                    .text(queryText)
                                    .suggesters("product-suggest", cs -> cs
                                            .completion(c -> c
                                                    .field("nameSuggest")
                                                    .skipDuplicates(true)
                                                    .size(8)
                                            )
                                    )
                            ),
                    Void.class
            );

            Map<String, List<Suggestion<Void>>> suggestMap = resp.suggest();
            if (suggestMap == null || suggestMap.isEmpty()) {
                return List.of();
            }

            return suggestMap.getOrDefault("product-suggest", List.of())
                    .stream()
                    .flatMap(sug -> sug.completion().options().stream())
                    .map(opt -> opt.text())
                    .distinct()
                    .limit(10)
                    .toList();

        } catch (Exception e) {
            log.error("Suggest error with prefix '{}': {}", queryText, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional
    public void discontinueBySellerId(String sellerId, String reason) {
        if (sellerId == null || sellerId.isEmpty()) return;

        Instant now = Instant.now();

        // 1. Update MongoDB
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("sellerId").is(sellerId),
                        Criteria.where("status").ne(Status.DISCONTINUED)
                )
        );
        Update u = new Update()
                .set("status", Status.DISCONTINUED)
                .set("deleteAt", now)
                .set("reasonDelete", reason);

        var result = mongoTemplate.updateMulti(q, u, Product.class);
        log.info("Discontinued products of seller {}, matched={}, modified={}",
                sellerId, result.getMatchedCount(), result.getModifiedCount());

        // 2. Delete Elasticsearch indices
        List<String> ids = productRepository.findBySellerId(sellerId).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!ids.isEmpty()) {
            productElasticRepository.deleteAllById(ids);
            log.info("Deleted {} product indices from Elasticsearch", ids.size());
            for ( String id: ids
                 ) {
                try {
                    log.info("Removing product {} from Gemini index", id);
                    geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder()
                            .product_id(id)
                            .build());
                    log.info("Removing image product {} from Gemini index",id);
                    geminiClient.removeProductImages(RemoveProductImagesRequest.builder()
                            .product_id(id)
                            .build());
                } catch (FeignException e){
                    log.error("Gemini removing index error for product {}: {}",id, e.getMessage());
                };
            }
        }
    }

    @Override
    @Transactional
    public void updateView(String productId) {
        // 1) Tăng viewCount trong Mongo atomically
        Query query = new Query(Criteria.where("id").is(productId));
        Update update = new Update().inc("viewCount", 1);

        Product updated = mongoTemplate.findAndModify(
                query,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options()
                        .returnNew(true),             // trả về document sau khi update
                Product.class
        );

        if (updated == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2) Đồng bộ viewCount sang Elasticsearch (nếu có)
        productElasticRepository.findById(productId).ifPresent(pe -> {
            pe.setViewCount(updated.getViewCount());
            productElasticRepository.save(pe);
        });

        log.info("Increase view for product {} to {}", productId, updated.getViewCount());
    }


    @Override
    public List<ProductResponse> findAllByStatus(Status status) {
        log.info("Finding all products with status: {}", status);
        List<Product> products = productRepository.findByStatus(status.name());
        return products.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findBySellerIdAndStatus(String sellerId, Status status) {
        log.info("Finding products for seller {} with status: {}", sellerId, status);
        List<Product> products = productRepository.findBySellerIdAndStatus(sellerId, status.name());
        return products.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductResponse approveProduct(String productId, Status status, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Chỉ cho phép duyệt sản phẩm đang ở trạng thái PENDING
//        if (product.getStatus() != Status.PENDING) {
//            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Có thể tạo ErrorCode.INVALID_STATUS
//        }

        // Validate status phải là AVAILABLE hoặc DISCONTINUED
        if (status != Status.AVAILABLE && status != Status.DISCONTINUED) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Cập nhật trạng thái
        product.setStatus(status);

        if (status == Status.DISCONTINUED) {
            // Nếu từ chối, lưu lý do và thời gian xóa
            if (reason == null || reason.isBlank()) {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            product.setReasonDelete(reason);
            product.setDeleteAt(Instant.now());

            // Xóa khỏi Elasticsearch
            productElasticRepository.deleteById(productId);

            // Xóa khỏi Gemini index
            kafkaTemplate.send("product-remove-gemini-request", ProductRemoveGeminiEvent.builder()
                    .productId(product.getId())
                    .build());
            log.info("Sent remove Gemini request for rejected product {}", productId);

            // Gửi thông báo cho seller
            ApiResponse<SellerResponse> seller = userClient.searchBySellerId(product.getSellerId());
            kafkaTemplate.send("product-invalid-notify", ProductInvalidNotify.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .reason(reason)
                    .email(seller.getResult().getEmail())
                    .build());
        } else {
            // Nếu chấp nhận, thêm vào Elasticsearch
            productElasticRepository.save(productMapper.toProductElastic(product));

            // Index vào Gemini
            // Thay sync call bằng Kafka
            kafkaTemplate.send("product-index-request", ProductIndexEvent.builder()
                    .productId(product.getId())
                    .forceReindex(true)
                    .build());
            log.info("Sent index request after approve for product {}", product.getId());
        }
        product.setReUpdate(false);
        Product saved = productRepository.save(product);
        log.info("Product {} approved with status: {}", productId, status);

        return productMapper.toProductResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse suspendProduct(String productId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Chỉ cho phép suspend sản phẩm đang AVAILABLE
        if (product.getStatus() != Status.AVAILABLE) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Cập nhật trạng thái
        product.setStatus(Status.SUSPENDED);
        product.setReasonDelete(reason);
        product.setDeleteAt(Instant.now());
        product.setReUpdate(true);
        // Xóa khỏi Elasticsearch (tạm thời không cho tìm kiếm)
        productElasticRepository.deleteById(productId);

        // Xóa khỏi Gemini index
        kafkaTemplate.send("product-remove-gemini-request", ProductRemoveGeminiEvent.builder()
                .productId(product.getId())
                .build());
        log.info("Sent remove Gemini request for product {}", product.getId());

        Product saved = productRepository.save(product);
        log.info("Product {} suspended with reason: {}", productId, reason);
        ApiResponse<SellerResponse> seller = userClient.searchBySellerId(product.getSellerId());
        kafkaTemplate.send("product-invalid-notify", ProductInvalidNotify.builder()
                .productId(product.getId())
                .productName(product.getName())
                .reason(reason)
                .email(seller.getResult().getEmail())
                .build());
        return productMapper.toProductResponse(saved);
    }


    @Override
    @Transactional
    public void suspendAllProductsBySeller(String sellerId, String reason) {
        if (sellerId == null || sellerId.isEmpty()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        Instant now = Instant.now();

        // 1. Update MongoDB: chỉ suspend những sản phẩm đang AVAILABLE
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("sellerId").is(sellerId),
                        Criteria.where("status").is(Status.AVAILABLE.name())
                )
        );
        Update u = new Update()
                .set("status", Status.SUSPENDED)
                .set("deleteAt", now)
                .set("reasonDelete", reason);

        var result = mongoTemplate.updateMulti(q, u, Product.class);
        log.info("Suspended products of seller {}, matched={}, modified={}",
                sellerId, result.getMatchedCount(), result.getModifiedCount());

        // 2. Lấy danh sách ID sản phẩm vừa bị suspend
        List<Product> suspendedProducts = productRepository.findBySellerIdAndStatus(sellerId, Status.SUSPENDED.name());
        List<String> ids = suspendedProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!ids.isEmpty()) {
            // Xóa Elasticsearch ngay (không có quota limit)
            productElasticRepository.deleteAllById(ids);
            log.info("Deleted {} product indices from Elasticsearch", ids.size());

            // 3. Xóa khỏi Gemini index với rate limiting + retry
            removeFromGeminiWithRateLimit(ids);
        }
    }

    private ExecutorService getOrCreateExecutor() {
        return geminiExecutor;
    }
    /**
     * Xóa products từ Gemini index với rate limiting
     * - Xử lý theo batch 5 products
     * - Delay 8 giây giữa các batch (tuân thủ 8 requests/minute của Python)
     * - Xử lý parallel trong mỗi batch
     * - Tự động retry khi gặp lỗi 429 (nhờ FeignConfig)
     */
    private void removeFromGeminiWithRateLimit(List<String> productIds) {
        final int BATCH_SIZE = 5;
        final long DELAY_MS = 8000;

        int totalBatches = (productIds.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        int successCount = 0;
        int failCount = 0;

        // Dùng geminiExecutor thay vì executorService cũ
        log.info("Starting Gemini index removal for {} products in {} batches", productIds.size(), totalBatches);

        for (int i = 0; i < productIds.size(); i += BATCH_SIZE) {
            int batchNumber = (i / BATCH_SIZE) + 1;
            int end = Math.min(i + BATCH_SIZE, productIds.size());
            List<String> batch = productIds.subList(i, end);

            log.info("Processing Gemini removal batch {}/{}: {} products", batchNumber, totalBatches, batch.size());

            List<CompletableFuture<Boolean>> futures = batch.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> removeProductFromGemini(id), geminiExecutor))
                    .collect(Collectors.toList());

            List<Boolean> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            long batchSuccess = results.stream().filter(r -> r).count();
            long batchFail = results.size() - batchSuccess;
            successCount += batchSuccess;
            failCount += batchFail;

            log.info("Batch {}/{} completed: {} success, {} failed", batchNumber, totalBatches, batchSuccess, batchFail);

            if (end < productIds.size()) {
                try {
                    log.info("Waiting {}ms before next batch to respect rate limit...", DELAY_MS);
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted during delay", e);
                    break;
                }
            }
        }

        log.info("Gemini index removal completed: {} total, {} success, {} failed",
                productIds.size(), successCount, failCount);
    }

    /**
     * Xóa 1 product từ Gemini index (text + images)
     * Return true nếu thành công, false nếu thất bại
     * FeignConfig sẽ tự động retry khi gặp lỗi 429
     */
    private boolean removeProductFromGemini(String productId) {
        try {
            // Xóa text embedding
            log.debug("Removing text embedding for product {}", productId);
            geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder()
                    .product_id(productId)
                    .build());

            // Xóa image embeddings
            log.debug("Removing image embeddings for product {}", productId);
            geminiClient.removeProductImages(RemoveProductImagesRequest.builder()
                    .product_id(productId)
                    .build());

            log.info("Successfully removed product {} from Gemini index", productId);
            return true;

        } catch (FeignException.TooManyRequests e) {
            // Lỗi 429 sau khi retry hết (FeignConfig đã retry 3 lần)
            log.error("Rate limit exceeded for product {} after retries: {}",
                    productId, e.getMessage());
            return false;

        } catch (FeignException.ServiceUnavailable e) {
            // Lỗi 503 sau khi retry hết
            log.error("Gemini service unavailable for product {} after retries: {}",
                    productId, e.getMessage());
            return false;

        } catch (FeignException e) {
            // Các lỗi Feign khác
            log.error("Feign error removing product {} from Gemini: {} - {}",
                    productId, e.status(), e.getMessage());
            return false;

        } catch (Exception e) {
            // Lỗi không xác định
            log.error("Unexpected error removing product {} from Gemini: {}",
                    productId, e.getMessage(), e);
            return false;
        }
    }
    @Override
    @Transactional
    public void activateAllProductsBySeller(String sellerId) {
        if (sellerId == null || sellerId.isEmpty()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // 1. Update MongoDB
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("sellerId").is(sellerId),
                        Criteria.where("status").is(Status.SUSPENDED.name()),
                        Criteria.where("reUpdate").is(false)
                )
        );
        Update u = new Update()
                .set("status", Status.AVAILABLE)
                .set("deleteAt", null)
                .set("reasonDelete", null);

        var result = mongoTemplate.updateMulti(q, u, Product.class);
        log.info("Activated products of seller {}, matched={}, modified={}",
                sellerId, result.getMatchedCount(), result.getModifiedCount());

        // 2. Thêm lại vào Elasticsearch
        List<Product> products = productRepository.findBySellerIdAndStatus(sellerId, Status.AVAILABLE.name());

        if (!products.isEmpty()) {
            List<ProductElastic> elasticProducts = products.stream()
                    .map(productMapper::toProductElastic)
                    .collect(Collectors.toList());

            productElasticRepository.saveAll(elasticProducts);
            log.info("Indexed {} products to Elasticsearch", elasticProducts.size());

            // 3. Index lại vào Gemini với rate limiting
            List<String> productIds = products.stream()
                    .map(Product::getId)
                    .filter(Objects::nonNull)
                    .toList();

            indexToGeminiWithRateLimit(productIds);
        }
    }
    private void indexToGeminiWithRateLimit(List<String> productIds) {
        int totalBatches = (productIds.size() + 5 - 1) / 5;
        int successCount = 0;
        int failCount = 0;

        log.info("Starting Gemini indexing for {} products in {} batches (batch_size=5, delay=8000ms)",
                productIds.size(), totalBatches);

        for (int i = 0; i < productIds.size(); i += 5) {
            int batchNumber = (i / 5) + 1;
            int end = Math.min(i + 5, productIds.size());
            List<String> batch = productIds.subList(i, end);

            log.info("Processing Gemini indexing batch {}/{}: {} products", batchNumber, totalBatches, batch.size());

            List<CompletableFuture<Boolean>> futures = batch.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> indexProductToGemini(id), geminiExecutor))
                    .collect(Collectors.toList());

            List<Boolean> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            long batchSuccess = results.stream().filter(r -> r).count();
            long batchFail = results.size() - batchSuccess;
            successCount += batchSuccess;
            failCount += batchFail;

            log.info("Batch {}/{} completed: {} success, {} failed", batchNumber, totalBatches, batchSuccess, batchFail);

            if (end < productIds.size()) {
                try {
                    log.info("Waiting 8000ms before next batch to respect rate limit...");
                    Thread.sleep(8000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted during delay", e);
                    break;
                }
            }
        }

        log.info("Gemini indexing completed: {} total, {} success, {} failed",
                productIds.size(), successCount, failCount);
    }
    private boolean indexProductToGemini(String productId) {
        try {
            log.debug("Indexing text embedding for product {}", productId);
            geminiClient.indexSingleProduct(IndexSingleProductRequest.builder()
                    .product_id(productId)
                    .force_reindex(true)
                    .build());

            log.debug("Indexing image embeddings for product {}", productId);
            geminiClient.indexSingleProductImages(IndexSingleProductImagesRequest.builder()
                    .product_id(productId)
                    .force_reindex(true)
                    .build());

            log.info("Successfully indexed product {} to Gemini", productId);
            return true;

        } catch (FeignException.TooManyRequests e) {
            log.error("Rate limit exceeded for product {} after retries: {}",
                    productId, e.getMessage());
            return false;

        } catch (FeignException.ServiceUnavailable e) {
            log.error("Gemini service unavailable for product {} after retries: {}",
                    productId, e.getMessage());
            return false;

        } catch (FeignException e) {
            log.error("Feign error indexing product {} to Gemini: {} - {}",
                    productId, e.status(), e.getMessage());
            return false;

        } catch (Exception e) {
            log.error("Unexpected error indexing product {} to Gemini: {}",
                    productId, e.getMessage(), e);
            return false;
        }
    }
    @Override
    @Transactional
    public ProductResponse reregisterProduct(ProductUpdateRequest request, List<MultipartFile> images) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Chỉ cho phép nếu SUSPENDED + reUpdate=true, hoặc DISCONTINUED
        boolean allow =
                product.getStatus() == Status.DISCONTINUED
                        || (product.getStatus() == Status.SUSPENDED && Boolean.TRUE.equals(product.isReUpdate()));
        if (!allow) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // 0) Lọc file hợp lệ
        List<MultipartFile> validFiles = (images == null) ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty() && f.getSize() > 0).toList();

        log.info("Reregister product {}, incomingFiles={}, validFiles={}",
                request.getId(), images == null ? 0 : images.size(), validFiles.size());

        // 1) Normalize position 1..n
        List<Image> working = new ArrayList<>(product.getImages() != null ? product.getImages() : List.of());
        working.sort((a, b) -> {
            int pa = a.position() == null ? Integer.MAX_VALUE : a.position();
            int pb = b.position() == null ? Integer.MAX_VALUE : b.position();
            return Integer.compare(pa, pb);
        });
        List<Image> normalized = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            Image img = working.get(i);
            normalized.add(Image.builder().url(img.url()).position(i + 1).build());
        }
        working = normalized;

        // Ghi nhớ URL ảnh cũ
        Map<Integer, String> oldPositionToUrl = new HashMap<>();
        for (Image img : working) oldPositionToUrl.put(img.position(), img.url());

        // 2) Xóa ảnh theo position nếu có yêu cầu
        Set<String> removedImageUrls = new HashSet<>();
        if (request.getRemoveImage() != null && !request.getRemoveImage().isEmpty()) {
            Set<Integer> removePositions = new HashSet<>(request.getRemoveImage());
            int size = working.size();
            for (Integer p : removePositions) {
                if (p == null || p < 1 || p > size) {
                    throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                }
            }
            List<Image> kept = new ArrayList<>();
            for (Image img : working) {
                if (!removePositions.contains(img.position())) kept.add(img);
                else removedImageUrls.add(img.url());
            }
            if (!removedImageUrls.isEmpty()) {
                try {
                    FileClientResponse delResp = fileClient.deleteByUrl(DeleteRequest.builder()
                            .urls(new ArrayList<>(removedImageUrls)).build());
                    log.info("Deleted images response: {}", delResp.getMessage());
                } catch (Exception e) {
                    log.warn("Failed to delete some images during reregister: {}", e.getMessage());
                }
            }
            working = kept;
        }

        // 3) Upload & append ảnh mới (nếu có)
        if (!validFiles.isEmpty()) {
            FileClientResponse up = fileClient.uploadFile(validFiles);
            List<String> urls = (up == null) ? null : up.getResult();
            if (urls == null || urls.isEmpty()) {
                throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
            }
            int nextPos = working.size() + 1;
            for (String url : urls) {
                working.add(Image.builder().url(url).position(nextPos++).build());
            }
        }

        // 4) Normalize lại position 1..n lần cuối
        List<Image> finalImages = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            finalImages.add(Image.builder().url(working.get(i).url()).position(i + 1).build());
        }
        product.setImages(finalImages);

        // Map URL -> position mới
        Map<String, Integer> newUrlToPosition = new HashMap<>();
        for (Image img : finalImages) newUrlToPosition.put(img.url(), img.position());

        // 5) Partial update các field (giống updateProduct)
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getOptionDefs() != null) product.setOptionDefs(request.getOptionDefs());
        if (request.getVariants() != null) product.setVariants(request.getVariants());
        // KHÔNG cho client set status ở flow này

        // 6) mediaByOption
        if (request.getMediaByOption() != null) {
            List<OptionMediaGroup> mapped = new ArrayList<>();
            for (OptionMediaGroup mg : request.getMediaByOption()) {
                String resolvedUrl = resolveImageRefToUrl(mg.image(), finalImages);
                if (resolvedUrl == null) throw new AppException(ErrorCode.INVALID_IMAGE_INDEX);
                mapped.add(OptionMediaGroup.builder()
                        .optionName(mg.optionName())
                        .optionValue(mg.optionValue())
                        .image(resolvedUrl)
                        .build());
            }
            product.setMediaByOption(mapped);
        } else {
            if (product.getMediaByOption() != null && !product.getMediaByOption().isEmpty()) {
                List<OptionMediaGroup> updatedMediaByOption = new ArrayList<>();
                for (OptionMediaGroup mg : product.getMediaByOption()) {
                    String currentImageUrl = mg.image();
                    if (removedImageUrls.contains(currentImageUrl)) continue;
                    if (!newUrlToPosition.containsKey(currentImageUrl)) continue;
                    updatedMediaByOption.add(mg);
                }
                product.setMediaByOption(updatedMediaByOption);
                log.info("Auto-updated mediaByOption (reregister): {} -> {} mappings",
                        (product.getMediaByOption() == null ? 0 : product.getMediaByOption().size()),
                        updatedMediaByOption.size());
            }
        }

        // 7) Đặt trạng thái về PENDING & reset reason/deleteAt
        product.setStatus(Status.PENDING);
        product.setReasonDelete(null);
        product.setDeleteAt(null);

        // 8) Đảm bảo không còn trong Elasticsearch
        try {
            productElasticRepository.deleteById(product.getId());
        } catch (Exception ignore) { }

        // 9) Đảm bảo không còn trong Gemini index
        kafkaTemplate.send("product-remove-gemini-request", ProductRemoveGeminiEvent.builder()
                .productId(product.getId())
                .build());
        log.info("Sent remove Gemini request before reregister for product {}", product.getId());

        // 10) Lưu Mongo
        Product saved = productRepository.save(product);

        log.info("Product {} re-registered. Status=PENDING, images={}, optionDefs={}, mediaByOption={}, variants={}",
                saved.getId(),
                saved.getImages() == null ? 0 : saved.getImages().size(),
                saved.getOptionDefs() == null ? 0 : saved.getOptionDefs().size(),
                saved.getMediaByOption() == null ? 0 : saved.getMediaByOption().size(),
                saved.getVariants() == null ? 0 : saved.getVariants().size()
        );

        return productMapper.toProductResponse(saved);
    }

}