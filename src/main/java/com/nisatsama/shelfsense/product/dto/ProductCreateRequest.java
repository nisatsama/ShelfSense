package com.nisatsama.shelfsense.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotBlank
        @Size(max = 100)
        String sku,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 100)
        String category,

        @NotNull
        @DecimalMin(value = "0.0")
        BigDecimal unitPrice
) {
}