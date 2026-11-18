package com.nisatsama.shelfsense.procurement;

import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptRequest;
import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptResponse;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderCreateRequest;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final ProcurementService procurementService;

    public PurchaseOrderController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @GetMapping
    public List<PurchaseOrderResponse> findAll(
            @RequestParam(required = false) PurchaseOrderStatus status
    ) {
        return procurementService.findAll(status).stream()
                .map(PurchaseOrderResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse findById(@PathVariable Long id) {
        return PurchaseOrderResponse.from(procurementService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderCreateRequest request) {
        return PurchaseOrderResponse.from(procurementService.createPurchaseOrder(request));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public PurchaseOrderResponse approve(@PathVariable Long id) {
        return PurchaseOrderResponse.from(procurementService.approve(id));
    }

    @PutMapping("/{id}/order")
    @PreAuthorize("hasRole('ADMIN')")
    public PurchaseOrderResponse markOrdered(@PathVariable Long id) {
        return PurchaseOrderResponse.from(procurementService.markOrdered(id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public PurchaseOrderResponse cancel(@PathVariable Long id) {
        return PurchaseOrderResponse.from(procurementService.cancel(id));
    }

    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public GoodsReceiptResponse receiveGoods(
            @PathVariable Long id,
            @Valid @RequestBody GoodsReceiptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return GoodsReceiptResponse.from(procurementService.receiveGoods(id, request, idempotencyKey));
    }

    @GetMapping("/{id}/receipts")
    public List<GoodsReceiptResponse> getReceipts(@PathVariable Long id) {
        return procurementService.getReceipts(id).stream()
                .map(GoodsReceiptResponse::from)
                .toList();
    }
}
