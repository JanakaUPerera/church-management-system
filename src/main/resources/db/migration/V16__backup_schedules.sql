CREATE TABLE IF NOT EXISTS backup_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_name VARCHAR(120) NOT NULL,
    backup_time TIME NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_backup_schedules_enabled_time (enabled, backup_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO backup_schedules (schedule_name, backup_time, enabled, created_at)
SELECT 'Default', auto_backup_time, auto_backup_enabled, CURRENT_TIMESTAMP
FROM backup_settings
WHERE auto_backup_time IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM backup_schedules);
