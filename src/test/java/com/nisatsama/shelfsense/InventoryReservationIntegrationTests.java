package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InventoryReservationIntegrationTests {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createReservationWithSufficientStockReservesQuantity() {
        Inventory inventory = createInventory(10);

        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(),
                inventory.getLocation().getId(),
                4
        );

        entityManager.flush();
        entityManager.clear();

        Reservation persistedReservation = reservationService.findById(reservation.getId());
        Inventory persistedInventory = findInventory(inventory);

        assertThat(persistedReservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(persistedInventory.getOnHand()).isEqualTo(10);
        assertThat(persistedInventory.getReserved()).isEqualTo(4);
        assertThat(persistedInventory.getAvailable()).isEqualTo(6);
    }

    @Test
    void createReservationWithInsufficientStockLeavesInventoryUnchanged() {
        Inventory inventory = createInventory(3);

        assertThatThrownBy(() -> reservationService.create(
                inventory.getProduct().getId(),
                inventory.getLocation().getId(),
                4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient inventory");

        entityManager.flush();
        entityManager.clear();
        assertInventory(inventory, 3, 0, 3);
        assertThat(reservationRepository.findByProductIdAndLocationId(
                inventory.getProduct().getId(), inventory.getLocation().getId()
        )).isEmpty();
    }

    @Test
    void createReservationWithZeroQuantityIsRejectedWithoutChangingInventory() {
        Inventory inventory = createInventory(5);

        assertThatThrownBy(() -> reservationService.create(
                inventory.getProduct().getId(),
                inventory.getLocation().getId(),
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        entityManager.flush();
        entityManager.clear();
        assertInventory(inventory, 5, 0, 5);
        assertThat(reservationRepository.findByProductIdAndLocationId(
                inventory.getProduct().getId(), inventory.getLocation().getId()
        )).isEmpty();
    }

    @Test
    void createReservationWithNegativeQuantityIsRejectedWithoutChangingInventory() {
        Inventory inventory = createInventory(5);

        assertThatThrownBy(() -> reservationService.create(
                inventory.getProduct().getId(),
                inventory.getLocation().getId(),
                -2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        entityManager.flush();
        entityManager.clear();
        assertInventory(inventory, 5, 0, 5);
        assertThat(reservationRepository.findByProductIdAndLocationId(
                inventory.getProduct().getId(), inventory.getLocation().getId()
        )).isEmpty();
    }

    @Test
    void confirmActiveReservationConsumesOnHandAndReservedQuantity() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );

        Reservation confirmed = reservationService.confirm(reservation.getId());

        entityManager.flush();
        entityManager.clear();

        assertThat(reservationService.findById(confirmed.getId()).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertInventory(inventory, 6, 0, 6);
    }

    @Test
    void releaseActiveReservationFreesReservedQuantityAndPreservesOnHand() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );

        Reservation released = reservationService.release(reservation.getId());

        entityManager.flush();
        entityManager.clear();

        assertThat(reservationService.findById(released.getId()).getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertInventory(inventory, 10, 0, 10);
    }

    @Test
    void confirmingAlreadyConfirmedReservationIsRejectedWithoutChangingInventoryAgain() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );
        reservationService.confirm(reservation.getId());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> reservationService.confirm(reservation.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only ACTIVE reservations can be confirmed");

        entityManager.clear();
        assertThat(reservationService.findById(reservation.getId()).getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertInventory(inventory, 6, 0, 6);
    }

    @Test
    void releasingAlreadyReleasedReservationIsRejectedWithoutChangingInventoryAgain() {
        Inventory inventory = createInventory(10);
        Reservation reservation = reservationService.create(
                inventory.getProduct().getId(), inventory.getLocation().getId(), 4
        );
        reservationService.release(reservation.getId());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> reservationService.release(reservation.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only ACTIVE reservations can be released");

        entityManager.clear();
        assertThat(reservationService.findById(reservation.getId()).getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertInventory(inventory, 10, 0, 10);
    }

    private Inventory createInventory(int onHand) {
        String unique = UUID.randomUUID().toString();

        Product product = new Product();
        product.setSku("TEST-" + unique);
        product.setName("Integration Test Product");
        product.setCategory("TEST");
        product.setUnitPrice(new BigDecimal("1.00"));
        product = productRepository.saveAndFlush(product);

        Location location = new Location();
        location.setCode("TEST-" + unique);
        location.setName("Integration Test Store");
        location.setType(LocationType.STORE);
        location = locationRepository.saveAndFlush(location);

        return inventoryService.create(product.getId(), location.getId(), onHand);
    }

    private Inventory findInventory(Inventory inventory) {
        return inventoryService.findByProductAndLocation(
                inventory.getProduct().getId(), inventory.getLocation().getId()
        );
    }

    private void assertInventory(Inventory inventory, int onHand, int reserved, int available) {
        Inventory persistedInventory = findInventory(inventory);
        assertThat(persistedInventory.getOnHand()).isEqualTo(onHand);
        assertThat(persistedInventory.getReserved()).isEqualTo(reserved);
        assertThat(persistedInventory.getAvailable()).isEqualTo(available);
    }
}
