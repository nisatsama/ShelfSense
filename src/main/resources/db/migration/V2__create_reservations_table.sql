CREATE TABLE reservations (
                              id BIGSERIAL PRIMARY KEY,

                              product_id BIGINT NOT NULL,
                              location_id BIGINT NOT NULL,

                              quantity INTEGER NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              expires_at TIMESTAMP,

                              CONSTRAINT fk_reservation_product
                                  FOREIGN KEY (product_id)
                                      REFERENCES products(id),

                              CONSTRAINT fk_reservation_location
                                  FOREIGN KEY (location_id)
                                      REFERENCES locations(id),

                              CONSTRAINT chk_reservation_quantity
                                  CHECK (quantity > 0),

                              CONSTRAINT chk_reservation_status
                                  CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX idx_reservations_product_location
    ON reservations(product_id, location_id);

CREATE INDEX idx_reservations_status
    ON reservations(status);