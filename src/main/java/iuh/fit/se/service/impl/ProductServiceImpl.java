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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
            try {
                log.info("Indexing product {} in Gemini", product.getId());
                geminiClient.indexSingleProduct(IndexSingleProductRequest.builder()
                        .product_id(product.getId())
                        .force_reindex(true)
                        .build());
                log.info("Indexing product {} images in Gemini", product.getId());
                geminiClient.indexSingleProductImages(IndexSingleProductImagesRequest.builder()
                        .product_id(product.getId())
                        .force_reindex(true)
                        .build());
            } catch (FeignException e){
                log.error("Gemini indexing error for product {}: {}", product.getId(), e.getMessage());
            };
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
        Product saved = productRepository.save(product);

        try {
            // Upsert bản ghi sản phẩm
            geminiClient.upsertSingleProduct(UpsertSingleProductRequest.builder()
                    .product_id(product.getId())
                    .build());

            // Upsert toàn bộ ảnh theo position hiện có
            for (Image img : product.getImages()) {
                geminiClient.upsertSingleImageJson(UpsertSingleImageJsonRequest.builder()
                        .product_id(product.getId())
                        .position(img.position())
                        .image_url(img.url())
                        .build());
            }
            log.info("Gemini upserted product {} and {} images", product.getId(),
                    product.getImages() == null ? 0 : product.getImages().size());
        } catch (FeignException e) {
            log.error("Gemini upsert error for product {}: {}", product.getId(), e.getMessage());
        }

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
        product.setStatus(Status.DISCONTINUED);
        product.setReasonDelete(productInvalid.getReason());
        productRepository.save(product);
        productElasticRepository.deleteById(productInvalid.getProductId());
        try {
            log.info("Removing product {} from Gemini index", product.getId());
            geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder()
                    .product_id(product.getId())
                    .build());
            log.info("Removing image product {} from Gemini index",product.getId());
            geminiClient.removeProductImages(RemoveProductImagesRequest.builder()
                    .product_id(product.getId())
                    .build());
        } catch (FeignException e){
            log.error("Gemini removing index error for product {}: {}",product.getId(), e.getMessage());
        };
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
        productRepository.save(product);
        productElasticRepository.deleteById(productInvalid.getProductId());
        try {
            log.info("Removing product {} from Gemini index", product.getId());
            geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder()
                    .product_id(product.getId())
                    .build());
            log.info("Removing image product {} from Gemini index",product.getId());
            geminiClient.removeProductImages(RemoveProductImagesRequest.builder()
                    .product_id(product.getId())
                    .build());
        } catch (FeignException e){
            log.error("Gemini removing index error for product {}: {}",product.getId(), e.getMessage());
        };
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
        Iterable<ProductElastic> products = productElasticRepository.findAll();
        return StreamSupport.stream(products.spliterator(), false)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findAllBySellerId(String sellerId) {
        return productElasticRepository.findBySellerId(sellerId).stream()
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
    public void updateView(String productId){
        Product product = productRepository.findById(productId).orElseThrow(()-> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setViewCount(product.getViewCount() + 1);
        productRepository.save(product);

        ProductElastic productElastic = productElasticRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        productElastic.setViewCount(product.getViewCount());
        productElasticRepository.save(productElastic);
    }
}