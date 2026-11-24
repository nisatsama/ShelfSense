package com.nisatsama.shelfsense.inventory;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private InventoryTransaction transaction;

    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "previous_on_hand", nullable = false, updatable = false)
    private int previousOnHand;

    @Column(name = "new_on_hand", nullable = false, updatable = false)
    private int newOnHand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private AdjustmentReason reason;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "idempotency_key", length = 128, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    @Column(name = "performed_by_account_id", updatable = false)
    private Long performedByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public InventoryTransaction getTransaction() { return transaction; }
    public void setTransaction(InventoryTransaction transaction) { this.transaction = transaction; }
    public int getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(int quantityDelta) { this.quantityDelta = quantityDelta; }
    public int getPreviousOnHand() { return previousOnHand; }
    public void setPreviousOnHand(int previousOnHand) { this.previousOnHand = previousOnHand; }
    public int getNewOnHand() { return newOnHand; }
    public void setNewOnHand(int newOnHand) { this.newOnHand = newOnHand; }
    public AdjustmentReason getReason() { return reason; }
    public void setReason(AdjustmentReason reason) { this.reason = reason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Long getPerformedByAccountId() { return performedByAccountId; }
    public void setPerformedByAccountId(Long performedByAccountId) { this.performedByAccountId = performedByAccountId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
