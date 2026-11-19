CREATE TABLE inventory_adjustments (
    id BIGSERIAL PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    previous_on_hand INTEGER NOT NULL CHECK (previous_on_hand >= 0),
    new_on_hand INTEGER NOT NULL CHECK (new_on_hand >= 0),
    reason VARCHAR(60) NOT NULL,
    notes VARCHAR(500),
    idempotency_key VARCHAR(128),
    performed_by_account_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_adjustment_inventory
        FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    CONSTRAINT fk_inventory_adjustment_transaction
        FOREIGN KEY (transaction_id) REFERENCES inventory_transactions(id),
    CONSTRAINT fk_inventory_adjustment_account
        FOREIGN KEY (performed_by_account_id) REFERENCES accounts(id),
    CONSTRAINT uq_inventory_adjustment_idempotency
        UNIQUE (idempotency_key)
);

CREATE INDEX idx_inventory_adjustments_inventory_created
    ON inventory_adjustments(inventory_id, created_at DESC);

CREATE TABLE inventory_transfers (
    id BIGSERIAL PRIMARY KEY,
    transfer_number VARCHAR(64) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    source_location_id BIGINT NOT NULL,
    destination_location_id BIGINT NOT NULL,
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    dispatched_quantity INTEGER NOT NULL DEFAULT 0 CHECK (dispatched_quantity >= 0),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    damaged_quantity INTEGER NOT NULL DEFAULT 0 CHECK (damaged_quantity >= 0),
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(500),
    created_by_account_id BIGINT,
    approved_by_account_id BIGINT,
    dispatched_at TIMESTAMP,
    received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_transfer_product
        FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_transfer_source_location
        FOREIGN KEY (source_location_id) REFERENCES locations(id),
    CONSTRAINT fk_transfer_destination_location
        FOREIGN KEY (destination_location_id) REFERENCES locations(id),
    CONSTRAINT fk_transfer_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transfer_approved_by
        FOREIGN KEY (approved_by_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_transfer_locations_distinct
        CHECK (source_location_id <> destination_location_id),
    CONSTRAINT chk_transfer_status
        CHECK (status IN ('CREATED', 'APPROVED', 'DISPATCHED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT chk_transfer_receipt_bounds
        CHECK (received_quantity + damaged_quantity <= dispatched_quantity)
);

CREATE INDEX idx_inventory_transfers_status
    ON inventory_transfers(status);

CREATE INDEX idx_inventory_transfers_destination_status
    ON inventory_transfers(destination_location_id, product_id, status);

CREATE INDEX idx_inventory_transfers_source
    ON inventory_transfers(source_location_id);
