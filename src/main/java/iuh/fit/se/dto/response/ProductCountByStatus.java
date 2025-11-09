package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductCountByStatus {
    Long available;
    Long pending;
    Long suspended;
    Long discontinued;
    Long total;
}