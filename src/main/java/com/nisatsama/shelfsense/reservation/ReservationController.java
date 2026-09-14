package com.nisatsama.shelfsense.reservation;

import com.nisatsama.shelfsense.reservation.dto.ReservationCreateRequest;
import com.nisatsama.shelfsense.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        Reservation reservation = reservationService.create(
                request.productId(),
                request.locationId(),
                request.quantity()
        );

        return toResponse(reservation);
    }
    @GetMapping("/{id}")
    public ReservationResponse findById(
            @PathVariable Long id
    ) {
        return toResponse(
                reservationService.findById(id)
        );
    }
    @PutMapping("/{id}/release")
    public ReservationResponse release(
            @PathVariable Long id
    ) {
        return toResponse(
                reservationService.release(id)
        );
    }
    private static ReservationResponse toResponse(
            Reservation reservation
    ) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getProduct().getId(),
                reservation.getProduct().getSku(),
                reservation.getLocation().getId(),
                reservation.getLocation().getCode(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getExpiresAt()
        );
    }
}