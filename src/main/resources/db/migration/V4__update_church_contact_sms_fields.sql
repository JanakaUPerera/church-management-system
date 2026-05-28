ALTER TABLE churches
    ADD COLUMN authorized_person_name VARCHAR(150) NULL AFTER status,
    ADD COLUMN authorized_person_position VARCHAR(100) NULL AFTER authorized_person_name,
    ADD COLUMN authorized_person_position_other VARCHAR(100) NULL AFTER authorized_person_position,
    ADD COLUMN sms_mobile_number VARCHAR(20) NULL AFTER authorized_person_position_other;
