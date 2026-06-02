SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'roles'
          AND column_name = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE roles ADD COLUMN status ENUM(''ACTIVE'',''INACTIVE'') NOT NULL DEFAULT ''ACTIVE'' AFTER description'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'permissions'
          AND column_name = 'module'
    ),
    'SELECT 1',
    'ALTER TABLE permissions ADD COLUMN module VARCHAR(100) NOT NULL DEFAULT ''General'' AFTER name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE roles
SET status = 'ACTIVE'
WHERE status IS NULL;

INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'user.manage' permission_code, 'User Management' module_name, 'Manage users' permission_description
    UNION ALL SELECT 'role.manage', 'Roles & Permissions', 'Manage roles and assign permissions'
    UNION ALL SELECT 'region.view', 'Regions', 'View regions'
    UNION ALL SELECT 'region.create', 'Regions', 'Create regions'
    UNION ALL SELECT 'region.update', 'Regions', 'Update regions'
    UNION ALL SELECT 'region.delete', 'Regions', 'Deactivate regions'
    UNION ALL SELECT 'church.view', 'Churches', 'View churches'
    UNION ALL SELECT 'church.create', 'Churches', 'Create churches'
    UNION ALL SELECT 'church.update', 'Churches', 'Update churches'
    UNION ALL SELECT 'church.delete', 'Churches', 'Deactivate churches'
    UNION ALL SELECT 'receipt.create', 'Receipts', 'Create receipts'
    UNION ALL SELECT 'receipt.view', 'Receipts', 'View receipts'
    UNION ALL SELECT 'receipt.cancel', 'Receipts', 'Cancel receipts'
    UNION ALL SELECT 'receipt.print', 'Receipts', 'Print receipts'
    UNION ALL SELECT 'report.view', 'Reports', 'View reports'
    UNION ALL SELECT 'report.export', 'Reports', 'Export reports'
    UNION ALL SELECT 'report.print', 'Reports', 'Print reports'
    UNION ALL SELECT 'backup.view', 'Backup', 'View backup and restore history'
    UNION ALL SELECT 'backup.create', 'Backup', 'Create database backups'
    UNION ALL SELECT 'backup.restore', 'Backup', 'Restore database backups'
    UNION ALL SELECT 'backup.settings.manage', 'Backup', 'Manage backup settings'
    UNION ALL SELECT 'sms.logs.view', 'SMS', 'View SMS logs'
    UNION ALL SELECT 'sms.resend', 'SMS', 'Resend SMS messages'
    UNION ALL SELECT 'sms.settings.manage', 'SMS', 'Manage SMS settings'
    UNION ALL SELECT 'activity.view', 'Activity', 'View activity logs'
    UNION ALL SELECT 'settings.manage', 'Settings', 'Manage application settings'
) required_permissions
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.name = required_permissions.permission_code
);

UPDATE permissions p
JOIN (
    SELECT 'user.manage' permission_code, 'User Management' module_name, 'Manage users' permission_description
    UNION ALL SELECT 'role.manage', 'Roles & Permissions', 'Manage roles and assign permissions'
    UNION ALL SELECT 'region.view', 'Regions', 'View regions'
    UNION ALL SELECT 'region.create', 'Regions', 'Create regions'
    UNION ALL SELECT 'region.update', 'Regions', 'Update regions'
    UNION ALL SELECT 'region.delete', 'Regions', 'Deactivate regions'
    UNION ALL SELECT 'church.view', 'Churches', 'View churches'
    UNION ALL SELECT 'church.create', 'Churches', 'Create churches'
    UNION ALL SELECT 'church.update', 'Churches', 'Update churches'
    UNION ALL SELECT 'church.delete', 'Churches', 'Deactivate churches'
    UNION ALL SELECT 'receipt.create', 'Receipts', 'Create receipts'
    UNION ALL SELECT 'receipt.view', 'Receipts', 'View receipts'
    UNION ALL SELECT 'receipt.cancel', 'Receipts', 'Cancel receipts'
    UNION ALL SELECT 'receipt.print', 'Receipts', 'Print receipts'
    UNION ALL SELECT 'report.view', 'Reports', 'View reports'
    UNION ALL SELECT 'report.export', 'Reports', 'Export reports'
    UNION ALL SELECT 'report.print', 'Reports', 'Print reports'
    UNION ALL SELECT 'backup.view', 'Backup', 'View backup and restore history'
    UNION ALL SELECT 'backup.create', 'Backup', 'Create database backups'
    UNION ALL SELECT 'backup.restore', 'Backup', 'Restore database backups'
    UNION ALL SELECT 'backup.settings.manage', 'Backup', 'Manage backup settings'
    UNION ALL SELECT 'sms.logs.view', 'SMS', 'View SMS logs'
    UNION ALL SELECT 'sms.resend', 'SMS', 'Resend SMS messages'
    UNION ALL SELECT 'sms.settings.manage', 'SMS', 'Manage SMS settings'
    UNION ALL SELECT 'activity.view', 'Activity', 'View activity logs'
    UNION ALL SELECT 'settings.manage', 'Settings', 'Manage application settings'
) required_permissions ON required_permissions.permission_code = p.name
SET p.module = required_permissions.module_name,
    p.description = required_permissions.permission_description;

UPDATE permissions
SET module = CASE
        WHEN name IN ('USER_MANAGE') THEN 'User Management'
        WHEN name IN ('ROLE_MANAGE') THEN 'Roles & Permissions'
        WHEN name IN ('REGION_MANAGE') THEN 'Regions'
        WHEN name IN ('CHURCH_MANAGE') THEN 'Churches'
        WHEN name IN ('RECEIPT_CREATE', 'RECEIPT_CANCEL') THEN 'Receipts'
        WHEN name IN ('REPORT_VIEW') THEN 'Reports'
        WHEN name IN ('BACKUP_MANAGE') THEN 'Backup'
        WHEN name IN ('SETTINGS_MANAGE') THEN 'Settings'
        ELSE module
    END
WHERE module = 'General';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'user.manage',
    'role.manage',
    'region.view',
    'region.create',
    'region.update',
    'region.delete',
    'church.view',
    'church.create',
    'church.update',
    'church.delete',
    'receipt.create',
    'receipt.view',
    'receipt.cancel',
    'receipt.print',
    'report.view',
    'report.export',
    'report.print',
    'backup.view',
    'backup.create',
    'backup.restore',
    'backup.settings.manage',
    'sms.logs.view',
    'sms.resend',
    'sms.settings.manage',
    'activity.view',
    'settings.manage'
)
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
