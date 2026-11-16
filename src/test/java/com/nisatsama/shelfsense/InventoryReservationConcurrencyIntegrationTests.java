package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationService;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.product.ProductService;
import com.nisatsama.shelfsense.reservation.Reservation;
import com.nisatsama.shelfsense.reservation.ReservationRepository;
import com.nisatsama.shelfsense.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReservationConcurrencyIntegrationTests {

    private static final int REQUEST_COUNT = 100;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final long FUTURE_TIMEOUT_SECONDS = 40;

    @LocalServerPort
    private int port;

    @Autowired
    private ProductService productService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

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
    void concurrentReservationsNeverReserveMoreThanAvailableStock() throws Exception {
        Fixture fixture = createFixture();
        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        List<Future<HttpResponse<String>>> futures = new ArrayList<>(REQUEST_COUNT);
        boolean workersTerminated = false;

        try {
            String requestBody = "{\"productId\":" + fixture.productId()
                    + ",\"locationId\":" + fixture.locationId()
                    + ",\"quantity\":1}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/reservations"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent request start");
                    }
                    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }

            boolean allWorkersReady = ready.await(30, TimeUnit.SECONDS);
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>(REQUEST_COUNT);
            List<String> workerErrors = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    responses.add(futures.get(i).get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    workerErrors.add("request " + i + ": " + exception.getCause());
                } catch (TimeoutException exception) {
                    futures.get(i).cancel(true);
                    workerErrors.add("request " + i + " exceeded the future timeout");
                }
            }

            Map<Integer, Long> statusCounts = new TreeMap<>();
            List<Long> successfulReservationIds = new ArrayList<>();
            List<String> unexpectedStatuses = new ArrayList<>();
            for (HttpResponse<String> response : responses) {
                int status = response.statusCode();
                statusCounts.merge(status, 1L, Long::sum);
                if (status == HttpStatus.CREATED.value()) {
                    successfulReservationIds.add(jsonLong(response.body(), "id"));
                    assertThat(jsonString(response.body(), "status")).isEqualTo("ACTIVE");
                    assertThat(jsonLong(response.body(), "quantity")).isEqualTo(1);
                    assertThat(jsonLong(response.body(), "productId")).isEqualTo(fixture.productId());
                    assertThat(jsonLong(response.body(), "locationId")).isEqualTo(fixture.locationId());
                } else if (status != HttpStatus.BAD_REQUEST.value()
                        && status != HttpStatus.CONFLICT.value()) {
                    unexpectedStatuses.add(status + " " + response.body());
                }
            }
            System.out.println("Concurrent reservation HTTP status counts: " + statusCounts);
            System.out.println("Concurrent reservation worker errors: " + workerErrors);

            DatabaseState state = readDatabaseState(fixture);
            System.out.println("Final concurrency test inventory: onHand=" + state.inventory().getOnHand()
                    + ", reserved=" + state.inventory().getReserved()
                    + ", available=" + state.inventory().getAvailable()
                    + ", persistedReservations=" + state.reservations().size()
                    + ", ledgerEntries=" + state.transactions().size());

            assertThat(allWorkersReady).as("all request workers reach the start latch").isTrue();
            assertThat(workerErrors).as("all HTTP requests complete without timeout or transport errors")
                    .isEmpty();
            assertThat(unexpectedStatuses).as("no unexpected status or HTTP 5xx response")
                    .isEmpty();
            assertThat(statusCounts.getOrDefault(HttpStatus.CREATED.value(), 0L))
                    .as("successful reservations")
                    .isEqualTo(10);
            assertThat(statusCounts.getOrDefault(HttpStatus.BAD_REQUEST.value(), 0L)
                    + statusCounts.getOrDefault(HttpStatus.CONFLICT.value(), 0L))
                    .as("rejected reservations")
                    .isEqualTo(90);
            assertThat(responses).hasSize(REQUEST_COUNT);
            assertThat(successfulReservationIds)
                    .hasSize(10)
                    .doesNotContain(0L);
            assertThat(new HashSet<>(successfulReservationIds))
                    .as("successful response reservation IDs are unique")
                    .hasSameSizeAs(successfulReservationIds);

            assertThat(state.reservations()).hasSize(10);
            assertThat(state.reservations())
                    .allSatisfy(reservation -> {
                        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
                        assertThat(reservation.getQuantity()).isEqualTo(1);
                    });
            Set<Long> persistedIds = state.reservations().stream()
                    .map(Reservation::getId)
                    .collect(Collectors.toSet());
            assertThat(persistedIds)
                    .as("successful response IDs match persisted reservations")
                    .isEqualTo(new HashSet<>(successfulReservationIds));
            assertThat(state.transactions()).hasSize(11);
            assertThat(state.transactions())
                    .extracting(InventoryTransaction::getType)
                    .containsExactly(
                            InventoryTransactionType.INITIAL_STOCK,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED,
                            InventoryTransactionType.RESERVATION_CREATED
                    );
            assertThat(state.transactions().getFirst().getOnHandDelta()).isEqualTo(10);
            assertThat(state.transactions().getFirst().getReservedDelta()).isZero();
            assertThat(state.transactions().subList(1, state.transactions().size()))
                    .allSatisfy(transaction -> {
                        assertThat(transaction.getInventory().getId()).isEqualTo(fixture.inventoryId());
                        assertThat(transaction.getOnHandDelta()).isZero();
                        assertThat(transaction.getReservedDelta()).isEqualTo(1);
                        assertThat(transaction.getReservationId()).isNotNull();
                    });
            assertThat(state.inventory().getOnHand()).isEqualTo(10);
            assertThat(state.inventory().getReserved()).isEqualTo(10);
            assertThat(state.inventory().getAvailable()).isZero();
        } finally {
            start.countDown();
            executor.shutdown();
            try {
                workersTerminated = executor.awaitTermination(45, TimeUnit.SECONDS);
                if (!workersTerminated) {
                    executor.shutdownNow();
                    workersTerminated = executor.awaitTermination(15, TimeUnit.SECONDS);
                }
            } catch (InterruptedException exception) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            cleanupFixture(fixture);
            assertThat(workersTerminated)
                    .as("all HTTP worker tasks finish before fixture cleanup")
                    .isTrue();
        }
    }

    private Fixture createFixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();

            Product product = new Product();
            product.setSku("CONCURRENCY-" + suffix);
            product.setName("Concurrency Integration Product");
            product.setCategory("TEST");
            product.setUnitPrice(new BigDecimal("1.00"));
            product = productService.create(product);

            Location location = new Location();
            location.setCode("CONCURRENCY-" + suffix);
            location.setName("Concurrency Integration Store");
            location.setType(LocationType.STORE);
            location = locationService.create(location);

            Inventory inventory = inventoryService.create(product.getId(), location.getId(), 10);
            return new Fixture(product.getId(), location.getId(), inventory.getId());
        });
    }

    private DatabaseState readDatabaseState(Fixture fixture) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Inventory inventory = inventoryRepository.findById(fixture.inventoryId())
                    .orElseThrow(() -> new AssertionError("Test inventory was not persisted"));
            List<Reservation> reservations = reservationRepository.findByProductIdAndLocationId(
                    fixture.productId(), fixture.locationId()
            );
            List<InventoryTransaction> transactions = transactionRepository
                    .findByInventoryIdOrderByCreatedAtAscIdAsc(fixture.inventoryId());
            return new DatabaseState(inventory, reservations, transactions);
        });
    }

    private void cleanupFixture(Fixture fixture) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            reservationRepository.findByProductIdAndLocationId(
                    fixture.productId(), fixture.locationId()
            ).forEach(reservationRepository::delete);
            jdbcTemplate.update("DELETE FROM inventory_transactions WHERE inventory_id = ?", fixture.inventoryId());
            inventoryRepository.findById(fixture.inventoryId())
                    .ifPresent(inventoryRepository::delete);
            productRepository.deleteById(fixture.productId());
            locationRepository.deleteById(fixture.locationId());
        });
    }

    private static long jsonLong(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*(\\d+)").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Response did not contain numeric JSON field " + field + ": " + body);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String jsonString(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Response did not contain string JSON field " + field + ": " + body);
        }
        return matcher.group(1);
    }

    private record Fixture(Long productId, Long locationId, Long inventoryId) {
    }

    private record DatabaseState(
            Inventory inventory,
            List<Reservation> reservations,
            List<InventoryTransaction> transactions
    ) {
    }
}
