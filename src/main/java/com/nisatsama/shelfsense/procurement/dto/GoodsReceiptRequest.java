package com.nisatsama.shelfsense.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GoodsReceiptRequest(
        @Size(max = 500) String notes,
        @Size(max = 128) String idempotencyKey,
        @NotEmpty @Valid List<ItemReceipt> items
) {
    public record ItemReceipt(
            @NotNull Long purchaseOrderItemId,
            @NotNull @Min(1) Integer receivedQuantity
    ) {
    }
}
