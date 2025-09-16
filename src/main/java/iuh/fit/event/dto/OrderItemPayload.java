package iuh.fit.event.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemPayload {
    String productId;
    String productName;
    String size;
    String orderId;
    Integer quantity;
    BigDecimal subTotal;
    BigDecimal unitPrice;
}