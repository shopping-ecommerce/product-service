package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryStatistics {
    String categoryId;
    String categoryName;
    Long productCount;
    Long totalSold;
    Double totalRevenue;
    Long totalViews;
    Double conversionRate; // (totalSold / totalViews) * 100
}