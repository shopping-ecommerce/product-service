package iuh.fit.se.dto.request;

import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.OptionDef;
import iuh.fit.se.entity.records.OptionMediaGroup;
import iuh.fit.se.entity.records.Variant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

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

    @NotNull(message = "Option definitions must not be null")
    @NotEmpty(message = "Option definitions must not be empty")
    List<OptionDef> optionDefs;  // Ví dụ: [{"name":"Color","values":["Black","White"]}, {"name":"Size","values":["M","L"]}]

    @NotNull(message = "Variants must not be null")
    @NotEmpty(message = "Variants must not be empty")
    List<Variant> variants;  // Các biến thể với giá, số lượng

    // Mapping giữa option value và thứ tự ảnh
    // Ví dụ: [{"optionName":"Color","optionValue":"Black","imageIndex":0}]
    List<OptionMediaGroup> mediaByOption;

    @NotBlank(message = "Category ID must not be empty")
    String categoryId;
    Status status;
}