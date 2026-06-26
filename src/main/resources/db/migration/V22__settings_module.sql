CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(150) NOT NULL UNIQUE,
    setting_value TEXT NULL,
    setting_type VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_system_settings_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT setting_key, setting_value, setting_type, category, description, editable, CURRENT_TIMESTAMP
FROM (
    SELECT 'organization.name' setting_key, '' setting_value, 'STRING' setting_type, 'GENERAL' category,
           'Organization name printed and displayed across the system' description, TRUE editable
    UNION ALL SELECT 'organization.address', '', 'TEXT', 'GENERAL', 'Organization address', TRUE
    UNION ALL SELECT 'organization.phone', '', 'STRING', 'GENERAL', 'Organization phone number', TRUE
    UNION ALL SELECT 'receipt.number.prefix', 'REC', 'STRING', 'RECEIPT', 'Receipt number prefix', TRUE
    UNION ALL SELECT 'receipt.sequence.padding', '6', 'INTEGER', 'RECEIPT', 'Receipt number sequence padding', TRUE
    UNION ALL SELECT 'receipt.allow.back.week', 'true', 'BOOLEAN', 'RECEIPT', 'Allow receipt entry for prior week', TRUE
    UNION ALL SELECT 'receipt.default.language', 'ENGLISH', 'ENUM', 'RECEIPT', 'Default receipt language', TRUE
    UNION ALL SELECT 'sms.enabled', 'false', 'BOOLEAN', 'SMS', 'Enable SMS delivery', TRUE
    UNION ALL SELECT 'sms.gateway.type', 'SIM_DONGLE', 'ENUM', 'SMS', 'SMS gateway type', TRUE
    UNION ALL SELECT 'sms.retry.enabled', 'true', 'BOOLEAN', 'SMS', 'Enable SMS retry', TRUE
    UNION ALL SELECT 'sms.retry.max.attempts', '3', 'INTEGER', 'SMS', 'Maximum SMS retry attempts', TRUE
    UNION ALL SELECT 'backup.auto.enabled', 'false', 'BOOLEAN', 'BACKUP', 'Enable automatic database backups', TRUE
    UNION ALL SELECT 'backup.retention.days', '30', 'INTEGER', 'BACKUP', 'Backup retention period in days', TRUE
    UNION ALL SELECT 'system.date.format', 'yyyy-MM-dd', 'STRING', 'SYSTEM', 'System date format', TRUE
    UNION ALL SELECT 'system.time.format', 'HH:mm:ss', 'STRING', 'SYSTEM', 'System time format', TRUE
    UNION ALL SELECT 'system.theme', 'ORCHID', 'ENUM', 'SYSTEM', 'Application theme', TRUE
) defaults
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings s WHERE s.setting_key = defaults.setting_key
);

INSERT INTO permissions (name, module, description)
SELECT 'settings.manage', 'Settings', 'Manage application settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'settings.manage'
);

UPDATE permissions
SET module = 'Settings',
    description = 'Manage application settings'
WHERE name = 'settings.manage';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'settings.manage'
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
