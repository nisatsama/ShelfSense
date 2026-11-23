package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.forecast.ForecastService;
import com.nisatsama.shelfsense.forecast.MlServiceClient;
import com.nisatsama.shelfsense.forecast.dto.ForecastGenerateRequest;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ForecastDemandExtractionTests {

    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryTransactionRepository transactionRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ForecastService forecastService;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void demandExtractionExcludesTransfersAndAdjustmentsAndAvoidsDoubleCounting() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product();
        product.setSku("DEMAND-SKU-" + u);
        product.setName("Demand Test Product");
        product.setCategory("FOODS");
        product.setUnitPrice(new BigDecimal("12.00"));
        product = productRepository.saveAndFlush(product);

        Location warehouse = new Location();
        warehouse.setCode("WH-" + u);
        warehouse.setName("Central Distribution Center");
        warehouse.setType(LocationType.WAREHOUSE);
        warehouse = locationRepository.saveAndFlush(warehouse);

        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setLocation(warehouse);
        inv.setOnHand(500);
        inv.setReserved(0);
        inv = inventoryRepository.saveAndFlush(inv);

        // 1. Inflows and initial stock (must NOT count as demand)
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.INITIAL_STOCK, 100, 0, null));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.PURCHASE_RECEIVED, 200, 0, null));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.TRANSFER_IN, 50, 0, null));

        // 2. Internal inter-location transfer out (CRITICAL P0: warehouse transfer out must NOT be counted as customer demand)
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.TRANSFER_OUT, -80, 0, null));

        // 3. Inventory adjustment (shrinkage/damaged stock: must NOT count as customer demand)
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.ADJUSTMENT, -10, 0, null));

        // Create actual reservations for foreign key integrity
        Reservation res1 = new Reservation();
        res1.setProduct(product);
        res1.setLocation(warehouse);
        res1.setQuantity(20);
        res1.setStatus(ReservationStatus.ACTIVE);
        res1.setExpiresAt(LocalDateTime.now().plusHours(1));
        res1 = reservationRepository.saveAndFlush(res1);

        Reservation res2 = new Reservation();
        res2.setProduct(product);
        res2.setLocation(warehouse);
        res2.setQuantity(10);
        res2.setStatus(ReservationStatus.EXPIRED);
        res2.setExpiresAt(LocalDateTime.now().minusHours(1));
        res2 = reservationRepository.saveAndFlush(res2);

        Reservation res3 = new Reservation();
        res3.setProduct(product);
        res3.setLocation(warehouse);
        res3.setQuantity(15);
        res3.setStatus(ReservationStatus.CONFIRMED);
        res3.setExpiresAt(LocalDateTime.now().plusHours(1));
        res3 = reservationRepository.saveAndFlush(res3);

        // 4. Reservation holds and releases (must NOT count as demand until confirmed)
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.RESERVATION_CREATED, 0, 20, res1.getId()));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.RESERVATION_RELEASED, 0, -20, res1.getId()));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.RESERVATION_EXPIRED, 0, -10, res2.getId()));

        // 5. Genuine customer demand: Confirmed reservation (committed customer order fulfilled)
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.RESERVATION_CONFIRMED, -15, -15, res3.getId()));

        // 6. Genuine customer demand: Direct sales
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.SALE, -25, 0, null));

        // 7. Duplicate/referenced sale referring to the already confirmed reservation res3
        transactionRepository.append(InventoryTransaction.createWithReference(inv, InventoryTransactionType.SALE, -15, 0, res3.getId()));

        when(mlServiceClient.predict(any())).thenReturn(new MlServiceClient.MlPredictionResult(
                product.getSku(),
                warehouse.getCode(),
                14,
                2.8571,
                40.0,
                "m5-hgbr-v1",
                LocalDateTime.now().toString(),
                List.of(new MlServiceClient.MlDailyPoint(1, "2026-11-24", 2.8571))
        ));

        // When generating forecast without explicit history -> extracts from ledger
        forecastService.generateForecast(new ForecastGenerateRequest(product.getId(), warehouse.getId(), 14, null, null));

        ArgumentCaptor<MlServiceClient.MlPredictionPayload> captor =
                ArgumentCaptor.forClass(MlServiceClient.MlPredictionPayload.class);
        verify(mlServiceClient).predict(captor.capture());

        MlServiceClient.MlPredictionPayload payload = captor.getValue();
        List<Double> extractedHistory = payload.recent_daily_sales();

        assertThat(extractedHistory).isNotNull();
        assertThat(extractedHistory).hasSize(28);

        // Total extracted demand over the 28-day window should ONLY be 15 (reservation confirmed) + 25 (sale) = 40 units!
        // TRANSFER_OUT (-80) and ADJUSTMENT (-10) must be 0!
        // Duplicate sale for reservation 9003L must NOT be double-counted!
        double totalDemand = extractedHistory.stream().mapToDouble(Double::doubleValue).sum();
        assertThat(totalDemand).isEqualTo(40.0);
    }

    @Test
    void warehouseWithOnlyTransfersYieldsZeroCustomerDemand() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product();
        product.setSku("TRANSFER-ONLY-SKU-" + u);
        product.setName("Transfer Only Product");
        product.setCategory("FOODS");
        product.setUnitPrice(new BigDecimal("5.00"));
        product = productRepository.saveAndFlush(product);

        Location warehouse = new Location();
        warehouse.setCode("WH-TRANS-" + u);
        warehouse.setName("Warehouse Transfer Hub");
        warehouse.setType(LocationType.WAREHOUSE);
        warehouse = locationRepository.saveAndFlush(warehouse);

        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setLocation(warehouse);
        inv.setOnHand(1000);
        inv.setReserved(0);
        inv = inventoryRepository.saveAndFlush(inv);

        // Only transfer operations at this warehouse
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.INITIAL_STOCK, 1000, 0, null));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.TRANSFER_OUT, -300, 0, null));
        transactionRepository.append(InventoryTransaction.create(inv, InventoryTransactionType.TRANSFER_OUT, -200, 0, null));

        when(mlServiceClient.predict(any())).thenReturn(new MlServiceClient.MlPredictionResult(
                product.getSku(),
                warehouse.getCode(),
                14,
                0.0,
                0.0,
                "m5-hgbr-v1",
                LocalDateTime.now().toString(),
                List.of()
        ));

        forecastService.generateForecast(new ForecastGenerateRequest(product.getId(), warehouse.getId(), 14, null, null));

        ArgumentCaptor<MlServiceClient.MlPredictionPayload> captor =
                ArgumentCaptor.forClass(MlServiceClient.MlPredictionPayload.class);
        verify(mlServiceClient).predict(captor.capture());

        // Because there are NO customer sales or confirmed reservations, history passed to ML service is empty
        assertThat(captor.getValue().recent_daily_sales()).isEmpty();
    }
}
