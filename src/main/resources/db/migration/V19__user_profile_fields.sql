ALTER TABLE users
    ADD COLUMN mobile_number VARCHAR(20) NULL AFTER email,
    ADD COLUMN profile_picture_path VARCHAR(500) NULL AFTER mobile_number;
