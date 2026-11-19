package com.nisatsama.shelfsense.replenishment;

import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "replenishment_recommendations")
public class ReplenishmentRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "current_on_hand", nullable = false)
    private int currentOnHand;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "incoming_stock", nullable = false)
    private int incomingStock;

    @Column(name = "avg_daily_demand", nullable = false, precision = 12, scale = 4)
    private BigDecimal avgDailyDemand;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "target_stock", nullable = false)
    private int targetStock;

    @Column(name = "recommended_quantity", nullable = false)
    private int recommendedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplenishmentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplenishmentStatus status = ReplenishmentStatus.PENDING;

    @Column(name = "forecast_id")
    private Long forecastId;

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(name = "reviewed_by_account_id")
    private Long reviewedByAccountId;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = ReplenishmentStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public int getCurrentOnHand() { return currentOnHand; }
    public void setCurrentOnHand(int currentOnHand) { this.currentOnHand = currentOnHand; }
    public int getReservedStock() { return reservedStock; }
    public void setReservedStock(int reservedStock) { this.reservedStock = reservedStock; }
    public int getAvailableStock() { return availableStock; }
    public void setAvailableStock(int availableStock) { this.availableStock = availableStock; }
    public int getIncomingStock() { return incomingStock; }
    public void setIncomingStock(int incomingStock) { this.incomingStock = incomingStock; }
    public BigDecimal getAvgDailyDemand() { return avgDailyDemand; }
    public void setAvgDailyDemand(BigDecimal avgDailyDemand) { this.avgDailyDemand = avgDailyDemand; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public int getSafetyStock() { return safetyStock; }
    public void setSafetyStock(int safetyStock) { this.safetyStock = safetyStock; }
    public int getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(int reorderPoint) { this.reorderPoint = reorderPoint; }
    public int getTargetStock() { return targetStock; }
    public void setTargetStock(int targetStock) { this.targetStock = targetStock; }
    public int getRecommendedQuantity() { return recommendedQuantity; }
    public void setRecommendedQuantity(int recommendedQuantity) { this.recommendedQuantity = recommendedQuantity; }
    public ReplenishmentPriority getPriority() { return priority; }
    public void setPriority(ReplenishmentPriority priority) { this.priority = priority; }
    public ReplenishmentStatus getStatus() { return status; }
    public void setStatus(ReplenishmentStatus status) { this.status = status; }
    public Long getForecastId() { return forecastId; }
    public void setForecastId(Long forecastId) { this.forecastId = forecastId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public Long getReviewedByAccountId() { return reviewedByAccountId; }
    public void setReviewedByAccountId(Long reviewedByAccountId) { this.reviewedByAccountId = reviewedByAccountId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
