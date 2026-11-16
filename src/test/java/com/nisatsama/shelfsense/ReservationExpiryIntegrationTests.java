package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationService;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductService;
import com.nisatsama.shelfsense.reservation.Reservation;
import com.nisatsama.shelfsense.reservation.ReservationExpiryProcessor;
import com.nisatsama.shelfsense.reservation.ReservationExpiryService;
import com.nisatsama.shelfsense.reservation.ReservationRepository;
import com.nisatsama.shelfsense.reservation.ReservationService;
import com.nisatsama.shelfsense.reservation.ReservationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "shelfsense.reservations.expiry.scheduling-enabled=false"
)
@Import(ReservationExpiryIntegrationTests.FixedClockConfiguration.class)
class ReservationExpiryIntegrationTests {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2032, 4, 5, 12, 0);
    private static final String TEST_CONSTRAINT_PREFIX = "chk_expiry_failure_";

    @Autowired private Clock clock;
    @Autowired private InventoryService inventoryService;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationExpiryService expiryService;
    @Autowired private ReservationExpiryProcessor expiryProcessor;
    @Autowired private ProductService productService;
    @Autowired private LocationService locationService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @LocalServerPort private int port;

    private final List<Fixture> fixtures = new ArrayList<>();
    private Long httpTestAccountId;

    @AfterEach
    void cleanupFixtures() {
        try {
            for (Fixture fixture : fixtures) {
                deleteFixture(fixture);
            }
        } finally {
            fixtures.clear();
            if (httpTestAccountId != null) {
                Long accountId = httpTestAccountId;
                accountRepository.deleteById(accountId);
                httpTestAccountId = null;
                assertThat(accountRepository.findById(accountId)).isEmpty();
            }
        }
    }

    @Test
    void creationUsesOneFixedTimestampAndExpiresExactlyTenMinutesLater() {
        assertThat(LocalDateTime.now(clock)).isEqualTo(FIXED_NOW);
        Fixture fixture = createFixture(5);
        Reservation reservation = createReservation(fixture, 2);

        assertThat(reservation.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(reservation.getExpiresAt()).isEqualTo(FIXED_NOW.plusMinutes(10));

        Reservation persisted = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(persisted.getExpiresAt()).isEqualTo(FIXED_NOW.plusMinutes(10));
    }

    @Test
    void beforeDeadlineReservationIsNotExpiredAndAtDeadlineReservationIsExpired() {
        Fixture fixture = createFixture(10);
        Reservation before = createReservation(fixture, 3);
        Reservation due = createReservation(fixture, 2);
        setExpiry(before.getId(), FIXED_NOW.plusSeconds(1));
        setExpiry(due.getId(), FIXED_NOW);

        assertThat(expiryService.expireIfDue(before.getId())).isFalse();
        assertThat(expiryProcessor.processDueBatch()).isEqualTo(1);

        assertThat(reservationRepository.findById(before.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservationRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertInventory(fixture, 10, 3);
        assertThat(expiryEntries(due.getId())).hasSize(1);
        assertThat(expiryEntries(before.getId())).isEmpty();
    }

    @Test
    void successfulExpiryWritesExactLedgerDeltasAndLegacyFields() {
        Fixture fixture = createFixture(12);
        Reservation reservation = createReservation(fixture, 4);
        setExpiry(reservation.getId(), FIXED_NOW);

        assertThat(expiryService.expireIfDue(reservation.getId())).isTrue();

        Reservation expired = reservationRepository.findById(reservation.getId()).orElseThrow();
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        InventoryTransaction ledger = expiryEntries(reservation.getId()).getFirst();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory.getOnHand()).isEqualTo(12);
        assertThat(inventory.getReserved()).isZero();
        assertThat(inventory.getAvailable()).isEqualTo(12);
        assertThat(ledger.getType()).isEqualTo(InventoryTransactionType.RESERVATION_EXPIRED);
        assertThat(ledger.getOnHandDelta()).isZero();
        assertThat(ledger.getReservedDelta()).isEqualTo(-4);
        assertThat(ledger.getQuantity()).isEqualTo(4);
        assertThat(ledger.getReferenceId()).isEqualTo(reservation.getId());
        assertThat(ledger.getReservationId()).isEqualTo(reservation.getId());
        assertThat(ledger.getInventory().getId()).isEqualTo(fixture.inventoryId());
    }

    @Test
    void repeatedExpiryProcessingDoesNotChangeStockOrAppendAnotherLedgerRow() {
        Fixture fixture = createFixture(8);
        Reservation reservation = createReservation(fixture, 3);
        setExpiry(reservation.getId(), FIXED_NOW);

        assertThat(expiryService.expireIfDue(reservation.getId())).isTrue();
        assertThat(expiryService.expireIfDue(reservation.getId())).isFalse();

        assertInventory(fixture, 8, 0);
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expiryEntries(reservation.getId())).hasSize(1);
    }

    @Test
    void confirmAndReleaseRejectReservationAtDeadlineWithoutChangingInventory() {
        Fixture fixture = createFixture(10);
        Reservation confirm = createReservation(fixture, 2);
        Reservation release = createReservation(fixture, 3);
        setExpiry(confirm.getId(), FIXED_NOW);
        setExpiry(release.getId(), FIXED_NOW);

        assertThatThrownBy(() -> reservationService.confirm(confirm.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> reservationService.release(release.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");

        assertThat(reservationRepository.findById(confirm.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservationRepository.findById(release.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertInventory(fixture, 10, 5);
        assertThat(expiryEntries(confirm.getId())).isEmpty();
        assertThat(expiryEntries(release.getId())).isEmpty();
    }

    @Test
    void concurrentExpiryAttemptsProduceOneStockReleaseAndOneLedgerEntry() throws Exception {
        Fixture fixture = createFixture(7);
        Reservation reservation = createReservation(fixture, 4);
        setExpiry(reservation.getId(), FIXED_NOW);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> concurrentExpiry(reservation.getId(), ready, start));
            Future<Boolean> second = executor.submit(() -> concurrentExpiry(reservation.getId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean firstResult = first.get(20, TimeUnit.SECONDS);
            boolean secondResult = second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult)).containsExactlyInAnyOrder(true, false);
            assertInventory(fixture, 7, 0);
            assertThat(expiryEntries(reservation.getId())).hasSize(1);
            assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.EXPIRED);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void expiryRacingWithConfirmationSerializesToOneExpiredTransition() throws Exception {
        assertExpiryWinsRaceAgainstLifecycleOperation(true);
    }

    @Test
    void expiryRacingWithReleaseSerializesToOneExpiredTransition() throws Exception {
        assertExpiryWinsRaceAgainstLifecycleOperation(false);
    }

    @Test
    void ledgerConstraintFailureRollsBackExpiryAndProcessorContinuesWithNextReservation() {
        Fixture blockedFixture = createFixture(10);
        Fixture goodFixture = createFixture(10);
        Reservation blocked = createReservation(blockedFixture, 3);
        Reservation good = createReservation(goodFixture, 4);
        setExpiry(blocked.getId(), FIXED_NOW);
        setExpiry(good.getId(), FIXED_NOW);
        String constraint = TEST_CONSTRAINT_PREFIX + UUID.randomUUID().toString().replace("-", "");

        try {
            jdbcTemplate.execute("ALTER TABLE inventory_transactions ADD CONSTRAINT " + constraint
                    + " CHECK (reservation_id <> " + blocked.getId()
                    + " OR type <> 'RESERVATION_EXPIRED')");

            assertThat(expiryProcessor.processDueBatch()).isEqualTo(1);

            Reservation blockedAfterFailure = reservationRepository.findById(blocked.getId()).orElseThrow();
            assertThat(blockedAfterFailure.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            assertInventory(blockedFixture, 10, 3);
            assertThat(expiryEntries(blocked.getId())).isEmpty();
            assertThat(reservationRepository.findById(good.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.EXPIRED);
            assertInventory(goodFixture, 10, 0);
            assertThat(expiryEntries(good.getId())).hasSize(1);
        } finally {
            jdbcTemplate.execute("ALTER TABLE inventory_transactions DROP CONSTRAINT IF EXISTS " + constraint);
        }

        assertThat(expiryProcessor.processDueBatch()).isEqualTo(1);
        assertThat(reservationRepository.findById(blocked.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertInventory(blockedFixture, 10, 0);
        assertThat(expiryEntries(blocked.getId())).hasSize(1);
    }

    @Test
    void freshPollerRecoversPersistedExpiryAndDrainsBacklogInBoundedBatches() {
        Fixture fixture = createFixture(101);
        List<Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            reservations.add(createReservation(fixture, 1));
        }
        reservations.forEach(reservation -> setExpiry(reservation.getId(), FIXED_NOW));

        // A fresh stateless poller sees committed database state; no in-memory queue is involved.
        ReservationExpiryProcessor freshPoller = new ReservationExpiryProcessor(
                reservationRepository, expiryService, clock);
        assertThat(freshPoller.processDueBatch()).isEqualTo(100);
        assertThat(freshPoller.processDueBatch()).isEqualTo(1);

        assertThat(reservations.stream()
                .map(reservation -> reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .filter(status -> status == ReservationStatus.EXPIRED))
                .hasSize(101);
        assertInventory(fixture, 101, 0);
        for (Reservation reservation : reservations) {
            assertThat(expiryEntries(reservation.getId())).hasSize(1);
        }
    }

    @Test
    void rotatingCursorSkipsRepeatedlyFailingFirstHundredAndEventuallyProcessesThem() throws Exception {
        Fixture fixture = createFixture(101);
        List<Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            reservations.add(createReservation(fixture, 1));
        }
        reservations.forEach(reservation -> setExpiry(reservation.getId(), FIXED_NOW));

        List<Long> reservationIds = reservations.stream().map(Reservation::getId).toList();
        List<Long> firstHundred = reservationIds.subList(0, 100);
        assertThat(reservationRepository.findDueReservationIdsAfter(
                FIXED_NOW, 0L, PageRequest.of(0, 100)))
                .containsExactlyElementsOf(firstHundred);

        ReservationExpiryProcessor rotatingPoller = new ReservationExpiryProcessor(
                reservationRepository, expiryService, clock);
        String constraint = TEST_CONSTRAINT_PREFIX + UUID.randomUUID().toString().replace("-", "");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            long lastFailingId = firstHundred.getLast();
            jdbcTemplate.execute("ALTER TABLE inventory_transactions ADD CONSTRAINT " + constraint
                    + " CHECK (reservation_id > " + lastFailingId
                    + " OR type <> 'RESERVATION_EXPIRED')");

            Future<Integer> firstPoll = executor.submit(() -> concurrentPoll(rotatingPoller, ready, start));
            Future<Integer> secondPoll = executor.submit(() -> concurrentPoll(rotatingPoller, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstPoll.get(30, TimeUnit.SECONDS), secondPoll.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);

            assertThat(reservationRepository.findById(reservationIds.getLast()).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.EXPIRED);
            assertThat(expiryEntries(reservationIds.getLast())).hasSize(1);
            assertInventory(fixture, 101, 100);
            for (Long reservationId : firstHundred) {
                assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                        .isEqualTo(ReservationStatus.ACTIVE);
                assertThat(expiryEntries(reservationId)).isEmpty();
            }

            // Wrapping reaches the failing page again, proving failures do not stop the batch.
            assertThat(rotatingPoller.processDueBatch()).isZero();
            assertInventory(fixture, 101, 100);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.execute("ALTER TABLE inventory_transactions DROP CONSTRAINT IF EXISTS " + constraint);
        }

        // The cursor is at the end of the failing page, so wrap processes that page after recovery.
        assertThat(rotatingPoller.processDueBatch()).isEqualTo(100);
        assertThat(rotatingPoller.processDueBatch()).isZero();
        assertInventory(fixture, 101, 0);
        for (Long reservationId : reservationIds) {
            assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.EXPIRED);
            assertThat(expiryEntries(reservationId)).hasSize(1);
        }
    }

    private int concurrentPoll(ReservationExpiryProcessor processor, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent poll start timed out");
        }
        return processor.processDueBatch();
    }
    @Test
    void reservationHttpResponseIncludesPopulatedExpiryTimestamp() throws Exception {
        Fixture fixture = createFixture(6);
        String requestBody = "{\"productId\":" + fixture.productId()
                + ",\"locationId\":" + fixture.locationId()
                + ",\"quantity\":2}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/reservations"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + loginForHttp())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.body()).contains("\"status\":\"ACTIVE\"");
        Matcher expiresAt = Pattern.compile("\\\"expiresAt\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        assertThat(expiresAt.find()).isTrue();
        assertThat(expiresAt.group(1)).isEqualTo(FIXED_NOW.plusMinutes(10).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
    }

    private String loginForHttp() throws Exception {
        String login = "expiry-http-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString() + UUID.randomUUID();
        Account account = new Account();
        account.setLoginIdentifier(login);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(AccountRole.USER);
        accountRepository.saveAndFlush(account);
        httpTestAccountId = account.getId();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginIdentifier\":\"" + login
                        + "\",\"password\":\"" + password + "\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        Matcher token = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"").matcher(response.body());
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

    private void assertExpiryWinsRaceAgainstLifecycleOperation(boolean confirmation) throws Exception {
        Fixture fixture = createFixture(10);
        Reservation reservation = createReservation(fixture, 4);
        setExpiry(reservation.getId(), FIXED_NOW);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> expiry = executor.submit(() -> concurrentExpiry(reservation.getId(), ready, start));
            Future<Boolean> lifecycle = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Lifecycle race start timed out");
                }
                try {
                    if (confirmation) {
                        reservationService.confirm(reservation.getId());
                    } else {
                        reservationService.release(reservation.getId());
                    }
                    return true;
                } catch (IllegalArgumentException rejected) {
                    return false;
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(expiry.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycle.get(20, TimeUnit.SECONDS)).isFalse();

            assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.EXPIRED);
            assertInventory(fixture, 10, 0);
            assertThat(expiryEntries(reservation.getId())).hasSize(1);
            List<InventoryTransaction> lifecycleEntries = transactionRepository
                    .findByReservationId(reservation.getId()).stream()
                    .filter(entry -> entry.getType() != InventoryTransactionType.RESERVATION_CREATED)
                    .toList();
            assertThat(lifecycleEntries)
                    .extracting(InventoryTransaction::getType)
                    .containsExactly(InventoryTransactionType.RESERVATION_EXPIRED);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean concurrentExpiry(Long reservationId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Expiry race start timed out");
        }
        return expiryService.expireIfDue(reservationId);
    }

    private Fixture createFixture(int onHand) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            String unique = UUID.randomUUID().toString();
            Product product = new Product();
            product.setSku("EXPIRY-" + unique);
            product.setName("Expiry Integration Product");
            product.setCategory("TEST");
            product.setUnitPrice(new BigDecimal("1.00"));
            product = productService.create(product);

            Location location = new Location();
            location.setCode("EXPIRY-" + unique);
            location.setName("Expiry Integration Location");
            location.setType(LocationType.STORE);
            location = locationService.create(location);

            Inventory inventory = inventoryService.create(product.getId(), location.getId(), onHand);
            return new Fixture(product.getId(), location.getId(), inventory.getId());
        });
        fixtures.add(fixture);
        return fixture;
    }

    private Reservation createReservation(Fixture fixture, int quantity) {
        return reservationService.create(fixture.productId(), fixture.locationId(), quantity);
    }

    private void setExpiry(Long reservationId, LocalDateTime expiresAt) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
            reservation.setExpiresAt(expiresAt);
        });
    }

    private List<InventoryTransaction> expiryEntries(Long reservationId) {
        return transactionRepository.findByReservationId(reservationId).stream()
                .filter(entry -> entry.getType() == InventoryTransactionType.RESERVATION_EXPIRED)
                .toList();
    }

    private void assertInventory(Fixture fixture, int onHand, int reserved) {
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getOnHand()).isEqualTo(onHand);
        assertThat(inventory.getReserved()).isEqualTo(reserved);
        assertThat(inventory.getAvailable()).isEqualTo(onHand - reserved);
    }

    private void deleteFixture(Fixture fixture) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM inventory_transactions WHERE inventory_id = ?", fixture.inventoryId());
            jdbcTemplate.update("DELETE FROM reservations WHERE product_id = ? AND location_id = ?",
                    fixture.productId(), fixture.locationId());
            jdbcTemplate.update("DELETE FROM inventory WHERE id = ?", fixture.inventoryId());
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", fixture.productId());
            jdbcTemplate.update("DELETE FROM locations WHERE id = ?", fixture.locationId());
        });
    }

    private record Fixture(Long productId, Long locationId, Long inventoryId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedReservationClock() {
            return Clock.fixed(Instant.parse("2032-04-05T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
