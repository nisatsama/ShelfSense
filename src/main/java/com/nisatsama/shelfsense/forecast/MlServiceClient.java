package com.nisatsama.shelfsense.forecast;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class MlServiceClient {

    private final RestClient restClient;

    public MlServiceClient(
            @Value("${shelfsense.ml.base-url:http://localhost:8001}") String baseUrl,
            @Value("${shelfsense.ml.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${shelfsense.ml.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public MlPredictionResult predict(MlPredictionPayload payload) {
        try {
            MlPredictionResult result = restClient.post()
                    .uri("/api/v1/forecast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MlPredictionResult.class);
            if (result == null || result.predicted_daily_demand() < 0
                    || Double.isNaN(result.predicted_daily_demand())
                    || Double.isInfinite(result.predicted_daily_demand())
                    || result.model_version() == null || result.model_version().isBlank()) {
                throw new MlServiceUnavailableException("ML service returned an invalid forecast payload.");
            }
            return result;
        } catch (MlServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MlServiceUnavailableException("Demand forecasting ML service is currently unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getModelInfo() {
        try {
            Map<String, Object> info = restClient.get()
                    .uri("/model/info")
                    .retrieve()
                    .body(Map.class);
            return info != null ? info : Map.of("status", "UNKNOWN");
        } catch (Exception ex) {
            return Map.of(
                    "status", "UNAVAILABLE",
                    "error", "ML forecasting service is unreachable."
            );
        }
    }

    public record MlPredictionPayload(
            String product_id,
            String store_id,
            String category,
            int horizon_days,
            double sell_price,
            List<Double> recent_daily_sales
    ) {
    }

    public record MlDailyPoint(
            int day_offset,
            String date,
            double predicted_demand
    ) {
    }

    public record MlPredictionResult(
            String product_id,
            String store_id,
            int horizon_days,
            double predicted_daily_demand,
            double predicted_total_demand,
            String model_version,
            String prediction_timestamp,
            List<MlDailyPoint> daily_predictions
    ) {
    }
}
