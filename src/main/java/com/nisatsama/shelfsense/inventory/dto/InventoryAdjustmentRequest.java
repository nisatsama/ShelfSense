package com.nisatsama.shelfsense.inventory.dto;

import com.nisatsama.shelfsense.inventory.AdjustmentReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(
        @NotNull Long productId,
        @NotNull Long locationId,
        @NotNull Integer quantityDelta,
        @NotNull AdjustmentReason reason,
        @Size(max = 500) String notes,
        @Size(max = 128) String idempotencyKey
) {
}
