package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAlertDto {
    String productId;
    String productName;
    String imageUrl;
    Integer currentStock;
    Long daysSinceLastSold;
    String alertType; // LOW_STOCK, OUT_OF_STOCK, SLOW_MOVING
    String message;
}