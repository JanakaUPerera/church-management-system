ALTER TABLE receipt_cancellations
    RENAME COLUMN reason TO cancel_reason;

ALTER TABLE receipt_cancellations
    MODIFY receipt_id BIGINT NOT NULL,
    MODIFY cancelled_by_user_id BIGINT NOT NULL,
    MODIFY cancel_reason VARCHAR(500) NOT NULL,
    MODIFY cancelled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE receipts
    ADD INDEX idx_receipts_corrected_from (corrected_from_receipt_id);
