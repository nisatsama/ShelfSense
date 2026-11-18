package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.*;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.procurement.*;
import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptRequest;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderCreateRequest;
import com.nisatsama.shelfsense.procurement.dto.SupplierRequest;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProcurementIntegrationTests {

    @Autowired SupplierService supplierService;
    @Autowired ProcurementService procurementService;
    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryTransactionRepository transactionRepository;

    @Test
    void purchaseOrderPartialAndFullGoodsReceiptUpdatesInventoryAndLedgerIdempotently() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Supplier supplier = supplierService.create(new SupplierRequest(
                "SUP-" + unique, "Acme Distributors", "orders@acme.example", "+1-555-0100", "100 Supply Way", 5, true
        ));

        Product product = new Product();
        product.setSku("PO-SKU-" + unique);
        product.setName("Bulk Espresso Beans");
        product.setCategory("COFFEE");
        product.setUnitPrice(new BigDecimal("18.00"));
        product = productRepository.saveAndFlush(product);

        Location warehouse = new Location();
        warehouse.setCode("PO-WH-" + unique);
        warehouse.setName("Central Warehouse");
        warehouse.setType(LocationType.WAREHOUSE);
        warehouse = locationRepository.saveAndFlush(warehouse);

        PurchaseOrder po = procurementService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                supplier.getId(),
                warehouse.getId(),
                LocalDate.now().plusDays(5),
                "Q4 restock",
                List.of(new PurchaseOrderCreateRequest.ItemRequest(product.getId(), 50, new BigDecimal("11.50")))
        ));
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("575.00");

        Long poId = po.getId();
        Long poItemId = po.getItems().getFirst().getId();

        // Cannot receive while DRAFT
        assertThatThrownBy(() -> procurementService.receiveGoods(
                poId,
                new GoodsReceiptRequest("Early", null, List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 10))),
                null
        )).isInstanceOf(IllegalStateException.class);

        po = procurementService.approve(poId);
        po = procurementService.markOrdered(poId);
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.ORDERED);

        // Partial delivery: 20 units with idempotency key
        String key = "grn-" + unique;
        GoodsReceipt firstReceipt = procurementService.receiveGoods(
                poId,
                new GoodsReceiptRequest("Shipment 1", key, List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 20))),
                null
        );
        GoodsReceipt duplicateReceipt = procurementService.receiveGoods(
                poId,
                new GoodsReceiptRequest("Shipment 1 retry", key, List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 20))),
                null
        );
        assertThat(duplicateReceipt.getId()).isEqualTo(firstReceipt.getId());
        assertThat(procurementService.findById(poId).getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(inventoryService.findByProductAndLocation(product.getId(), warehouse.getId()).getOnHand()).isEqualTo(20);

        // Over-delivery rejected
        assertThatThrownBy(() -> procurementService.receiveGoods(
                poId,
                new GoodsReceiptRequest("Too much", null, List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 31))),
                null
        )).isInstanceOf(IllegalArgumentException.class);

        // Final delivery: 30 units
        procurementService.receiveGoods(
                poId,
                new GoodsReceiptRequest("Shipment 2", null, List.of(new GoodsReceiptRequest.ItemReceipt(poItemId, 30))),
                null
        );
        PurchaseOrder completed = procurementService.findById(poId);
        assertThat(completed.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(completed.getItems().getFirst().getReceivedQuantity()).isEqualTo(50);

        Inventory inv = inventoryService.findByProductAndLocation(product.getId(), warehouse.getId());
        assertThat(inv.getOnHand()).isEqualTo(50);
        List<InventoryTransaction> ledger = transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(inv.getId());
        assertThat(ledger).extracting(InventoryTransaction::getType)
                .containsExactly(
                        InventoryTransactionType.INITIAL_STOCK,
                        InventoryTransactionType.PURCHASE_RECEIVED,
                        InventoryTransactionType.PURCHASE_RECEIVED
                );
    }
}
