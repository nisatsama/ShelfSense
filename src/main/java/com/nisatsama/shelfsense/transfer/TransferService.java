package com.nisatsama.shelfsense.transfer;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransferService {

    private static final DateTimeFormatter NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransferRepository transferRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final Clock clock;

    public TransferService(
            InventoryTransferRepository transferRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            InventoryService inventoryService,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            CurrentAccountProvider currentAccountProvider,
            Clock clock
    ) {
        this.transferRepository = transferRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.clock = clock;
    }

    public InventoryTransfer create(
            Long productId,
            Long sourceLocationId,
            Long destinationLocationId,
            int quantity,
            String notes
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Transfer quantity must be greater than zero.");
        }
        if (sourceLocationId.equals(destinationLocationId)) {
            throw new IllegalArgumentException("Source and destination locations must be different.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        Location source = locationRepository.findById(sourceLocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found: " + sourceLocationId));
        Location destination = locationRepository.findById(destinationLocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found: " + destinationLocationId));

        Inventory sourceInventory = inventoryRepository.findByProductIdAndLocationId(productId, sourceLocationId)
                .orElseThrow(() -> new IllegalArgumentException("Source location has no inventory for this product."));
        if (sourceInventory.getAvailable() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient available stock at source location. Available: "
                            + sourceInventory.getAvailable() + ", requested: " + quantity
            );
        }

        InventoryTransfer transfer = new InventoryTransfer();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        transfer.setTransferNumber("TRF-" + LocalDateTime.now(clock).format(NUMBER_FORMAT) + "-" + suffix);
        transfer.setProduct(product);
        transfer.setSourceLocation(source);
        transfer.setDestinationLocation(destination);
        transfer.setRequestedQuantity(quantity);
        transfer.setStatus(TransferStatus.CREATED);
        transfer.setNotes(notes);
        currentAccountProvider.currentAccountId().ifPresent(transfer::setCreatedByAccountId);
        return transferRepository.save(transfer);
    }

    public InventoryTransfer approve(Long id) {
        InventoryTransfer transfer = lockTransfer(id);
        if (transfer.getStatus() != TransferStatus.CREATED) {
            throw new IllegalStateException("Only CREATED transfers can be approved. Current status: " + transfer.getStatus());
        }
        transfer.setStatus(TransferStatus.APPROVED);
        currentAccountProvider.currentAccountId().ifPresent(transfer::setApprovedByAccountId);
        return transferRepository.save(transfer);
    }

    public InventoryTransfer dispatch(Long id, Integer dispatchedQuantityOverride) {
        InventoryTransfer transfer = lockTransfer(id);
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED transfers can be dispatched. Current status: " + transfer.getStatus());
        }

        int dispatchQty = dispatchedQuantityOverride != null
                ? dispatchedQuantityOverride
                : transfer.getRequestedQuantity();
        if (dispatchQty <= 0 || dispatchQty > transfer.getRequestedQuantity()) {
            throw new IllegalArgumentException(
                    "Dispatched quantity must be between 1 and requested quantity (" + transfer.getRequestedQuantity() + ")."
            );
        }

        Inventory sourceInventory = inventoryRepository
                .findByProductIdAndLocationId(transfer.getProduct().getId(), transfer.getSourceLocation().getId())
                .orElseThrow(() -> new IllegalArgumentException("Source inventory not found for transfer."));

        if (sourceInventory.getAvailable() < dispatchQty) {
            throw new IllegalArgumentException(
                    "Insufficient available stock at source to dispatch. Available: "
                            + sourceInventory.getAvailable() + ", dispatch: " + dispatchQty
            );
        }

        sourceInventory.setOnHand(sourceInventory.getOnHand() - dispatchQty);
        inventoryRepository.save(sourceInventory);

        inventoryTransactionRepository.append(InventoryTransaction.createWithReference(
                sourceInventory,
                InventoryTransactionType.TRANSFER_OUT,
                -dispatchQty,
                0,
                transfer.getId()
        ));

        transfer.setDispatchedQuantity(dispatchQty);
        transfer.setDispatchedAt(LocalDateTime.now(clock));
        transfer.setStatus(TransferStatus.DISPATCHED);
        return transferRepository.save(transfer);
    }

    public InventoryTransfer markInTransit(Long id) {
        InventoryTransfer transfer = lockTransfer(id);
        if (transfer.getStatus() != TransferStatus.DISPATCHED) {
            throw new IllegalStateException("Only DISPATCHED transfers can transition to IN_TRANSIT. Current status: " + transfer.getStatus());
        }
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        return transferRepository.save(transfer);
    }

    public InventoryTransfer receive(Long id, int receivedQty, int damagedQty, String notes) {
        InventoryTransfer transfer = lockTransfer(id);
        if (transfer.getStatus() != TransferStatus.DISPATCHED
                && transfer.getStatus() != TransferStatus.IN_TRANSIT
                && transfer.getStatus() != TransferStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Transfer cannot be received in status: " + transfer.getStatus());
        }
        if (receivedQty < 0 || damagedQty < 0 || (receivedQty + damagedQty) <= 0) {
            throw new IllegalArgumentException("Received and damaged quantities must be non-negative and sum to at least 1.");
        }
        int remaining = transfer.getRemainingToReceive();
        if (receivedQty + damagedQty > remaining) {
            throw new IllegalArgumentException(
                    "Total received + damaged (" + (receivedQty + damagedQty)
                            + ") exceeds remaining dispatched quantity (" + remaining + ")."
            );
        }

        if (receivedQty > 0) {
            Inventory destinationInventory = inventoryService.findOrCreateZeroStock(
                    transfer.getProduct(),
                    transfer.getDestinationLocation()
            );
            destinationInventory.setOnHand(destinationInventory.getOnHand() + receivedQty);
            inventoryRepository.save(destinationInventory);

            inventoryTransactionRepository.append(InventoryTransaction.createWithReference(
                    destinationInventory,
                    InventoryTransactionType.TRANSFER_IN,
                    receivedQty,
                    0,
                    transfer.getId()
            ));
        }

        transfer.setReceivedQuantity(transfer.getReceivedQuantity() + receivedQty);
        transfer.setDamagedQuantity(transfer.getDamagedQuantity() + damagedQty);
        if (notes != null && !notes.isBlank()) {
            String combined = transfer.getNotes() == null || transfer.getNotes().isBlank()
                    ? notes.trim()
                    : transfer.getNotes() + " | " + notes.trim();
            transfer.setNotes(combined.length() > 500 ? combined.substring(0, 500) : combined);
        }

        if (transfer.getRemainingToReceive() == 0) {
            transfer.setStatus(TransferStatus.RECEIVED);
            transfer.setReceivedAt(LocalDateTime.now(clock));
        } else {
            transfer.setStatus(TransferStatus.PARTIALLY_RECEIVED);
        }

        return transferRepository.save(transfer);
    }

    public InventoryTransfer cancel(Long id) {
        InventoryTransfer transfer = lockTransfer(id);
        if (transfer.getStatus() != TransferStatus.CREATED && transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only CREATED or APPROVED transfers can be cancelled. Current status: " + transfer.getStatus()
            );
        }
        transfer.setStatus(TransferStatus.CANCELLED);
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findAll(TransferStatus status, Long locationId) {
        List<InventoryTransfer> transfers = status == null
                ? transferRepository.findAllByOrderByCreatedAtDescIdDesc()
                : transferRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
        if (locationId != null) {
            return transfers.stream()
                    .filter(t -> t.getSourceLocation().getId().equals(locationId)
                            || t.getDestinationLocation().getId().equals(locationId))
                    .toList();
        }
        return transfers;
    }

    @Transactional(readOnly = true)
    public InventoryTransfer findById(Long id) {
        return transferRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
    }

    private InventoryTransfer lockTransfer(Long id) {
        return transferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
    }
}
