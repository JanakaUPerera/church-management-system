DROP TEMPORARY TABLE IF EXISTS seed_receipt_weeks;
DROP TEMPORARY TABLE IF EXISTS seed_receipt_rows;

CREATE TEMPORARY TABLE seed_receipt_weeks (
    week_start_date DATE NOT NULL PRIMARY KEY,
    week_index INT NOT NULL
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO seed_receipt_weeks (week_start_date, week_index)
WITH RECURSIVE weeks AS (
    SELECT DATE('2025-01-06') AS week_start_date, 1 AS week_index
    UNION ALL
    SELECT DATE_ADD(week_start_date, INTERVAL 7 DAY), week_index + 1
    FROM weeks
    WHERE week_start_date < DATE('2026-02-23')
)
SELECT week_start_date, week_index
FROM weeks;

CREATE TEMPORARY TABLE seed_receipt_rows (
    receipt_no VARCHAR(20) NOT NULL PRIMARY KEY,
    church_id BIGINT NOT NULL,
    church_code VARCHAR(20) NOT NULL,
    church_number INT NOT NULL,
    region_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    week_index INT NOT NULL,
    receipt_datetime DATETIME NOT NULL,
    submitted_by_name VARCHAR(150) NOT NULL,
    issued_by_user_id BIGINT NOT NULL,
    is_late_submission BOOLEAN NOT NULL,
    late_submission_reason VARCHAR(255) NULL,
    UNIQUE KEY uk_seed_receipt_rows_church_week (church_id, week_start_date)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO seed_receipt_rows (
    receipt_no,
    church_id,
    church_code,
    church_number,
    region_id,
    week_start_date,
    week_end_date,
    week_index,
    receipt_datetime,
    submitted_by_name,
    issued_by_user_id,
    is_late_submission,
    late_submission_reason
)
SELECT
    CONCAT('TST', DATE_FORMAT(seed_week.week_start_date, '%y%m%d'), LPAD(CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED), 3, '0')) AS receipt_no,
    church.id AS church_id,
    church.church_code,
    CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED) AS church_number,
    church.region_id,
    seed_week.week_start_date,
    DATE_ADD(seed_week.week_start_date, INTERVAL 6 DAY) AS week_end_date,
    seed_week.week_index,
    DATE_ADD(
        DATE_ADD(seed_week.week_start_date, INTERVAL 6 DAY),
        INTERVAL (10 + (CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED) MOD 7)) HOUR
    ) AS receipt_datetime,
    CONCAT('Seed Treasurer ', LPAD(CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED), 3, '0')) AS submitted_by_name,
    admin_user.id AS issued_by_user_id,
    CASE WHEN (seed_week.week_index + CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED)) MOD 11 = 0 THEN TRUE ELSE FALSE END AS is_late_submission,
    CASE
        WHEN (seed_week.week_index + CAST(SUBSTRING(church.church_code, 4) AS UNSIGNED)) MOD 11 = 0
            THEN 'Seeded late submission.'
        ELSE NULL
    END AS late_submission_reason
FROM seed_receipt_weeks seed_week
JOIN churches church
    ON church.church_code LIKE 'TCH%'
   AND church.status = 'ACTIVE'
JOIN users admin_user
    ON admin_user.username = 'admin';

INSERT IGNORE INTO receipts (
    receipt_no,
    church_id,
    region_id,
    week_start_date,
    week_end_date,
    receipt_datetime,
    submitted_by_name,
    issued_by_user_id,
    status,
    is_late_submission,
    late_submission_reason,
    corrected_from_receipt_id,
    created_at
)
SELECT
    seed_row.receipt_no,
    seed_row.church_id,
    seed_row.region_id,
    seed_row.week_start_date,
    seed_row.week_end_date,
    seed_row.receipt_datetime,
    seed_row.submitted_by_name,
    seed_row.issued_by_user_id,
    'ACTIVE',
    seed_row.is_late_submission,
    seed_row.late_submission_reason,
    NULL,
    seed_row.receipt_datetime
FROM seed_receipt_rows seed_row
LEFT JOIN receipts existing_active
    ON existing_active.church_id = seed_row.church_id
   AND existing_active.week_start_date = seed_row.week_start_date
   AND existing_active.status = 'ACTIVE'
WHERE existing_active.id IS NULL;

INSERT INTO receipt_items (receipt_id, collection_type, amount, note, created_at)
SELECT
    receipt.id,
    item.collection_type,
    CASE item.collection_type
        WHEN 'OFFERTORY' THEN 2000.00 + (seed_row.church_number * 17) + (seed_row.week_index * 11)
        WHEN 'TITHES' THEN 1500.00 + (seed_row.church_number * 13) + (seed_row.week_index * 7)
        ELSE 700.00 + (seed_row.church_number * 5) + (seed_row.week_index * 3)
    END AS amount,
    'Seeded receipt item.',
    receipt.created_at
FROM seed_receipt_rows seed_row
JOIN receipts receipt
    ON receipt.receipt_no = seed_row.receipt_no
JOIN (
    SELECT 'OFFERTORY' AS collection_type
    UNION ALL SELECT 'TITHES'
    UNION ALL SELECT 'OTHER_DONATIONS'
) item
LEFT JOIN receipt_items existing_item
    ON existing_item.receipt_id = receipt.id
   AND existing_item.collection_type = item.collection_type
WHERE existing_item.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS seed_receipt_rows;
DROP TEMPORARY TABLE IF EXISTS seed_receipt_weeks;
