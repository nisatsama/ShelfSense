package com.nisatsama.shelfsense.dashboard;

import com.nisatsama.shelfsense.forecast.DemandForecastRepository;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.procurement.PurchaseOrderRepository;
import com.nisatsama.shelfsense.procurement.PurchaseOrderStatus;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.replenishment.ReplenishmentRecommendationRepository;
import com.nisatsama.shelfsense.replenishment.ReplenishmentStatus;
import com.nisatsama.shelfsense.reservation.ReservationRepository;
import com.nisatsama.shelfsense.reservation.ReservationStatus;
import com.nisatsama.shelfsense.transfer.InventoryTransferRepository;
import com.nisatsama.shelfsense.transfer.TransferStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryTransferRepository transferRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReplenishmentRecommendationRepository replenishmentRepository;
    private final DemandForecastRepository forecastRepository;

    public DashboardController(
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            ReservationRepository reservationRepository,
            InventoryTransferRepository transferRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            ReplenishmentRecommendationRepository replenishmentRepository,
            DemandForecastRepository forecastRepository
    ) {
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.transferRepository = transferRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.replenishmentRepository = replenishmentRepository;
        this.forecastRepository = forecastRepository;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(defaultValue = "20") int lowStockThreshold,
            @RequestParam(defaultValue = "5") int criticalStockThreshold
    ) {
        List<Inventory> allInventory = inventoryRepository.findAll();
        long totalUnits = allInventory.stream().mapToLong(Inventory::getOnHand).sum();
        long totalReserved = allInventory.stream().mapToLong(Inventory::getReserved).sum();
        long lowStockCount = allInventory.stream().filter(i -> i.getAvailable() <= lowStockThreshold).count();
        long criticalStockCount = allInventory.stream().filter(i -> i.getAvailable() <= criticalStockThreshold).count();

        long pendingTransfers = transferRepository.countByStatusIn(EnumSet.of(
                TransferStatus.CREATED, TransferStatus.APPROVED, TransferStatus.DISPATCHED,
                TransferStatus.IN_TRANSIT, TransferStatus.PARTIALLY_RECEIVED
        ));
        long pendingPurchaseOrders = purchaseOrderRepository.countByStatusIn(EnumSet.of(
                PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED,
                PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED
        ));
        long activeReservations = reservationRepository.findByStatus(ReservationStatus.ACTIVE).size();

        return new DashboardSummaryResponse(
                productRepository.count(),
                locationRepository.count(),
                totalUnits,
                totalReserved,
                totalUnits - totalReserved,
                lowStockCount,
                criticalStockCount,
                activeReservations,
                pendingTransfers,
                pendingPurchaseOrders,
                replenishmentRepository.countByStatus(ReplenishmentStatus.PENDING),
                forecastRepository.count()
        );
    }

    public record DashboardSummaryResponse(
            long totalProducts,
            long totalLocations,
            long totalInventoryUnits,
            long totalReservedUnits,
            long totalAvailableUnits,
            long lowStockItems,
            long criticalStockItems,
            long activeReservations,
            long pendingTransfers,
            long pendingPurchaseOrders,
            long pendingReplenishments,
            long totalForecasts
    ) {
    }
}
