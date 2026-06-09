INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'report.view' permission_code, 'Reports' module_name, 'Open and view reports' permission_description
    UNION ALL SELECT 'report.export', 'Reports', 'Export reports to PDF or Excel'
    UNION ALL SELECT 'report.print', 'Reports', 'Print reports'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('report.view', 'report.export', 'report.print')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions menu_permission ON menu_permission.id = rp.permission_id
JOIN permissions p ON p.name = 'report.view'
WHERE menu_permission.name = 'report.menu.view'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
  );
