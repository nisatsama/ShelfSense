package com.nisatsama.shelfsense.forecast.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ForecastGenerateRequest(
        @NotNull Long productId,
        @NotNull Long locationId,
        @Min(1) @Max(90) Integer horizonDays,
        List<Double> recentDailySales,
        BigDecimal sellPrice
) {
}
