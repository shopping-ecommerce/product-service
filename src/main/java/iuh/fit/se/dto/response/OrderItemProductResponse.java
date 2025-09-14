package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemProductResponse {
     String productId;
     String sellerId;
     String name;
     String image;   // chỉ lấy ảnh đầu tiên
     String size;    // size user chọn
     BigDecimal price;
     BigDecimal compareAtPrice;
     Boolean available;
     Integer stock;  // quantity
     String status;  // AVAILABLE, OUT_OF_STOCK...
}
