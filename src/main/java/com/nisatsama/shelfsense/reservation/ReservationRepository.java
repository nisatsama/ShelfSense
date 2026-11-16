package com.nisatsama.shelfsense.reservation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("select reservation.id from Reservation reservation "
            + "where reservation.status = com.nisatsama.shelfsense.reservation.ReservationStatus.ACTIVE "
            + "and reservation.expiresAt <= :now and reservation.id > :afterId "
            + "order by reservation.id asc")
    List<Long> findDueReservationIdsAfter(
            @Param("now") LocalDateTime now,
            @Param("afterId") Long afterId,
            Pageable pageable
    );
}
