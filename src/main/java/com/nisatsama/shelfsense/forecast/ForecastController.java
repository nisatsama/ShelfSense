package com.nisatsama.shelfsense.forecast;

import com.nisatsama.shelfsense.forecast.dto.ForecastGenerateRequest;
import com.nisatsama.shelfsense.forecast.dto.ForecastResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/forecasts")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public List<ForecastResponse> findAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId
    ) {
        return forecastService.findAll(productId, locationId);
    }

    @GetMapping("/model-info")
    public Map<String, Object> modelInfo() {
        return forecastService.getModelInfo();
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ForecastResponse generate(@Valid @RequestBody ForecastGenerateRequest request) {
        return forecastService.generateForecast(request);
    }
}
