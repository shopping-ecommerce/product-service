// src/main/java/iuh/fit/se/dto/request/IndexSingleProductRequest.java
package iuh.fit.se.dto.request;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexSingleProductRequest {
    private String product_id;       // map đúng key Flask
    private Boolean force_reindex;   // optional
}
