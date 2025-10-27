package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.Map;

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
     Map<String, String> options;  // các option đã chọn, ví dụ {"Color":"Black","Size":"M"}
     String optionsLabel;
     BigDecimal price;
     BigDecimal compareAtPrice;
     Boolean available;
     Integer stock;  // quantity
     String status;  // AVAILABLE, OUT_OF_STOCK...
}
