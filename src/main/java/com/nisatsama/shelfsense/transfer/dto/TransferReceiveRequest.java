package com.nisatsama.shelfsense.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferReceiveRequest(
        @NotNull @Min(0) Integer receivedQuantity,
        @NotNull @Min(0) Integer damagedQuantity,
        @Size(max = 500) String notes
) {
}
