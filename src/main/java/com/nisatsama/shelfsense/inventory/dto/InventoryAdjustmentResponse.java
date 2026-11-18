package com.nisatsama.shelfsense.inventory.dto;

import com.nisatsama.shelfsense.inventory.AdjustmentReason;
import com.nisatsama.shelfsense.inventory.InventoryAdjustment;

import java.time.LocalDateTime;

public record InventoryAdjustmentResponse(
        Long id,
        Long inventoryId,
        Long productId,
        String sku,
        Long locationId,
        String locationCode,
        Long transactionId,
        int quantityDelta,
        int previousOnHand,
        int newOnHand,
        AdjustmentReason reason,
        String notes,
        Long performedByAccountId,
        LocalDateTime createdAt
) {
    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getInventory().getId(),
                adjustment.getInventory().getProduct().getId(),
                adjustment.getInventory().getProduct().getSku(),
                adjustment.getInventory().getLocation().getId(),
                adjustment.getInventory().getLocation().getCode(),
                adjustment.getTransaction().getId(),
                adjustment.getQuantityDelta(),
                adjustment.getPreviousOnHand(),
                adjustment.getNewOnHand(),
                adjustment.getReason(),
                adjustment.getNotes(),
                adjustment.getPerformedByAccountId(),
                adjustment.getCreatedAt()
        );
    }
}
