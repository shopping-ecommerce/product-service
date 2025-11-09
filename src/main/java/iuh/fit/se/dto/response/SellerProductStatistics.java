package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SellerProductStatistics {
    // Thống kê theo trạng thái
    ProductCountByStatus productCountByStatus;

    // Thống kê hiệu suất bán hàng
    SalesPerformance salesPerformance;

    // Thống kê kho hàng
    InventoryStatistics inventoryStatistics;

    // Thống kê theo danh mục
    List<CategoryStatistics> categoryStatistics;
}





