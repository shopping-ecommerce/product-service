// src/main/java/iuh/fit/se/dto/request/UpsertSingleImageJsonRequest.java
package iuh.fit.se.dto.request;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpsertSingleImageJsonRequest {
    private String product_id;   // required
    private Integer position;    // required
    private String image_url;    // required nếu không upload file
}
