package com.nisatsama.shelfsense.replenishment.dto;

import com.nisatsama.shelfsense.replenishment.ReplenishmentPriority;
import com.nisatsama.shelfsense.replenishment.ReplenishmentRecommendation;
import com.nisatsama.shelfsense.replenishment.ReplenishmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReplenishmentResponse(
        Long id,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        String locationName,
        int currentOnHand,
        int reservedStock,
        int availableStock,
        int incomingStock,
        BigDecimal avgDailyDemand,
        int leadTimeDays,
        int safetyStock,
        int reorderPoint,
        int targetStock,
        int recommendedQuantity,
        ReplenishmentPriority priority,
        ReplenishmentStatus status,
        Long forecastId,
        Long purchaseOrderId,
        Long reviewedByAccountId,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReplenishmentResponse from(ReplenishmentRecommendation r) {
        return new ReplenishmentResponse(
                r.getId(),
                r.getProduct().getId(),
                r.getProduct().getSku(),
                r.getProduct().getName(),
                r.getLocation().getId(),
                r.getLocation().getCode(),
                r.getLocation().getName(),
                r.getCurrentOnHand(),
                r.getReservedStock(),
                r.getAvailableStock(),
                r.getIncomingStock(),
                r.getAvgDailyDemand(),
                r.getLeadTimeDays(),
                r.getSafetyStock(),
                r.getReorderPoint(),
                r.getTargetStock(),
                r.getRecommendedQuantity(),
                r.getPriority(),
                r.getStatus(),
                r.getForecastId(),
                r.getPurchaseOrderId(),
                r.getReviewedByAccountId(),
                r.getNotes(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
