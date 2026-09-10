
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_location_type
        CHECK (type IN ('STORE', 'WAREHOUSE', 'DISTRIBUTION_CENTER'))
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,

    product_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,

    on_hand INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,

    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT fk_inventory_location
        FOREIGN KEY (location_id)
        REFERENCES locations(id),

    CONSTRAINT chk_inventory_on_hand
        CHECK (on_hand >= 0),

    CONSTRAINT chk_inventory_reserved
        CHECK (reserved >= 0),

    CONSTRAINT chk_inventory_reserved_limit
        CHECK (reserved <= on_hand),

    CONSTRAINT uq_inventory_product_location
        UNIQUE (product_id, location_id)
);

CREATE INDEX idx_inventory_product_id
    ON inventory(product_id);

CREATE INDEX idx_inventory_location_id
    ON inventory(location_id);

