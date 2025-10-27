// src/main/java/iuh/fit/se/dto/response/BasicOperationResponse.java
package iuh.fit.se.dto.response;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BasicOperationResponse {
    private Boolean success;
    private String message;
    private String error;
    private String datapoint_id; // remove-single-image trả về
}
