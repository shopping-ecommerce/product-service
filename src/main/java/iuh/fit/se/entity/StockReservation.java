package iuh.fit.se.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("stock_reservations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockReservation {
    @Id
    private String id;
    private String userId;

    @Builder.Default
    private List<Item> items = List.of();

    @Indexed
    private Instant expiresAt; // để cron quét nhanh

    @Builder.Default
    private Status status = Status.PENDING; // PENDING | CONFIRMED | RELEASED | EXPIRED

    private Instant createdAt;

    public enum Status { PENDING, CONFIRMED, RELEASED, EXPIRED; }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private String productId;
        private Map<String,String> options; // match variant bằng options
        private Integer qty;
    }
}