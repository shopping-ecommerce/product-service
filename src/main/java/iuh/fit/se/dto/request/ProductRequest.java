package iuh.fit.se.dto.request;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;

import java.time.Instant;
import java.util.List;

@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    @NotBlank(message = "Seller ID must not be empty")
    String sellerId;
    @NotBlank(message = "Name must not be empty")
    String name;
    @NotBlank(message = "Description must not be empty")
    String description;
    List<Image> images;
    @NotEmpty(message = "Sizes must not be empty")
    List<Size> sizes;
    String categoryId;
    Double percentDiscount;
    Status status;
}