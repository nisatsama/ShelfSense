package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationService;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductService;
import com.nisatsama.shelfsense.reservation.ReservationRepository;
import com.nisatsama.shelfsense.reservation.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InventoryLedgerAtomicityIntegrationTests {

    private static final String TEST_CONSTRAINT_PREFIX = "chk_ledger_failure_";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductService productService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void ledgerConstraintViolationRollsBackReservationAndInventoryMutation() {
        Fixture fixture = createFixture();
        String testConstraint = TEST_CONSTRAINT_PREFIX
                + UUID.randomUUID().toString().replace("-", "");
        try {
            jdbcTemplate.execute("ALTER TABLE inventory_transactions ADD CONSTRAINT "
                    + testConstraint
                    + " CHECK (inventory_id <> " + fixture.inventoryId()
                    + " OR type <> 'RESERVATION_CREATED')");

            assertThatThrownBy(() -> reservationService.create(
                    fixture.productId(), fixture.locationId(), 4
            )).isInstanceOf(DataIntegrityViolationException.class);

            Inventory persistedInventory = inventoryRepository.findById(fixture.inventoryId())
                    .orElseThrow();
            assertThat(persistedInventory.getOnHand()).isEqualTo(10);
            assertThat(persistedInventory.getReserved()).isZero();
            assertThat(persistedInventory.getAvailable()).isEqualTo(10);
            assertThat(reservationRepository.findByProductIdAndLocationId(
                    fixture.productId(), fixture.locationId()
            )).isEmpty();

            List<InventoryTransaction> entries = transactionRepository
                    .findByInventoryIdOrderByCreatedAtAscIdAsc(fixture.inventoryId());
            assertThat(entries)
                    .extracting(InventoryTransaction::getType)
                    .containsExactly(InventoryTransactionType.INITIAL_STOCK);
            assertThat(entries.getFirst().getOnHandDelta()).isEqualTo(10);
            assertThat(entries.getFirst().getReservedDelta()).isZero();
        } finally {
            try {
                jdbcTemplate.execute("ALTER TABLE inventory_transactions DROP CONSTRAINT IF EXISTS "
                        + testConstraint);
            } finally {
                cleanupFixture(fixture);
            }
        }
    }

    private Fixture createFixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String unique = UUID.randomUUID().toString();
            Product product = new Product();
            product.setSku("LEDGER-ROLLBACK-" + unique);
            product.setName("Ledger Rollback Product");
            product.setCategory("TEST");
            product.setUnitPrice(new BigDecimal("1.00"));
            product = productService.create(product);

            Location location = new Location();
            location.setCode("LEDGER-ROLLBACK-" + unique);
            location.setName("Ledger Rollback Location");
            location.setType(LocationType.STORE);
            location = locationService.create(location);

            Inventory inventory = inventoryService.create(product.getId(), location.getId(), 10);
            return new Fixture(product.getId(), location.getId(), inventory.getId());
        });
    }

    private void cleanupFixture(Fixture fixture) {
        jdbcTemplate.update("DELETE FROM inventory_transactions WHERE inventory_id = ?",
                fixture.inventoryId());
        jdbcTemplate.update("DELETE FROM reservations WHERE product_id = ? AND location_id = ?",
                fixture.productId(), fixture.locationId());
        jdbcTemplate.update("DELETE FROM inventory WHERE id = ?", fixture.inventoryId());
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", fixture.productId());
        jdbcTemplate.update("DELETE FROM locations WHERE id = ?", fixture.locationId());
    }

    private record Fixture(Long productId, Long locationId, Long inventoryId) {
    }
}
