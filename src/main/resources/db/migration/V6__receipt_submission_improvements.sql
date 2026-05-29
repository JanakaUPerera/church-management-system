ALTER TABLE receipts
    ADD COLUMN region_id BIGINT NULL AFTER church_id,
    ADD COLUMN receipt_datetime DATETIME NULL AFTER week_end_date,
    ADD COLUMN submitted_by_name VARCHAR(150) NULL AFTER receipt_datetime,
    ADD COLUMN issued_by_user_id BIGINT NULL AFTER submitted_by_name,
    ADD COLUMN is_late_submission BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN late_submission_reason VARCHAR(255) NULL AFTER is_late_submission;

UPDATE receipts r
JOIN churches c ON c.id = r.church_id
SET r.region_id = c.region_id,
    r.receipt_datetime = CAST(r.receipt_date AS DATETIME),
    r.submitted_by_name = COALESCE(NULLIF(r.notes, ''), 'Unknown'),
    r.issued_by_user_id = r.created_by_user_id
WHERE r.region_id IS NULL
   OR r.receipt_datetime IS NULL
   OR r.submitted_by_name IS NULL
   OR r.issued_by_user_id IS NULL;

ALTER TABLE receipts
    DROP FOREIGN KEY fk_receipts_created_by_user;

ALTER TABLE receipts
    MODIFY receipt_no VARCHAR(20) NOT NULL,
    MODIFY region_id BIGINT NOT NULL,
    MODIFY week_start_date DATE NOT NULL,
    MODIFY week_end_date DATE NOT NULL,
    MODIFY receipt_datetime DATETIME NOT NULL,
    MODIFY submitted_by_name VARCHAR(150) NOT NULL,
    MODIFY issued_by_user_id BIGINT NOT NULL,
    MODIFY status ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    MODIFY corrected_from_receipt_id BIGINT NULL,
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE receipts
    DROP COLUMN created_by_user_id,
    DROP COLUMN receipt_date,
    DROP COLUMN total_amount,
    DROP COLUMN notes;

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipts_region
        FOREIGN KEY (region_id) REFERENCES regions(id),
    ADD CONSTRAINT fk_receipts_issued_by_user
        FOREIGN KEY (issued_by_user_id) REFERENCES users(id);

ALTER TABLE receipts
    ADD INDEX idx_receipts_church_week_status (church_id, week_start_date, status);

ALTER TABLE receipt_items
    RENAME COLUMN notes TO note;

ALTER TABLE receipt_items
    MODIFY collection_type ENUM('OFFERTORY','TITHES','OTHER_DONATIONS') NOT NULL,
    MODIFY amount DECIMAL(12,2) NOT NULL,
    MODIFY note VARCHAR(255) NULL,
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE receipt_items
    DROP COLUMN updated_at;

ALTER TABLE receipt_items
    ADD CONSTRAINT chk_receipt_items_amount_positive CHECK (amount > 0);
