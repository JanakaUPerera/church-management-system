INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'receipt.late.reason.required', 'false', 'BOOLEAN', 'RECEIPT',
       'Require late submission reason', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'receipt.late.reason.required'
);
