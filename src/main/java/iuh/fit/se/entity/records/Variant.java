package iuh.fit.se.entity.records;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@Builder
public record Variant(
        Map<String, String> options, // ví dụ {"Dung tích":"500ml"} hoặc {"RAM":"8GB","Storage":"256GB"}
        BigDecimal price,
        BigDecimal compareAtPrice,
        Integer quantity,
        Boolean available
) {}
