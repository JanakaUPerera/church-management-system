ALTER TABLE receipts
    ADD COLUMN pdf_file_path VARCHAR(500) NULL AFTER corrected_from_receipt_id,
    ADD COLUMN original_printed BOOLEAN NOT NULL DEFAULT FALSE AFTER pdf_file_path,
    ADD COLUMN original_printed_at DATETIME NULL AFTER original_printed,
    ADD COLUMN original_printed_by_user_id BIGINT NULL AFTER original_printed_at,
    ADD COLUMN print_attempt_count INT NOT NULL DEFAULT 0 AFTER original_printed_by_user_id,
    ADD CONSTRAINT fk_receipts_original_printed_by_user
        FOREIGN KEY (original_printed_by_user_id) REFERENCES users (id);

CREATE TABLE receipt_print_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    printed_by_user_id BIGINT NOT NULL,
    print_type ENUM('ORIGINAL','INTERNAL_VIEW') NOT NULL,
    status ENUM('SUCCESS','FAILED') NOT NULL,
    error_message VARCHAR(500) NULL,
    printed_at DATETIME NOT NULL,
    CONSTRAINT fk_receipt_print_logs_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id),
    CONSTRAINT fk_receipt_print_logs_user
        FOREIGN KEY (printed_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO permissions (name, description)
SELECT 'receipt.print', 'Print original receipts'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'receipt.print'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'receipt.print'
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'receipt.pdf.output.folder', './receipts', 'Folder where generated receipt PDFs are stored.'
WHERE NOT EXISTS (
    SELECT 1 FROM app_settings WHERE setting_key = 'receipt.pdf.output.folder'
);
