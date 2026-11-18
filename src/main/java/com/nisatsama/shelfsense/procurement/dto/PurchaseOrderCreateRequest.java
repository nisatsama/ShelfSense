package com.nisatsama.shelfsense.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderCreateRequest(
        @NotNull Long supplierId,
        @NotNull Long destinationLocationId,
        LocalDate expectedDeliveryDate,
        @Size(max = 500) String notes,
        @NotEmpty @Valid List<ItemRequest> items
) {
    public record ItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer orderedQuantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost
    ) {
    }
}
