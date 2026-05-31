ALTER TABLE sms_logs
    ADD COLUMN sms_log_uuid CHAR(36) NULL AFTER id;

UPDATE sms_logs
SET sms_log_uuid = UUID()
WHERE sms_log_uuid IS NULL;

ALTER TABLE sms_logs
    MODIFY sms_log_uuid CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_sms_logs_sms_log_uuid (sms_log_uuid);
