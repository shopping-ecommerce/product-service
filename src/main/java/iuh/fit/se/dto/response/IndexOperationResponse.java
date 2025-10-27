// src/main/java/iuh/fit/se/dto/response/IndexOperationResponse.java
package iuh.fit.se.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexOperationResponse {
    private Boolean success;     // true/false
    private String product_id;   // id chuỗi
    private String message;      // ví dụ: "Successfully indexed product"
    private String error;        // nếu có lỗi, Flask trả "error"
}
