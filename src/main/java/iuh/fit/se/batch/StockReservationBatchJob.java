package iuh.fit.se.batch;

import iuh.fit.se.service.StockReservationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Batch job để tự động expire các reservation đã hết hạn
 * Chạy mỗi 5 phút để kiểm tra
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockReservationBatchJob {

    final StockReservationService reservationService;

    @Value("${stock.reservation.cleanup.enabled:true}")
    boolean enabled;

    /**
     * Chạy mỗi 5 phút để expire các reservation hết hạn
     * Cron: "0 *5 * * * *" = phút 0 của mỗi 5 phút */
    @Scheduled(cron = "${stock.reservation.cleanup.cron:0 */5 * * * *}")
    public void expireReservations() {
        if (!enabled) {
            log.debug("[StockReservation] Cleanup disabled -> skip");
            return;
        }

        log.info("[StockReservation] Starting cleanup of expired reservations...");

        try {
            reservationService.expireReservations();
            log.info("[StockReservation] Cleanup completed successfully");
        } catch (Exception e) {
            log.error("[StockReservation] Cleanup failed: {}", e.getMessage(), e);
        }
    }
}