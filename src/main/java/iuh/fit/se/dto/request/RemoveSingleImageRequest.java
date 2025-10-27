// src/main/java/iuh/fit/se/dto/request/RemoveSingleImageRequest.java
package iuh.fit.se.dto.request;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RemoveSingleImageRequest {
    private String datapoint_id; // optional
    private String product_id;   // optional (cần với position)
    private Integer position;    // optional
}
