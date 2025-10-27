package iuh.fit.se.dto.response;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.OptionDef;
import iuh.fit.se.entity.records.OptionMediaGroup;
import iuh.fit.se.entity.records.Variant;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    List<OptionDef> optionDefs;
    List<OptionMediaGroup> mediaByOption;
    List<Variant> variants;
    Integer viewCount;
    Integer soldCount;
    Status status;
    String categoryId;
    Instant createdAt;
    Instant updatedAt;
}