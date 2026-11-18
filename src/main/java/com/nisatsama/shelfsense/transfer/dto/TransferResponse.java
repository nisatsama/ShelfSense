package com.nisatsama.shelfsense.transfer.dto;

import com.nisatsama.shelfsense.transfer.InventoryTransfer;
import com.nisatsama.shelfsense.transfer.TransferStatus;

import java.time.LocalDateTime;

public record TransferResponse(
        Long id,
        String transferNumber,
        Long productId,
        String sku,
        String productName,
        Long sourceLocationId,
        String sourceLocationCode,
        String sourceLocationName,
        Long destinationLocationId,
        String destinationLocationCode,
        String destinationLocationName,
        int requestedQuantity,
        int dispatchedQuantity,
        int receivedQuantity,
        int damagedQuantity,
        int remainingToReceive,
        TransferStatus status,
        String notes,
        LocalDateTime dispatchedAt,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TransferResponse from(InventoryTransfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTransferNumber(),
                t.getProduct().getId(),
                t.getProduct().getSku(),
                t.getProduct().getName(),
                t.getSourceLocation().getId(),
                t.getSourceLocation().getCode(),
                t.getSourceLocation().getName(),
                t.getDestinationLocation().getId(),
                t.getDestinationLocation().getCode(),
                t.getDestinationLocation().getName(),
                t.getRequestedQuantity(),
                t.getDispatchedQuantity(),
                t.getReceivedQuantity(),
                t.getDamagedQuantity(),
                t.getRemainingToReceive(),
                t.getStatus(),
                t.getNotes(),
                t.getDispatchedAt(),
                t.getReceivedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
