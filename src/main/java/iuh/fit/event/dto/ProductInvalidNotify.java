package iuh.fit.event.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductInvalidNotify {
    String productId;
    String productName;
    String reason;
    String email;
}