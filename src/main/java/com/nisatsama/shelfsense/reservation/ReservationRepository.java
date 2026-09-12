package com.nisatsama.shelfsense.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    List<Reservation> findByProductIdAndLocationId(
            Long productId,
            Long locationId
    );

    List<Reservation> findByStatus(ReservationStatus status);
}