package iuh.fit.se.service;

import iuh.fit.se.dto.response.SellerProductStatistics;

public interface ProductStatisticsService {
    SellerProductStatistics getSellerStatistics(String sellerId);
    SellerProductStatistics getSellerStatistics(String sellerId, Integer lowStockThreshold, Integer slowMovingDays);
}