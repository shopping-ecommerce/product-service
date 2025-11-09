package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VariantAlertDto {
    String productId;
    String productName;
    String imageUrl;
    Map<String, String> variantOptions; // {"Color": "Red", "Size": "M"}
    String variantLabel;                // "Color Red - Size M"
    Integer currentStock;
    String alertType;
    String message;
}
