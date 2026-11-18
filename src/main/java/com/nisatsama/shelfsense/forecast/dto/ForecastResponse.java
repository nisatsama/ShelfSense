package com.nisatsama.shelfsense.forecast.dto;

import com.nisatsama.shelfsense.forecast.DemandForecast;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ForecastResponse(
        Long id,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        String locationName,
        int horizonDays,
        BigDecimal predictedDailyDemand,
        BigDecimal predictedTotalDemand,
        String modelVersion,
        String source,
        List<DailyPrediction> dailyPredictions,
        LocalDateTime generatedAt
) {
    public record DailyPrediction(int dayOffset, String date, double predictedDemand) {
    }

    public static ForecastResponse from(DemandForecast f, List<DailyPrediction> breakdown) {
        return new ForecastResponse(
                f.getId(),
                f.getProduct().getId(),
                f.getProduct().getSku(),
                f.getProduct().getName(),
                f.getLocation().getId(),
                f.getLocation().getCode(),
                f.getLocation().getName(),
                f.getHorizonDays(),
                f.getPredictedDailyDemand(),
                f.getPredictedTotalDemand(),
                f.getModelVersion(),
                f.getSource(),
                breakdown,
                f.getGeneratedAt()
        );
    }
}
