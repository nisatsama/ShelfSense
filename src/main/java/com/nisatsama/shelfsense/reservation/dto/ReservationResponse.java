package com.nisatsama.shelfsense.reservation.dto;

import com.nisatsama.shelfsense.reservation.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long productId,
        String sku,
        Long locationId,
        String locationCode,
        int quantity,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}