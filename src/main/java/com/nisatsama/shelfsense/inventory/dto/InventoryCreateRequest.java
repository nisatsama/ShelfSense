package com.nisatsama.shelfsense.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryCreateRequest(

        @NotNull
        Long productId,

        @NotNull
        Long locationId,

        @Min(0)
        int onHand
) {
}