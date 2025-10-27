// src/main/java/iuh/fit/se/dto/response/UpsertSingleImageResponse.java
package iuh.fit.se.dto.response;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpsertSingleImageResponse {
    private Boolean success;
    private String datapoint_id;
    private String product_id;
    private Integer position;
    private String message;
    private String error;
}
