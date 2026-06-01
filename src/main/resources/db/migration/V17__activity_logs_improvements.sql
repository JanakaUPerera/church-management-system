ALTER TABLE activity_logs
    ADD COLUMN username VARCHAR(100) NULL AFTER user_id,
    ADD COLUMN module VARCHAR(100) NULL AFTER action,
    ADD COLUMN record_id VARCHAR(100) NULL AFTER module,
    ADD COLUMN old_value TEXT NULL AFTER record_id,
    ADD COLUMN new_value TEXT NULL AFTER old_value,
    ADD COLUMN ip_address VARCHAR(100) NULL AFTER new_value,
    ADD COLUMN machine_name VARCHAR(150) NULL AFTER ip_address,
    ADD COLUMN description VARCHAR(500) NULL AFTER machine_name;

UPDATE activity_logs al
LEFT JOIN users u ON u.id = al.user_id
SET al.username = COALESCE(al.username, u.username),
    al.module = COALESCE(al.module, al.entity_name),
    al.record_id = COALESCE(al.record_id, CAST(al.entity_id AS CHAR)),
    al.description = COALESCE(al.description, LEFT(al.details, 500));

ALTER TABLE activity_logs
    MODIFY COLUMN username VARCHAR(100) NULL,
    MODIFY COLUMN action VARCHAR(100) NOT NULL,
    MODIFY COLUMN module VARCHAR(100) NULL,
    MODIFY COLUMN record_id VARCHAR(100) NULL,
    MODIFY COLUMN ip_address VARCHAR(100) NULL,
    MODIFY COLUMN machine_name VARCHAR(150) NULL,
    MODIFY COLUMN description VARCHAR(500) NULL,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

INSERT INTO permissions (name, description)
SELECT 'activity.view', 'View system activity logs'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'activity.view'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'activity.view'
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
