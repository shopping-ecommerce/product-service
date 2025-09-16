package iuh.fit.event.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderFailedEvent {
    private String orderId;
    private String reason;
}