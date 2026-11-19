package com.nisatsama.shelfsense.forecast;

import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "demand_forecasts")
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "predicted_daily_demand", nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedDailyDemand;

    @Column(name = "predicted_total_demand", nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedTotalDemand;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(nullable = false, length = 40)
    private String source = "ML_SERVICE";

    @Column(name = "daily_breakdown_json", columnDefinition = "TEXT")
    private String dailyBreakdownJson;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public BigDecimal getPredictedDailyDemand() { return predictedDailyDemand; }
    public void setPredictedDailyDemand(BigDecimal predictedDailyDemand) { this.predictedDailyDemand = predictedDailyDemand; }
    public BigDecimal getPredictedTotalDemand() { return predictedTotalDemand; }
    public void setPredictedTotalDemand(BigDecimal predictedTotalDemand) { this.predictedTotalDemand = predictedTotalDemand; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDailyBreakdownJson() { return dailyBreakdownJson; }
    public void setDailyBreakdownJson(String dailyBreakdownJson) { this.dailyBreakdownJson = dailyBreakdownJson; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
