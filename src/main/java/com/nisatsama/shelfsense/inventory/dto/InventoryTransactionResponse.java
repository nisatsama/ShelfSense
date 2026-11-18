package com.nisatsama.shelfsense.inventory.dto;

import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;

import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long id,
        Long inventoryId,
        Long productId,
        String sku,
        Long locationId,
        String locationCode,
        InventoryTransactionType type,
        int quantity,
        int onHandDelta,
        int reservedDelta,
        Long referenceId,
        Long reservationId,
        LocalDateTime createdAt
) {
    public static InventoryTransactionResponse from(InventoryTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getInventory().getId(),
                tx.getInventory().getProduct().getId(),
                tx.getInventory().getProduct().getSku(),
                tx.getInventory().getLocation().getId(),
                tx.getInventory().getLocation().getCode(),
                tx.getType(),
                tx.getQuantity(),
                tx.getOnHandDelta(),
                tx.getReservedDelta(),
                tx.getReferenceId(),
                tx.getReservationId(),
                tx.getCreatedAt()
        );
    }
}
