ALTER TABLE sms_logs
    MODIFY status VARCHAR(30) NOT NULL,
    ADD COLUMN modem_message_reference VARCHAR(50) NULL AFTER provider,
    ADD COLUMN modem_raw_response TEXT NULL AFTER modem_message_reference,
    ADD COLUMN delivery_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' AFTER status,
    ADD COLUMN delivery_report_raw TEXT NULL AFTER delivery_status,
    ADD COLUMN error_code VARCHAR(50) NULL AFTER error_message,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 AFTER delivery_report_raw,
    ADD COLUMN last_attempt_at DATETIME NULL AFTER attempt_count;

UPDATE sms_logs
SET status = 'SENT'
WHERE status = 'SUCCESS';

UPDATE sms_logs
SET delivery_status = CASE
    WHEN status = 'FAILED' THEN 'FAILED'
    ELSE 'UNKNOWN'
END
WHERE delivery_status = 'UNKNOWN';

CREATE INDEX idx_sms_logs_status ON sms_logs (status);
CREATE INDEX idx_sms_logs_delivery_status ON sms_logs (delivery_status);
