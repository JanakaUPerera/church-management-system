ALTER TABLE churches
    ADD COLUMN receipt_language ENUM('ENGLISH','SINHALA','TAMIL') NOT NULL DEFAULT 'ENGLISH'
        AFTER sms_mobile_number;

UPDATE churches
SET receipt_language = CASE MOD(id, 3)
    WHEN 0 THEN 'ENGLISH'
    WHEN 1 THEN 'SINHALA'
    ELSE 'TAMIL'
END
WHERE church_code LIKE 'TCH%';
