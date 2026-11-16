package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.reservation.Reservation;
import com.nisatsama.shelfsense.reservation.ReservationRepository;
import com.nisatsama.shelfsense.reservation.ReservationService;
import com.nisatsama.shelfsense.reservation.ReservationStatus;
import jakarta.persistence.EntityManager;
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
class InventoryLedgerServiceIntegrationTests {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void inventoryCreationWritesOneInitialStockEntryForPositiveAndZeroQuantities() {
        Inventory stocked = createInventory(7);
        Inventory empty = createInventory(0);

        assertEntry(stocked, 0, InventoryTransactionType.INITIAL_STOCK, 7, 0, 7, null);
        assertEntry(empty, 0, InventoryTransactionType.INITIAL_STOCK, 0, 0, 0, null);
    }

    @Test
    void reservationCreationWritesSignedDeltasAndReservationReference() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertEntry(inventory, 0, InventoryTransactionType.INITIAL_STOCK, 10, 0, 10, null);
        assertEntry(inventory, 1, InventoryTransactionType.RESERVATION_CREATED,
                0, 4, 4, reservation.getId());
    }

    @Test
    void reservationReleaseWritesNegativeReservedDelta() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );

        reservationService.release(reservation.getId());

        assertThat(reservationService.findById(reservation.getId()).getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertEntry(inventory, 2, InventoryTransactionType.RESERVATION_RELEASED,
                0, -4, 4, reservation.getId());
    }

    @Test
    void reservationConfirmationWritesNegativeOnHandAndReservedDeltas() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );

        reservationService.confirm(reservation.getId());

        assertThat(reservationService.findById(reservation.getId()).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertEntry(inventory, 2, InventoryTransactionType.RESERVATION_CONFIRMED,
                -4, -4, 4, reservation.getId());
    }

    @Test
    void rejectedReservationOperationsDoNotWriteLedgerEntries() {
        Inventory inventory = createInventory(2);

        assertThatThrownBy(() -> reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 3
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient inventory");
        assertThatThrownBy(() -> reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        entityManager.flush();
        entityManager.clear();
        assertOnlyInitialStockEntry(inventory, 2);
        assertThat(reservationRepository.findByProductIdAndLocationId(
                inventory.getProduct().getId(), inventory.getLocation().getId()
        )).isEmpty();
    }

    @Test
    void repeatedTerminalReservationOperationsDoNotWriteAdditionalLedgerEntries() {
        Inventory inventory = createInventory(6);
        Reservation confirmed = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 2
        );
        reservationService.confirm(confirmed.getId());
        assertThatThrownBy(() -> reservationService.confirm(confirmed.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        Reservation released = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 1
        );
        reservationService.release(released.getId());
        assertThatThrownBy(() -> reservationService.release(released.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        List<InventoryTransaction> entries = entriesFor(inventory);
        assertThat(entries)
                .extracting(InventoryTransaction::getType)
                .containsExactly(
                        InventoryTransactionType.INITIAL_STOCK,
                        InventoryTransactionType.RESERVATION_CREATED,
                        InventoryTransactionType.RESERVATION_CONFIRMED,
                        InventoryTransactionType.RESERVATION_CREATED,
                        InventoryTransactionType.RESERVATION_RELEASED
                );
    }

    private Inventory createInventory(int onHand) {
        String unique = UUID.randomUUID().toString();
        Product product = new Product();
        product.setSku("LEDGER-SERVICE-" + unique);
        product.setName("Ledger Service Integration Product");
        product.setCategory("TEST");
        product.setUnitPrice(new BigDecimal("1.00"));
        product = productRepository.saveAndFlush(product);

        Location location = new Location();
        location.setCode("LEDGER-SERVICE-" + unique);
        location.setName("Ledger Service Integration Location");
        location.setType(LocationType.STORE);
        location = locationRepository.saveAndFlush(location);

        return inventoryService.create(product.getId(), location.getId(), onHand);
    }

    private void assertOnlyInitialStockEntry(Inventory inventory, int initialQuantity) {
        List<InventoryTransaction> entries = entriesFor(inventory);
        assertThat(entries).hasSize(1);
        assertEntry(entries.getFirst(), inventory, InventoryTransactionType.INITIAL_STOCK,
                initialQuantity, 0, initialQuantity, null);
    }

    private void assertEntry(
            Inventory inventory,
            int index,
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            int legacyQuantity,
            Long reservationId
    ) {
        List<InventoryTransaction> entries = entriesFor(inventory);
        assertThat(entries).hasSizeGreaterThan(index);
        assertEntry(entries.get(index), inventory, type, onHandDelta,
                reservedDelta, legacyQuantity, reservationId);
    }

    private void assertEntry(
            InventoryTransaction entry,
            Inventory inventory,
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            int legacyQuantity,
            Long reservationId
    ) {
        assertThat(entry.getType()).isEqualTo(type);
        assertThat(entry.getInventory().getId()).isEqualTo(inventory.getId());
        assertThat(entry.getOnHandDelta()).isEqualTo(onHandDelta);
        assertThat(entry.getReservedDelta()).isEqualTo(reservedDelta);
        assertThat(entry.getQuantity()).isEqualTo(legacyQuantity);
        assertThat(entry.getReservationId()).isEqualTo(reservationId);
        assertThat(entry.getReferenceId()).isEqualTo(reservationId);
    }

    private List<InventoryTransaction> entriesFor(Inventory inventory) {
        entityManager.flush();
        return transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(inventory.getId());
    }
}
