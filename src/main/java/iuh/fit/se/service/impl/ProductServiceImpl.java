package iuh.fit.se.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import iuh.fit.event.dto.*;
import iuh.fit.se.dto.request.DeleteRequest;
import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.*;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.ProductElastic;
import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.Size;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.mapper.ProductMapper;
import iuh.fit.se.repository.ProductElasticRepository;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.repository.httpclient.FileClient;
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

        List<String> uploadedUrls = null;

        try {
            // 2. Upload ảnh đến AWS qua file-service (đã validate bên trong)
            log.info("Uploading {} image(s) to file service", images.size());
            FileClientResponse fileClientResponse = null;

            try {
                fileClientResponse = fileClient.uploadFile(images);
            } catch (feign.FeignException.BadRequest e) {
                // Parse error message từ file service
                String errorMsg = e.contentUTF8();
                log.error("File upload validation failed: {}", errorMsg);

                // Check if it's image content error
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

            // 3. Ghép URL vào position từ request
            List<Image> finalImages = new ArrayList<>();
            for (int i = 0; i < uploadedUrls.size(); i++) {
                finalImages.add(Image.builder()
                        .url(uploadedUrls.get(i))
                        .position(i + 1)
                        .build());
            }

            // 4. Map DTO -> Entity
            Product product = productMapper.toProduct(request);
            product.setImages(finalImages);
            product.setStatus(Status.AVAILABLE);
            product.setViewCount(0);
            product.setSoldCount(0);
            // 5. Lưu DB
            product = productRepository.save(product);
            log.info("Product saved to database with ID: {}", product.getId());

            // 6. Lưu elasticsearch
            ProductElastic productElastic = productElasticRepository.save(
                    productMapper.toProductElastic(product)
            );
            log.info("Product indexed in Elasticsearch with ID: {}", productElastic.getId());

            return productMapper.toProductResponse(product);

        } catch (AppException e) {
            // Nếu upload thất bại hoặc validation lỗi, cleanup uploaded files
            if (uploadedUrls != null && !uploadedUrls.isEmpty()) {
                log.warn("Rolling back uploaded files due to error: {}", e.getMessage());
                cleanupUploadedFiles(uploadedUrls);
            }
            throw e;
        } catch (Exception e) {
            // Cleanup on unexpected errors
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

    @Override
    @Transactional
    public ProductResponse updateProduct(ProductUpdateRequest request, List<MultipartFile> images) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // === 0) Lọc file hợp lệ (tránh gọi file-service khi rỗng) ===
        List<MultipartFile> validFiles = (images == null) ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty() && f.getSize() > 0).toList();

        log.info("Update product {}, incomingFiles={}, validFiles={}",
                request.getId(), images == null ? 0 : images.size(), validFiles.size());

        // === 1) Lấy & chuẩn hoá position 1..n hiện tại ===
        List<Image> working = new ArrayList<>(product.getImages() != null ? product.getImages() : List.of());
        // sort theo position (null đưa xuống cuối)
        working.sort((a, b) -> {
            int pa = a.position() == null ? Integer.MAX_VALUE : a.position();
            int pb = b.position() == null ? Integer.MAX_VALUE : b.position();
            return Integer.compare(pa, pb);
        });
        List<Image> normalized = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            Image img = working.get(i);
            normalized.add(Image.builder()
                    .url(img.url())
                    .position(i + 1)
                    .build());
        }
        working = normalized;

        // === 2) Xoá theo position (nếu có) ===
        if (request.getRemoveImage() != null && !request.getRemoveImage().isEmpty()) {
            Set<Integer> removePositions = new HashSet<>(request.getRemoveImage());

            int size = working.size();
            for (Integer p : removePositions) {
                if (p == null || p < 1 || p > size) {
                    throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                }
            }
            List<Image> kept = new ArrayList<>();
            List<String> removeImages  = new ArrayList<>();
            for (Image img : working) {
                if (!removePositions.contains(img.position())) {
                    kept.add(img);
                }else{
                    removeImages.add(img.url());
                }
            }
            log.info("Removing {} image(s) from product {}, kept {} images",
                    removeImages.getFirst(), product.getId(), kept.size());
            // Gọi file-service để xoá ảnh
            FileClientResponse fileClientResponse = fileClient.deleteByUrl(DeleteRequest.builder()
                    .urls(removeImages)
                    .build());
            log.info("Deleted image from product:", fileClientResponse.getMessage());

            working = kept;
        }

        // === 3) Upload & append ảnh mới vào cuối (nếu có) ===
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

        // === 4) Chuẩn hoá lại position 1..n lần cuối ===
        List<Image> finalImages = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            finalImages.add(Image.builder()
                    .url(working.get(i).url())
                    .position(i + 1)
                    .build());
        }
        product.setImages(finalImages);

        // === 5) Partial update các field khác ===
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getSizes() != null) product.setSizes(request.getSizes());
        if (request.getPercentDiscount() != null) product.setPercentDiscount(request.getPercentDiscount());

        ProductElastic productElastic = productElasticRepository.save(productMapper.toProductElastic(product));
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    public void deleteProduct(ProductInvalid productInvalid) {
        Product product = productRepository.findById(productInvalid.getProductId()).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setStatus(Status.DISCONTINUED);
        product.setReasonDelete(productInvalid.getReason());
        productRepository.save(product);
        productElasticRepository.deleteById(productInvalid.getProductId());
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
    }

    @Override
    public OrderItemProductResponse findByIdAndSize(SearchSizeAndIDRequest request) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        Size selectedSize = product.getSizes().stream()
                .filter(s -> s.size().equals(request.getSize()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        return OrderItemProductResponse.builder()
                .productId(product.getId())
                .sellerId(product.getSellerId())
                .name(product.getName())
                .image(product.getImages() != null && !product.getImages().isEmpty()
                        ? product.getImages().get(0).url() : null)
                .size(selectedSize.size())
                .price(selectedSize.price())
                .compareAtPrice(selectedSize.compareAtPrice())
                .available(selectedSize.available())
                .stock(selectedSize.quantity())
                .status(product.getStatus().name())
                .build();
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

            List<Size> updatedSizes = product.getSizes().stream()
                    .map(size -> {
                        if (size.size().equals(item.getSize())) {
                            int newQuantity = size.quantity() - item.getQuantity();
                            if (newQuantity < 0) {
                                throw new AppException(ErrorCode.QUANTITY_INVALID);
                            }
                            return Size.builder()
                                    .size(size.size())
                                    .price(size.price())
                                    .compareAtPrice(size.compareAtPrice())
                                    .quantity(newQuantity)
                                    .available(newQuantity > 0)
                                    .build();
                        }
                        return size;
                    })
                    .collect(Collectors.toList());

            // Tăng soldCount
            int currentSoldCount = product.getSoldCount() != null ? product.getSoldCount() : 0;

            Update update = new Update()
                    .set("sizes", updatedSizes)
                    .inc("soldCount", item.getQuantity())  // Tăng số lượng đã bán
                    .set("version", product.getVersion() + 1);

            long updatedCount = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getProductId()).and("version").is(product.getVersion())),
                    update,
                    Product.class
            ).getModifiedCount();

            if (updatedCount == 0) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }

            log.info("Đã cập nhật kho cho sản phẩm {} (kích cỡ: {}, số lượng giảm: {}, đã bán tăng: {})",
                    item.getProductId(), item.getSize(), item.getQuantity(), item.getQuantity());
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

            List<Size> updatedSizes = product.getSizes().stream()
                    .map(size -> {
                        if (size.size().equals(item.getSize())) {
                            int newQuantity = size.quantity() + item.getQuantity();
                            return Size.builder()
                                    .size(size.size())
                                    .price(size.price())
                                    .compareAtPrice(size.compareAtPrice())
                                    .quantity(newQuantity)
                                    .available(true)
                                    .build();
                        }
                        return size;
                    })
                    .collect(Collectors.toList());

            // Giảm soldCount khi hủy đơn
            int currentSoldCount = product.getSoldCount() != null ? product.getSoldCount() : 0;
            int newSoldCount = Math.max(0, currentSoldCount - item.getQuantity()); // Đảm bảo không âm

            Update update = new Update()
                    .set("sizes", updatedSizes)
                    .set("soldCount", newSoldCount)  // Giảm số lượng đã bán
                    .set("version", product.getVersion() + 1);

            long updatedCount = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getProductId()).and("version").is(product.getVersion())),
                    update,
                    Product.class
            ).getModifiedCount();

            if (updatedCount == 0) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }

            log.info("Đã hoàn kho cho sản phẩm {} (kích cỡ: {}, số lượng tăng: {}, đã bán giảm: {})",
                    item.getProductId(), item.getSize(), item.getQuantity(), item.getQuantity());
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