package com.nisatsama.shelfsense.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferCreateRequest(
        @NotNull Long productId,
        @NotNull Long sourceLocationId,
        @NotNull Long destinationLocationId,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 500) String notes
) {
}
