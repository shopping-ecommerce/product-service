    package iuh.fit.se.entity;

    import iuh.fit.se.entity.enums.Status;
    import iuh.fit.se.entity.records.Image;
    import iuh.fit.se.entity.records.OptionDef;
    import iuh.fit.se.entity.records.OptionMediaGroup;
    import iuh.fit.se.entity.records.Variant;
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
        List<OptionDef> optionDefs;         // khai báo các trục biến thể
        List<OptionMediaGroup> mediaByOption; // ảnh theo 1 trục (thường Color)
        List<Variant> variants;
        Integer viewCount;  // Lượt xem sản phẩm
        Integer soldCount;  // Số lượng đã bán (để thống kê)

        boolean reUpdate;
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