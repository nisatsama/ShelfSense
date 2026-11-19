ALTER TABLE reservations
    ADD COLUMN account_id BIGINT;

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservation_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(id);

CREATE INDEX idx_reservations_account_id
    ON reservations(account_id);
