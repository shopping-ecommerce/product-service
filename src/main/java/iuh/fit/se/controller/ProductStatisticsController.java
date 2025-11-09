package iuh.fit.se.controller;

import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.SellerProductStatistics;
import iuh.fit.se.service.ProductStatisticsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/statistics")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class ProductStatisticsController {

    ProductStatisticsService productStatisticsService;

    /**
     * Lấy thống kê sản phẩm cho seller
     *
     * @param sellerId ID của seller
     * @return Thống kê chi tiết về sản phẩm
     */
    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerProductStatistics> getSellerStatistics(
            @PathVariable("sellerId") String sellerId
    ) {
        log.info("Fetching statistics for seller: {}", sellerId);

        return ApiResponse.<SellerProductStatistics>builder()
                .code(200)
                .message("Statistics fetched successfully")
                .result(productStatisticsService.getSellerStatistics(sellerId))
                .build();
    }

    /**
     * Lấy thống kê sản phẩm cho seller với tùy chỉnh ngưỡng
     *
     * @param sellerId ID của seller
     * @param lowStockThreshold Ngưỡng cảnh báo tồn kho thấp (mặc định: 10)
     * @param slowMovingDays Số ngày coi là tồn kho lâu (mặc định: 30)
     * @return Thống kê chi tiết về sản phẩm
     */
    @GetMapping("/seller/{sellerId}/custom")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerProductStatistics> getSellerStatisticsCustom(
            @PathVariable("sellerId") String sellerId,
            @RequestParam(value = "lowStockThreshold", defaultValue = "10") Integer lowStockThreshold,
            @RequestParam(value = "slowMovingDays", defaultValue = "30") Integer slowMovingDays
    ) {
        log.info("Fetching custom statistics for seller: {}, lowStockThreshold={}, slowMovingDays={}",
                sellerId, lowStockThreshold, slowMovingDays);

        return ApiResponse.<SellerProductStatistics>builder()
                .code(200)
                .message("Statistics fetched successfully")
                .result(productStatisticsService.getSellerStatistics(sellerId, lowStockThreshold, slowMovingDays))
                .build();
    }
}