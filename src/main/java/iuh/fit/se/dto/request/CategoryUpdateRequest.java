package iuh.fit.se.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryUpdateRequest {
    @NotBlank(message = "Category ID not be empty")
    String categoryId;
    @Size(max = 100, message = "Name must be <= 100 characters")
    String name;
    @Size(max = 500, message = "Description must be <= 500 characters")
    String description;
}
