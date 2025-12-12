// src/main/java/iuh/fit/se/service/impl/ProductIndexConsumer.java
package iuh.fit.se.service.impl;

import iuh.fit.event.dto.ProductIndexEvent;
import iuh.fit.event.dto.ProductRemoveGeminiEvent;
import iuh.fit.se.dto.request.*;
import iuh.fit.se.repository.httpclient.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductIndexConsumer {

    private final GeminiClient geminiClient;
    private final ExecutorService geminiExecutor;

    @KafkaListener(topics = "product-index-request", groupId = "product-index-group")
    public void handleIndexRequest(ProductIndexEvent event) {
        log.info("Received INDEX request for product {}", event.getProductId());
        geminiExecutor.submit(() -> processIndex(event));
    }

    @KafkaListener(topics = "product-remove-gemini-request", groupId = "product-index-group")
    public void handleRemoveRequest(ProductRemoveGeminiEvent event) {
        log.info("Received REMOVE request for product {}", event.getProductId());
        geminiExecutor.submit(() -> processRemove(event));
    }

    private void processIndex(ProductIndexEvent event) {
        try {
            geminiClient.indexSingleProduct(IndexSingleProductRequest.builder()
                    .product_id(event.getProductId())
                    .force_reindex(event.isForceReindex())
                    .build());
            geminiClient.indexSingleProductImages(IndexSingleProductImagesRequest.builder()
                    .product_id(event.getProductId())
                    .force_reindex(event.isForceReindex())
                    .build());
            log.info("Successfully indexed product {} via Kafka", event.getProductId());
        } catch (Exception e) {
            log.error("Failed to index product {}: {}", event.getProductId(), e.getMessage(), e);
        }
    }

    private void processRemove(ProductRemoveGeminiEvent event) {
        try {
            geminiClient.removeSingleProduct(RemoveSingleProductRequest.builder()
                    .product_id(event.getProductId())
                    .build());
            geminiClient.removeProductImages(RemoveProductImagesRequest.builder()
                    .product_id(event.getProductId())
                    .build());
            log.info("Successfully removed product {} from Gemini via Kafka", event.getProductId());
        } catch (Exception e) {
            log.error("Failed to remove product {} from Gemini: {}", event.getProductId(), e.getMessage(), e);
        }
    }
}