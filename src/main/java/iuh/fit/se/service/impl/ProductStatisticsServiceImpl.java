package iuh.fit.se.service.impl;

import iuh.fit.se.dto.response.*;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Variant;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.service.ProductStatisticsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class ProductStatisticsServiceImpl implements ProductStatisticsService {

    ProductRepository productRepository;

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    private static final int DEFAULT_SLOW_MOVING_DAYS = 30;

    @Override
    public SellerProductStatistics getSellerStatistics(String sellerId) {
        return getSellerStatistics(sellerId, DEFAULT_LOW_STOCK_THRESHOLD, DEFAULT_SLOW_MOVING_DAYS);
    }

    @Override
    public SellerProductStatistics getSellerStatistics(String sellerId, Integer lowStockThreshold, Integer slowMovingDays) {
        log.info("Calculating statistics for seller: {}", sellerId);

        if (sellerId == null || sellerId.isEmpty()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Lấy tất cả sản phẩm của seller
        List<Product> allProducts = productRepository.findBySellerId(sellerId);

        if (allProducts.isEmpty()) {
            log.warn("No products found for seller: {}", sellerId);
            return buildEmptyStatistics();
        }

        // 1. Thống kê theo trạng thái
        ProductCountByStatus countByStatus = calculateProductCountByStatus(allProducts);

        // 2. Thống kê hiệu suất bán hàng
        SalesPerformance salesPerformance = calculateSalesPerformance(allProducts);

        // 3. Thống kê kho hàng
        InventoryStatistics inventoryStatistics = calculateInventoryStatistics(
                allProducts,
                lowStockThreshold,
                slowMovingDays
        );

        // 4. Thống kê theo danh mục
        List<CategoryStatistics> categoryStatistics = calculateCategoryStatistics(allProducts);

        return SellerProductStatistics.builder()
                .productCountByStatus(countByStatus)
                .salesPerformance(salesPerformance)
                .inventoryStatistics(inventoryStatistics)
                .categoryStatistics(categoryStatistics)
                .build();
    }

    /**
     * 1. Thống kê theo trạng thái sản phẩm
     */
    private ProductCountByStatus calculateProductCountByStatus(List<Product> products) {
        Map<Status, Long> statusCounts = products.stream()
                .collect(Collectors.groupingBy(Product::getStatus, Collectors.counting()));

        return ProductCountByStatus.builder()
                .available(statusCounts.getOrDefault(Status.AVAILABLE, 0L))
                .pending(statusCounts.getOrDefault(Status.PENDING, 0L))
                .suspended(statusCounts.getOrDefault(Status.SUSPENDED, 0L))
                .discontinued(statusCounts.getOrDefault(Status.DISCONTINUED, 0L))
                .total((long) products.size())
                .build();
    }

    /**
     * 2. Thống kê hiệu suất bán hàng
     */
    private SalesPerformance calculateSalesPerformance(List<Product> products) {
        // Tính tổng lượt xem và số lượng đã bán
        long totalViews = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .mapToLong(p -> p.getViewCount() != null ? p.getViewCount() : 0)
                .sum();

        long totalSold = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .mapToLong(p -> p.getSoldCount() != null ? p.getSoldCount() : 0)
                .sum();

        // Tính doanh thu ước tính
        double estimatedRevenue = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .mapToDouble(this::calculateProductRevenue)
                .sum();

        // Top 10 sản phẩm bán chạy nhất
        List<TopProductDto> topSelling = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .filter(p -> p.getSoldCount() != null && p.getSoldCount() > 0)
                .sorted((p1, p2) -> Integer.compare(
                        p2.getSoldCount() != null ? p2.getSoldCount() : 0,
                        p1.getSoldCount() != null ? p1.getSoldCount() : 0
                ))
                .limit(10)
                .map(this::convertToTopProductDto)
                .collect(Collectors.toList());

        // Top 10 sản phẩm được xem nhiều nhất
        List<TopProductDto> topViewed = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .filter(p -> p.getViewCount() != null && p.getViewCount() > 0)
                .sorted((p1, p2) -> Integer.compare(
                        p2.getViewCount() != null ? p2.getViewCount() : 0,
                        p1.getViewCount() != null ? p1.getViewCount() : 0
                ))
                .limit(10)
                .map(this::convertToTopProductDto)
                .collect(Collectors.toList());

        return SalesPerformance.builder()
                .totalViews(totalViews)
                .totalSold(totalSold)
                .estimatedRevenue(estimatedRevenue)
                .topSellingProducts(topSelling)
                .topViewedProducts(topViewed)
                .build();
    }

    /**
     * 3. Thống kê kho hàng (theo VARIANT)
     */
    private InventoryStatistics calculateInventoryStatistics(
            List<Product> products,
            int lowStockThreshold,
            int slowMovingDays) {

        // Tổng số lượng tồn kho
        long totalStockQuantity = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .filter(p -> p.getVariants() != null)
                .flatMap(p -> p.getVariants().stream())
                .mapToLong(v -> v.quantity() != null ? v.quantity() : 0)
                .sum();

        // Tổng số variants
//        long totalVariants = products.stream()
//                .filter(p -> p.getStatus() == Status.AVAILABLE)
//                .filter(p -> p.getVariants() != null)
//                .mapToLong(p -> p.getVariants().size())
//                .sum();

        // Đếm variants và tạo alerts theo từng variant
        long lowStockVariantsCount = 0;
        long outOfStockVariantsCount = 0;
        List<VariantAlertDto> lowStockAlerts = new ArrayList<>();
        List<VariantAlertDto> outOfStockAlerts = new ArrayList<>();

        for (Product product : products) {
            if (product.getVariants() == null || product.getVariants().isEmpty()) {
                continue;
            }

            // Duyệt qua TỪNG VARIANT
            for (Variant variant : product.getVariants()) {
                int quantity = variant.quantity() != null ? variant.quantity() : 0;

                if (quantity == 0) {
                    outOfStockVariantsCount++;
                    outOfStockAlerts.add(createVariantAlert(
                            product,
                            variant,
                            quantity,
                            "OUT_OF_STOCK",
                            "Biến thể đã hết hàng"
                    ));
                } else if (quantity <= lowStockThreshold) {
                    lowStockVariantsCount++;
                    lowStockAlerts.add(createVariantAlert(
                            product,
                            variant,
                            quantity,
                            "LOW_STOCK",
                            String.format("Biến thể sắp hết hàng (còn %d)", quantity)
                    ));
                }
            }
        }

        // Tính số biến thể trung bình
//        double avgVariants = products.stream()
//                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
//                .mapToInt(p -> p.getVariants().size())
//                .average()
//                .orElse(0.0);

        // Sản phẩm tồn kho lâu chưa bán (vẫn tính theo sản phẩm)
        List<ProductAlertDto> slowMovingProducts = findSlowMovingProducts(products, slowMovingDays);

        return InventoryStatistics.builder()
                .totalStockQuantity(totalStockQuantity)
//                .totalVariants(totalVariants)
                .lowStockVariants(lowStockVariantsCount)
                .outOfStockVariants(outOfStockVariantsCount)
//                .averageVariantsPerProduct(Math.round(avgVariants * 100.0) / 100.0)
                .lowStockAlerts(lowStockAlerts)
                .outOfStockAlerts(outOfStockAlerts)
                .slowMovingProducts(slowMovingProducts)
                .build();
    }

    /**
     * 4. Thống kê theo danh mục
     */
    private List<CategoryStatistics> calculateCategoryStatistics(List<Product> products) {
        Map<String, List<Product>> productsByCategory = products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getCategoryId));

        List<CategoryStatistics> categoryStats = new ArrayList<>();

        for (Map.Entry<String, List<Product>> entry : productsByCategory.entrySet()) {
            String categoryId = entry.getKey();
            List<Product> categoryProducts = entry.getValue();

            long productCount = categoryProducts.size();

            long totalSold = categoryProducts.stream()
                    .mapToLong(p -> p.getSoldCount() != null ? p.getSoldCount() : 0)
                    .sum();

            double totalRevenue = categoryProducts.stream()
                    .mapToDouble(this::calculateProductRevenue)
                    .sum();

            long totalViews = categoryProducts.stream()
                    .mapToLong(p -> p.getViewCount() != null ? p.getViewCount() : 0)
                    .sum();

            double conversionRate = totalViews > 0
                    ? Math.round((totalSold * 100.0 / totalViews) * 100.0) / 100.0
                    : 0.0;

            categoryStats.add(CategoryStatistics.builder()
                    .categoryId(categoryId)
                    .categoryName(categoryId) // TODO: Lấy tên từ CategoryService nếu có
                    .productCount(productCount)
                    .totalSold(totalSold)
                    .totalRevenue(Math.round(totalRevenue * 100.0) / 100.0)
                    .totalViews(totalViews)
                    .conversionRate(conversionRate)
                    .build());
        }

        // Sắp xếp theo doanh thu cao nhất
        categoryStats.sort((c1, c2) -> Double.compare(c2.getTotalRevenue(), c1.getTotalRevenue()));

        return categoryStats;
    }

    // ========== Helper Methods ==========

    /**
     * Tính doanh thu ước tính của 1 sản phẩm
     */
    private double calculateProductRevenue(Product product) {
        if (product.getSoldCount() == null || product.getSoldCount() == 0) {
            return 0.0;
        }

        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0.0;
        }

        // Tính giá trung bình của các variants
        double avgPrice = product.getVariants().stream()
                .filter(v -> v.price() != null)
                .mapToDouble(v -> {
                    try {
                        return Double.parseDouble(String.valueOf(v.price()));
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                })
                .average()
                .orElse(0.0);

        return avgPrice * product.getSoldCount();
    }

    /**
     * Convert Product -> TopProductDto
     */
    private TopProductDto convertToTopProductDto(Product product) {
        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).url()
                : null;

        int totalStock = product.getVariants() != null
                ? product.getVariants().stream()
                .mapToInt(v -> v.quantity() != null ? v.quantity() : 0)
                .sum()
                : 0;

        return TopProductDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(imageUrl)
                .soldCount(product.getSoldCount() != null ? product.getSoldCount().longValue() : 0L)
                .viewCount(product.getViewCount() != null ? product.getViewCount().longValue() : 0L)
                .revenue(Math.round(calculateProductRevenue(product) * 100.0) / 100.0)
                .stockQuantity(totalStock)
                .build();
    }

    /**
     * Tạo alert cho variant cụ thể
     */
    private VariantAlertDto createVariantAlert(Product product, Variant variant, int quantity, String alertType, String message) {
        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).url()
                : null;

        // Tạo label cho variant: "Màu Đỏ - Size M"
        String variantLabel = formatVariantLabel(variant.options());

        return VariantAlertDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(imageUrl)
                .variantOptions(variant.options())
                .variantLabel(variantLabel)
                .currentStock(quantity)
                .alertType(alertType)
                .message(message)
                .build();
    }

    /**
     * Format variant options thành label dễ đọc
     * VD: {"Color": "Red", "Size": "M"} -> "Màu Red - Size M"
     */
    private String formatVariantLabel(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return "Không có biến thể";
        }

        return options.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(" - "));
    }

    /**
     * Tạo alert cho sản phẩm (dùng cho slow-moving)
     */
    private ProductAlertDto createProductAlert(Product product, int currentStock, String alertType, String message) {
        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).url()
                : null;

        return ProductAlertDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(imageUrl)
                .currentStock(currentStock)
                .alertType(alertType)
                .message(message)
                .build();
    }

    /**
     * Tìm sản phẩm tồn kho lâu chưa bán
     */
    private List<ProductAlertDto> findSlowMovingProducts(List<Product> products, int days) {
        Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);

        return products.stream()
                .filter(p -> p.getStatus() == Status.AVAILABLE)
                .filter(p -> {
                    // Có tồn kho
                    int stock = p.getVariants() != null
                            ? p.getVariants().stream().mapToInt(v -> v.quantity() != null ? v.quantity() : 0).sum()
                            : 0;
                    if (stock == 0) return false;

                    // Không bán được hoặc bán rất ít
                    int soldCount = p.getSoldCount() != null ? p.getSoldCount() : 0;
                    if (soldCount > 5) return false; // Nếu bán > 5 thì không coi là slow-moving

                    // Đã tồn tại lâu
                    return p.getCreatedAt() != null && p.getCreatedAt().isBefore(threshold);
                })
                .map(p -> {
                    long daysSinceCreated = p.getCreatedAt() != null
                            ? ChronoUnit.DAYS.between(p.getCreatedAt(), Instant.now())
                            : 0;

                    int stock = p.getVariants().stream()
                            .mapToInt(v -> v.quantity() != null ? v.quantity() : 0)
                            .sum();

                    return ProductAlertDto.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .imageUrl(p.getImages() != null && !p.getImages().isEmpty()
                                    ? p.getImages().get(0).url() : null)
                            .currentStock(stock)
                            .daysSinceLastSold(daysSinceCreated)
                            .alertType("SLOW_MOVING")
                            .message(String.format("Sản phẩm tồn kho %d ngày chưa bán được", daysSinceCreated))
                            .build();
                })
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * Build empty statistics khi không có sản phẩm
     */
    private SellerProductStatistics buildEmptyStatistics() {
        return SellerProductStatistics.builder()
                .productCountByStatus(ProductCountByStatus.builder()
                        .available(0L).pending(0L).suspended(0L).discontinued(0L).total(0L).build())
                .salesPerformance(SalesPerformance.builder()
                        .totalViews(0L).totalSold(0L).estimatedRevenue(0.0)
                        .topSellingProducts(List.of()).topViewedProducts(List.of()).build())
                .inventoryStatistics(InventoryStatistics.builder()
                        .totalStockQuantity(0L)
//                        .totalVariants(0L)
                        .lowStockVariants(0L)
                        .outOfStockVariants(0L)
//                        .averageVariantsPerProduct(0.0)
                        .lowStockAlerts(List.of())
                        .outOfStockAlerts(List.of())
                        .slowMovingProducts(List.of()).build())
                .categoryStatistics(List.of())
                .build();
    }
}