CREATE TABLE inventory_transactions (
                                        id BIGSERIAL PRIMARY KEY,

                                        inventory_id BIGINT NOT NULL,

                                        type VARCHAR(30) NOT NULL,

                                        quantity INTEGER NOT NULL,

                                        reference_id BIGINT,

                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        CONSTRAINT fk_inventory_transaction_inventory
                                            FOREIGN KEY (inventory_id)
                                                REFERENCES inventory(id),

                                        CONSTRAINT chk_inventory_transaction_type
                                            CHECK (
                                                type IN (
                                                         'PURCHASE_RECEIVED',
                                                         'SALE',
                                                         'RESERVATION_CREATED',
                                                         'RESERVATION_RELEASED',
                                                         'RESERVATION_CONFIRMED',
                                                         'TRANSFER_OUT',
                                                         'TRANSFER_IN',
                                                         'ADJUSTMENT',
                                                         'RETURN'
                                                    )
                                                )
);

CREATE INDEX idx_inventory_transactions_inventory_id
    ON inventory_transactions(inventory_id);

CREATE INDEX idx_inventory_transactions_type
    ON inventory_transactions(type);

CREATE INDEX idx_inventory_transactions_reference_id
    ON inventory_transactions(reference_id);