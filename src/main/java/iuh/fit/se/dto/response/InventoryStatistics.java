package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryStatistics {
    Long totalStockQuantity;
//    Long totalVariants; // Tổng số variants
    Long lowStockVariants; // Số variants sắp hết hàng
    Long outOfStockVariants; // Số variants hết hàng
//    Double averageVariantsPerProduct;
    List<VariantAlertDto> lowStockAlerts; // Cảnh báo theo từng variant
    List<VariantAlertDto> outOfStockAlerts; // Variants hết hàng
    List<ProductAlertDto> slowMovingProducts; // Sản phẩm tồn kho lâu
}
