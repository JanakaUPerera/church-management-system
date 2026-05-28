ALTER TABLE churches
    ADD COLUMN church_code VARCHAR(20) NULL AFTER id;

UPDATE churches
SET church_code = CONCAT('CH', LPAD(id, 3, '0'))
WHERE church_code IS NULL;

ALTER TABLE churches
    MODIFY church_code VARCHAR(20) NOT NULL;

ALTER TABLE churches
    ADD UNIQUE KEY uk_churches_church_code (church_code);

ALTER TABLE churches
    ADD INDEX idx_churches_region_id (region_id);

ALTER TABLE churches
    DROP INDEX uk_churches_region_name;

ALTER TABLE churches
    RENAME COLUMN name TO church_name;

ALTER TABLE churches
    MODIFY church_name VARCHAR(200) NOT NULL;

ALTER TABLE churches
    ADD COLUMN status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' AFTER region_id;

UPDATE churches
SET status = CASE WHEN active = TRUE THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE churches
    DROP COLUMN active;

ALTER TABLE churches
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP;
