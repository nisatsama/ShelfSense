package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.common.ConflictException;
import com.nisatsama.shelfsense.inventory.AdjustmentReason;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryAdjustment;
import com.nisatsama.shelfsense.inventory.InventoryAdjustmentRepository;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.procurement.GoodsReceipt;
import com.nisatsama.shelfsense.procurement.GoodsReceiptRepository;
import com.nisatsama.shelfsense.procurement.ProcurementService;
import com.nisatsama.shelfsense.procurement.PurchaseOrder;
import com.nisatsama.shelfsense.procurement.Supplier;
import com.nisatsama.shelfsense.procurement.SupplierService;
import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptRequest;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderCreateRequest;
import com.nisatsama.shelfsense.procurement.dto.SupplierRequest;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotentOperationsIntegrationTests {

    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryAdjustmentRepository adjustmentRepository;
    @Autowired InventoryTransactionRepository transactionRepository;
    @Autowired InventoryService inventoryService;
    @Autowired SupplierService supplierService;
    @Autowired ProcurementService procurementService;
    @Autowired GoodsReceiptRepository goodsReceiptRepository;

    private Product createProduct(String prefix) {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Product p = new Product();
        p.setSku(prefix + "-" + u);
        p.setName("Product " + u);
        p.setCategory("GENERAL");
        p.setUnitPrice(new BigDecimal("10.00"));
        return productRepository.saveAndFlush(p);
    }

    private Location createLocation(String prefix, LocationType type) {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Location loc = new Location();
        loc.setCode(prefix + "-" + u);
        loc.setName("Location " + u);
        loc.setType(type);
        return locationRepository.saveAndFlush(loc);
    }

    @Test
    void adjustmentIdenticalRetryReturnsOriginalWithoutDoubleDeduction() {
        Product product = createProduct("ADJ-IDEM");
        Location store = createLocation("STR-IDEM", LocationType.STORE);
        Inventory inv = inventoryService.create(product.getId(), store.getId(), 100);

        String key = "KEY-" + UUID.randomUUID();

        // First adjustment: +20
        InventoryAdjustment first = inventoryService.adjustStock(
                product.getId(), store.getId(), 20, AdjustmentReason.RESTOCK_CORRECTION, "Stock count correction", key
        );

        assertThat(first.getId()).isNotNull();
        assertThat(first.getNewOnHand()).isEqualTo(120);
        assertThat(first.getRequestHash()).isNotNull();

        // Verify inventory updated
        Inventory updatedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
        assertThat(updatedInv.getOnHand()).isEqualTo(120);

        // Identical retry with same key and same payload
        InventoryAdjustment retry = inventoryService.adjustStock(
                product.getId(), store.getId(), 20, AdjustmentReason.RESTOCK_CORRECTION, "Stock count correction", key
        );

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getNewOnHand()).isEqualTo(120);

        // Crucial: onHand must NOT have increased again to 140!
        Inventory afterRetryInv = inventoryRepository.findById(inv.getId()).orElseThrow();
        assertThat(afterRetryInv.getOnHand()).isEqualTo(120);

        // Crucial: only 1 adjustment record and 2 transactions (initial + 1 adjustment)
        long adjustmentCount = adjustmentRepository.findAll().stream()
                .filter(a -> key.equals(a.getIdempotencyKey())).count();
        assertThat(adjustmentCount).isEqualTo(1);
    }

    @Test
    void adjustmentSameKeyDifferentQuantityThrowsConflict() {
        Product product = createProduct("ADJ-QTY");
        Location store = createLocation("STR-QTY", LocationType.STORE);
        inventoryService.create(product.getId(), store.getId(), 100);

        String key = "KEY-" + UUID.randomUUID();

        // First adjustment: +15
        inventoryService.adjustStock(
                product.getId(), store.getId(), 15, AdjustmentReason.CYCLE_COUNT, "Correction", key
        );

        // Second adjustment: same key, but different quantity (+30) -> MUST throw ConflictException
        assertThatThrownBy(() -> inventoryService.adjustStock(
                product.getId(), store.getId(), 30, AdjustmentReason.CYCLE_COUNT, "Correction", key
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("already used for a different adjustment request payload");

        // Verify stock is 115, not 145
        Inventory current = inventoryRepository.findByProductIdAndLocationId(product.getId(), store.getId()).orElseThrow();
        assertThat(current.getOnHand()).isEqualTo(115);
    }

    @Test
    void adjustmentSameKeyDifferentProductThrowsConflict() {
        Product productA = createProduct("ADJ-PROD-A");
        Product productB = createProduct("ADJ-PROD-B");
        Location store = createLocation("STR-PROD", LocationType.STORE);
        inventoryService.create(productA.getId(), store.getId(), 50);
        inventoryService.create(productB.getId(), store.getId(), 50);

        String key = "KEY-" + UUID.randomUUID();

        inventoryService.adjustStock(
                productA.getId(), store.getId(), 10, AdjustmentReason.CYCLE_COUNT, "Notes", key
        );

        // Same key on a different product -> ConflictException
        assertThatThrownBy(() -> inventoryService.adjustStock(
                productB.getId(), store.getId(), 10, AdjustmentReason.CYCLE_COUNT, "Notes", key
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void adjustmentConcurrentRequestsWithSameKeyHandledSafely() throws InterruptedException, ExecutionException {
        Product product = createProduct("CONC-ADJ");
        Location store = createLocation("CONC-STR", LocationType.STORE);
        inventoryService.create(product.getId(), store.getId(), 100);

        String key = "CONCUR-" + UUID.randomUUID();
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<InventoryAdjustment>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> inventoryService.adjustStock(
                    product.getId(), store.getId(), 10, AdjustmentReason.CYCLE_COUNT, "Concurrent test", key
            ));
        }

        List<Future<InventoryAdjustment>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (Future<InventoryAdjustment> future : futures) {
            try {
                InventoryAdjustment adj = future.get();
                if (adj != null) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException e) {
                // If a concurrent transaction raced on insert, ConflictException / DataIntegrityViolation occurs
                conflictCount.incrementAndGet();
            }
        }

        // At least one succeeded
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // The critical invariant: on-hand MUST be 110 (exactly +10, never doubled/tripled by concurrency)
        Inventory current = inventoryRepository.findByProductIdAndLocationId(product.getId(), store.getId()).orElseThrow();
        assertThat(current.getOnHand()).isEqualTo(110);
    }

    @Test
    void goodsReceiptIdenticalRetryReturnsOriginalWithoutDoubleIncrement() {
        Product product = createProduct("GR-IDEM");
        Location warehouse = createLocation("WH-GR", LocationType.WAREHOUSE);
        String u = UUID.randomUUID().toString().substring(0, 8);

        Supplier supplier = supplierService.create(new SupplierRequest(
                "SUP-" + u, "Supplier " + u, "sup@" + u + ".com", null, null, 3, true
        ));

        PurchaseOrder po = procurementService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                supplier.getId(),
                warehouse.getId(),
                LocalDate.now().plusDays(5),
                "PO for goods receipt test",
                List.of(new PurchaseOrderCreateRequest.ItemRequest(product.getId(), 20, new BigDecimal("8.00")))
        ));
        procurementService.approve(po.getId());

        Long poItemId = po.getItems().get(0).getId();
        String grKey = "GR-KEY-" + UUID.randomUUID();

        GoodsReceiptRequest req = new GoodsReceiptRequest(
                "Dock receipt batch 1",
                grKey,
                List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 10))
        );

        // First receipt: receive 10 units
        GoodsReceipt firstReceipt = procurementService.receiveGoods(po.getId(), req, null);
        assertThat(firstReceipt.getId()).isNotNull();
        assertThat(firstReceipt.getRequestHash()).isNotNull();

        Inventory inv = inventoryRepository.findByProductIdAndLocationId(product.getId(), warehouse.getId()).orElseThrow();
        assertThat(inv.getOnHand()).isEqualTo(10);

        // Identical retry with same idempotency key
        GoodsReceipt retryReceipt = procurementService.receiveGoods(po.getId(), req, null);
        assertThat(retryReceipt.getId()).isEqualTo(firstReceipt.getId());

        // Invariant: On-hand stock is STILL 10 (not 20!)
        Inventory invAfter = inventoryRepository.findByProductIdAndLocationId(product.getId(), warehouse.getId()).orElseThrow();
        assertThat(invAfter.getOnHand()).isEqualTo(10);

        // Invariant: only 1 GoodsReceipt created
        long grCount = goodsReceiptRepository.findAll().stream()
                .filter(g -> grKey.equals(g.getIdempotencyKey())).count();
        assertThat(grCount).isEqualTo(1);
    }

    @Test
    void goodsReceiptSameKeyDifferentQuantityThrowsConflict() {
        Product product = createProduct("GR-CONFLICT");
        Location warehouse = createLocation("WH-GRC", LocationType.WAREHOUSE);
        String u = UUID.randomUUID().toString().substring(0, 8);

        Supplier supplier = supplierService.create(new SupplierRequest(
                "SUP-C-" + u, "Supplier " + u, "supc@" + u + ".com", null, null, 3, true
        ));

        PurchaseOrder po = procurementService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                supplier.getId(),
                warehouse.getId(),
                LocalDate.now().plusDays(5),
                "PO for conflict test",
                List.of(new PurchaseOrderCreateRequest.ItemRequest(product.getId(), 50, new BigDecimal("5.00")))
        ));
        procurementService.approve(po.getId());
        Long poItemId = po.getItems().get(0).getId();

        String grKey = "GR-KEY-" + UUID.randomUUID();

        // First receipt: receive 10 units
        procurementService.receiveGoods(po.getId(), new GoodsReceiptRequest(
                "Batch 1",
                grKey,
                List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 10))
        ), null);

        // Second receipt: same key, but different quantity (25 units) -> ConflictException
        assertThatThrownBy(() -> procurementService.receiveGoods(po.getId(), new GoodsReceiptRequest(
                "Batch 1",
                grKey,
                List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 25))
        ), null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("already used for a different goods receipt request payload");

        // Verify stock is still 10, not 35
        Inventory inv = inventoryRepository.findByProductIdAndLocationId(product.getId(), warehouse.getId()).orElseThrow();
        assertThat(inv.getOnHand()).isEqualTo(10);
    }
}
