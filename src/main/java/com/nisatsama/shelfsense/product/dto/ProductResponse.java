package com.nisatsama.shelfsense.product.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        boolean active
) {
}