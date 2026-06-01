ALTER TABLE users
    ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE AFTER password_hash;

INSERT INTO permissions (name, description)
SELECT 'user.manage', 'Manage system users'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'user.manage'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'user.manage'
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
