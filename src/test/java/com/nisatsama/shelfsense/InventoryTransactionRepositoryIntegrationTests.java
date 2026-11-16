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

@SpringBootTest
@Transactional
class InventoryTransactionRepositoryIntegrationTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void appendsAndReadsLedgerEntriesByInventoryAndReservation() {
        String suffix = UUID.randomUUID().toString();
        Product product = new Product();
        product.setSku("LEDGER-" + suffix);
        product.setName("Ledger Integration Product");
        product.setCategory("TEST");
        product.setUnitPrice(new BigDecimal("1.00"));
        product = productRepository.saveAndFlush(product);

        Location location = new Location();
        location.setCode("LEDGER-" + suffix);
        location.setName("Ledger Integration Location");
        location.setType(LocationType.STORE);
        location = locationRepository.saveAndFlush(location);

        Inventory inventory = inventoryService.create(product.getId(), location.getId(), 5);

        Reservation reservation = new Reservation();
        reservation.setProduct(product);
        reservation.setLocation(location);
        reservation.setQuantity(2);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(java.time.LocalDateTime.now());
        reservation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        reservation = reservationRepository.saveAndFlush(reservation);

        InventoryTransaction reserve = InventoryTransaction.create(
                inventory, InventoryTransactionType.RESERVATION_CREATED, 0, 2, reservation.getId()
        );
        inventoryTransactionRepository.append(reserve);
        entityManager.flush();
        entityManager.clear();

        List<InventoryTransaction> byInventory = inventoryTransactionRepository
                .findByInventoryIdOrderByCreatedAtAscIdAsc(inventory.getId());
        List<InventoryTransaction> byReservation = inventoryTransactionRepository
                .findByReservationId(reservation.getId());

        assertThat(byInventory)
                .extracting(InventoryTransaction::getType)
                .containsExactly(
                        InventoryTransactionType.INITIAL_STOCK,
                        InventoryTransactionType.RESERVATION_CREATED
                );
        assertThat(byInventory)
                .extracting(InventoryTransaction::getOnHandDelta)
                .containsExactly(5, 0);
        assertThat(byInventory)
                .extracting(InventoryTransaction::getReservedDelta)
                .containsExactly(0, 2);
        assertThat(byInventory)
                .extracting(InventoryTransaction::getQuantity)
                .containsExactly(5, 2);
        assertThat(byInventory.get(1).getReferenceId()).isEqualTo(reservation.getId());
        assertThat(byInventory.get(1).getReservationId()).isEqualTo(reservation.getId());
        assertThat(byReservation)
                .extracting(InventoryTransaction::getId)
                .containsExactly(byInventory.get(1).getId());
    }
}
