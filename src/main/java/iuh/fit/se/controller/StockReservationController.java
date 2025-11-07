package iuh.fit.se.controller;

import iuh.fit.se.dto.request.ReserveStockRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.entity.StockReservation;
import iuh.fit.se.service.StockReservationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class StockReservationController {

    StockReservationService reservationService;

    /**
     * API để tạo reservation khi user bắt đầu thanh toán
     * Gọi API này trước khi redirect sang VNPay
     */
    @PostMapping("/reserve")
    public ApiResponse<StockReservation> reserveStock(
            @Valid @RequestBody ReserveStockRequest request
    ) {
        log.info("Creating stock reservation for payment intent: {}", request.getUserId());

        StockReservation reservation = reservationService.reserveStock(request);

        return ApiResponse.<StockReservation>builder()
                .code(200)
                .message("Stock reserved successfully")
                .result(reservation)
                .build();
    }

    /**
     * API để confirm reservation khi thanh toán thành công
     * Gọi trong callback từ VNPay (IPN) hoặc return URL
     */
    @PostMapping("/confirm/{userId}")
    public ApiResponse<Void> confirmReservation(
            @PathVariable("userId") String userId
    ) {
        log.info("Confirming reservation for payment intent: {}",userId);

        reservationService.confirmReservation(userId);

        return ApiResponse.<Void>builder()
                .code(200)
                .message("Reservation confirmed successfully")
                .build();
    }

    /**
     * API để release reservation khi thanh toán thất bại hoặc user cancel
     */
    @PostMapping("/release/{paymentIntentId}")
    public ApiResponse<Void> releaseReservation(
            @PathVariable("paymentIntentId") String paymentIntentId
    ) {
        log.info("Releasing reservation for payment intent: {}", paymentIntentId);

        reservationService.releaseReservation(paymentIntentId);

        return ApiResponse.<Void>builder()
                .code(200)
                .message("Reservation released successfully")
                .build();
    }

    /**
     * API trigger manual cleanup (dành cho testing/admin)
     */
    @PostMapping("/expire")
    public ApiResponse<Void> expireReservations() {
        log.info("Manual trigger: expiring reservations");

        reservationService.expireReservations();

        return ApiResponse.<Void>builder()
                .code(200)
                .message("Expired reservations cleaned up successfully")
                .build();
    }
}