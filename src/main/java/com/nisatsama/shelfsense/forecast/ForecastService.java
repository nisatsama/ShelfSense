package com.nisatsama.shelfsense.forecast;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.forecast.dto.ForecastGenerateRequest;
import com.nisatsama.shelfsense.forecast.dto.ForecastResponse;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastService {

    private final DemandForecastRepository forecastRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MlServiceClient mlServiceClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ForecastService(
            DemandForecastRepository forecastRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository transactionRepository,
            MlServiceClient mlServiceClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.forecastRepository = forecastRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.mlServiceClient = mlServiceClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ForecastResponse generateForecast(ForecastGenerateRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));
        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + request.locationId()));

        int horizonDays = request.horizonDays() != null ? request.horizonDays() : 14;
        double sellPrice = request.sellPrice() != null
                ? request.sellPrice().doubleValue()
                : product.getUnitPrice().doubleValue();

        List<Double> history = request.recentDailySales();
        if (history == null || history.isEmpty()) {
            history = extractRecentDemandFromLedger(product.getId(), location.getId());
        }

        MlServiceClient.MlPredictionResult prediction = mlServiceClient.predict(
                new MlServiceClient.MlPredictionPayload(
                        product.getSku(),
                        location.getCode(),
                        product.getCategory(),
                        horizonDays,
                        sellPrice,
                        history
                )
        );

        List<ForecastResponse.DailyPrediction> breakdown = prediction.daily_predictions() != null
                ? prediction.daily_predictions().stream()
                .map(p -> new ForecastResponse.DailyPrediction(p.day_offset(), p.date(), p.predicted_demand()))
                .toList()
                : List.of();

        DemandForecast entity = persistForecast(product, location, horizonDays, prediction, breakdown);
        return ForecastResponse.from(entity, breakdown);
    }

    @Transactional
    protected DemandForecast persistForecast(
            Product product,
            Location location,
            int horizonDays,
            MlServiceClient.MlPredictionResult prediction,
            List<ForecastResponse.DailyPrediction> breakdown
    ) {
        DemandForecast entity = new DemandForecast();
        entity.setProduct(product);
        entity.setLocation(location);
        entity.setHorizonDays(horizonDays);
        entity.setPredictedDailyDemand(BigDecimal.valueOf(prediction.predicted_daily_demand()).setScale(4, RoundingMode.HALF_UP));
        entity.setPredictedTotalDemand(BigDecimal.valueOf(prediction.predicted_total_demand()).setScale(4, RoundingMode.HALF_UP));
        entity.setModelVersion(prediction.model_version());
        entity.setSource("ML_SERVICE");
        entity.setGeneratedAt(LocalDateTime.now(clock));
        try {
            entity.setDailyBreakdownJson(objectMapper.writeValueAsString(breakdown));
        } catch (Exception ignored) {
            entity.setDailyBreakdownJson("[]");
        }
        return forecastRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse> findAll(Long productId, Long locationId) {
        List<DemandForecast> rows;
        if (productId != null && locationId != null) {
            rows = forecastRepository.findByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(productId, locationId);
        } else if (productId != null) {
            rows = forecastRepository.findByProductIdOrderByGeneratedAtDescIdDesc(productId);
        } else if (locationId != null) {
            rows = forecastRepository.findByLocationIdOrderByGeneratedAtDescIdDesc(locationId);
        } else {
            rows = forecastRepository.findAllByOrderByGeneratedAtDescIdDesc();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    public Map<String, Object> getModelInfo() {
        return mlServiceClient.getModelInfo();
    }

    private ForecastResponse toResponse(DemandForecast f) {
        List<ForecastResponse.DailyPrediction> breakdown = Collections.emptyList();
        if (f.getDailyBreakdownJson() != null && !f.getDailyBreakdownJson().isBlank()) {
            try {
                breakdown = objectMapper.readValue(
                        f.getDailyBreakdownJson(),
                        new TypeReference<List<ForecastResponse.DailyPrediction>>() {}
                );
            } catch (Exception ignored) {
                breakdown = Collections.emptyList();
            }
        }
        return ForecastResponse.from(f, breakdown);
    }

    private List<Double> extractRecentDemandFromLedger(Long productId, Long locationId) {
        Inventory inv = inventoryRepository.findByProductIdAndLocationId(productId, locationId).orElse(null);
        if (inv == null) {
            return List.of();
        }
        List<InventoryTransaction> txs = transactionRepository.findByInventoryIdOrderByCreatedAtAscIdAsc(inv.getId());
        Map<LocalDate, Integer> dailyOutflow = new HashMap<>();
        for (InventoryTransaction tx : txs) {
            if (tx.getType() == InventoryTransactionType.SALE
                    || tx.getType() == InventoryTransactionType.RESERVATION_CONFIRMED
                    || tx.getType() == InventoryTransactionType.TRANSFER_OUT) {
                LocalDate day = tx.getCreatedAt().toLocalDate();
                dailyOutflow.merge(day, Math.abs(tx.getOnHandDelta()), Integer::sum);
            }
        }
        if (dailyOutflow.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<Double> series = new ArrayList<>(28);
        for (int i = 28; i >= 1; i--) {
            LocalDate d = today.minusDays(i);
            series.add((double) dailyOutflow.getOrDefault(d, 0));
        }
        return series;
    }
}
