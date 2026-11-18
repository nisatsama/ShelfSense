package com.nisatsama.shelfsense.inventory;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private InventoryTransactionType type;

    // Legacy compatibility column. New records derive this from canonical deltas.
    @Column(nullable = false, updatable = false)
    private int quantity;

    // Legacy compatibility column; for reservation events it mirrors reservationId.
    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "on_hand_delta", nullable = false, updatable = false)
    private int onHandDelta;

    @Column(name = "reserved_delta", nullable = false, updatable = false)
    private int reservedDelta;

    @Column(name = "reservation_id", updatable = false)
    private Long reservationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventoryTransaction() {
        // Required by JPA. Application code must use the validated factory.
    }

    private InventoryTransaction(
            Inventory inventory,
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            Long reservationId,
            Long referenceId
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.type = Objects.requireNonNull(type, "transaction type is required");
        validateDeltaShape(type, onHandDelta, reservedDelta);
        validateReservationReference(type, reservationId);
        this.onHandDelta = onHandDelta;
        this.reservedDelta = reservedDelta;
        this.reservationId = reservationId;
        this.referenceId = referenceId != null ? referenceId : reservationId;
        this.quantity = legacyQuantity(type, onHandDelta, reservedDelta);
    }

    public static InventoryTransaction create(
            Inventory inventory,
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            Long reservationId
    ) {
        return new InventoryTransaction(inventory, type, onHandDelta, reservedDelta, reservationId, reservationId);
    }

    public static InventoryTransaction createWithReference(
            Inventory inventory,
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            Long referenceId
    ) {
        return new InventoryTransaction(inventory, type, onHandDelta, reservedDelta, null, referenceId);
    }

    private static void validateDeltaShape(
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta
    ) {
        boolean valid = switch (type) {
            case INITIAL_STOCK -> onHandDelta >= 0 && reservedDelta == 0;
            case RESERVATION_CREATED -> onHandDelta == 0 && reservedDelta > 0;
            case RESERVATION_RELEASED -> onHandDelta == 0 && reservedDelta < 0;
            case RESERVATION_CONFIRMED -> onHandDelta < 0
                    && reservedDelta < 0
                    && onHandDelta == reservedDelta;
            case RESERVATION_EXPIRED -> onHandDelta == 0 && reservedDelta < 0;
            case PURCHASE_RECEIVED, TRANSFER_IN, RETURN -> onHandDelta > 0 && reservedDelta == 0;
            case SALE, TRANSFER_OUT -> onHandDelta < 0 && reservedDelta == 0;
            case ADJUSTMENT -> onHandDelta != 0 && reservedDelta == 0;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid on-hand/reserved deltas for transaction type " + type
            );
        }
    }

    private static void validateReservationReference(
            InventoryTransactionType type,
            Long reservationId
    ) {
        boolean reservationEvent = type == InventoryTransactionType.RESERVATION_CREATED
                || type == InventoryTransactionType.RESERVATION_RELEASED
                || type == InventoryTransactionType.RESERVATION_CONFIRMED
                || type == InventoryTransactionType.RESERVATION_EXPIRED;
        if (reservationEvent != (reservationId != null)
                || (reservationId != null && reservationId <= 0)) {
            throw new IllegalArgumentException(
                    "Reservation lifecycle entries require a valid reservation ID"
            );
        }
    }

    private static int legacyQuantity(
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta
    ) {
        return switch (type) {
            case INITIAL_STOCK, PURCHASE_RECEIVED, TRANSFER_IN, RETURN -> onHandDelta;
            case SALE, TRANSFER_OUT, RESERVATION_CONFIRMED -> Math.negateExact(onHandDelta);
            case RESERVATION_CREATED, RESERVATION_RELEASED, RESERVATION_EXPIRED -> Math.abs(reservedDelta);
            case ADJUSTMENT -> onHandDelta;
        };
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public InventoryTransactionType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public int getOnHandDelta() {
        return onHandDelta;
    }

    public int getReservedDelta() {
        return reservedDelta;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
