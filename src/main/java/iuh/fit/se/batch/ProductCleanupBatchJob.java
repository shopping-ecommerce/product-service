package iuh.fit.se.batch;

import iuh.fit.se.dto.request.DeleteRequest;
import iuh.fit.se.dto.request.RemoveProductImagesRequest;
import iuh.fit.se.dto.request.RemoveSingleProductRequest;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.repository.ProductElasticRepository;
import iuh.fit.se.repository.httpclient.FileClient;
import iuh.fit.se.repository.httpclient.GeminiClient;
import iuh.fit.se.repository.httpclient.ReviewClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Batch job xoá vĩnh viễn các sản phẩm đã DISCONTINUED quá thời gian giữ lại.
 *
 * Cấu hình qua application.yml:
 *
 * product:
 *   cleanup:
 *     enabled: true
 *     days-before-deletion: 30
 *     cron: "0 0 2 * * *"   # 6-field cron cho Spring, 2h sáng hàng ngày
 *     batch-size: 100
 *     delete-images: true
 *
 * YÊU CẦU: Khi chuyển sang DISCONTINUED phải set deleteAt = Instant.now()
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductCleanupBatchJob {

    final ProductRepository productRepository;
    final ProductElasticRepository productElasticRepository;
    final MongoTemplate mongoTemplate;
    final FileClient fileClient;
    final GeminiClient geminiClient;
    final ReviewClient reviewClient;
    // --- Config bind từ application.yml ---
    @Value("${product.cleanup.enabled:true}")
    boolean enabled;

    @Value("${product.cleanup.days-before-deletion:30}")
    int daysBeforeDeletion;

    @Value("${product.cleanup.batch-size:100}")
    int batchSize;

    @Value("${product.cleanup.delete-images:true}")
    boolean deleteImages;

    /**
     * Lịch chạy đọc từ product.cleanup.cron
     * Mặc định mỗi 2 phút (test) nếu không set.
     * Nên set zone Asia/Ho_Chi_Minh để khớp giờ VN.
     */
    @Scheduled(cron = "${product.cleanup.cron:0 */2 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void cleanupDiscontinuedProducts() {
        if (!enabled) {
            log.info("[ProductCleanup] Disabled -> skip");
            return;
        }

        Instant cutoff = Instant.now().minus(daysBeforeDeletion, ChronoUnit.DAYS);
        log.info("[ProductCleanup] START | cutoff={}, daysBeforeDeletion={}, batchSize={}, deleteImages={}",
                cutoff, daysBeforeDeletion, batchSize, deleteImages);

        int totalDeleted = 0;

        while (true) {
            Query q = new Query(new Criteria().andOperator(
                    Criteria.where("status").is(Status.DISCONTINUED),
                    Criteria.where("deleteAt").exists(true).lte(cutoff)
            )).limit(batchSize);

            List<Product> batch = mongoTemplate.find(q, Product.class);
            if (batch.isEmpty()) break;

            for (Product p : batch) {
                try {
                    permanentlyDeleteProduct(p);
                    totalDeleted++;
                } catch (Exception ex) {
                    log.error("[ProductCleanup] Failed to delete product {}: {}", p.getId(), ex.getMessage());
                }
            }

            // nếu lần này lấy < batchSize -> khả năng không còn nữa, thoát
            if (batch.size() < batchSize) break;
        }

        log.info("[ProductCleanup] DONE | totalDeleted={}", totalDeleted);
    }

    private void permanentlyDeleteProduct(Product product) {
        String id = product.getId();
        // xoá review trước để thu gom ảnh review (nếu muốn)
        try {
            reviewClient.deleteByProduct(id); // hoặc reviewClient...
            log.info("  ✓ Deleted reviews for product {}", id);
        } catch (Exception e) {
            log.warn("  ⚠ Delete reviews failed for {}: {}", id, e.getMessage());
        }
        log.info("[ProductCleanup] Deleting product {} - {}", id, product.getName());

        // 1) Xoá ảnh S3 (tuỳ chọn)
        if (deleteImages && product.getImages() != null && !product.getImages().isEmpty()) {
            List<String> urls = product.getImages().stream()
                    .map(Image::url)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!urls.isEmpty()) {
                try {
                    fileClient.deleteByUrl(DeleteRequest.builder().urls(urls).build());
                    log.info("  ✓ Deleted {} image(s) from file-service/S3", urls.size());
                } catch (Exception e) {
                    log.warn("  ⚠ Delete images failed: {}", e.getMessage());
                }
            }
        }

        // 2) Xoá index trên Gemini (idempotent)
        try {
            geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder().product_id(id).build());
            geminiClient.removeProductImages(RemoveProductImagesRequest.builder().product_id(id).build());
            log.info("  ✓ Removed from Gemini index");
        } catch (Exception e) {
            log.warn("  ⚠ Remove from Gemini failed: {}", e.getMessage());
        }

        // 3) Xoá index Elasticsearch (phòng trường hợp trước đó chưa xoá)
        try {
            productElasticRepository.deleteById(id);
            log.info("  ✓ Deleted Elasticsearch index for product {}", id);
        } catch (Exception e) {
            log.warn("  ⚠ Delete Elasticsearch index failed for {}: {}", id, e.getMessage());
        }

        // 4) Xoá document Mongo
        productRepository.deleteById(id);
        log.info("  ✓ Deleted from MongoDB");
    }
}
