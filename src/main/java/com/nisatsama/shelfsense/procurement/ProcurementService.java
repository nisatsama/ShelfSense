package com.nisatsama.shelfsense.procurement;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.procurement.dto.GoodsReceiptRequest;
import com.nisatsama.shelfsense.procurement.dto.PurchaseOrderCreateRequest;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProcurementService {

    private static final DateTimeFormatter NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final Clock clock;

    public ProcurementService(
            PurchaseOrderRepository purchaseOrderRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            SupplierRepository supplierRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            CurrentAccountProvider currentAccountProvider,
            Clock clock
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.clock = clock;
    }

    public PurchaseOrder createPurchaseOrder(PurchaseOrderCreateRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
        if (!supplier.isActive()) {
            throw new IllegalArgumentException("Cannot create purchase order for an inactive supplier.");
        }
        Location destination = locationRepository.findById(request.destinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found: " + request.destinationLocationId()));

        PurchaseOrder po = new PurchaseOrder();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        po.setPoNumber("PO-" + LocalDateTime.now(clock).format(NUMBER_FORMAT) + "-" + suffix);
        po.setSupplier(supplier);
        po.setDestinationLocation(destination);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        LocalDate expected = request.expectedDeliveryDate() != null
                ? request.expectedDeliveryDate()
                : LocalDate.now(clock).plusDays(supplier.getLeadTimeDays());
        po.setExpectedDeliveryDate(expected);
        po.setNotes(request.notes());
        currentAccountProvider.currentAccountId().ifPresent(po::setCreatedByAccountId);

        Set<Long> seenProducts = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderCreateRequest.ItemRequest itemReq : request.items()) {
            if (!seenProducts.add(itemReq.productId())) {
                throw new IllegalArgumentException("Duplicate product ID in purchase order: " + itemReq.productId());
            }
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setOrderedQuantity(itemReq.orderedQuantity());
            item.setReceivedQuantity(0);
            item.setUnitCost(itemReq.unitCost());
            po.addItem(item);

            totalAmount = totalAmount.add(itemReq.unitCost().multiply(BigDecimal.valueOf(itemReq.orderedQuantity())));
        }
        po.setTotalAmount(totalAmount);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderRepository.findDetailedById(saved.getId()).orElse(saved);
    }

    public PurchaseOrder approve(Long id) {
        PurchaseOrder po = lockPurchaseOrder(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT purchase orders can be approved. Current status: " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.APPROVED);
        currentAccountProvider.currentAccountId().ifPresent(po::setApprovedByAccountId);
        purchaseOrderRepository.save(po);
        return purchaseOrderRepository.findDetailedById(id).orElse(po);
    }

    public PurchaseOrder markOrdered(Long id) {
        PurchaseOrder po = lockPurchaseOrder(id);
        if (po.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED purchase orders can be marked ORDERED. Current status: " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.ORDERED);
        purchaseOrderRepository.save(po);
        return purchaseOrderRepository.findDetailedById(id).orElse(po);
    }

    public PurchaseOrder cancel(Long id) {
        PurchaseOrder po = lockPurchaseOrder(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new IllegalStateException("Only DRAFT or APPROVED purchase orders can be cancelled. Current status: " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        purchaseOrderRepository.save(po);
        return purchaseOrderRepository.findDetailedById(id).orElse(po);
    }

    public GoodsReceipt receiveGoods(Long purchaseOrderId, GoodsReceiptRequest request, String headerIdempotencyKey) {
        String key = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey().trim()
                : (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank() ? headerIdempotencyKey.trim() : null);

        if (key != null) {
            var existing = goodsReceiptRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        PurchaseOrder po = lockPurchaseOrder(purchaseOrderId);
        if (po.getStatus() != PurchaseOrderStatus.APPROVED
                && po.getStatus() != PurchaseOrderStatus.ORDERED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Cannot receive goods for purchase order in status: " + po.getStatus());
        }

        Map<Long, PurchaseOrderItem> itemMap = new HashMap<>();
        for (PurchaseOrderItem item : po.getItems()) {
            itemMap.put(item.getId(), item);
        }

        GoodsReceipt receipt = new GoodsReceipt();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        receipt.setReceiptNumber("GRN-" + LocalDateTime.now(clock).format(NUMBER_FORMAT) + "-" + suffix);
        receipt.setPurchaseOrder(po);
        receipt.setNotes(request.notes());
        receipt.setIdempotencyKey(key);
        receipt.setReceivedAt(LocalDateTime.now(clock));
        currentAccountProvider.currentAccountId().ifPresent(receipt::setReceivedByAccountId);

        Set<Long> seenItemIds = new HashSet<>();
        for (GoodsReceiptRequest.ItemReceipt itemReceipt : request.items()) {
            if (!seenItemIds.add(itemReceipt.purchaseOrderItemId())) {
                throw new IllegalArgumentException("Duplicate purchaseOrderItemId in goods receipt: " + itemReceipt.purchaseOrderItemId());
            }
            PurchaseOrderItem poItem = itemMap.get(itemReceipt.purchaseOrderItemId());
            if (poItem == null) {
                throw new IllegalArgumentException("Purchase order item does not belong to this PO: " + itemReceipt.purchaseOrderItemId());
            }
            int qty = itemReceipt.receivedQuantity();
            if (qty <= 0 || qty > poItem.getRemainingQuantity()) {
                throw new IllegalArgumentException(
                        "Invalid received quantity (" + qty + ") for SKU " + poItem.getProduct().getSku()
                                + ". Remaining ordered quantity: " + poItem.getRemainingQuantity()
                );
            }

            poItem.setReceivedQuantity(poItem.getReceivedQuantity() + qty);

            Inventory destinationInventory = inventoryService.findOrCreateZeroStock(
                    poItem.getProduct(),
                    po.getDestinationLocation()
            );
            destinationInventory.setOnHand(destinationInventory.getOnHand() + qty);
            inventoryRepository.save(destinationInventory);

            inventoryTransactionRepository.append(InventoryTransaction.createWithReference(
                    destinationInventory,
                    InventoryTransactionType.PURCHASE_RECEIVED,
                    qty,
                    0,
                    po.getId()
            ));

            GoodsReceiptItem gri = new GoodsReceiptItem();
            gri.setPurchaseOrderItem(poItem);
            gri.setProduct(poItem.getProduct());
            gri.setReceivedQuantity(qty);
            receipt.addItem(gri);
        }

        boolean allReceived = po.getItems().stream().allMatch(i -> i.getRemainingQuantity() == 0);
        po.setStatus(allReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        purchaseOrderRepository.save(po);

        return goodsReceiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll(PurchaseOrderStatus status) {
        return status == null
                ? purchaseOrderRepository.findAllByOrderByCreatedAtDescIdDesc()
                : purchaseOrderRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<GoodsReceipt> getReceipts(Long purchaseOrderId) {
        findById(purchaseOrderId);
        return goodsReceiptRepository.findByPurchaseOrderIdOrderByReceivedAtDescIdDesc(purchaseOrderId);
    }

    private PurchaseOrder lockPurchaseOrder(Long id) {
        return purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }
}
