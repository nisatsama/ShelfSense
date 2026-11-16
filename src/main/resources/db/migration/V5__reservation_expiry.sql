-- Extend the reservation expiry invariant and ledger schema without rewriting V1-V4.
-- Existing active reservations with no expiry receive the same ten-minute policy
-- relative to their persisted creation timestamp.
UPDATE reservations
SET expires_at = created_at + INTERVAL '10 minutes'
WHERE status = 'ACTIVE' AND expires_at IS NULL;

ALTER TABLE reservations
    ADD CONSTRAINT chk_active_reservation_expiry
        CHECK (status <> 'ACTIVE' OR expires_at IS NOT NULL);

CREATE INDEX idx_reservations_active_expiry
    ON reservations(expires_at, id)
    WHERE status = 'ACTIVE';

ALTER TABLE inventory_transactions
    DROP CONSTRAINT chk_inventory_transaction_delta_shape,
    DROP CONSTRAINT chk_inventory_transaction_reservation_reference,
    DROP CONSTRAINT chk_inventory_transaction_type;

ALTER TABLE inventory_transactions
    ADD CONSTRAINT chk_inventory_transaction_delta_shape CHECK (
        (type = 'INITIAL_STOCK' AND on_hand_delta >= 0 AND reserved_delta = 0)
        OR (type = 'RESERVATION_CREATED' AND on_hand_delta = 0 AND reserved_delta > 0)
        OR (type = 'RESERVATION_RELEASED' AND on_hand_delta = 0 AND reserved_delta < 0)
        OR (type = 'RESERVATION_CONFIRMED' AND on_hand_delta < 0 AND reserved_delta < 0
            AND on_hand_delta = reserved_delta)
        OR (type = 'RESERVATION_EXPIRED' AND on_hand_delta = 0 AND reserved_delta < 0)
        OR (type = 'PURCHASE_RECEIVED' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'SALE' AND on_hand_delta < 0 AND reserved_delta = 0)
        OR (type = 'TRANSFER_OUT' AND on_hand_delta < 0 AND reserved_delta = 0)
        OR (type = 'TRANSFER_IN' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'RETURN' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'ADJUSTMENT' AND on_hand_delta <> 0 AND reserved_delta = 0)
    ),
    ADD CONSTRAINT chk_inventory_transaction_reservation_reference CHECK (
        (type IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED', 'RESERVATION_EXPIRED')
            AND reservation_id IS NOT NULL)
        OR (type NOT IN ('RESERVATION_CREATED', 'RESERVATION_RELEASED', 'RESERVATION_CONFIRMED', 'RESERVATION_EXPIRED')
            AND reservation_id IS NULL)
    ),
    ADD CONSTRAINT chk_inventory_transaction_type CHECK (
        type IN (
            'INITIAL_STOCK',
            'PURCHASE_RECEIVED',
            'SALE',
            'RESERVATION_CREATED',
            'RESERVATION_RELEASED',
            'RESERVATION_CONFIRMED',
            'RESERVATION_EXPIRED',
            'TRANSFER_OUT',
            'TRANSFER_IN',
            'ADJUSTMENT',
            'RETURN'
        )
    );

DROP INDEX uq_inventory_transactions_reservation_type;
CREATE UNIQUE INDEX uq_inventory_transactions_reservation_type
    ON inventory_transactions(reservation_id, type)
    WHERE type IN (
        'RESERVATION_CREATED',
        'RESERVATION_RELEASED',
        'RESERVATION_CONFIRMED',
        'RESERVATION_EXPIRED'
    );
