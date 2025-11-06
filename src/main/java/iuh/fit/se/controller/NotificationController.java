package iuh.fit.se.controller;

import iuh.fit.event.dto.OrderCreatedEvent;
import iuh.fit.event.dto.OrderStatusChangedEvent;
import iuh.fit.event.dto.ProductInvalid;
import iuh.fit.se.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.RestController;

@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class NotificationController {
    ProductService productService;

    @KafkaListener(topics = "create-order")
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Nhận được sự kiện create-order cho orderId: {}", event.getOrderId());
        try {
            productService.updateStockFromOrder(event);
            log.info("Đã cập nhật kho cho đơn hàng: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật kho cho đơn hàng {}: {}", event.getOrderId(), e.getMessage());
            // TODO: Gửi sự kiện thất bại đến topic "order-failed" nếu cần
        }
    }

    @KafkaListener(topics = "order-updated", groupId = "product-service-group", concurrency = "1")
    public void handleOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        log.info("Nhận được sự kiện order-updated cho orderId: {}", event.getOrderId());
        try {
            productService.restoreStockFromOrder(event);
            log.info("Đã xử lý hoàn kho cho đơn hàng: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Lỗi khi hoàn kho cho đơn hàng {}: {}", event.getOrderId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "user-cancel-order", groupId = "product-service-group", concurrency = "1")
    public void handleUserCancelChangedEvent(OrderStatusChangedEvent event) {
        log.info("Nhận được sự kiện user-cancel-order cho orderId: {}", event.getOrderId());
        try {
            productService.restoreStockFromOrder(event);
            log.info("Đã xử lý hoàn kho cho đơn hàng: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Lỗi khi hoàn kho cho đơn hàng {}: {}", event.getOrderId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "product-invalid", groupId = "product-service-group", concurrency = "1")
    public void handleProductInvalidEvent(ProductInvalid productInvalid) {
        log.info("Nhận được sự kiện product-invalid cho productId: {}", productInvalid.getProductId());
        try {
            productService.deleteProduct(productInvalid);
//            productService.suspendProduct(productInvalid.getProductId(), "Sản phẩm vi phạm chính sách");
            log.info("Đã đánh dấu sản phẩm là không hoạt động: {}", productInvalid.getProductId());
        } catch (Exception e) {
            log.error("Lỗi khi đánh dấu sản phẩm không hoạt động {}: {}", productInvalid.getProductId(), e.getMessage());
        }
    }

}
