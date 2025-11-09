package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SalesPerformance {
    Long totalViews;
    Long totalSold;
    Double estimatedRevenue;
    List<TopProductDto> topSellingProducts;
    List<TopProductDto> topViewedProducts;
}
