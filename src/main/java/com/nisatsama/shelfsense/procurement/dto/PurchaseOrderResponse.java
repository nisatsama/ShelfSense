package com.nisatsama.shelfsense.procurement.dto;

import com.nisatsama.shelfsense.procurement.PurchaseOrder;
import com.nisatsama.shelfsense.procurement.PurchaseOrderItem;
import com.nisatsama.shelfsense.procurement.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long destinationLocationId,
        String destinationLocationCode,
        String destinationLocationName,
        PurchaseOrderStatus status,
        LocalDate expectedDeliveryDate,
        BigDecimal totalAmount,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long id,
            Long productId,
            String sku,
            String productName,
            int orderedQuantity,
            int receivedQuantity,
            int remainingQuantity,
            BigDecimal unitCost
    ) {
        public static ItemResponse from(PurchaseOrderItem item) {
            return new ItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getSku(),
                    item.getProduct().getName(),
                    item.getOrderedQuantity(),
                    item.getReceivedQuantity(),
                    item.getRemainingQuantity(),
                    item.getUnitCost()
            );
        }
    }

    public static PurchaseOrderResponse from(PurchaseOrder po) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplier().getId(),
                po.getSupplier().getCode(),
                po.getSupplier().getName(),
                po.getDestinationLocation().getId(),
                po.getDestinationLocation().getCode(),
                po.getDestinationLocation().getName(),
                po.getStatus(),
                po.getExpectedDeliveryDate(),
                po.getTotalAmount(),
                po.getNotes(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                po.getItems().stream().map(ItemResponse::from).toList()
        );
    }
}
