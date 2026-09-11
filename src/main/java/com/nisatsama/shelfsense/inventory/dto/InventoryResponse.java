package com.nisatsama.shelfsense.inventory.dto;

public record InventoryResponse(
        Long id,
        Long productId,
        String sku,
        Long locationId,
        String locationCode,
        int onHand,
        int reserved,
        int available,
        Long version,

        String name
) {
}