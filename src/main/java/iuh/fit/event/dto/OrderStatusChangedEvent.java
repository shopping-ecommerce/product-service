    package iuh.fit.event.dto;

    import lombok.*;
    import lombok.experimental.FieldDefaults;

    import java.math.BigDecimal;
    import java.time.LocalDateTime;
    import java.util.List;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public class OrderStatusChangedEvent {
        String orderId;
        String userId;
        String userEmail;
        String sellerId;
    
        BigDecimal subtotal;
        BigDecimal shippingFee;
        BigDecimal discountAmount;
        BigDecimal totalAmount;

        String voucherCode;
        String status;
        String recipientName;
        String phoneNumber;
        String shippingAddress;
        String reason;
        LocalDateTime createdAt;
    
        List<OrderItemPayload> items;
    }
