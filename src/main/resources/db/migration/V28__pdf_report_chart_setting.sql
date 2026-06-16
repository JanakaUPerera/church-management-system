INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'reports.pdf.charts.enabled', 'true', 'BOOLEAN', 'SYSTEM',
       'Show charts at the bottom of PDF reports where chart summaries are available', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'reports.pdf.charts.enabled'
);
