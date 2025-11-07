package iuh.fit.se.repository;

import iuh.fit.se.entity.StockReservation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends MongoRepository<StockReservation, String> {


    List<StockReservation> findByStatusAndExpiresAtBefore(
            StockReservation.Status status,
            Instant expiresAt
    );

    List<StockReservation> findByUserIdOrderByCreatedAtDesc(String userId);}