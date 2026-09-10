package com.nisatsama.shelfsense.inventory;

import com.nisatsama.shelfsense.inventory.dto.InventoryResponse;
import org.springframework.web.bind.annotation.*;
import com.nisatsama.shelfsense.inventory.dto.InventoryCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse create(
            @Valid @RequestBody InventoryCreateRequest request
    ) {
        Inventory inventory = inventoryService.create(
                request.productId(),
                request.locationId(),
                request.onHand()
        );

        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getLocation().getId(),
                inventory.getLocation().getCode(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable(),
                inventory.getVersion()
        );
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

        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getLocation().getId(),
                inventory.getLocation().getCode(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable(),
                inventory.getVersion()
        );
    }
}