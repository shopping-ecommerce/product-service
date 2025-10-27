package iuh.fit.se.dto.request;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.OptionDef;
import iuh.fit.se.entity.records.OptionMediaGroup;
import iuh.fit.se.entity.records.Variant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductUpdateRequest {
    @NotBlank(message = "Product ID must not be empty")
    String id;
    String name;
    String description;
    List<OptionDef> optionDefs;               // ["Color":["Black","White"], "Size":["M","L"]]
    List<OptionMediaGroup> mediaByOption;     // [{optionName:"Color", optionValue:"Black", image:"0|1|URL"}]
    List<Variant> variants;
    String categoryId;
    Double percentDiscount;
    Status status;
    List<Integer> removeImage;
}
