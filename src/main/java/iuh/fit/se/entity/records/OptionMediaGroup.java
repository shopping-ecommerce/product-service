package iuh.fit.se.entity.records;

import lombok.Builder;

import java.util.List;

@Builder
public record OptionMediaGroup(
        String optionName,     // ví dụ "Color"
        String optionValue,    // "Black"
        String image
) {}
