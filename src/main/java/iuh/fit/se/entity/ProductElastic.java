package iuh.fit.se.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.OptionDef;
import iuh.fit.se.entity.records.OptionMediaGroup;
import iuh.fit.se.entity.records.Variant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.core.suggest.Completion;

import java.time.Instant;
import java.util.List;

@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch-settings.json")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductElastic {
    @Id
    private String id;

    @CompletionField(maxInputLength = 100)
    private Completion nameSuggest;

    // Trường này để lưu tên gốc của sản phẩm để hiển thị
    // và dùng cho tìm kiếm full-text
    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String originalName;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String description;

    @Field(type = FieldType.Nested)
    private List<Image> images;

    @Field(type = FieldType.Nested)
    private List<Variant> variants;
    @Field(type = FieldType.Nested)
    List<OptionDef> optionDefs;         // khai báo các trục biến thể
    @Field(type = FieldType.Nested)
    List<OptionMediaGroup> mediaByOption; // ảnh theo 1 trục (thường Color)
    @Field(type = FieldType.Double)
    private Double percentDiscount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String sellerId;
    @Field(type = FieldType.Date)
    private Instant createdAt;
    @Field(type = FieldType.Integer)
    private Integer soldCount;  // Số lượng đã bán (để thống kê)
    @Field(type = FieldType.Integer)
    private Integer viewCount;  // Lượt xem sản phẩm
}