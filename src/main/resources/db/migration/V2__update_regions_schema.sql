ALTER TABLE regions
    ADD COLUMN region_code VARCHAR(20) NULL AFTER id;

UPDATE regions
SET region_code = CONCAT('REG', LPAD(id, 3, '0'))
WHERE region_code IS NULL;

ALTER TABLE regions
    MODIFY region_code VARCHAR(20) NOT NULL;

ALTER TABLE regions
    ADD UNIQUE KEY uk_regions_region_code (region_code);

ALTER TABLE regions
    RENAME COLUMN name TO region_name;

ALTER TABLE regions
    ADD COLUMN status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' AFTER region_name;

UPDATE regions
SET status = CASE WHEN active = TRUE THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE regions
    DROP COLUMN active;

ALTER TABLE regions
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP;
