package iuh.fit.se.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveStockRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotEmpty(message = "Items cannot be empty")
    private List<ReservationItem> items;

    private Integer expirationMinutes; // Thời gian hết hạn (mặc định 15 phút)

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationItem {
        @NotBlank(message = "Product ID is required")
        private String productId;

        private Map<String, String> options; // Variant options

        private Integer quantity;
    }
}