// src/main/java/iuh/fit/se/dto/request/UpsertSingleProductRequest.java
package iuh.fit.se.dto.request;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertSingleProductRequest {
    private String product_id; // map đúng key Flask
}
