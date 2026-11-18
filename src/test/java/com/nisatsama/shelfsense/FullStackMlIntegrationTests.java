package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.forecast.DemandForecast;
import com.nisatsama.shelfsense.forecast.DemandForecastRepository;
import com.nisatsama.shelfsense.forecast.ForecastService;
import com.nisatsama.shelfsense.forecast.MlServiceClient;
import com.nisatsama.shelfsense.forecast.dto.ForecastGenerateRequest;
import com.nisatsama.shelfsense.forecast.dto.ForecastResponse;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.procurement.GoodsReceipt;
import com.nisatsama.shelfsense.procurement.ProcurementService;
import com.nisatsama.shelfsense.procurement.PurchaseOrder;
import com.nisatsama.shelfsense.procurement.PurchaseOrderStatus;
import com.nisatsama.shelfsense.procurement.Supplier;
import com.nisatsama.shelfsense.procurement.SupplierService;
import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptRequest;
import com.nisatsama.shelfsense.procurement.dto.SupplierRequest;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.replenishment.ReplenishmentRecommendation;
import com.nisatsama.shelfsense.replenishment.ReplenishmentService;
import com.nisatsama.shelfsense.replenishment.ReplenishmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FullStackMlIntegrationTests {

    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired ForecastService forecastService;
    @Autowired DemandForecastRepository forecastRepository;
    @Autowired ReplenishmentService replenishmentService;
    @Autowired SupplierService supplierService;
    @Autowired ProcurementService procurementService;
    @Autowired MlServiceClient mlServiceClient;

    @Test
    void endToEndMlForecastingReplenishmentAndProcurementLifecycle() {
        String u = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Product and Location
        Product product = new Product();
        product.setSku("E2E-SKU-" + u);
        product.setName("Organic Rolled Oats 1kg");
        product.setCategory("FOODS");
        product.setUnitPrice(new BigDecimal("4.50"));
        product = productRepository.saveAndFlush(product);

        Location store = new Location();
        store.setCode("E2E-STR-" + u);
        store.setName("Bay Street Market");
        store.setType(LocationType.STORE);
        store = locationRepository.saveAndFlush(store);

        // 2. Setup initial low inventory
        Inventory initialInv = inventoryService.create(product.getId(), store.getId(), 5);
        assertThat(initialInv.getOnHand()).isEqualTo(5);

        // 3. Setup supplier with lead time
        Supplier supplier = supplierService.create(new SupplierRequest(
                "E2E-SUP-" + u,
                "WholeGrains Ltd",
                "orders@" + u + ".example.com",
                null,
                null,
                5,
                true
        ));

        // 4. Test live MlServiceClient communication against running FastAPI daemon
        Map<String, Object> modelInfo = mlServiceClient.getModelInfo();
        assertThat(modelInfo).isNotEmpty();
        assertThat(modelInfo.get("status")).isIn("READY", "UNAVAILABLE", "UNKNOWN");

        // 5. Generate and persist demand forecast
        ForecastResponse forecast = forecastService.generateForecast(new ForecastGenerateRequest(
                product.getId(),
                store.getId(),
                14,
                List.of(3.0, 4.0, 5.0, 4.0, 3.0, 5.0, 4.0),
                new BigDecimal("4.50")
        ));
        assertThat(forecast.productId()).isEqualTo(product.getId());
        assertThat(forecast.predictedDailyDemand()).isNotNull();
        assertThat(forecast.predictedTotalDemand()).isNotNull();

        List<DemandForecast> persistedForecasts = forecastRepository.findByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(
                product.getId(), store.getId()
        );
        assertThat(persistedForecasts).isNotEmpty();

        // 6. Run replenishment calculation
        replenishmentService.generateRecommendations(5, 14, new BigDecimal("2.0"));

        final Long pid = product.getId();
        final Long lid = store.getId();
        List<ReplenishmentRecommendation> recommendations = replenishmentService
                .findAll(ReplenishmentStatus.PENDING, null, lid)
                .stream()
                .filter(r -> r.getProduct().getId().equals(pid))
                .toList();

        assertThat(recommendations).hasSize(1);
        ReplenishmentRecommendation rec = recommendations.getFirst();
        assertThat(rec.getStatus()).isEqualTo(ReplenishmentStatus.PENDING);
        assertThat(rec.getRecommendedQuantity()).isGreaterThan(0);

        // 7. Auto-create Purchase Order by approving the recommendation
        ReplenishmentRecommendation approvedRec = replenishmentService.approve(rec.getId(), supplier.getId(), "Auto-approved for test");
        assertThat(approvedRec.getStatus()).isEqualTo(ReplenishmentStatus.ORDERED);
        assertThat(approvedRec.getPurchaseOrderId()).isNotNull();

        PurchaseOrder po = procurementService.findById(approvedRec.getPurchaseOrderId());
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        po = procurementService.approve(po.getId());
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
        po = procurementService.markOrdered(po.getId());
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.ORDERED);
        assertThat(po.getItems()).hasSize(1);
        int qtyOrdered = po.getItems().getFirst().getOrderedQuantity();
        assertThat(qtyOrdered).isEqualTo(rec.getRecommendedQuantity());

        // 8. Receive goods into inventory
        GoodsReceipt gr = procurementService.receiveGoods(po.getId(), new GoodsReceiptRequest(
                "E2E full receipt",
                "IDEM-E2E-" + u,
                List.of(new GoodsReceiptRequest.ItemReceipt(
                        po.getItems().getFirst().getId(),
                        qtyOrdered
                ))
        ), "admin@shelfsense.com");
        assertThat(gr.getItems()).hasSize(1);
        assertThat(gr.getItems().getFirst().getReceivedQuantity()).isEqualTo(qtyOrdered);

        // 9. Verify inventory increased by received quantity
        Inventory updatedInv = inventoryService.findByProductAndLocation(product.getId(), store.getId());
        assertThat(updatedInv.getOnHand()).isEqualTo(5 + qtyOrdered);
        assertThat(updatedInv.getReserved()).isEqualTo(0);
    }
}
