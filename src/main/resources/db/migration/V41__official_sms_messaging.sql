ALTER TABLE sms_logs
    ADD COLUMN sms_type VARCHAR(30) NOT NULL DEFAULT 'RECEIPT' AFTER sms_log_uuid,
    ADD COLUMN batch_uuid CHAR(36) NULL AFTER sms_type,
    ADD INDEX idx_sms_logs_sms_type (sms_type),
    ADD INDEX idx_sms_logs_batch_uuid (batch_uuid);

INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'sms.official.menu.view' permission_code, 'SMS' module_name, 'View Send Message menu' permission_description
    UNION ALL SELECT 'sms.official.send', 'SMS', 'Send official SMS messages to branches'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('sms.official.menu.view', 'sms.official.send')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'sms.official.footer', '', 'STRING', 'SMS',
       'Footer added on its own line to every official SMS message (counts toward the length)',
       TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'sms.official.footer'
);
