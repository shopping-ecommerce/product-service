// src/main/java/iuh/fit/se/dto/request/RemoveSingleProductRequest.java
package iuh.fit.se.dto.request;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemoveSingleProductRequest {
    private String product_id; // map đúng key Flask
}
