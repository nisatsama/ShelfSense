ALTER TABLE inventory_adjustments
    ADD COLUMN request_hash VARCHAR(64);

ALTER TABLE goods_receipts
    ADD COLUMN request_hash VARCHAR(64);
