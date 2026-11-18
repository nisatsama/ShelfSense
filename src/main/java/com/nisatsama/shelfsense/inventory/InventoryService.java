package com.nisatsama.shelfsense.inventory;

import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            CurrentAccountProvider currentAccountProvider
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.currentAccountProvider = currentAccountProvider;
    }

    public Inventory create(Long productId, Long locationId, int onHand) {

        if (onHand < 0) {
            throw new IllegalArgumentException(
                    "On-hand quantity cannot be negative"
            );
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found: " + productId
                        )
                );

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Location not found: " + locationId
                        )
                );

        if (inventoryRepository.existsByProductIdAndLocationId(
                productId,
                locationId
        )) {
            throw new IllegalArgumentException(
                    "Inventory already exists for this product and location"
            );
        }

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setLocation(location);
        inventory.setOnHand(onHand);
        inventory.setReserved(0);

        Inventory created = inventoryRepository.save(inventory);
        inventoryTransactionRepository.append(InventoryTransaction.create(
                created, InventoryTransactionType.INITIAL_STOCK, onHand, 0, null
        ));
        return created;
    }

    public Inventory findOrCreateZeroStock(Product product, Location location) {
        return inventoryRepository.findByProductIdAndLocationId(product.getId(), location.getId())
                .orElseGet(() -> create(product.getId(), location.getId(), 0));
    }

    public InventoryAdjustment adjustStock(
            Long productId,
            Long locationId,
            int quantityDelta,
            AdjustmentReason reason,
            String notes,
            String idempotencyKey
    ) {
        String normalizedKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey.trim()
                : null;
        if (normalizedKey != null) {
            var existing = inventoryAdjustmentRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (quantityDelta == 0) {
            throw new IllegalArgumentException("Adjustment quantity delta must be non-zero.");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Adjustment reason is required.");
        }

        Inventory inventory = findByProductAndLocation(productId, locationId);
        int previousOnHand = inventory.getOnHand();
        int newOnHand = previousOnHand + quantityDelta;
        if (newOnHand < 0) {
            throw new IllegalArgumentException(
                    "Adjustment would result in negative on-hand inventory. Current on-hand: " + previousOnHand
            );
        }
        if (newOnHand < inventory.getReserved()) {
            throw new IllegalArgumentException(
                    "Adjustment cannot reduce on-hand stock below reserved quantity (" + inventory.getReserved() + ")."
            );
        }

        inventory.setOnHand(newOnHand);
        inventoryRepository.save(inventory);

        InventoryTransaction tx = inventoryTransactionRepository.append(
                InventoryTransaction.create(
                        inventory,
                        InventoryTransactionType.ADJUSTMENT,
                        quantityDelta,
                        0,
                        null
                )
        );

        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setInventory(inventory);
        adjustment.setTransaction(tx);
        adjustment.setQuantityDelta(quantityDelta);
        adjustment.setPreviousOnHand(previousOnHand);
        adjustment.setNewOnHand(newOnHand);
        adjustment.setReason(reason);
        adjustment.setNotes(notes);
        adjustment.setIdempotencyKey(normalizedKey);
        currentAccountProvider.currentAccountId().ifPresent(adjustment::setPerformedByAccountId);
        return inventoryAdjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public List<Inventory> findAll(Long productId, Long locationId, Integer lowStockThreshold) {
        List<Inventory> items;
        if (productId != null && locationId != null) {
            items = inventoryRepository.findByProductIdAndLocationId(productId, locationId)
                    .map(List::of).orElseGet(List::of);
        } else if (productId != null) {
            items = inventoryRepository.findByProductIdOrderByIdAsc(productId);
        } else if (locationId != null) {
            items = inventoryRepository.findByLocationIdOrderByIdAsc(locationId);
        } else {
            items = inventoryRepository.findAllByOrderByIdAsc();
        }
        if (lowStockThreshold != null) {
            return items.stream()
                    .filter(inv -> inv.getAvailable() <= lowStockThreshold)
                    .toList();
        }
        return items;
    }

    @Transactional(readOnly = true)
    public Inventory findByProductAndLocation(
            Long productId,
            Long locationId
    ) {
        return inventoryRepository
                .findByProductIdAndLocationId(productId, locationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Inventory not found for product "
                                        + productId
                                        + " at location "
                                        + locationId
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> getTransactions(
            Long inventoryId,
            InventoryTransactionType type,
            int limit
    ) {
        return inventoryTransactionRepository.findLedgerEntries(inventoryId, type, limit);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getAdjustments(Long inventoryId) {
        return inventoryId == null
                ? inventoryAdjustmentRepository.findAllByOrderByCreatedAtDescIdDesc()
                : inventoryAdjustmentRepository.findByInventoryIdOrderByCreatedAtDescIdDesc(inventoryId);
    }
}
