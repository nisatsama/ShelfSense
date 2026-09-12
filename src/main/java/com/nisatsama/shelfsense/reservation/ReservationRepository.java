package com.nisatsama.shelfsense.reservation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    List<Reservation> findByProductIdAndLocationId(
            Long productId,
            Long locationId
    );

    List<Reservation> findByStatus(ReservationStatus status);

    @EntityGraph(attributePaths = {"product", "location"})
    Optional<Reservation> findWithProductAndLocationById(Long id);
}