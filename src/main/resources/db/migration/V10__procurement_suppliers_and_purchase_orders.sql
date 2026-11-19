CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    phone VARCHAR(60),
    address VARCHAR(500),
    lead_time_days INTEGER NOT NULL DEFAULT 7 CHECK (lead_time_days >= 1),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(64) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    destination_location_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    expected_delivery_date DATE,
    total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    notes VARCHAR(500),
    created_by_account_id BIGINT,
    approved_by_account_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_po_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_po_destination_location
        FOREIGN KEY (destination_location_id) REFERENCES locations(id),
    CONSTRAINT fk_po_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_po_approved_by
        FOREIGN KEY (approved_by_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_po_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

CREATE INDEX idx_po_status ON purchase_orders(status);
CREATE INDEX idx_po_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_po_destination_status ON purchase_orders(destination_location_id, status);

CREATE TABLE purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    ordered_quantity INTEGER NOT NULL CHECK (ordered_quantity > 0),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),

    CONSTRAINT fk_po_item_po
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_po_item_product
        FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_po_item_received_bounds
        CHECK (received_quantity <= ordered_quantity),
    CONSTRAINT uq_po_item_product
        UNIQUE (purchase_order_id, product_id)
);

CREATE INDEX idx_po_items_product ON purchase_order_items(product_id);

CREATE TABLE goods_receipts (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(64) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL,
    received_by_account_id BIGINT,
    notes VARCHAR(500),
    idempotency_key VARCHAR(128),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_goods_receipt_po
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_goods_receipt_account
        FOREIGN KEY (received_by_account_id) REFERENCES accounts(id),
    CONSTRAINT uq_goods_receipt_idempotency
        UNIQUE (idempotency_key)
);

CREATE INDEX idx_goods_receipts_po ON goods_receipts(purchase_order_id);

CREATE TABLE goods_receipt_items (
    id BIGSERIAL PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    received_quantity INTEGER NOT NULL CHECK (received_quantity > 0),

    CONSTRAINT fk_gri_receipt
        FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_gri_po_item
        FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
    CONSTRAINT fk_gri_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);
