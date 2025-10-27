// src/main/java/iuh/fit/se/dto/response/RemoveImagesOperationResponse.java
package iuh.fit.se.dto.response;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RemoveImagesOperationResponse {
    private Boolean success;
    private String product_id;
    private Integer removed_count;
    private String message;
    private String error;
}
