package com.nisatsama.shelfsense.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String category,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @NotNull Boolean active
) {
}
