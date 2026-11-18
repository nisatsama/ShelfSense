package com.nisatsama.shelfsense.transfer.dto;

import jakarta.validation.constraints.Min;

public record TransferDispatchRequest(
        @Min(1) Integer dispatchedQuantity
) {
}
