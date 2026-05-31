CREATE TABLE sms_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT NULL,
    church_id BIGINT NULL,
    mobile_number VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    provider VARCHAR(100) NULL,
    status ENUM('SUCCESS','FAILED') NOT NULL,
    error_message VARCHAR(500) NULL,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_sms_logs_receipt_id (receipt_id),
    INDEX idx_sms_logs_church_id (church_id),
    CONSTRAINT fk_sms_logs_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_sms_logs_church
        FOREIGN KEY (church_id) REFERENCES churches (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sms_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    gateway_type ENUM('MOCK','SIM_DONGLE') NOT NULL DEFAULT 'MOCK',
    com_port VARCHAR(50) NULL,
    baud_rate INT NULL DEFAULT 9600,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sms_settings (sms_enabled, gateway_type, baud_rate, created_at)
SELECT FALSE, 'MOCK', 9600, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sms_settings
);

INSERT INTO permissions (name, description)
SELECT 'sms.settings.manage', 'Manage SMS gateway settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'sms.settings.manage'
);

INSERT INTO permissions (name, description)
SELECT 'sms.logs.view', 'View SMS logs'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'sms.logs.view'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('sms.settings.manage', 'sms.logs.view')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
