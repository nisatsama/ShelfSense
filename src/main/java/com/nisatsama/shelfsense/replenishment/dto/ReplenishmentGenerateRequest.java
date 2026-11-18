package com.nisatsama.shelfsense.replenishment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record ReplenishmentGenerateRequest(
        @Min(1) Integer leadTimeDays,
        @Min(1) Integer reviewPeriodDays,
        @DecimalMin("0.01") BigDecimal fallbackDailyDemand
) {
}
