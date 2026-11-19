package com.nisatsama.shelfsense.inventory;

import com.nisatsama.shelfsense.inventory.dto.InventoryAdjustmentRequest;
import com.nisatsama.shelfsense.inventory.dto.InventoryAdjustmentResponse;
import com.nisatsama.shelfsense.inventory.dto.InventoryCreateRequest;
import com.nisatsama.shelfsense.inventory.dto.InventoryResponse;
import com.nisatsama.shelfsense.inventory.dto.InventoryTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryResponse create(
            @Valid @RequestBody InventoryCreateRequest request
    ) {
        Inventory inventory = inventoryService.create(
                request.productId(),
                request.locationId(),
                request.onHand()
        );
        return toResponse(inventory);
    }

    @GetMapping
    public List<InventoryResponse> findAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Integer lowStockThreshold
    ) {
        return inventoryService.findAll(productId, locationId, lowStockThreshold).stream()
                .map(InventoryController::toResponse)
                .toList();
    }

    @GetMapping("/products/{productId}/locations/{locationId}")
    public InventoryResponse findByProductAndLocation(
            @PathVariable Long productId,
            @PathVariable Long locationId
    ) {
        Inventory inventory =
                inventoryService.findByProductAndLocation(
                        productId,
                        locationId
                );
        return toResponse(inventory);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryAdjustmentResponse adjust(
            @Valid @RequestBody InventoryAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerIdempotencyKey
    ) {
        String key = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey()
                : headerIdempotencyKey;
        InventoryAdjustment adjustment = inventoryService.adjustStock(
                request.productId(),
                request.locationId(),
                request.quantityDelta(),
                request.reason(),
                request.notes(),
                key
        );
        return InventoryAdjustmentResponse.from(adjustment);
    }

    @GetMapping("/adjustments")
    public List<InventoryAdjustmentResponse> listAdjustments(
            @RequestParam(required = false) Long inventoryId
    ) {
        return inventoryService.getAdjustments(inventoryId).stream()
                .map(InventoryAdjustmentResponse::from)
                .toList();
    }

    @GetMapping("/transactions")
    public List<InventoryTransactionResponse> listTransactions(
            @RequestParam(required = false) Long inventoryId,
            @RequestParam(required = false) InventoryTransactionType type,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return inventoryService.getTransactions(inventoryId, type, limit).stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }

    @GetMapping("/{inventoryId}/transactions")
    public List<InventoryTransactionResponse> listInventoryTransactions(
            @PathVariable Long inventoryId,
            @RequestParam(required = false) InventoryTransactionType type,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return inventoryService.getTransactions(inventoryId, type, limit).stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }

    private static InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getLocation().getId(),
                inventory.getLocation().getCode(),
                inventory.getLocation().getName(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable(),
                inventory.getVersion()
        );
    }
}
