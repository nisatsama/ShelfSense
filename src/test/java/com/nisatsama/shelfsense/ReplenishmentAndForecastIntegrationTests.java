package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.forecast.DemandForecast;
import com.nisatsama.shelfsense.forecast.DemandForecastRepository;
import com.nisatsama.shelfsense.forecast.ForecastService;
import com.nisatsama.shelfsense.forecast.MlServiceClient;
import com.nisatsama.shelfsense.forecast.MlServiceUnavailableException;
import com.nisatsama.shelfsense.forecast.dto.ForecastGenerateRequest;
import com.nisatsama.shelfsense.forecast.dto.ForecastResponse;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.procurement.ProcurementService;
import com.nisatsama.shelfsense.procurement.PurchaseOrder;
import com.nisatsama.shelfsense.procurement.Supplier;
import com.nisatsama.shelfsense.procurement.SupplierService;
import com.nisatsama.shelfsense.procurement.dto.SupplierRequest;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.replenishment.ReplenishmentPriority;
import com.nisatsama.shelfsense.replenishment.ReplenishmentRecommendation;
import com.nisatsama.shelfsense.replenishment.ReplenishmentService;
import com.nisatsama.shelfsense.replenishment.ReplenishmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ReplenishmentAndForecastIntegrationTests {

    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired ForecastService forecastService;
    @Autowired DemandForecastRepository forecastRepository;
    @Autowired ReplenishmentService replenishmentService;
    @Autowired SupplierService supplierService;
    @Autowired ProcurementService procurementService;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void forecastFeedsReplenishmentDeduplicatesPendingAndCanCreatePurchaseOrder() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product();
        product.setSku("REP-SKU-" + u);
        product.setName("Sparkling Water 12pk");
        product.setCategory("BEVERAGES");
        product.setUnitPrice(new BigDecimal("8.50"));
        product = productRepository.saveAndFlush(product);

        Location store = new Location();
        store.setCode("REP-STR-" + u);
        store.setName("Downtown Store");
        store.setType(LocationType.STORE);
        store = locationRepository.saveAndFlush(store);

        inventoryService.create(product.getId(), store.getId(), 10);

        when(mlServiceClient.predict(any())).thenReturn(new MlServiceClient.MlPredictionResult(
                product.getSku(),
                store.getCode(),
                14,
                5.0,
                70.0,
                "m5-hgbr-v1",
                "2026-11-18T12:00:00Z",
                List.of(new MlServiceClient.MlDailyPoint(1, "2026-11-19", 5.0))
        ));

        ForecastResponse forecast = forecastService.generateForecast(
                new ForecastGenerateRequest(product.getId(), store.getId(), 14, List.of(4.0, 5.0, 6.0), null)
        );
        assertThat(forecast.predictedDailyDemand()).isEqualByComparingTo("5.0000");
        assertThat(forecast.modelVersion()).isEqualTo("m5-hgbr-v1");

        // Generate replenishment twice -> must NOT create duplicates for the same (product, location)
        replenishmentService.generateRecommendations(7, 14, new BigDecimal("2.0"));
        replenishmentService.generateRecommendations(7, 14, new BigDecimal("2.0"));

        final Long pid = product.getId();
        final Long lid = store.getId();
        List<ReplenishmentRecommendation> pending = replenishmentService
                .findAll(ReplenishmentStatus.PENDING, null, lid)
                .stream()
                .filter(r -> r.getProduct().getId().equals(pid))
                .toList();
        assertThat(pending).hasSize(1);

        ReplenishmentRecommendation rec = pending.getFirst();
        assertThat(rec.getAvgDailyDemand()).isEqualByComparingTo("5.0000");
        assertThat(rec.getForecastId()).isEqualTo(forecast.id());
        assertThat(rec.getReorderPoint()).isGreaterThan(35);
        assertThat(rec.getRecommendedQuantity()).isGreaterThan(0);
        assertThat(rec.getPriority()).isIn(ReplenishmentPriority.CRITICAL, ReplenishmentPriority.HIGH);

        // Approve with supplier creates a real linked Purchase Order
        Supplier supplier = supplierService.create(new SupplierRequest(
                "REP-SUP-" + u, "Beverage Supply Co", "sales@bev.example", null, null, 7, true
        ));
        ReplenishmentRecommendation ordered = replenishmentService.approve(rec.getId(), supplier.getId(), "Approved");
        assertThat(ordered.getStatus()).isEqualTo(ReplenishmentStatus.ORDERED);
        assertThat(ordered.getPurchaseOrderId()).isNotNull();

        PurchaseOrder linkedPo = procurementService.findById(ordered.getPurchaseOrderId());
        assertThat(linkedPo.getItems().getFirst().getOrderedQuantity()).isEqualTo(rec.getRecommendedQuantity());
    }

    @Test
    void unavailableMlServiceFailsCleanlyWithoutCorruptingInventory() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product();
        product.setSku("MLERR-SKU-" + u);
        product.setName("Cereal Box");
        product.setCategory("FOODS");
        product.setUnitPrice(new BigDecimal("4.99"));
        product = productRepository.saveAndFlush(product);

        Location store = new Location();
        store.setCode("MLERR-STR-" + u);
        store.setName("West Store");
        store.setType(LocationType.STORE);
        store = locationRepository.saveAndFlush(store);

        inventoryService.create(product.getId(), store.getId(), 25);

        when(mlServiceClient.predict(any()))
                .thenThrow(new MlServiceUnavailableException("ML service offline"));

        Long pid = product.getId();
        Long lid = store.getId();
        assertThatThrownBy(() -> forecastService.generateForecast(
                new ForecastGenerateRequest(pid, lid, 14, null, null)))
                .isInstanceOf(MlServiceUnavailableException.class);

        // Inventory remains completely untouched
        assertThat(inventoryService.findByProductAndLocation(pid, lid).getOnHand()).isEqualTo(25);
        assertThat(forecastRepository.findByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(pid, lid)).isEmpty();
    }
}
