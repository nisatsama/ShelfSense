package com.nisatsama.shelfsense.transfer;

import com.nisatsama.shelfsense.transfer.dto.TransferCreateRequest;
import com.nisatsama.shelfsense.transfer.dto.TransferDispatchRequest;
import com.nisatsama.shelfsense.transfer.dto.TransferReceiveRequest;
import com.nisatsama.shelfsense.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse create(@Valid @RequestBody TransferCreateRequest request) {
        return TransferResponse.from(transferService.create(
                request.productId(),
                request.sourceLocationId(),
                request.destinationLocationId(),
                request.quantity(),
                request.notes()
        ));
    }

    @GetMapping
    public List<TransferResponse> findAll(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long locationId
    ) {
        return transferService.findAll(status, locationId).stream()
                .map(TransferResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public TransferResponse findById(@PathVariable Long id) {
        return TransferResponse.from(transferService.findById(id));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse approve(@PathVariable Long id) {
        return TransferResponse.from(transferService.approve(id));
    }

    @PutMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse dispatch(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TransferDispatchRequest request
    ) {
        Integer dispatchedQty = request != null ? request.dispatchedQuantity() : null;
        return TransferResponse.from(transferService.dispatch(id, dispatchedQty));
    }

    @PutMapping("/{id}/in-transit")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse markInTransit(@PathVariable Long id) {
        return TransferResponse.from(transferService.markInTransit(id));
    }

    @PutMapping("/{id}/receive")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse receive(
            @PathVariable Long id,
            @Valid @RequestBody TransferReceiveRequest request
    ) {
        return TransferResponse.from(transferService.receive(
                id,
                request.receivedQuantity(),
                request.damagedQuantity(),
                request.notes()
        ));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse cancel(@PathVariable Long id) {
        return TransferResponse.from(transferService.cancel(id));
    }
}
