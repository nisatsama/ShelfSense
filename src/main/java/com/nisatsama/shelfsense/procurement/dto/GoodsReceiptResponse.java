package com.nisatsama.shelfsense.procurement.dto;

import com.nisatsama.shelfsense.procurement.GoodsReceipt;
import com.nisatsama.shelfsense.procurement.GoodsReceiptItem;

import java.time.LocalDateTime;
import java.util.List;

public record GoodsReceiptResponse(
        Long id,
        String receiptNumber,
        Long purchaseOrderId,
        String poNumber,
        String notes,
        Long receivedByAccountId,
        LocalDateTime receivedAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long id,
            Long purchaseOrderItemId,
            Long productId,
            String sku,
            int receivedQuantity
    ) {
        public static ItemResponse from(GoodsReceiptItem item) {
            return new ItemResponse(
                    item.getId(),
                    item.getPurchaseOrderItem().getId(),
                    item.getProduct().getId(),
                    item.getProduct().getSku(),
                    item.getReceivedQuantity()
            );
        }
    }

    public static GoodsReceiptResponse from(GoodsReceipt receipt) {
        return new GoodsReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getPurchaseOrder().getId(),
                receipt.getPurchaseOrder().getPoNumber(),
                receipt.getNotes(),
                receipt.getReceivedByAccountId(),
                receipt.getReceivedAt(),
                receipt.getItems().stream().map(ItemResponse::from).toList()
        );
    }
}
