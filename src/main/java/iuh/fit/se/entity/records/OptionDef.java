package iuh.fit.se.entity.records;
import lombok.Builder;
import java.util.List;
@Builder
public record OptionDef(
        String name,              // ví dụ: "Dung tích", "Vị", "RAM", "Storage"
        List<String> values       // ["250ml","500ml"] hoặc ["Dâu","Matcha","Socola"]
) {}