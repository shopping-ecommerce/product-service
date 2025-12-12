// src/main/java/iuh/fit/event/dto/ProductRemoveGeminiEvent.java
package iuh.fit.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRemoveGeminiEvent {
    private String productId;
}