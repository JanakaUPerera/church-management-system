-- Separate navigation/page-load permissions from action permissions.
-- Existing nav permissions are renamed to the <module>.menu.view pattern,
-- and new dedicated nav permissions are added for modules that previously
-- reused a coarse action permission to gate menu access.

-- Rename existing navigation permissions to <module>.menu.view
UPDATE permissions SET name = 'region.menu.view', description = 'View Regions menu'
WHERE name = 'region.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'region.menu.view');

UPDATE permissions SET name = 'church.menu.view', description = 'View Churches menu'
WHERE name = 'church.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'church.menu.view');

UPDATE permissions SET name = 'receipt.menu.view', description = 'View Receipts menu (history & submission status)'
WHERE name = 'receipt.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'receipt.menu.view');

UPDATE permissions SET name = 'report.menu.view', description = 'View Reports menu'
WHERE name = 'report.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'report.menu.view');

UPDATE permissions SET name = 'activity.menu.view', description = 'View Activity Logs menu'
WHERE name = 'activity.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'activity.menu.view');

UPDATE permissions SET name = 'sms.menu.view', description = 'View SMS Logs menu'
WHERE name = 'sms.logs.view' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM permissions) p2 WHERE p2.name = 'sms.menu.view');

-- Add dedicated navigation permissions for modules that previously
-- reused a coarse action permission for menu access
INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'receipt.entry.menu.view' permission_code, 'Receipts' module_name, 'View Weekly Receipts entry menu' permission_description
    UNION ALL SELECT 'user.menu.view', 'User Management', 'View Users menu'
    UNION ALL SELECT 'role.menu.view', 'Roles & Permissions', 'View Roles & Permissions menu'
    UNION ALL SELECT 'backup.menu.view', 'Backup', 'View Backup & Restore menu'
    UNION ALL SELECT 'settings.menu.view', 'Settings', 'View Settings menu'
) new_nav_permissions
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.name = new_nav_permissions.permission_code
);

-- Preserve existing access: grant each new nav permission to every role
-- that currently holds the equivalent action permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'receipt.create'
JOIN permissions np ON np.name = 'receipt.entry.menu.view'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'user.manage'
JOIN permissions np ON np.name = 'user.menu.view'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'role.manage'
JOIN permissions np ON np.name = 'role.menu.view'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'settings.manage'
JOIN permissions np ON np.name = 'settings.menu.view'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('backup.view', 'backup.create', 'backup.restore')
JOIN permissions np ON np.name = 'backup.menu.view'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = np.id
);
