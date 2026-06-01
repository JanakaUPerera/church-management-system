SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND column_name = 'backup_type'
    ),
    'SELECT 1',
    'ALTER TABLE backup_logs ADD COLUMN backup_type ENUM(''MANUAL'',''AUTO'',''PRE_RESTORE'') NOT NULL DEFAULT ''MANUAL'' AFTER id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND column_name = 'file_name'
    ),
    'SELECT 1',
    'ALTER TABLE backup_logs ADD COLUMN file_name VARCHAR(255) NULL AFTER backup_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND column_name = 'file_path'
    ),
    'SELECT 1',
    'ALTER TABLE backup_logs ADD COLUMN file_path VARCHAR(1000) NULL AFTER file_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND column_name = 'file_size_bytes'
    ),
    'SELECT 1',
    'ALTER TABLE backup_logs ADD COLUMN file_size_bytes BIGINT NULL AFTER file_path'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND column_name = 'error_message'
    ),
    'SELECT 1',
    'ALTER TABLE backup_logs ADD COLUMN error_message VARCHAR(1000) NULL AFTER status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE backup_logs
SET file_name = COALESCE(file_name, backup_file),
    file_path = COALESCE(file_path, backup_file),
    error_message = COALESCE(error_message, message),
    status = CASE
        WHEN UPPER(status) = 'SUCCESS' THEN 'SUCCESS'
        ELSE 'FAILED'
    END;

ALTER TABLE backup_logs
    MODIFY COLUMN file_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN file_path VARCHAR(1000) NOT NULL,
    MODIFY COLUMN status ENUM('SUCCESS','FAILED') NOT NULL,
    MODIFY COLUMN created_at DATETIME NOT NULL;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'backup_logs'
          AND index_name = 'idx_backup_logs_created_at'
    ),
    'SELECT 1',
    'CREATE INDEX idx_backup_logs_created_at ON backup_logs (created_at)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS restore_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    backup_file_name VARCHAR(255) NOT NULL,
    backup_file_path VARCHAR(1000) NOT NULL,
    pre_restore_backup_log_id BIGINT NULL,
    status ENUM('SUCCESS','FAILED') NOT NULL,
    error_message VARCHAR(1000) NULL,
    restored_by_user_id BIGINT NOT NULL,
    restored_at DATETIME NOT NULL,
    INDEX idx_restore_logs_restored_at (restored_at),
    CONSTRAINT fk_restore_logs_restored_by_user
        FOREIGN KEY (restored_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_restore_logs_pre_restore_backup
        FOREIGN KEY (pre_restore_backup_log_id) REFERENCES backup_logs (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS backup_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    backup_folder VARCHAR(1000) NOT NULL,
    auto_backup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_backup_time TIME NULL,
    retention_days INT NOT NULL DEFAULT 30,
    mysqldump_path VARCHAR(1000) NULL,
    mysql_client_path VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO backup_settings (backup_folder, auto_backup_enabled, retention_days, created_at)
SELECT './backups', FALSE, 30, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM backup_settings
);

INSERT INTO permissions (name, description)
SELECT 'backup.create', 'Create database backups'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'backup.create');

INSERT INTO permissions (name, description)
SELECT 'backup.restore', 'Restore database backups'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'backup.restore');

INSERT INTO permissions (name, description)
SELECT 'backup.view', 'View backup and restore history'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'backup.view');

INSERT INTO permissions (name, description)
SELECT 'backup.settings.manage', 'Manage backup settings'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'backup.settings.manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('backup.create', 'backup.restore', 'backup.view', 'backup.settings.manage')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
