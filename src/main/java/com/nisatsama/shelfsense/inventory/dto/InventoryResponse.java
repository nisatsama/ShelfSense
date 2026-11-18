package com.nisatsama.shelfsense.inventory.dto;

public record InventoryResponse(
        Long id,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        String locationName,
        int onHand,
        int reserved,
        int available,
        Long version
) {
}
