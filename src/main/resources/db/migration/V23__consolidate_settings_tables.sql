INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT setting_key, setting_value, setting_type, category, description, editable, CURRENT_TIMESTAMP
FROM (
    SELECT 'backup.folder' setting_key, './backups' setting_value, 'STRING' setting_type, 'BACKUP' category,
           'Backup folder path' description, TRUE editable
    UNION ALL SELECT 'backup.mysqldump.path', '', 'STRING', 'BACKUP', 'mysqldump executable path', TRUE
    UNION ALL SELECT 'backup.mysql.client.path', '', 'STRING', 'BACKUP', 'mysql client executable path', TRUE
    UNION ALL SELECT 'sms.com.port', '', 'STRING', 'SMS', 'SIM dongle COM port', TRUE
    UNION ALL SELECT 'sms.baud.rate', '9600', 'INTEGER', 'SMS', 'SIM dongle baud rate', TRUE
) defaults
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings s WHERE s.setting_key = defaults.setting_key
);

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_settings'
    ),
    'UPDATE system_settings ss
     JOIN (SELECT backup_folder, auto_backup_enabled, retention_days, mysqldump_path, mysql_client_path FROM backup_settings ORDER BY id LIMIT 1) bs
     SET ss.setting_value = CASE ss.setting_key
         WHEN ''backup.folder'' THEN bs.backup_folder
         WHEN ''backup.auto.enabled'' THEN IF(bs.auto_backup_enabled, ''true'', ''false'')
         WHEN ''backup.retention.days'' THEN CAST(bs.retention_days AS CHAR)
         WHEN ''backup.mysqldump.path'' THEN COALESCE(bs.mysqldump_path, '''')
         WHEN ''backup.mysql.client.path'' THEN COALESCE(bs.mysql_client_path, '''')
         ELSE ss.setting_value
     END,
     ss.updated_at = CURRENT_TIMESTAMP
     WHERE ss.setting_key IN (''backup.folder'', ''backup.retention.days'', ''backup.mysqldump.path'', ''backup.mysql.client.path'', ''backup.auto.enabled'')',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'sms_settings'
    ),
    'UPDATE system_settings ss
     JOIN (SELECT sms_enabled, gateway_type, com_port, baud_rate FROM sms_settings ORDER BY id LIMIT 1) sms
     SET ss.setting_value = CASE ss.setting_key
         WHEN ''sms.enabled'' THEN IF(sms.sms_enabled, ''true'', ''false'')
         WHEN ''sms.gateway.type'' THEN sms.gateway_type
         WHEN ''sms.com.port'' THEN COALESCE(sms.com_port, '''')
         WHEN ''sms.baud.rate'' THEN COALESCE(CAST(sms.baud_rate AS CHAR), ''9600'')
         ELSE ss.setting_value
     END,
     ss.updated_at = CURRENT_TIMESTAMP
     WHERE ss.setting_key IN (''sms.enabled'', ''sms.gateway.type'', ''sms.com.port'', ''sms.baud.rate'')',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS sms_settings;
DROP TABLE IF EXISTS backup_settings;
