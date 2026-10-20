CREATE TABLE fiscal_years (
    year INT NOT NULL PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    closed_by BIGINT NULL,
    closed_at DATETIME NULL,
    CONSTRAINT fk_fiscal_years_closed_by FOREIGN KEY (closed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code CHAR(4) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(10) NOT NULL,
    parent_id BIGINT NULL,
    is_group BOOLEAN NOT NULL DEFAULT FALSE,
    cash_flow_class VARCHAR(10) NOT NULL,
    system_key VARCHAR(40) NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT uq_accounts_code UNIQUE (code),
    CONSTRAINT uq_accounts_system_key UNIQUE (system_key),
    CONSTRAINT fk_accounts_parent FOREIGN KEY (parent_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journal_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entry_no VARCHAR(20) NULL,
    entry_date DATE NOT NULL,
    fiscal_year INT NOT NULL,
    narration VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    reversal_of_id BIGINT NULL,
    prepared_by BIGINT NOT NULL,
    prepared_at DATETIME NOT NULL,
    posted_by BIGINT NULL,
    posted_at DATETIME NULL,
    updated_at DATETIME NULL,
    CONSTRAINT uq_journal_entries_entry_no UNIQUE (entry_no),
    CONSTRAINT uq_journal_entries_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_journal_entries_year FOREIGN KEY (fiscal_year) REFERENCES fiscal_years (year),
    CONSTRAINT fk_journal_entries_reversal FOREIGN KEY (reversal_of_id) REFERENCES journal_entries (id),
    CONSTRAINT fk_journal_entries_prepared_by FOREIGN KEY (prepared_by) REFERENCES users (id),
    CONSTRAINT fk_journal_entries_posted_by FOREIGN KEY (posted_by) REFERENCES users (id),
    INDEX idx_journal_entries_date (entry_date),
    INDEX idx_journal_entries_status (status),
    INDEX idx_journal_entries_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journal_lines (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    account_id BIGINT NOT NULL,
    debit DECIMAL(15, 2) NOT NULL DEFAULT 0,
    credit DECIMAL(15, 2) NOT NULL DEFAULT 0,
    category_id BIGINT NULL,
    church_id BIGINT NULL,
    memo VARCHAR(255) NULL,
    CONSTRAINT chk_journal_lines_one_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)),
    CONSTRAINT fk_journal_lines_entry FOREIGN KEY (entry_id) REFERENCES journal_entries (id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_lines_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_journal_lines_category FOREIGN KEY (category_id) REFERENCES collection_categories (id),
    CONSTRAINT fk_journal_lines_church FOREIGN KEY (church_id) REFERENCES churches (id),
    INDEX idx_journal_lines_account (account_id),
    INDEX idx_journal_lines_category (category_id),
    INDEX idx_journal_lines_church (church_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journal_entry_sequences (
    fiscal_year INT NOT NULL PRIMARY KEY,
    last_number INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seeded chart of accounts: top groups, then their children, then children of sub-groups.
INSERT INTO accounts (code, name, type, parent_id, is_group, cash_flow_class, system_key)
VALUES ('1000', 'Assets', 'ASSET', NULL, TRUE, 'OPERATING', NULL),
       ('2000', 'Liabilities', 'LIABILITY', NULL, TRUE, 'OPERATING', NULL),
       ('3000', 'Equity', 'EQUITY', NULL, TRUE, 'FINANCING', NULL),
       ('4000', 'Income', 'INCOME', NULL, TRUE, 'OPERATING', NULL),
       ('5000', 'Expenses', 'EXPENSE', NULL, TRUE, 'OPERATING', NULL);

INSERT INTO accounts (code, name, type, parent_id, is_group, cash_flow_class, system_key)
SELECT seed.code, seed.name, seed.type, parent.id, seed.is_group, seed.cash_flow_class, seed.system_key
FROM (
    SELECT '1100' code, 'Cash in Hand' name, 'ASSET' type, '1000' parent_code, FALSE is_group, 'CASH' cash_flow_class, 'CASH_IN_HAND' system_key
    UNION ALL SELECT '1200', 'Bank Accounts', 'ASSET', '1000', TRUE, 'CASH', NULL
    UNION ALL SELECT '1300', 'Receivables', 'ASSET', '1000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '1400', 'Advances & Deposits', 'ASSET', '1000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '1500', 'Fixed Assets', 'ASSET', '1000', TRUE, 'INVESTING', NULL
    UNION ALL SELECT '2100', 'Loans Payable', 'LIABILITY', '2000', FALSE, 'FINANCING', NULL
    UNION ALL SELECT '2200', 'Payables', 'LIABILITY', '2000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '3100', 'Accumulated Fund', 'EQUITY', '3000', FALSE, 'FINANCING', 'ACCUMULATED_FUND'
    UNION ALL SELECT '4100', 'Tithes', 'INCOME', '4000', FALSE, 'OPERATING', 'TITHES_INCOME'
    UNION ALL SELECT '4200', 'Offerings', 'INCOME', '4000', FALSE, 'OPERATING', 'OFFERINGS_INCOME'
    UNION ALL SELECT '4300', 'Other Donations', 'INCOME', '4000', FALSE, 'OPERATING', 'OTHER_DONATIONS_INCOME'
    UNION ALL SELECT '4400', 'Rent Income', 'INCOME', '4000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '4500', 'Interest Income', 'INCOME', '4000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '4600', 'Grants', 'INCOME', '4000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '4900', 'Other Income', 'INCOME', '4000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5100', 'Salaries & Allowances', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5200', 'Pastoral & Ministry Expenses', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5300', 'Utilities', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5400', 'Repairs & Maintenance', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5500', 'Travel & Transport', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5600', 'Printing & Stationery', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5700', 'Charity & Welfare', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5800', 'Events & Conventions', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5850', 'Bank Charges', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5900', 'Depreciation', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5950', 'Member Fees', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
    UNION ALL SELECT '5990', 'Miscellaneous Expenses', 'EXPENSE', '5000', FALSE, 'OPERATING', NULL
) seed
JOIN accounts parent ON parent.code = seed.parent_code;

INSERT INTO accounts (code, name, type, parent_id, is_group, cash_flow_class, system_key)
SELECT seed.code, seed.name, 'ASSET', parent.id, FALSE, 'INVESTING', NULL
FROM (
    SELECT '1510' code, 'Land & Buildings' name
    UNION ALL SELECT '1520', 'Vehicles'
    UNION ALL SELECT '1530', 'Furniture & Equipment'
) seed
JOIN accounts parent ON parent.code = '1500';

INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT new_settings.setting_key, new_settings.setting_value, new_settings.setting_type, 'FINANCE',
       new_settings.description, TRUE, CURRENT_TIMESTAMP
FROM (
    SELECT 'finance.go_live_date' setting_key, '2027-01-01' setting_value, 'DATE' setting_type,
           'First date the books accept entries; cannot change once anything is posted' description
    UNION ALL SELECT 'finance.approval.required', 'false', 'BOOLEAN',
           'Entries must be posted by an approver other than the person who prepared them'
) new_settings
WHERE NOT EXISTS (SELECT 1 FROM system_settings s WHERE s.setting_key = new_settings.setting_key);

INSERT INTO permissions (name, module, description)
SELECT permission_code, 'Finance', permission_description
FROM (
    SELECT 'finance.menu.view' permission_code, 'Open the Finance module and view accounts and entries' permission_description
    UNION ALL SELECT 'finance.entry', 'Prepare journal entries and opening balances'
    UNION ALL SELECT 'finance.approve', 'Post entries when approval is required, and reverse entries'
    UNION ALL SELECT 'finance.accounts.manage', 'Create, edit and deactivate accounts'
    UNION ALL SELECT 'finance.year.close', 'Close and reopen accounting years'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('finance.menu.view', 'finance.entry', 'finance.approve',
                                 'finance.accounts.manage', 'finance.year.close')
WHERE r.name = 'Admin'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
