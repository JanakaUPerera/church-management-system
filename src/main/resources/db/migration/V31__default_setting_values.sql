UPDATE system_settings
SET setting_value = CASE setting_key
        WHEN 'organization.name' THEN 'Gethsemane Prayer Center Church'
        WHEN 'organization.address' THEN 'Gethsemane Prayer Church Centre, 422 A3, Katunayake'
        WHEN 'organization.phone' THEN '0312235065'
        WHEN 'sms.enabled' THEN 'true'
        WHEN 'sms.retry.enabled' THEN 'true'
        WHEN 'system.date.format' THEN 'yyyy-MMM-dd'
        WHEN 'system.time.format' THEN 'HH:mm'
        ELSE setting_value
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE (setting_key = 'organization.name' AND (setting_value IS NULL OR setting_value = '' OR setting_value = 'Default Church'))
   OR (setting_key = 'organization.address' AND (setting_value IS NULL OR setting_value = ''))
   OR (setting_key = 'organization.phone' AND (setting_value IS NULL OR setting_value = ''))
   OR (setting_key = 'sms.enabled' AND setting_value = 'false')
   OR (setting_key = 'sms.retry.enabled' AND setting_value = 'true')
   OR (setting_key = 'system.date.format' AND setting_value = 'yyyy-MM-dd')
   OR (setting_key = 'system.time.format' AND setting_value = 'HH:mm:ss');
