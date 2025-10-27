// src/main/java/iuh/fit/se/dto/response/IndexImagesOperationResponse.java
package iuh.fit.se.dto.response;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class IndexImagesOperationResponse {
    private Boolean success;
    private String product_id;
    private Integer indexed_count; // có thể null
    private Integer failed_count;  // có thể null
    private String message;
    private String error;          // nếu có
}
