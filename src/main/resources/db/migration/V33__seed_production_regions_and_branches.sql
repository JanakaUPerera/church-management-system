-- ============================================================
-- V33: Seed production regions (R001–R008) and branch churches
-- ============================================================

INSERT IGNORE INTO regions (region_code, region_name, status, created_at)
VALUES
    ('R001', 'Region 1', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R002', 'Region 2', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R003', 'Region 3', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R004', 'Region 4', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R005', 'Region 5', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R006', 'Region 6', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R007', 'Region 7', 'ACTIVE', CURRENT_TIMESTAMP),
    ('R008', 'Region 8', 'ACTIVE', CURRENT_TIMESTAMP);

INSERT IGNORE INTO churches (church_code, region_id, status, church_name, created_at)
SELECT b.church_code, r.id, 'ACTIVE', b.church_name, CURRENT_TIMESTAMP
FROM (
    -- Region 1
    SELECT '0001' AS church_code, 'R001' AS region_code, 'Katunayake'             AS church_name UNION ALL
    SELECT '0002', 'R001', 'Seeduwa'                UNION ALL
    SELECT '0007', 'R001', 'Hanwella'               UNION ALL
    SELECT '0013', 'R001', 'Kandana'                UNION ALL
    SELECT '0049', 'R001', 'Handala'                UNION ALL
    SELECT '0110', 'R001', 'Jaffna'                 UNION ALL
    -- Region 2
    SELECT '0017', 'R002', 'Waliweriya'             UNION ALL
    SELECT '0035', 'R002', 'Pitipana'               UNION ALL
    SELECT '0036', 'R002', 'Ja-Ela'                 UNION ALL
    SELECT '0054', 'R002', 'Bopitiya'               UNION ALL
    -- Region 3
    SELECT '0004', 'R003', 'Wattala'                UNION ALL
    SELECT '0012', 'R003', 'Kelaniya'               UNION ALL
    SELECT '0018', 'R003', 'Galle'                  UNION ALL
    SELECT '0019', 'R003', 'Hikkaduwa'              UNION ALL
    SELECT '0038', 'R003', 'Nonagama'               UNION ALL
    SELECT '0039', 'R003', 'Lunugamwehera'          UNION ALL
    SELECT '0041', 'R003', 'Suriyawewa'             UNION ALL
    SELECT '0042', 'R003', 'Aplitiya'               UNION ALL
    SELECT '0046', 'R003', 'Maggona'                UNION ALL
    SELECT '0047', 'R003', 'Bentota'                UNION ALL
    SELECT '0071', 'R003', 'Kuttigala'              UNION ALL
    SELECT '0096', 'R003', 'Ranna'                  UNION ALL
    SELECT '0107', 'R003', 'Matara'                 UNION ALL
    -- Region 4
    SELECT '0020', 'R004', 'Kurunagala'             UNION ALL
    SELECT '0022', 'R004', 'Pandiwela'              UNION ALL
    SELECT '0024', 'R004', 'Nikawaratiya'           UNION ALL
    SELECT '0043', 'R004', 'Malsiripura'            UNION ALL
    SELECT '0078', 'R004', 'Wadurassa'              UNION ALL
    SELECT '0103', 'R004', 'Weerapokuna'            UNION ALL
    -- Region 5
    SELECT '0008', 'R005', 'Mahawewa'               UNION ALL
    SELECT '0023', 'R005', 'Kuliyapitiya'           UNION ALL
    SELECT '0025', 'R005', 'Dummalasuriya'          UNION ALL
    SELECT '0026', 'R005', 'Wilptaha'               UNION ALL
    SELECT '0027', 'R005', 'Chilaw'                 UNION ALL
    SELECT '0034', 'R005', 'Lihiriyagama'           UNION ALL
    SELECT '0037', 'R005', 'Wennappuwa'             UNION ALL
    SELECT '0050', 'R005', 'Kachchirawa'            UNION ALL
    SELECT '0064', 'R005', 'Kanjukuliya'            UNION ALL
    SELECT '0072', 'R005', 'Walipanna'              UNION ALL
    SELECT '0082', 'R005', 'Pallama'                UNION ALL
    SELECT '0105', 'R005', 'Arachchikattuwa'        UNION ALL
    -- Region 6
    SELECT '0028', 'R006', 'Kalpitiya'              UNION ALL
    SELECT '0029', 'R006', 'Puttalam'               UNION ALL
    SELECT '0030', 'R006', 'Anamaduwa'              UNION ALL
    SELECT '0031', 'R006', 'Norochchole'            UNION ALL
    SELECT '0045', 'R006', 'Kurinchampitiya'        UNION ALL
    SELECT '0069', 'R006', 'Rajanganaya'            UNION ALL
    SELECT '0074', 'R006', 'Kakirawa'               UNION ALL
    SELECT '0075', 'R006', 'Sinnapaduwa'            UNION ALL
    SELECT '0086', 'R006', 'Jayanthipura'           UNION ALL
    SELECT '0088', 'R006', 'Kandakuliya'            UNION ALL
    SELECT '0095', 'R006', 'Palaviya'               UNION ALL
    SELECT '0097', 'R006', 'Sewanapitiya'           UNION ALL
    SELECT '0100', 'R006', 'Sethapola'              UNION ALL
    SELECT '0102', 'R006', 'Padaviya'               UNION ALL
    SELECT '0108', 'R006', 'Anuradhapura'           UNION ALL
    -- Region 7
    SELECT '0009', 'R007', 'Nuwaraeliya'            UNION ALL
    SELECT '0010', 'R007', 'Kandy'                  UNION ALL
    SELECT '0044', 'R007', 'Mahiyanganaya/Ulhitiya' UNION ALL
    SELECT '0055', 'R007', 'Nawalapitiya'           UNION ALL
    SELECT '0056', 'R007', 'Gampola'                UNION ALL
    SELECT '0070', 'R007', 'Kandapola'              UNION ALL
    SELECT '0084', 'R007', 'Thalawakale'            UNION ALL
    SELECT '0101', 'R007', 'Kegalle'                UNION ALL
    SELECT '0106', 'R007', 'Dolosbage'              UNION ALL
    SELECT '0109', 'R007', 'Hadeniya'               UNION ALL
    -- Region 8
    SELECT '0003', 'R008', 'Ragama'                 UNION ALL
    SELECT '0006', 'R008', 'Avissawella'            UNION ALL
    SELECT '0011', 'R008', 'Ederamulla'             UNION ALL
    SELECT '0014', 'R008', 'Nugegoda'               UNION ALL
    SELECT '0015', 'R008', 'Moratuwa'               UNION ALL
    SELECT '0016', 'R008', 'Kamaragoda'             UNION ALL
    SELECT '0051', 'R008', 'Ganemulla'              UNION ALL
    SELECT '0053', 'R008', 'Dehiwala'               UNION ALL
    SELECT '0057', 'R008', 'Meerigama'              UNION ALL
    SELECT '0058', 'R008', 'Kuruwita'               UNION ALL
    SELECT '0087', 'R008', 'Katana'                 UNION ALL
    SELECT '0104', 'R008', 'Megoda'
) b
JOIN regions r ON r.region_code = b.region_code;
