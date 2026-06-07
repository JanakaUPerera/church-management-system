-- Add granular CRUD permissions for Users and Roles, replacing the coarse
-- user.manage / role.manage gates, and remove orphaned legacy permission codes.

INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'user.create' permission_code, 'User Management' module_name, 'Create users' permission_description
    UNION ALL SELECT 'user.update', 'User Management', 'Update users and reset passwords'
    UNION ALL SELECT 'user.delete', 'User Management', 'Activate or deactivate users'
    UNION ALL SELECT 'role.create', 'Roles & Permissions', 'Create roles'
    UNION ALL SELECT 'role.update', 'Roles & Permissions', 'Update roles and manage role permissions'
    UNION ALL SELECT 'role.delete', 'Roles & Permissions', 'Activate or deactivate roles'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

-- Preserve existing access: grant the granular permissions to every role
-- that currently holds the coarse permission being replaced
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'user.manage'
JOIN permissions np ON np.name IN ('user.create', 'user.update', 'user.delete')
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'role.manage'
JOIN permissions np ON np.name IN ('role.create', 'role.update', 'role.delete')
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

-- Remove the now-unused coarse permissions and legacy uppercase permission codes
DELETE FROM permissions WHERE name IN (
    'user.manage', 'role.manage',
    'BACKUP_MANAGE', 'CHURCH_MANAGE', 'RECEIPT_CANCEL', 'RECEIPT_CREATE',
    'REGION_MANAGE', 'REPORT_VIEW', 'ROLE_MANAGE', 'SETTINGS_MANAGE', 'USER_MANAGE'
);
