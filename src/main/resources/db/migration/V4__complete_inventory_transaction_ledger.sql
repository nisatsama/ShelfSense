-- Existing ledger rows were inspected before this migration was authored.
-- ADJUSTMENT has no historical sign convention, so refuse to guess if rows exist.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM inventory_transactions WHERE type = 'ADJUSTMENT'
    ) THEN
        RAISE EXCEPTION 'Cannot backfill historical ADJUSTMENT ledger rows: quantity sign semantics are undefined';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inventory_transactions t
        LEFT JOIN reservations r ON r.id = t.reference_id
        WHERE t.type IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED')
          AND (t.reference_id IS NULL OR r.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'Cannot backfill reservation ledger rows without a matching reservation reference';
    END IF;
END $$;

ALTER TABLE inventory_transactions
    ADD COLUMN on_hand_delta INTEGER,
    ADD COLUMN reserved_delta INTEGER,
    ADD COLUMN reservation_id BIGINT;

-- Non-adjustment transaction types define their own direction. quantity is
-- treated as a magnitude; abs(INT_MIN) intentionally fails rather than wrap.
UPDATE inventory_transactions
SET on_hand_delta = CASE type
        WHEN 'PURCHASE_RECEIVED' THEN abs(quantity)
        WHEN 'SALE' THEN -abs(quantity)
        WHEN 'TRANSFER_OUT' THEN -abs(quantity)
        WHEN 'TRANSFER_IN' THEN abs(quantity)
        WHEN 'RETURN' THEN abs(quantity)
        WHEN 'RESERVATION_CONFIRMED' THEN -abs(quantity)
        ELSE 0
    END,
    reserved_delta = CASE type
        WHEN 'RESERVATION_CREATED' THEN abs(quantity)
        WHEN 'RESERVATION_RELEASED' THEN -abs(quantity)
        WHEN 'RESERVATION_CONFIRMED' THEN -abs(quantity)
        ELSE 0
    END,
    reservation_id = CASE
        WHEN type IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED')
            THEN reference_id
        ELSE NULL
    END;

ALTER TABLE inventory_transactions
    ALTER COLUMN on_hand_delta SET NOT NULL,
    ALTER COLUMN reserved_delta SET NOT NULL,
    ADD CONSTRAINT fk_inventory_transaction_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    ADD CONSTRAINT chk_inventory_transaction_delta_shape CHECK (
        (type = 'INITIAL_STOCK' AND on_hand_delta >= 0 AND reserved_delta = 0)
        OR (type = 'RESERVATION_CREATED' AND on_hand_delta = 0 AND reserved_delta > 0)
        OR (type = 'RESERVATION_RELEASED' AND on_hand_delta = 0 AND reserved_delta < 0)
        OR (type = 'RESERVATION_CONFIRMED' AND on_hand_delta < 0 AND reserved_delta < 0
            AND on_hand_delta = reserved_delta)
        OR (type = 'PURCHASE_RECEIVED' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'SALE' AND on_hand_delta < 0 AND reserved_delta = 0)
        OR (type = 'TRANSFER_OUT' AND on_hand_delta < 0 AND reserved_delta = 0)
        OR (type = 'TRANSFER_IN' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'RETURN' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'ADJUSTMENT' AND on_hand_delta <> 0 AND reserved_delta = 0)
    ),
    ADD CONSTRAINT chk_inventory_transaction_reservation_reference CHECK (
        (type IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED')
            AND reservation_id IS NOT NULL)
        OR (type NOT IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED')
            AND reservation_id IS NULL)
    ),
    DROP CONSTRAINT chk_inventory_transaction_type,
    ADD CONSTRAINT chk_inventory_transaction_type CHECK (
        type IN (
            'INITIAL_STOCK',
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
    );

CREATE INDEX idx_inventory_transactions_inventory_created
    ON inventory_transactions(inventory_id, created_at, id);

CREATE INDEX idx_inventory_transactions_reservation_created
    ON inventory_transactions(reservation_id, created_at, id)
    WHERE reservation_id IS NOT NULL;

CREATE UNIQUE INDEX uq_inventory_transactions_initial_stock
    ON inventory_transactions(inventory_id)
    WHERE type = 'INITIAL_STOCK';

CREATE UNIQUE INDEX uq_inventory_transactions_reservation_type
    ON inventory_transactions(reservation_id, type)
    WHERE type IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED');
