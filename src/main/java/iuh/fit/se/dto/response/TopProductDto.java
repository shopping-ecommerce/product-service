package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopProductDto {
    String productId;
    String productName;
    String imageUrl;
    Long soldCount;
    Long viewCount;
    Double revenue;
    Integer stockQuantity;
}
