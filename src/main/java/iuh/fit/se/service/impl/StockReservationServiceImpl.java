package iuh.fit.se.service.impl;

import iuh.fit.se.dto.request.ReserveStockRequest;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.StockReservation;
import iuh.fit.se.entity.records.Variant;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.repository.StockReservationRepository;
import iuh.fit.se.service.StockReservationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationServiceImpl implements StockReservationService {

    StockReservationRepository reservationRepository;
    ProductRepository productRepository;
    MongoTemplate mongoTemplate;

    private static final int DEFAULT_EXPIRATION_MINUTES = 15;

    @Override
    @Transactional
    public StockReservation reserveStock(ReserveStockRequest request) {
        log.info("Reserving stock for payment intent: {}", request.getUserId());

        List<StockReservation.Item> reservationItems = new ArrayList<>();

        // Duyệt qua từng item và trừ số lượng
        for (ReserveStockRequest.ReservationItem item : request.getItems()) {
            reserveProductStock(item.getProductId(), item.getOptions(), item.getQuantity());

            reservationItems.add(StockReservation.Item.builder()
                    .productId(item.getProductId())
                    .options(item.getOptions())
                    .qty(item.getQuantity())
                    .build());
        }

        // Tạo reservation record
        int expirationMinutes = request.getExpirationMinutes() != null
                ? request.getExpirationMinutes()
                : DEFAULT_EXPIRATION_MINUTES;

        StockReservation reservation = StockReservation.builder()
                .userId(request.getUserId())
                .items(reservationItems)
                .expiresAt(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES))
                .status(StockReservation.Status.PENDING)
                .createdAt(Instant.now())
                .build();

        StockReservation saved = reservationRepository.save(reservation);
        log.info("Stock reserved successfully for payment intent: {}", request.getUserId());

        return saved;
    }

    @Override
    @Transactional
    public void confirmReservation(String userId) {
        log.info("Confirming reservation for payment intent: {}", userId);

        StockReservation reservation = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() != StockReservation.Status.PENDING) {
            log.warn("Reservation {} is not PENDING, current status: {}",
                    userId, reservation.getStatus());
            return;
        }

        // Cập nhật status sang CONFIRMED
        reservation.setStatus(StockReservation.Status.CONFIRMED);
        reservationRepository.save(reservation);

        log.info("Reservation confirmed for payment intent: {}", userId);
    }

    @Override
    @Transactional
    public void releaseReservation(String userId) {
        log.info("Releasing reservation for payment intent: {}", userId);

        StockReservation reservation = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() == StockReservation.Status.CONFIRMED) {
            log.warn("Cannot release CONFIRMED reservation: {}", userId);
            return;
        }

        if (reservation.getStatus() == StockReservation.Status.RELEASED ||
                reservation.getStatus() == StockReservation.Status.EXPIRED) {
            log.info("Reservation {} already released/expired", userId);
            return;
        }

        // Hoàn trả số lượng cho từng item
        for (StockReservation.Item item : reservation.getItems()) {
            restoreProductStock(item.getProductId(), item.getOptions(), item.getQty());
        }

        // Cập nhật status
        reservation.setStatus(StockReservation.Status.RELEASED);
        reservationRepository.save(reservation);

        log.info("Reservation released for payment intent: {}", userId);
    }

    @Override
    @Transactional
    public void expireReservations() {
        log.info("Checking for expired reservations...");

        List<StockReservation> expiredReservations = reservationRepository
                .findByStatusAndExpiresAtBefore(
                        StockReservation.Status.PENDING,
                        Instant.now()
                );

        log.info("Found {} expired reservations", expiredReservations.size());

        for (StockReservation reservation : expiredReservations) {
            try {
                // Hoàn trả số lượng
                for (StockReservation.Item item : reservation.getItems()) {
                    restoreProductStock(item.getProductId(), item.getOptions(), item.getQty());
                }

                // Cập nhật status
                reservation.setStatus(StockReservation.Status.EXPIRED);
                reservationRepository.save(reservation);

                log.info("Expired reservation {} and restored stock",reservation.getId());
            } catch (Exception e) {
                log.error("Failed to expire reservation {}: {}", e.getMessage());
            }
        }
    }

    /**
     * Trừ số lượng sản phẩm (với optimistic locking)
     */
    private void reserveProductStock(String productId, Map<String, String> options, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Tìm variant phù hợp
        Variant matchedVariant = findMatchingVariant(product, options);

        if (matchedVariant == null) {
            throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
        }

        if (matchedVariant.quantity() < quantity) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        // Trừ số lượng bằng atomic operation
        Query query = new Query(Criteria.where("_id").is(productId)
                .and("variants").elemMatch(buildVariantCriteria(options))
                .and("version").is(product.getVersion()));

        Update update = new Update()
                .inc("variants.$.quantity", -quantity)
                .inc("version", 1);

        var result = mongoTemplate.updateFirst(query, update, Product.class);

        if (result.getModifiedCount() == 0) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE_CONFLICT);
        }

        log.info("Reserved {} units of product {} (variant: {})", quantity, productId, options);
    }

    /**
     * Hoàn trả số lượng sản phẩm
     */
    private void restoreProductStock(String productId, Map<String, String> options, Integer quantity) {
        Query query = new Query(Criteria.where("_id").is(productId)
                .and("variants").elemMatch(buildVariantCriteria(options)));

        Update update = new Update()
                .inc("variants.$.quantity", quantity)
                .inc("version", 1);

        var result = mongoTemplate.updateFirst(query, update, Product.class);

        if (result.getModifiedCount() == 0) {
            log.error("Failed to restore stock for product {} (variant: {})", productId, options);
        } else {
            log.info("Restored {} units of product {} (variant: {})", quantity, productId, options);
        }
    }

    /**
     * Tìm variant khớp với options
     */
    private Variant findMatchingVariant(Product product, Map<String, String> options) {
        if (product.getVariants() == null || options == null) {
            return null;
        }

        return product.getVariants().stream()
                .filter(v -> v.options() != null && v.options().equals(options))
                .findFirst()
                .orElse(null);
    }

    /**
     * Build criteria để match variant trong MongoDB query
     */
    private Criteria buildVariantCriteria(Map<String, String> options) {
        Criteria criteria = new Criteria();

        if (options != null && !options.isEmpty()) {
            options.forEach((key, value) ->
                    criteria.and("options." + key).is(value)
            );
        }

        return criteria;
    }
}