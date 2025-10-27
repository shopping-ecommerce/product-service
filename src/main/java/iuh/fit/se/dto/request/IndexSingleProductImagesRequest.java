// src/main/java/iuh/fit/se/dto/request/IndexSingleProductImagesRequest.java
package iuh.fit.se.dto.request;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class IndexSingleProductImagesRequest {
    private String product_id;        // required
    private Boolean force_reindex;    // optional
}
