package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.*;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.reservation.ReservationService;
import com.nisatsama.shelfsense.transfer.InventoryTransfer;
import com.nisatsama.shelfsense.transfer.TransferService;
import com.nisatsama.shelfsense.transfer.TransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InventoryAdjustmentAndTransferIntegrationTests {

    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryTransactionRepository transactionRepository;
    @Autowired ReservationService reservationService;
    @Autowired TransferService transferService;

    @Test
    void stockAdjustmentUpdatesInventoryAppendsLedgerIsIdempotentAndProtectsReservedStock() {
        Product product = createProduct();
        Location warehouse = createLocation(LocationType.WAREHOUSE);
        Inventory inv = inventoryService.create(product.getId(), warehouse.getId(), 20);

        String idempKey = "adj-" + UUID.randomUUID();
        InventoryAdjustment first = inventoryService.adjustStock(
                product.getId(), warehouse.getId(), 15, AdjustmentReason.CYCLE_COUNT, "Count surplus", idempKey);
        InventoryAdjustment duplicate = inventoryService.adjustStock(
                product.getId(), warehouse.getId(), 15, AdjustmentReason.CYCLE_COUNT, "Count surplus", idempKey);

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(inventoryService.findByProductAndLocation(product.getId(), warehouse.getId()).getOnHand())
                .isEqualTo(35);

        // Reserve 30, leaving only 5 unreserved
        reservationService.create(product.getId(), warehouse.getId(), 30);

        // Attempting to deduct 10 must fail because 35 - 10 = 25 < reserved 30
        assertThatThrownBy(() -> inventoryService.adjustStock(
                product.getId(), warehouse.getId(), -10, AdjustmentReason.DAMAGE, "Broken", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below reserved");

        // Deducting 5 succeeds
        InventoryAdjustment deduct = inventoryService.adjustStock(
                product.getId(), warehouse.getId(), -5, AdjustmentReason.DAMAGE, "5 damaged", null);
        assertThat(deduct.getNewOnHand()).isEqualTo(30);

        List<InventoryTransaction> ledger = transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(inv.getId());
        assertThat(ledger).extracting(InventoryTransaction::getType)
                .containsExactly(
                        InventoryTransactionType.INITIAL_STOCK,
                        InventoryTransactionType.ADJUSTMENT,
                        InventoryTransactionType.RESERVATION_CREATED,
                        InventoryTransactionType.ADJUSTMENT
                );
    }

    @Test
    void fullTransferLifecycleWithPartialReceiptAndDamagedGoodsPreventsDoubleMutation() {
        Product product = createProduct();
        Location warehouse = createLocation(LocationType.WAREHOUSE);
        Location store = createLocation(LocationType.STORE);
        Inventory sourceInv = inventoryService.create(product.getId(), warehouse.getId(), 100);

        InventoryTransfer transfer = transferService.create(
                product.getId(), warehouse.getId(), store.getId(), 40, "Weekly store replenishment");
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CREATED);
        Long transferId = transfer.getId();

        // Cannot dispatch before approval
        assertThatThrownBy(() -> transferService.dispatch(transferId, null))
                .isInstanceOf(IllegalStateException.class);

        transfer = transferService.approve(transferId);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.APPROVED);

        transfer = transferService.dispatch(transferId, 40);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DISPATCHED);
        assertThat(inventoryService.findByProductAndLocation(product.getId(), warehouse.getId()).getOnHand())
                .isEqualTo(60);

        // Cannot dispatch twice or cancel after dispatch
        assertThatThrownBy(() -> transferService.dispatch(transferId, 40))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transferService.cancel(transferId))
                .isInstanceOf(IllegalStateException.class);

        transfer = transferService.markInTransit(transferId);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);

        // Partial receipt: 25 good, 2 damaged (13 remaining)
        transfer = transferService.receive(transferId, 25, 2, "First pallet received");
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PARTIALLY_RECEIVED);
        assertThat(transfer.getRemainingToReceive()).isEqualTo(13);
        assertThat(inventoryService.findByProductAndLocation(product.getId(), store.getId()).getOnHand())
                .isEqualTo(25);

        // Over-receipt rejected
        assertThatThrownBy(() -> transferService.receive(transferId, 14, 0, "Too many"))
                .isInstanceOf(IllegalArgumentException.class);

        // Final receipt: 12 good, 1 damaged (0 remaining)
        transfer = transferService.receive(transferId, 12, 1, "Second pallet received");
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RECEIVED);
        assertThat(transfer.getReceivedQuantity()).isEqualTo(37);
        assertThat(transfer.getDamagedQuantity()).isEqualTo(3);
        assertThat(inventoryService.findByProductAndLocation(product.getId(), store.getId()).getOnHand())
                .isEqualTo(37);

        // Verify source ledger has TRANSFER_OUT (-40) and destination ledger has two TRANSFER_IN (+25, +12)
        List<InventoryTransaction> srcLedger = transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(sourceInv.getId());
        assertThat(srcLedger).extracting(InventoryTransaction::getType)
                .containsExactly(InventoryTransactionType.INITIAL_STOCK, InventoryTransactionType.TRANSFER_OUT);

        Inventory destInv = inventoryService.findByProductAndLocation(product.getId(), store.getId());
        List<InventoryTransaction> destLedger = transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(destInv.getId());
        assertThat(destLedger).extracting(InventoryTransaction::getType)
                .containsExactly(
                        InventoryTransactionType.INITIAL_STOCK,
                        InventoryTransactionType.TRANSFER_IN,
                        InventoryTransactionType.TRANSFER_IN
                );
    }

    private Product createProduct() {
        String u = UUID.randomUUID().toString();
        Product p = new Product();
        p.setSku("M2-SKU-" + u);
        p.setName("Transfer Product");
        p.setCategory("GROCERY");
        p.setUnitPrice(new BigDecimal("9.99"));
        return productRepository.saveAndFlush(p);
    }

    private Location createLocation(LocationType type) {
        String u = UUID.randomUUID().toString();
        Location l = new Location();
        l.setCode(type.name() + "-" + u.substring(0, 8));
        l.setName("Test " + type.name());
        l.setType(type);
        return locationRepository.saveAndFlush(l);
    }
}
