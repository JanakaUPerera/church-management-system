ALTER TABLE sms_logs
    ADD COLUMN queued_by_user_id BIGINT NULL AFTER resend_reason,
    ADD CONSTRAINT fk_sms_logs_queued_by
        FOREIGN KEY (queued_by_user_id) REFERENCES users (id);
