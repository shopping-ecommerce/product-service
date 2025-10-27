package iuh.fit.event.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemPayload {
    String productId;
    String productName;
    Map<String, String> options;
    String orderId;
    Integer quantity;
    BigDecimal subTotal;
    BigDecimal unitPrice;
}