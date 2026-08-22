-- The backup destination folder and mysqldump/mysql tool paths are local to
-- whichever machine runs the backup. Storing them in the shared
-- system_settings table meant one client's configured tool path silently
-- broke every other client's automatic backup ("Backup failed. Please check
-- database credentials and tool paths."). They now live in each machine's
-- own external application.properties (see BackupSettingsRepository /
-- DatabaseConfig.setProperty). backup.retention.days stays shared — it's a
-- genuine global policy, not a filesystem path.
DELETE FROM system_settings WHERE setting_key IN
    ('backup.folder', 'backup.mysqldump.path', 'backup.mysql.client.path');
