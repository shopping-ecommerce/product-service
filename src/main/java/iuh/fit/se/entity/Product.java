package iuh.fit.se.entity;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "products")
public class Product {
    @Id
    String id;

    @Indexed
    String sellerId;

    @TextIndexed
    String name;

    String description;

    List<Image> images;
    Integer soldCount;  // Số lượng đã bán (để thống kê)
    Integer viewCount;  // Lượt xem sản phẩm
    List<Size> sizes;
    @Min(value = 0, message = "Giảm giá không được âm")
    @Max(value = 100, message = "Giảm giá không được vượt quá 100%")
    Double percentDiscount;

    Status status;

    @Indexed
    String categoryId;

    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;

    Instant deleteAt;

    String reasonDelete;

    @Version
    Long version;
}