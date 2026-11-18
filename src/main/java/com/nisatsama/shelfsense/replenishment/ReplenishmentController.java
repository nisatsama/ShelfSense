package com.nisatsama.shelfsense.replenishment;

import com.nisatsama.shelfsense.replenishment.dto.ReplenishmentActionRequest;
import com.nisatsama.shelfsense.replenishment.dto.ReplenishmentGenerateRequest;
import com.nisatsama.shelfsense.replenishment.dto.ReplenishmentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/replenishment")
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;

    public ReplenishmentController(ReplenishmentService replenishmentService) {
        this.replenishmentService = replenishmentService;
    }

    @GetMapping
    public List<ReplenishmentResponse> findAll(
            @RequestParam(required = false) ReplenishmentStatus status,
            @RequestParam(required = false) ReplenishmentPriority priority,
            @RequestParam(required = false) Long locationId
    ) {
        return replenishmentService.findAll(status, priority, locationId).stream()
                .map(ReplenishmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ReplenishmentResponse findById(@PathVariable Long id) {
        return ReplenishmentResponse.from(replenishmentService.findById(id));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReplenishmentResponse> generate(
            @Valid @RequestBody(required = false) ReplenishmentGenerateRequest request
    ) {
        Integer leadTime = request != null ? request.leadTimeDays() : null;
        Integer reviewDays = request != null ? request.reviewPeriodDays() : null;
        var fallback = request != null ? request.fallbackDailyDemand() : null;
        return replenishmentService.generateRecommendations(leadTime, reviewDays, fallback).stream()
                .map(ReplenishmentResponse::from)
                .toList();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ReplenishmentResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReplenishmentActionRequest request
    ) {
        Long supplierId = request != null ? request.supplierId() : null;
        String notes = request != null ? request.notes() : null;
        return ReplenishmentResponse.from(replenishmentService.approve(id, supplierId, notes));
    }

    @PutMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public ReplenishmentResponse dismiss(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReplenishmentActionRequest request
    ) {
        String notes = request != null ? request.notes() : null;
        return ReplenishmentResponse.from(replenishmentService.dismiss(id, notes));
    }
}
