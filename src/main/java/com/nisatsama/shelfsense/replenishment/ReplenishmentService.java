package com.nisatsama.shelfsense.replenishment;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.forecast.DemandForecast;
import com.nisatsama.shelfsense.forecast.DemandForecastRepository;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.procurement.ProcurementService;
import com.nisatsama.shelfsense.procurement.PurchaseOrder;
import com.nisatsama.shelfsense.procurement.PurchaseOrderRepository;
import com.nisatsama.shelfsense.procurement.PurchaseOrderStatus;
import com.nisatsama.shelfsense.procurement.Supplier;
import com.nisatsama.shelfsense.procurement.SupplierRepository;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderCreateRequest;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import com.nisatsama.shelfsense.transfer.InventoryTransferRepository;
import com.nisatsama.shelfsense.transfer.TransferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ReplenishmentService {

    private static final EnumSet<PurchaseOrderStatus> OPEN_PO_STATUSES = EnumSet.of(
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.ORDERED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );
    private static final EnumSet<TransferStatus> IN_TRANSIT_TRANSFER_STATUSES = EnumSet.of(
            TransferStatus.DISPATCHED,
            TransferStatus.IN_TRANSIT,
            TransferStatus.PARTIALLY_RECEIVED
    );
    private static final EnumSet<TransferStatus> APPROVED_TRANSFER_STATUSES = EnumSet.of(
            TransferStatus.APPROVED
    );

    private final ReplenishmentRecommendationRepository recommendationRepository;
    private final InventoryRepository inventoryRepository;
    private final DemandForecastRepository forecastRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryTransferRepository transferRepository;
    private final SupplierRepository supplierRepository;
    private final ProcurementService procurementService;
    private final CurrentAccountProvider currentAccountProvider;

    public ReplenishmentService(
            ReplenishmentRecommendationRepository recommendationRepository,
            InventoryRepository inventoryRepository,
            DemandForecastRepository forecastRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            InventoryTransferRepository transferRepository,
            SupplierRepository supplierRepository,
            ProcurementService procurementService,
            CurrentAccountProvider currentAccountProvider
    ) {
        this.recommendationRepository = recommendationRepository;
        this.inventoryRepository = inventoryRepository;
        this.forecastRepository = forecastRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.transferRepository = transferRepository;
        this.supplierRepository = supplierRepository;
        this.procurementService = procurementService;
        this.currentAccountProvider = currentAccountProvider;
    }

    public List<ReplenishmentRecommendation> generateRecommendations(
            Integer leadTimeDaysOverride,
            Integer reviewPeriodDaysOverride,
            BigDecimal fallbackDailyDemandOverride
    ) {
        int defaultLeadTime = leadTimeDaysOverride != null
                ? leadTimeDaysOverride
                : supplierRepository.findAll().stream()
                        .filter(Supplier::isActive)
                        .mapToInt(Supplier::getLeadTimeDays)
                        .findFirst()
                        .orElse(7);
        int reviewPeriodDays = reviewPeriodDaysOverride != null ? reviewPeriodDaysOverride : 14;
        BigDecimal fallbackDailyDemand = fallbackDailyDemandOverride != null
                ? fallbackDailyDemandOverride
                : new BigDecimal("2.0000");

        List<Inventory> allInventory = inventoryRepository.findAllByOrderByIdAsc();
        List<ReplenishmentRecommendation> activeOrUpdated = new ArrayList<>();

        for (Inventory inv : allInventory) {
            Long productId = inv.getProduct().getId();
            Long locationId = inv.getLocation().getId();

            Optional<DemandForecast> latestForecast = forecastRepository
                    .findTopByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(productId, locationId);

            BigDecimal avgDailyDemand = latestForecast
                    .map(DemandForecast::getPredictedDailyDemand)
                    .filter(d -> d.compareTo(BigDecimal.ZERO) > 0)
                    .orElse(fallbackDailyDemand);

            double demandDouble = avgDailyDemand.doubleValue();
            int safetyStock = Math.max(1, (int) Math.ceil(demandDouble * Math.sqrt(defaultLeadTime) * 0.5));
            int reorderPoint = (int) Math.ceil(demandDouble * defaultLeadTime) + safetyStock;
            int targetStock = (int) Math.ceil(demandDouble * (defaultLeadTime + reviewPeriodDays)) + safetyStock;

            int incomingFromPo = purchaseOrderRepository.sumIncomingQuantity(productId, locationId, OPEN_PO_STATUSES);
            int incomingFromInTransit = transferRepository.sumIncomingInTransit(productId, locationId, IN_TRANSIT_TRANSFER_STATUSES);
            int incomingFromApprovedTransfers = transferRepository.sumPendingRequested(productId, locationId, APPROVED_TRANSFER_STATUSES);
            int incomingStock = incomingFromPo + incomingFromInTransit + incomingFromApprovedTransfers;

            int available = inv.getAvailable();
            int effectiveStock = available + incomingStock;

            Optional<ReplenishmentRecommendation> existingPending = recommendationRepository
                    .findByProductIdAndLocationIdAndStatus(productId, locationId, ReplenishmentStatus.PENDING);

            if (effectiveStock > reorderPoint) {
                continue;
            }

            int recommendedQty = Math.max(1, targetStock - effectiveStock);
            ReplenishmentPriority priority = computePriority(available, safetyStock, reorderPoint);

            ReplenishmentRecommendation rec = existingPending.orElseGet(ReplenishmentRecommendation::new);
            rec.setProduct(inv.getProduct());
            rec.setLocation(inv.getLocation());
            rec.setCurrentOnHand(inv.getOnHand());
            rec.setReservedStock(inv.getReserved());
            rec.setAvailableStock(available);
            rec.setIncomingStock(incomingStock);
            rec.setAvgDailyDemand(avgDailyDemand.setScale(4, RoundingMode.HALF_UP));
            rec.setLeadTimeDays(defaultLeadTime);
            rec.setSafetyStock(safetyStock);
            rec.setReorderPoint(reorderPoint);
            rec.setTargetStock(targetStock);
            rec.setRecommendedQuantity(recommendedQty);
            rec.setPriority(priority);
            rec.setStatus(ReplenishmentStatus.PENDING);
            rec.setForecastId(latestForecast.map(DemandForecast::getId).orElse(null));

            activeOrUpdated.add(recommendationRepository.save(rec));
        }

        return activeOrUpdated;
    }

    public ReplenishmentRecommendation approve(Long id, Long supplierId, String notes) {
        ReplenishmentRecommendation rec = findById(id);
        if (rec.getStatus() != ReplenishmentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING recommendations can be approved. Current status: " + rec.getStatus());
        }
        currentAccountProvider.currentAccountId().ifPresent(rec::setReviewedByAccountId);
        if (notes != null && !notes.isBlank()) {
            rec.setNotes(notes.trim());
        }

        if (supplierId != null) {
            PurchaseOrder po = procurementService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                    supplierId,
                    rec.getLocation().getId(),
                    null,
                    "Auto-created from replenishment recommendation #" + rec.getId(),
                    List.of(new PurchaseOrderCreateRequest.ItemRequest(
                            rec.getProduct().getId(),
                            rec.getRecommendedQuantity(),
                            rec.getProduct().getUnitPrice()
                    ))
            ));
            rec.setPurchaseOrderId(po.getId());
            rec.setStatus(ReplenishmentStatus.ORDERED);
        } else {
            rec.setStatus(ReplenishmentStatus.APPROVED);
        }
        return recommendationRepository.save(rec);
    }

    public ReplenishmentRecommendation dismiss(Long id, String notes) {
        ReplenishmentRecommendation rec = findById(id);
        if (rec.getStatus() != ReplenishmentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING recommendations can be dismissed. Current status: " + rec.getStatus());
        }
        rec.setStatus(ReplenishmentStatus.DISMISSED);
        if (notes != null && !notes.isBlank()) {
            rec.setNotes(notes.trim());
        }
        currentAccountProvider.currentAccountId().ifPresent(rec::setReviewedByAccountId);
        return recommendationRepository.save(rec);
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentRecommendation> findAll(
            ReplenishmentStatus status,
            ReplenishmentPriority priority,
            Long locationId
    ) {
        List<ReplenishmentRecommendation> list = status == null
                ? recommendationRepository.findAllByOrderByUpdatedAtDescIdDesc()
                : recommendationRepository.findByStatusOrderByUpdatedAtDescIdDesc(status);
        return list.stream()
                .filter(r -> priority == null || r.getPriority() == priority)
                .filter(r -> locationId == null || r.getLocation().getId().equals(locationId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReplenishmentRecommendation findById(Long id) {
        return recommendationRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Replenishment recommendation not found: " + id));
    }

    private static ReplenishmentPriority computePriority(int available, int safetyStock, int reorderPoint) {
        if (available <= 0 || available <= safetyStock) {
            return ReplenishmentPriority.CRITICAL;
        }
        if (available <= Math.max(1, reorderPoint / 2)) {
            return ReplenishmentPriority.HIGH;
        }
        if (available <= reorderPoint) {
            return ReplenishmentPriority.MEDIUM;
        }
        return ReplenishmentPriority.LOW;
    }
}
