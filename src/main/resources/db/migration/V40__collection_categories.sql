-- Phase 02 / requirement 01: collection categories.
-- A category is assigned per church x collection type x accounting year and
-- is stored on each receipt line when the receipt is created.

CREATE TABLE collection_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_collection_categories_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO collection_categories (code, name, status, created_at) VALUES
    ('A', 'Category A', 'ACTIVE', CURRENT_TIMESTAMP),
    ('B', 'Category B', 'ACTIVE', CURRENT_TIMESTAMP),
    ('C', 'Category C', 'ACTIVE', CURRENT_TIMESTAMP),
    ('D', 'Category D', 'ACTIVE', CURRENT_TIMESTAMP),
    ('E', 'Category E', 'ACTIVE', CURRENT_TIMESTAMP);

CREATE TABLE church_category_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    church_id BIGINT NOT NULL,
    collection_type ENUM('OFFERTORY','TITHES','OTHER_DONATIONS') NOT NULL,
    category_id BIGINT NOT NULL,
    accounting_year SMALLINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_church_category_assignment (church_id, collection_type, accounting_year),
    KEY idx_church_category_assignments_category (category_id),
    CONSTRAINT fk_church_category_assignments_church
        FOREIGN KEY (church_id) REFERENCES churches (id),
    CONSTRAINT fk_church_category_assignments_category
        FOREIGN KEY (category_id) REFERENCES collection_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2026 assignments, matched by church_code at run time (docs/CATEGORY-ASSIGNMENTS.md).
-- Columns: church_code, tithes, offerings, others. Churches not listed get C.
INSERT INTO church_category_assignments (church_id, collection_type, category_id, accounting_year, created_at)
SELECT c.id, t.collection_type, cat.id, 2026, CURRENT_TIMESTAMP
FROM churches c
CROSS JOIN (
    SELECT 'TITHES' AS collection_type UNION ALL SELECT 'OFFERTORY' UNION ALL SELECT 'OTHER_DONATIONS'
) t
LEFT JOIN (
              SELECT '0001' AS church_code, 'D' AS tithes, 'D' AS offerings, 'D' AS others
    UNION ALL SELECT '0002', 'D', 'D', 'D'
    UNION ALL SELECT '0003', 'A', 'A', 'A'
    UNION ALL SELECT '0004', 'A', 'A', 'A'
    UNION ALL SELECT '0006', 'A', 'A', 'A'
    UNION ALL SELECT '0007', 'A', 'A', 'A'
    UNION ALL SELECT '0008', 'A', 'A', 'A'
    UNION ALL SELECT '0009', 'A', 'A', 'A'
    UNION ALL SELECT '0010', 'B', 'A', 'B'
    UNION ALL SELECT '0011', 'A', 'A', 'A'
    UNION ALL SELECT '0012', 'A', 'A', 'A'
    UNION ALL SELECT '0013', 'A', 'E', 'A'
    UNION ALL SELECT '0014', 'A', 'A', 'A'
    UNION ALL SELECT '0015', 'A', 'A', 'A'
    UNION ALL SELECT '0016', 'A', 'A', 'A'
    UNION ALL SELECT '0017', 'A', 'A', 'A'
    UNION ALL SELECT '0018', 'A', 'A', 'A'
    UNION ALL SELECT '0019', 'A', 'A', 'A'
    UNION ALL SELECT '0020', 'A', 'A', 'A'
    UNION ALL SELECT '0022', 'A', 'A', 'A'
    UNION ALL SELECT '0023', 'A', 'A', 'A'
    UNION ALL SELECT '0024', 'A', 'A', 'A'
    UNION ALL SELECT '0025', 'A', 'A', 'A'
    UNION ALL SELECT '0026', 'A', 'A', 'A'
    UNION ALL SELECT '0027', 'A', 'A', 'A'
    UNION ALL SELECT '0028', 'A', 'A', 'A'
    UNION ALL SELECT '0029', 'A', 'A', 'A'
    UNION ALL SELECT '0030', 'A', 'A', 'A'
    UNION ALL SELECT '0031', 'A', 'A', 'A'
    UNION ALL SELECT '0034', 'A', 'A', 'A'
    UNION ALL SELECT '0035', 'A', 'A', 'A'
    UNION ALL SELECT '0036', 'A', 'A', 'A'
    UNION ALL SELECT '0037', 'A', 'E', 'A'
    UNION ALL SELECT '0038', 'A', 'A', 'A'
    UNION ALL SELECT '0039', 'A', 'A', 'A'
    UNION ALL SELECT '0041', 'A', 'A', 'A'
    UNION ALL SELECT '0042', 'A', 'A', 'A'
    UNION ALL SELECT '0043', 'A', 'A', 'A'
    UNION ALL SELECT '0044', 'A', 'A', 'A'
    UNION ALL SELECT '0045', 'A', 'A', 'A'
    UNION ALL SELECT '0046', 'A', 'A', 'A'
    UNION ALL SELECT '0047', 'A', 'A', 'A'
    UNION ALL SELECT '0049', 'A', 'A', 'A'
    UNION ALL SELECT '0050', 'B', 'A', 'B'
    UNION ALL SELECT '0051', 'B', 'A', 'B'
    UNION ALL SELECT '0053', 'A', 'E', 'A'
    UNION ALL SELECT '0054', 'A', 'A', 'A'
    UNION ALL SELECT '0055', 'A', 'A', 'A'
    UNION ALL SELECT '0056', 'A', 'A', 'A'
    UNION ALL SELECT '0057', 'B', 'A', 'B'
    UNION ALL SELECT '0058', 'A', 'A', 'A'
    UNION ALL SELECT '0064', 'A', 'A', 'A'
    UNION ALL SELECT '0069', 'A', 'A', 'A'
    UNION ALL SELECT '0070', 'A', 'A', 'A'
    UNION ALL SELECT '0071', 'A', 'A', 'A'
    UNION ALL SELECT '0072', 'A', 'A', 'A'
    UNION ALL SELECT '0074', 'A', 'A', 'A'
    UNION ALL SELECT '0075', 'A', 'A', 'A'
    UNION ALL SELECT '0078', 'A', 'A', 'A'
    UNION ALL SELECT '0082', 'A', 'A', 'A'
    UNION ALL SELECT '0084', 'A', 'A', 'A'
    UNION ALL SELECT '0086', 'A', 'A', 'A'
    UNION ALL SELECT '0087', 'A', 'E', 'A'
    UNION ALL SELECT '0088', 'A', 'A', 'A'
    UNION ALL SELECT '0095', 'A', 'A', 'A'
    UNION ALL SELECT '0096', 'A', 'A', 'A'
    UNION ALL SELECT '0097', 'A', 'E', 'A'
    UNION ALL SELECT '0100', 'A', 'A', 'A'
    UNION ALL SELECT '0101', 'A', 'A', 'A'
    UNION ALL SELECT '0102', 'A', 'A', 'A'
    UNION ALL SELECT '0103', 'A', 'A', 'A'
    UNION ALL SELECT '0104', 'A', 'A', 'A'
    UNION ALL SELECT '0105', 'A', 'E', 'A'
    UNION ALL SELECT '0106', 'A', 'A', 'A'
    UNION ALL SELECT '0107', 'A', 'A', 'A'
    UNION ALL SELECT '0108', 'A', 'A', 'A'
    UNION ALL SELECT '0109', 'A', 'A', 'A'
    UNION ALL SELECT '0110', 'A', 'A', 'A'
    UNION ALL SELECT '0111', 'A', 'A', 'A'
) s ON s.church_code = c.church_code
JOIN collection_categories cat ON cat.code = COALESCE(
    CASE t.collection_type
        WHEN 'TITHES' THEN s.tithes
        WHEN 'OFFERTORY' THEN s.offerings
        ELSE s.others
    END, 'C');

-- Accounting year of each receipt: its entry year; a correction takes its original's year.
ALTER TABLE receipts
    ADD COLUMN accounting_year SMALLINT NULL AFTER receipt_datetime;

UPDATE receipts SET accounting_year = YEAR(receipt_datetime);

UPDATE receipts corrected
JOIN receipts original ON original.id = corrected.corrected_from_receipt_id
SET corrected.accounting_year = YEAR(original.receipt_datetime);

ALTER TABLE receipts
    MODIFY accounting_year SMALLINT NOT NULL,
    ADD INDEX idx_receipts_church_accounting_year (church_id, accounting_year);

-- Category of each existing receipt line: the church's 2026 assignment
-- (receipts entered before 2026, if any, also use 2026).
ALTER TABLE receipt_items
    ADD COLUMN category_id BIGINT NULL AFTER collection_type;

UPDATE receipt_items ri
JOIN receipts r ON r.id = ri.receipt_id
JOIN church_category_assignments a
    ON a.church_id = r.church_id
   AND a.collection_type = ri.collection_type
   AND a.accounting_year = 2026
SET ri.category_id = a.category_id;

ALTER TABLE receipt_items
    MODIFY category_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_receipt_items_category
        FOREIGN KEY (category_id) REFERENCES collection_categories (id);

-- Permissions.
INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'category.menu.view' permission_code, 'Categories' module_name, 'View Categories menu' permission_description
    UNION ALL SELECT 'category.create', 'Categories', 'Create collection categories'
    UNION ALL SELECT 'category.update', 'Categories', 'Edit collection categories'
    UNION ALL SELECT 'category.delete', 'Categories', 'Activate, deactivate or delete collection categories'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('category.menu.view', 'category.create', 'category.update', 'category.delete')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
