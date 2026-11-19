package com.nisatsama.shelfsense.replenishment.dto;

import jakarta.validation.constraints.Size;

public record ReplenishmentActionRequest(
        Long supplierId,
        @Size(max = 500) String notes
) {
}
