CREATE TABLE receipt_sequences_new (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sequence_year INT NOT NULL UNIQUE,
    last_number BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO receipt_sequences_new (sequence_year, last_number, created_at, updated_at)
SELECT YEAR(CURRENT_DATE), COALESCE(MAX(current_number), 0), CURRENT_TIMESTAMP, NULL
FROM receipt_sequences
HAVING COUNT(*) > 0;

DROP TABLE receipt_sequences;

RENAME TABLE receipt_sequences_new TO receipt_sequences;
