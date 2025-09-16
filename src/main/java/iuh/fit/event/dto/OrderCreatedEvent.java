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
public class OrderCreatedEvent {
    String orderId;
    String userId;
    String userEmail;          // có thể null nếu notify-service tự gọi user-service
    String sellerId;

    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal discountAmount;
    BigDecimal totalAmount;

    String status;             // PENDING,...
    String recipientName;
    String phoneNumber;
    String shippingAddress;

    LocalDateTime createdAt;

    List<OrderItemPayload> items;
}
