CREATE TABLE demand_forecasts (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    horizon_days INTEGER NOT NULL CHECK (horizon_days > 0),
    predicted_daily_demand NUMERIC(12, 4) NOT NULL CHECK (predicted_daily_demand >= 0),
    predicted_total_demand NUMERIC(12, 4) NOT NULL CHECK (predicted_total_demand >= 0),
    model_version VARCHAR(100) NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'ML_SERVICE',
    daily_breakdown_json TEXT,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_forecast_product
        FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_forecast_location
        FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE INDEX idx_demand_forecasts_prod_loc_gen
    ON demand_forecasts(product_id, location_id, generated_at DESC);

CREATE TABLE replenishment_recommendations (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    current_on_hand INTEGER NOT NULL,
    reserved_stock INTEGER NOT NULL,
    available_stock INTEGER NOT NULL,
    incoming_stock INTEGER NOT NULL DEFAULT 0,
    avg_daily_demand NUMERIC(12, 4) NOT NULL,
    lead_time_days INTEGER NOT NULL CHECK (lead_time_days >= 1),
    safety_stock INTEGER NOT NULL CHECK (safety_stock >= 0),
    reorder_point INTEGER NOT NULL CHECK (reorder_point >= 0),
    target_stock INTEGER NOT NULL CHECK (target_stock >= 0),
    recommended_quantity INTEGER NOT NULL CHECK (recommended_quantity >= 0),
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    forecast_id BIGINT,
    purchase_order_id BIGINT,
    reviewed_by_account_id BIGINT,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_replenishment_product
        FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_replenishment_location
        FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT fk_replenishment_forecast
        FOREIGN KEY (forecast_id) REFERENCES demand_forecasts(id),
    CONSTRAINT fk_replenishment_po
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_replenishment_reviewer
        FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_replenishment_priority
        CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_replenishment_status
        CHECK (status IN ('PENDING', 'APPROVED', 'ORDERED', 'DISMISSED'))
);

CREATE UNIQUE INDEX uq_replenishment_pending_product_location
    ON replenishment_recommendations(product_id, location_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_replenishment_status_priority
    ON replenishment_recommendations(status, priority);
