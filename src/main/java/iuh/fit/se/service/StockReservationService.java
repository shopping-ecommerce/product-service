package iuh.fit.se.service;

import iuh.fit.se.dto.request.ReserveStockRequest;
import iuh.fit.se.entity.StockReservation;

public interface StockReservationService {

    /**
     * Đặt chỗ số lượng sản phẩm khi bắt đầu thanh toán
     * @param request Thông tin đơn hàng và sản phẩm cần đặt chỗ
     * @return StockReservation đã tạo
     */
    StockReservation reserveStock(ReserveStockRequest request);

    /**
     * Xác nhận đơn hàng thành công -> chuyển reservation sang CONFIRMED
     * @param userId ID thanh toán (vnp_TxnRef)
     */
    void confirmReservation(String userId);

    /**
     * Huỷ/giải phóng số lượng đã đặt chỗ khi thanh toán thất bại
     * @param userId ID thanh toán
     */
    void releaseReservation(String userId);

    /**
     * Kiểm tra và giải phóng các reservation đã hết hạn
     * (Chạy bằng cron job)
     */
    void expireReservations();
}