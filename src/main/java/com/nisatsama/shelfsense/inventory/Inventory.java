package com.nisatsama.shelfsense.inventory;

import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.product.Product;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_inventory_product_location",
                        columnNames = {"product_id", "location_id"}
                )
        }
)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "on_hand", nullable = false)
    private int onHand = 0;

    @Column(nullable = false)
    private int reserved = 0;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Available inventory is derived from the source values.
     * It is NOT stored as a separate database column.
     */
    @Transient
    public int getAvailable() {
        return onHand - reserved;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getOnHand() {
        return onHand;
    }

    public void setOnHand(int onHand) {
        this.onHand = onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}