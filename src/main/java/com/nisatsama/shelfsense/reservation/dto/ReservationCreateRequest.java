package com.nisatsama.shelfsense.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(

        @NotNull
        Long productId,

        @NotNull
        Long locationId,

        @Min(1)
        int quantity
) {
}