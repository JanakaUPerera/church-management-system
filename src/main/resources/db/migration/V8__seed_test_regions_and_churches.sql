INSERT IGNORE INTO regions (region_code, region_name, status, created_at)
VALUES
    ('TREG01', 'Test Region 01', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG02', 'Test Region 02', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG03', 'Test Region 03', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG04', 'Test Region 04', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG05', 'Test Region 05', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG06', 'Test Region 06', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG07', 'Test Region 07', 'ACTIVE', CURRENT_TIMESTAMP),
    ('TREG08', 'Test Region 08', 'ACTIVE', CURRENT_TIMESTAMP);

INSERT INTO churches (
    church_code,
    region_id,
    status,
    authorized_person_name,
    authorized_person_position,
    authorized_person_position_other,
    sms_mobile_number,
    church_name,
    address,
    contact_number,
    created_at
)
WITH RECURSIVE church_numbers AS (
    SELECT 1 AS church_number
    UNION ALL
    SELECT church_number + 1
    FROM church_numbers
    WHERE church_number < 100
)
SELECT
    CONCAT('TCH', LPAD(church_number, 3, '0')) AS church_code,
    region.id AS region_id,
    'ACTIVE' AS status,
    CONCAT('Test Authorized Person ', LPAD(church_number, 3, '0')) AS authorized_person_name,
    'PASTOR' AS authorized_person_position,
    NULL AS authorized_person_position_other,
    CONCAT('+947700', LPAD(church_number, 4, '0')) AS sms_mobile_number,
    CONCAT('Test Church ', LPAD(church_number, 3, '0')) AS church_name,
    CONCAT('Test Address ', LPAD(church_number, 3, '0')) AS address,
    CONCAT('0112', LPAD(church_number, 6, '0')) AS contact_number,
    CURRENT_TIMESTAMP AS created_at
FROM church_numbers
JOIN regions region
    ON region.region_code = CONCAT('TREG', LPAD(((church_number - 1) MOD 8) + 1, 2, '0'))
WHERE NOT EXISTS (
    SELECT 1
    FROM churches church
    WHERE church.church_code = CONCAT('TCH', LPAD(church_number, 3, '0'))
);
