ALTER TABLE sms_logs
    ADD COLUMN resend_of_sms_log_id BIGINT NULL AFTER created_at,
    ADD COLUMN resent_by_user_id BIGINT NULL AFTER resend_of_sms_log_id,
    ADD COLUMN resend_reason VARCHAR(255) NULL AFTER resent_by_user_id,
    ADD CONSTRAINT fk_sms_logs_resend_of
        FOREIGN KEY (resend_of_sms_log_id) REFERENCES sms_logs (id),
    ADD CONSTRAINT fk_sms_logs_resent_by
        FOREIGN KEY (resent_by_user_id) REFERENCES users (id);

INSERT INTO permissions (name, description)
SELECT 'sms.resend', 'Resend SMS messages from SMS logs'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'sms.resend'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'sms.resend'
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
