package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    String name;
    String description;
}
