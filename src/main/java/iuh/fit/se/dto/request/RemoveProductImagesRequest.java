// src/main/java/iuh/fit/se/dto/request/RemoveProductImagesRequest.java
package iuh.fit.se.dto.request;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RemoveProductImagesRequest {
    private String product_id; // required
}
