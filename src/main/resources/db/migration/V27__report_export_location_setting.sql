INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'reports.export.folder', './reports', 'STRING', 'SYSTEM',
       'Folder where report PDF and Excel exports are saved', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'reports.export.folder'
);
