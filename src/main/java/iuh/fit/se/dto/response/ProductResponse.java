package iuh.fit.se.dto.response;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.Size;
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
public class ProductResponse {
    String id;
    String sellerId;
    String name;
    String description;
    List<Image> images;
    List<Size> sizes;
    Status status;
    String categoryId;
    Instant createdAt;
}
