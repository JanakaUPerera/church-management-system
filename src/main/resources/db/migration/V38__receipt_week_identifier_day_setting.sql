INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'receipt.week.identifier.day', 'MONDAY', 'ENUM', 'RECEIPT',
       'Day of week that identifies/ends a submission week (the week runs from 6 days before this day through this day)',
       TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'receipt.week.identifier.day'
);
