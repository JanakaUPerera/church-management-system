# Finance F1 — Ledger Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working double-entry ledger: fiscal years, an editable chart of accounts with account creation, a journal engine with optional approval and reversals, opening balances, and the General Journal, Chart of Accounts, Opening Balances and Account Ledger screens in a new Finance module.

**Architecture:** Records for the domain (`Account`, `FiscalYear`, `JournalEntry`, `JournalLine`), manual-JDBC repositories, and services with constructor injection. A pure `JournalValidator` holds the line rules; `JournalService` is the only way entries are saved, posted or reversed, and `JournalRepository.post` / `insertPosted` assign gap-free numbers inside one transaction (`FOR UPDATE` on a per-year sequence). Screens are FXML + code-built dialogs, registered as a Finance module in the navigation registry.

**Tech Stack:** Java 21, JavaFX 21, MySQL 8 (manual JDBC, Flyway), JUnit 5 with hand-written fakes, Maven.

**Spec:** [docs/superpowers/specs/2026-10-20-finance-ledger-foundation-design.md](../specs/2026-10-20-finance-ledger-foundation-design.md)

## Global Constraints

- Branch `feat/finance-ledger-foundation` (from `develop`). Never commit `docs/PHASE-02-DEVELOPMENT-PLAN.md` or `src/main/resources/application.properties` (user's local edits). Keep the branch after merging.
- No new Maven dependencies; tests use fakes (subclass with `null` DataSource) or `com.churchmanagement.testsupport.RecordingDataSource`, never Mockito.
- Migration `V43__finance_ledger_foundation.sql` (latest existing V42). Table options `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`; all ids `BIGINT`.
- Money: `java.math.BigDecimal`, `DECIMAL(15,2)` in SQL, at most 2 decimals, max `9999999999999.99`, LKR only. Display `#,##0.00`.
- Entry numbers `JE-<year>-<5 digits>` assigned at posting, per fiscal year, no gaps.
- Settings keys: `finance.go_live_date` (default `2027-01-01`), `finance.approval.required` (default `false`).
- Permissions (module `Finance`): `finance.menu.view`, `finance.entry`, `finance.approve`, `finance.accounts.manage`, `finance.year.close`.
- Navigation: module id `finance`, title "Finance", icon `fas-balance-scale`, shortcut 5, between Messaging and Administration; item ids `finance.journal`, `finance.accounts`, `finance.opening-balances`, `finance.ledger`.
- Category (fund) required on lines whose account type is INCOME, EXPENSE or EQUITY.
- All user-facing errors are thrown as `FinanceException` (or `JournalValidationException` from the validator) with the message shown as-is.
- Full test suite needs the dev database reachable (host in `src/main/resources/application.properties`); run single classes with `mvn -q test -Dtest=<Class>`.
- Commits: one per task, conventional-commits style with a detailed body, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

## File Structure

**Create** (`src/main/java/com/churchmanagement/...` unless noted)
- `resources/db/migration/V43__finance_ledger_foundation.sql` — tables, seed chart, settings, permissions.
- `enums/AccountType.java`, `enums/CashFlowClass.java`, `enums/JournalStatus.java`, `enums/JournalSourceType.java`.
- `entity/Account.java`, `entity/FiscalYear.java`, `entity/JournalEntry.java`, `entity/JournalLine.java` (records).
- `dto/finance/` — `AccountRequest`, `AccountNode`, `JournalEntryRequest`, `JournalLineRequest`, `JournalSearchCriteria`, `JournalEntrySummary`, `LedgerLine`, `AccountLedger`, `OpeningBalanceForm`, `PostedEntry` (records).
- `exception/JournalStateException.java`.
- `repository/FiscalYearRepository.java`, `repository/AccountRepository.java`, `repository/JournalRepository.java`.
- `validation/JournalValidator.java`, `validation/JournalValidationException.java`.
- `service/FinanceException.java`, `service/FinanceSettings.java`, `service/FiscalYearService.java`, `service/AccountService.java`, `service/JournalService.java`, `service/OpeningBalanceService.java`, `service/FinanceSettingsService.java`.
- `util/MoneyFormat.java`.
- `controller/FinanceAccountsController.java`, `controller/FinanceJournalController.java`, `controller/JournalEntryDialog.java`, `controller/FinanceOpeningBalancesController.java`, `controller/FinanceLedgerController.java`.
- `resources/.../view/finance-accounts-view.fxml`, `finance-journal-view.fxml`, `finance-opening-balances-view.fxml`, `finance-ledger-view.fxml`.
- Tests: `enums/AccountTypeTest`, `repository/JournalRepositoryTest`, `repository/AccountRepositoryTest`, `validation/JournalValidatorTest`, `service/FiscalYearServiceTest`, `service/AccountServiceTest`, `service/JournalServiceTest`, `service/OpeningBalanceServiceTest`, `service/FinanceSettingsServiceTest`, `util/MoneyFormatTest`, `testsupport/FinanceFakes.java`.

**Modify**
- `service/ActivityLogService.java` (finance codes + `logFinance`).
- `service/SystemSettingService.java` (+ two finance setting validations), test `SystemSettingServiceTest`.
- `navigation/NavigationRegistry.java`, test `NavigationRegistryTest`.
- `controller/RolePermissionController.java` (`MODULE_DISPLAY_ORDER` + "Finance").
- `controller/SettingsController.java`, `view/settings-view.fxml` (Finance tab).
- `docs/user-manual.md`, `docs/user-manual-si.md`.

---

### Task 1: Schema, enums and domain records

**Files:**
- Create: `src/main/resources/db/migration/V43__finance_ledger_foundation.sql`
- Create: `enums/AccountType.java`, `enums/CashFlowClass.java`, `enums/JournalStatus.java`, `enums/JournalSourceType.java`
- Create: `entity/Account.java`, `entity/FiscalYear.java`, `entity/JournalEntry.java`, `entity/JournalLine.java`
- Test: `src/test/java/com/churchmanagement/enums/AccountTypeTest.java`

**Interfaces:**
- Produces:
  - `enum AccountType { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }` with `char codeDigit()`, `boolean isDebitNormal()`, `boolean requiresCategory()`, `static AccountType forCode(String code)` (throws `IllegalArgumentException` for a non-4-digit or 0/6–9 first digit).
  - `enum CashFlowClass { CASH, OPERATING, INVESTING, FINANCING }`, `enum JournalStatus { DRAFT, POSTED }`, `enum JournalSourceType { OPENING, JOURNAL, REVERSAL, RECEIPT, RECEIPT_CANCEL, RECEIPT_VOUCHER, PAYMENT_VOUCHER, CONTRA, CLOSING }`.
  - `record Account(Long id, String code, String name, AccountType type, Long parentId, boolean group, CashFlowClass cashFlowClass, String systemKey, String description, Account.Status status)` with `enum Status { ACTIVE, INACTIVE }`, `isActive()`, `isTopGroup()`, `isSystem()`, `displayText()` ("1100 Cash in Hand"), `withId(long)`, `withStatus(Status)`.
  - `record FiscalYear(int year, LocalDate startDate, LocalDate endDate, FiscalYear.Status status)` with `enum Status { OPEN, CLOSED }`, `static FiscalYear open(int year)`, `isClosed()`.
  - `record JournalLine(Long id, int lineNo, long accountId, BigDecimal debit, BigDecimal credit, Long categoryId, Long churchId, String memo)`.
  - `record JournalEntry(Long id, String entryNo, LocalDate entryDate, int fiscalYear, String narration, JournalSourceType sourceType, Long sourceId, JournalStatus status, Long reversalOfId, Long preparedBy, LocalDateTime preparedAt, Long postedBy, LocalDateTime postedAt, List<JournalLine> lines)` with `isPosted()`, `totalDebit()`, `totalCredit()`.

- [ ] **Step 1: Write the migration**

```sql
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
```

- [ ] **Step 2: Write the failing test**

```java
package com.churchmanagement.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTypeTest {
    @Test
    void typeFollowsFirstCodeDigit() {
        assertEquals(AccountType.ASSET, AccountType.forCode("1201"));
        assertEquals(AccountType.LIABILITY, AccountType.forCode("2100"));
        assertEquals(AccountType.EQUITY, AccountType.forCode("3100"));
        assertEquals(AccountType.INCOME, AccountType.forCode("4900"));
        assertEquals(AccountType.EXPENSE, AccountType.forCode("5990"));
    }

    @Test
    void rejectsCodesOutsideTheChart() {
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("6000"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("120"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("12A0"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode(null));
    }

    @Test
    void normalBalanceAndCategoryRules() {
        assertTrue(AccountType.ASSET.isDebitNormal());
        assertTrue(AccountType.EXPENSE.isDebitNormal());
        assertFalse(AccountType.INCOME.isDebitNormal());
        assertTrue(AccountType.INCOME.requiresCategory());
        assertTrue(AccountType.EXPENSE.requiresCategory());
        assertTrue(AccountType.EQUITY.requiresCategory());
        assertFalse(AccountType.ASSET.requiresCategory());
        assertFalse(AccountType.LIABILITY.requiresCategory());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=AccountTypeTest`
Expected: compilation FAILURE — `cannot find symbol: class AccountType`.

- [ ] **Step 4: Implement enums and records**

`enums/AccountType.java`:

```java
package com.churchmanagement.enums;

/** Account type; the first digit of an account code decides it (1 Asset … 5 Expense). */
public enum AccountType {
    ASSET('1'),
    LIABILITY('2'),
    EQUITY('3'),
    INCOME('4'),
    EXPENSE('5');

    private final char codeDigit;

    AccountType(char codeDigit) {
        this.codeDigit = codeDigit;
    }

    public char codeDigit() {
        return codeDigit;
    }

    /** Assets and expenses grow with debits; liabilities, equity and income with credits. */
    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }

    /** Lines on these accounts must carry a collection category (fund). */
    public boolean requiresCategory() {
        return this == INCOME || this == EXPENSE || this == EQUITY;
    }

    public static AccountType forCode(String code) {
        if (code == null || !code.matches("\\d{4}")) {
            throw new IllegalArgumentException("Account code must be 4 digits.");
        }
        for (AccountType type : values()) {
            if (type.codeDigit == code.charAt(0)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Account codes start with 1 to 5.");
    }
}
```

`enums/CashFlowClass.java`:

```java
package com.churchmanagement.enums;

/** How an account's movements are classified in the cash flow statement (F5). */
public enum CashFlowClass {
    CASH,
    OPERATING,
    INVESTING,
    FINANCING
}
```

`enums/JournalStatus.java`:

```java
package com.churchmanagement.enums;

public enum JournalStatus {
    DRAFT,
    POSTED
}
```

`enums/JournalSourceType.java`:

```java
package com.churchmanagement.enums;

/** What created a journal entry. F1 uses OPENING, JOURNAL and REVERSAL; the rest are for F2, F3 and F6. */
public enum JournalSourceType {
    OPENING,
    JOURNAL,
    REVERSAL,
    RECEIPT,
    RECEIPT_CANCEL,
    RECEIPT_VOUCHER,
    PAYMENT_VOUCHER,
    CONTRA,
    CLOSING
}
```

`entity/Account.java`:

```java
package com.churchmanagement.entity;

import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;

/** A chart-of-accounts entry. Group accounts organise the chart; only posting accounts take entries. */
public record Account(Long id, String code, String name, AccountType type, Long parentId, boolean group,
                      CashFlowClass cashFlowClass, String systemKey, String description, Status status) {
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isTopGroup() {
        return parentId == null;
    }

    /** Accounts the system looks up by key (Cash in Hand, Accumulated Fund, income heads). */
    public boolean isSystem() {
        return systemKey != null;
    }

    public String displayText() {
        return code + " " + name;
    }

    public Account withId(long newId) {
        return new Account(newId, code, name, type, parentId, group, cashFlowClass, systemKey, description, status);
    }

    public Account withStatus(Status newStatus) {
        return new Account(id, code, name, type, parentId, group, cashFlowClass, systemKey, description, newStatus);
    }
}
```

`entity/FiscalYear.java`:

```java
package com.churchmanagement.entity;

import java.time.LocalDate;

/** A calendar accounting year. */
public record FiscalYear(int year, LocalDate startDate, LocalDate endDate, Status status) {
    public enum Status {
        OPEN,
        CLOSED
    }

    public static FiscalYear open(int year) {
        return new FiscalYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), Status.OPEN);
    }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }
}
```

`entity/JournalLine.java`:

```java
package com.churchmanagement.entity;

import java.math.BigDecimal;

/** One debit or credit line. Exactly one of debit / credit is greater than zero. */
public record JournalLine(Long id, int lineNo, long accountId, BigDecimal debit, BigDecimal credit,
                          Long categoryId, Long churchId, String memo) {
}
```

`entity/JournalEntry.java`:

```java
package com.churchmanagement.entity;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A journal entry with its lines. {@code entryNo} is null until the entry is posted. */
public record JournalEntry(Long id, String entryNo, LocalDate entryDate, int fiscalYear, String narration,
                           JournalSourceType sourceType, Long sourceId, JournalStatus status, Long reversalOfId,
                           Long preparedBy, LocalDateTime preparedAt, Long postedBy, LocalDateTime postedAt,
                           List<JournalLine> lines) {
    public JournalEntry {
        lines = List.copyOf(lines);
    }

    public boolean isPosted() {
        return status == JournalStatus.POSTED;
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(JournalLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(JournalLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=AccountTypeTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V43__finance_ledger_foundation.sql src/main/java/com/churchmanagement/enums/AccountType.java src/main/java/com/churchmanagement/enums/CashFlowClass.java src/main/java/com/churchmanagement/enums/JournalStatus.java src/main/java/com/churchmanagement/enums/JournalSourceType.java src/main/java/com/churchmanagement/entity/Account.java src/main/java/com/churchmanagement/entity/FiscalYear.java src/main/java/com/churchmanagement/entity/JournalEntry.java src/main/java/com/churchmanagement/entity/JournalLine.java src/test/java/com/churchmanagement/enums/AccountTypeTest.java
git commit -m "feat(finance): ledger schema, seeded chart of accounts and domain types" -m "V43 creates fiscal_years, accounts, journal_entries, journal_lines (a CHECK keeps each line one-sided) and the per-year entry number sequence; seeds the standard 4-digit church chart (Assets, Liabilities, Equity, Income, Expenses with Cash in Hand, Bank Accounts group, Accumulated Fund, Tithes, Offerings, Other Donations and the usual income and expense heads); adds the finance go-live date and approval settings; and grants the five finance permissions to Admin." -m "Adds the AccountType, CashFlowClass, JournalStatus and JournalSourceType enums and the Account, FiscalYear, JournalEntry and JournalLine records." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Repositories

**Files:**
- Create: `dto/finance/JournalSearchCriteria.java`, `dto/finance/JournalEntrySummary.java`, `dto/finance/LedgerLine.java`, `dto/finance/PostedEntry.java`
- Create: `exception/JournalStateException.java`
- Create: `repository/FiscalYearRepository.java`, `repository/AccountRepository.java`, `repository/JournalRepository.java`
- Test: `src/test/java/com/churchmanagement/repository/JournalRepositoryTest.java`, `src/test/java/com/churchmanagement/repository/AccountRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 records and enums.
- Produces:
  - `record JournalSearchCriteria(LocalDate dateFrom, LocalDate dateTo, JournalStatus status, JournalSourceType sourceType, String text)`.
  - `record JournalEntrySummary(long id, String entryNo, LocalDate entryDate, String narration, JournalSourceType sourceType, JournalStatus status, BigDecimal total, String preparedByName, String postedByName)`.
  - `record LedgerLine(long entryId, String entryNo, LocalDate entryDate, String narration, String memo, String categoryCode, String churchCode, BigDecimal debit, BigDecimal credit)`.
  - `record PostedEntry(long id, String entryNo)`.
  - `class JournalStateException extends RuntimeException` (message shown to users).
  - `FiscalYearRepository`: `Optional<FiscalYear> findByYear(int)`, `void insertIfMissing(FiscalYear)`.
  - `AccountRepository`: `List<Account> findAll()`, `Optional<Account> findById(long)`, `Optional<Account> findByCode(String)`, `Optional<Account> findBySystemKey(String)`, `Account insert(Account, LocalDateTime)`, `void update(Account, LocalDateTime)`, `void updateStatus(long, Account.Status, LocalDateTime)`, `void delete(long)`, `boolean isUsed(long)`, `boolean hasChildren(long)`, `boolean hasActiveChildren(long)`, `Map<Long, BigDecimal> postedNetDebits()`.
  - `JournalRepository`: `Optional<JournalEntry> findById(long)`, `List<JournalEntrySummary> search(JournalSearchCriteria)`, `long insertDraft(JournalEntry)`, `void updateDraft(JournalEntry)`, `void deleteDraft(long)`, `String post(long id, long postedBy, LocalDateTime postedAt)`, `PostedEntry insertPosted(JournalEntry, long postedBy, LocalDateTime postedAt)`, `boolean isReversed(long)`, `boolean hasPostedEntries()`, `Optional<JournalEntry> findLiveOpeningEntry()`, `List<LedgerLine> ledgerLines(long accountId, LocalDate from, LocalDate to)`, `BigDecimal netDebitBefore(long accountId, LocalDate date)`; static `String formatEntryNo(int year, int number)`.

- [ ] **Step 1: Write the failing tests**

`JournalRepositoryTest.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalRepositoryTest {
    @Test
    void entryNumbersArePerYearAndPadded() {
        assertEquals("JE-2027-00001", JournalRepository.formatEntryNo(2027, 1));
        assertEquals("JE-2028-12345", JournalRepository.formatEntryNo(2028, 12345));
    }

    @Test
    void postLocksTheEntryAndRollsBackWhenItIsMissing() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JournalRepository repository = new JournalRepository(dataSource);

        JournalStateException exception = assertThrows(JournalStateException.class,
                () -> repository.post(9L, 1L, LocalDateTime.of(2027, 1, 5, 10, 0)));

        assertEquals("Only draft entries can be posted.", exception.getMessage());
        assertTrue(dataSource.sqls().getFirst().contains("FOR UPDATE"));
        assertTrue(dataSource.rolledBack());
    }

    @Test
    void searchAppliesEveryFilter() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new JournalRepository(dataSource).search(new JournalSearchCriteria(LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31), JournalStatus.POSTED, JournalSourceType.JOURNAL, "rent"));

        String sql = dataSource.lastSql();
        assertTrue(sql.contains("je.entry_date >= ?"));
        assertTrue(sql.contains("je.entry_date <= ?"));
        assertTrue(sql.contains("je.status = ?"));
        assertTrue(sql.contains("je.source_type = ?"));
        assertTrue(sql.contains("je.narration LIKE ?"));
        assertEquals("POSTED", dataSource.lastValues().get(2));
        assertEquals("JOURNAL", dataSource.lastValues().get(3));
        assertEquals("%rent%", dataSource.lastValues().get(4));
    }

    @Test
    void ledgerReadsPostedLinesOnly() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new JournalRepository(dataSource).ledgerLines(4L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        assertTrue(dataSource.lastSql().contains("je.status = 'POSTED'"));
        assertEquals(4L, dataSource.lastValues().getFirst());
    }

    @Test
    void openingBalanceBeforeDateIsZeroWithoutRows() {
        RecordingDataSource dataSource = new RecordingDataSource();

        BigDecimal net = new JournalRepository(dataSource).netDebitBefore(4L, LocalDate.of(2027, 2, 1));

        assertEquals(0, BigDecimal.ZERO.compareTo(net));
        assertTrue(dataSource.lastSql().contains("je.entry_date < ?"));
    }
}
```

`AccountRepositoryTest.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRepositoryTest {
    @Test
    void usageChecksJournalLines() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertFalse(new AccountRepository(dataSource).isUsed(7L));
        assertTrue(dataSource.lastSql().contains("FROM journal_lines WHERE account_id = ?"));
        assertEquals(7L, dataSource.lastValues().getFirst());
    }

    @Test
    void balancesCountPostedEntriesOnly() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertTrue(new AccountRepository(dataSource).postedNetDebits().isEmpty());
        assertTrue(dataSource.lastSql().contains("je.status = 'POSTED'"));
    }

    @Test
    void activeChildrenCheck() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new AccountRepository(dataSource).hasActiveChildren(3L);

        assertTrue(dataSource.lastSql().contains("parent_id = ? AND status = 'ACTIVE'"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=JournalRepositoryTest,AccountRepositoryTest`
Expected: compilation FAILURE — `cannot find symbol: class JournalRepository`.

- [ ] **Step 3: Implement**

`dto/finance/JournalSearchCriteria.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.time.LocalDate;

/** Journal list filters; any field may be null. */
public record JournalSearchCriteria(LocalDate dateFrom, LocalDate dateTo, JournalStatus status,
                                    JournalSourceType sourceType, String text) {
}
```

`dto/finance/JournalEntrySummary.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JournalEntrySummary(long id, String entryNo, LocalDate entryDate, String narration,
                                  JournalSourceType sourceType, JournalStatus status, BigDecimal total,
                                  String preparedByName, String postedByName) {
}
```

`dto/finance/LedgerLine.java`:

```java
package com.churchmanagement.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A posted line on one account, for the Account Ledger. */
public record LedgerLine(long entryId, String entryNo, LocalDate entryDate, String narration, String memo,
                         String categoryCode, String churchCode, BigDecimal debit, BigDecimal credit) {
}
```

`dto/finance/PostedEntry.java`:

```java
package com.churchmanagement.dto.finance;

public record PostedEntry(long id, String entryNo) {
}
```

`exception/JournalStateException.java`:

```java
package com.churchmanagement.exception;

/** A journal entry is not in the state an operation needs (e.g. posting a posted entry). */
public class JournalStateException extends RuntimeException {
    public JournalStateException(String message) {
        super(message);
    }
}
```

`repository/FiscalYearRepository.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class FiscalYearRepository {
    private final DataSource dataSource;

    public FiscalYearRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public FiscalYearRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<FiscalYear> findByYear(int year) {
        String sql = "SELECT year, start_date, end_date, status FROM fiscal_years WHERE year = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FiscalYear(resultSet.getInt("year"),
                        resultSet.getDate("start_date").toLocalDate(),
                        resultSet.getDate("end_date").toLocalDate(),
                        FiscalYear.Status.valueOf(resultSet.getString("status"))));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the accounting year.", exception);
        }
    }

    public void insertIfMissing(FiscalYear fiscalYear) {
        String sql = "INSERT IGNORE INTO fiscal_years (year, start_date, end_date, status) VALUES (?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, fiscalYear.year());
            statement.setDate(2, Date.valueOf(fiscalYear.startDate()));
            statement.setDate(3, Date.valueOf(fiscalYear.endDate()));
            statement.setString(4, fiscalYear.status().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to create the accounting year.", exception);
        }
    }
}
```

`repository/AccountRepository.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AccountRepository {
    private static final String SELECT = """
            SELECT id, code, name, type, parent_id, is_group, cash_flow_class, system_key, description, status
            FROM accounts
            """;

    private final DataSource dataSource;

    public AccountRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public AccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Account> findAll() {
        return query(SELECT + " ORDER BY code", null);
    }

    public Optional<Account> findById(long id) {
        return query(SELECT + " WHERE id = ?", id).stream().findFirst();
    }

    public Optional<Account> findByCode(String code) {
        return query(SELECT + " WHERE code = ?", code).stream().findFirst();
    }

    public Optional<Account> findBySystemKey(String systemKey) {
        return query(SELECT + " WHERE system_key = ?", systemKey).stream().findFirst();
    }

    public Account insert(Account account, LocalDateTime now) {
        String sql = """
                INSERT INTO accounts (code, name, type, parent_id, is_group, cash_flow_class, system_key,
                                      description, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, account.code());
            statement.setString(2, account.name());
            statement.setString(3, account.type().name());
            setNullableLong(statement, 4, account.parentId());
            statement.setBoolean(5, account.group());
            statement.setString(6, account.cashFlowClass().name());
            statement.setString(7, account.systemKey());
            statement.setString(8, account.description());
            statement.setString(9, account.status().name());
            statement.setTimestamp(10, Timestamp.valueOf(now));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return account.withId(keys.getLong(1));
                }
            }
            throw new DatabaseException("Account id was not generated.");
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save the account.", exception);
        }
    }

    public void update(Account account, LocalDateTime now) {
        String sql = """
                UPDATE accounts
                SET code = ?, name = ?, parent_id = ?, cash_flow_class = ?, description = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.code());
            statement.setString(2, account.name());
            setNullableLong(statement, 3, account.parentId());
            statement.setString(4, account.cashFlowClass().name());
            statement.setString(5, account.description());
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setLong(7, account.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update the account.", exception);
        }
    }

    public void updateStatus(long id, Account.Status status, LocalDateTime now) {
        String sql = "UPDATE accounts SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update the account status.", exception);
        }
    }

    public void delete(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete the account.", exception);
        }
    }

    public boolean isUsed(long id) {
        return exists("SELECT 1 FROM journal_lines WHERE account_id = ? LIMIT 1", id);
    }

    public boolean hasChildren(long id) {
        return exists("SELECT 1 FROM accounts WHERE parent_id = ? LIMIT 1", id);
    }

    public boolean hasActiveChildren(long id) {
        return exists("SELECT 1 FROM accounts WHERE parent_id = ? AND status = 'ACTIVE' LIMIT 1", id);
    }

    /** Posted debits minus credits per account id. */
    public Map<Long, BigDecimal> postedNetDebits() {
        String sql = """
                SELECT jl.account_id, SUM(jl.debit) - SUM(jl.credit) AS net_debit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                WHERE je.status = 'POSTED'
                GROUP BY jl.account_id
                """;
        Map<Long, BigDecimal> balances = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                balances.put(resultSet.getLong("account_id"), resultSet.getBigDecimal("net_debit"));
            }
            return balances;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load account balances.", exception);
        }
    }

    private List<Account> query(String sql, Object parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setObject(1, parameter);
            }
            List<Account> accounts = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(map(resultSet));
                }
            }
            return accounts;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load accounts.", exception);
        }
    }

    private boolean exists(String sql, long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check the account.", exception);
        }
    }

    private Account map(ResultSet resultSet) throws SQLException {
        long parentId = resultSet.getLong("parent_id");
        Long parent = resultSet.wasNull() ? null : parentId;
        return new Account(resultSet.getLong("id"), resultSet.getString("code"), resultSet.getString("name"),
                AccountType.valueOf(resultSet.getString("type")), parent, resultSet.getBoolean("is_group"),
                CashFlowClass.valueOf(resultSet.getString("cash_flow_class")), resultSet.getString("system_key"),
                resultSet.getString("description"), Account.Status.valueOf(resultSet.getString("status")));
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
```

`repository/JournalRepository.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.exception.JournalStateException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Journal entries and lines. Posting and numbering happen inside one transaction. */
public class JournalRepository {
    private final DataSource dataSource;

    public JournalRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public JournalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static String formatEntryNo(int year, int number) {
        return String.format("JE-%d-%05d", year, number);
    }

    public Optional<JournalEntry> findById(long id) {
        String sql = """
                SELECT id, entry_no, entry_date, fiscal_year, narration, source_type, source_id, status,
                       reversal_of_id, prepared_by, prepared_at, posted_by, posted_at
                FROM journal_entries WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JournalEntry(resultSet.getLong("id"), resultSet.getString("entry_no"),
                        resultSet.getDate("entry_date").toLocalDate(), resultSet.getInt("fiscal_year"),
                        resultSet.getString("narration"), JournalSourceType.valueOf(resultSet.getString("source_type")),
                        nullableLong(resultSet, "source_id"), JournalStatus.valueOf(resultSet.getString("status")),
                        nullableLong(resultSet, "reversal_of_id"), nullableLong(resultSet, "prepared_by"),
                        toDateTime(resultSet.getTimestamp("prepared_at")), nullableLong(resultSet, "posted_by"),
                        toDateTime(resultSet.getTimestamp("posted_at")), findLines(connection, id)));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the journal entry.", exception);
        }
    }

    public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT je.id, je.entry_no, je.entry_date, je.narration, je.source_type, je.status,
                       COALESCE((SELECT SUM(jl.debit) FROM journal_lines jl WHERE jl.entry_id = je.id), 0) AS total,
                       prepared.full_name AS prepared_by_name, posted.full_name AS posted_by_name
                FROM journal_entries je
                LEFT JOIN users prepared ON prepared.id = je.prepared_by
                LEFT JOIN users posted ON posted.id = je.posted_by
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (criteria.dateFrom() != null) {
            sql.append(" AND je.entry_date >= ?");
            parameters.add(Date.valueOf(criteria.dateFrom()));
        }
        if (criteria.dateTo() != null) {
            sql.append(" AND je.entry_date <= ?");
            parameters.add(Date.valueOf(criteria.dateTo()));
        }
        if (criteria.status() != null) {
            sql.append(" AND je.status = ?");
            parameters.add(criteria.status().name());
        }
        if (criteria.sourceType() != null) {
            sql.append(" AND je.source_type = ?");
            parameters.add(criteria.sourceType().name());
        }
        if (criteria.text() != null && !criteria.text().isBlank()) {
            sql.append(" AND (je.narration LIKE ? OR je.entry_no LIKE ?)");
            String term = "%" + criteria.text().strip() + "%";
            parameters.add(term);
            parameters.add(term);
        }
        sql.append(" ORDER BY je.entry_date DESC, je.id DESC LIMIT 1000");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            List<JournalEntrySummary> summaries = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    summaries.add(new JournalEntrySummary(resultSet.getLong("id"), resultSet.getString("entry_no"),
                            resultSet.getDate("entry_date").toLocalDate(), resultSet.getString("narration"),
                            JournalSourceType.valueOf(resultSet.getString("source_type")),
                            JournalStatus.valueOf(resultSet.getString("status")), resultSet.getBigDecimal("total"),
                            resultSet.getString("prepared_by_name"), resultSet.getString("posted_by_name")));
                }
            }
            return summaries;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search journal entries.", exception);
        }
    }

    public long insertDraft(JournalEntry entry) {
        return inTransaction(connection -> {
            long id = insertHeader(connection, entry, JournalStatus.DRAFT, null, null, null);
            insertLines(connection, id, entry.lines());
            return id;
        }, "Unable to save the journal entry.");
    }

    public void updateDraft(JournalEntry entry) {
        inTransaction(connection -> {
            String sql = """
                    UPDATE journal_entries
                    SET entry_date = ?, fiscal_year = ?, narration = ?, updated_at = ?
                    WHERE id = ? AND status = 'DRAFT'
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDate(1, Date.valueOf(entry.entryDate()));
                statement.setInt(2, entry.fiscalYear());
                statement.setString(3, entry.narration());
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                statement.setLong(5, entry.id());
                if (statement.executeUpdate() != 1) {
                    throw new JournalStateException("Only draft entries can be edited.");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM journal_lines WHERE entry_id = ?")) {
                statement.setLong(1, entry.id());
                statement.executeUpdate();
            }
            insertLines(connection, entry.id(), entry.lines());
            return null;
        }, "Unable to save the journal entry.");
    }

    public void deleteDraft(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM journal_entries WHERE id = ? AND status = 'DRAFT'")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) {
                throw new JournalStateException("Only draft entries can be deleted.");
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete the journal entry.", exception);
        }
    }

    /** Re-checks the draft under a row lock, numbers it and marks it POSTED. Returns the entry number. */
    public String post(long id, long postedBy, LocalDateTime postedAt) {
        return inTransaction(connection -> {
            int fiscalYear;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, fiscal_year FROM journal_entries WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next() || !JournalStatus.DRAFT.name().equals(resultSet.getString("status"))) {
                        throw new JournalStateException("Only draft entries can be posted.");
                    }
                    fiscalYear = resultSet.getInt("fiscal_year");
                }
            }
            requireBalanced(connection, id);
            String entryNo = formatEntryNo(fiscalYear, nextNumber(connection, fiscalYear));
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE journal_entries
                    SET entry_no = ?, status = 'POSTED', posted_by = ?, posted_at = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, entryNo);
                statement.setLong(2, postedBy);
                statement.setTimestamp(3, Timestamp.valueOf(postedAt));
                statement.setTimestamp(4, Timestamp.valueOf(postedAt));
                statement.setLong(5, id);
                statement.executeUpdate();
            }
            return entryNo;
        }, "Unable to post the journal entry.");
    }

    /** Inserts an already-validated entry and posts it in the same transaction (reversals, documents). */
    public PostedEntry insertPosted(JournalEntry entry, long postedBy, LocalDateTime postedAt) {
        return inTransaction(connection -> {
            String entryNo = formatEntryNo(entry.fiscalYear(), nextNumber(connection, entry.fiscalYear()));
            long id = insertHeader(connection, entry, JournalStatus.POSTED, entryNo, postedBy, postedAt);
            insertLines(connection, id, entry.lines());
            requireBalanced(connection, id);
            return new PostedEntry(id, entryNo);
        }, "Unable to post the journal entry.");
    }

    public boolean isReversed(long id) {
        return exists("SELECT 1 FROM journal_entries WHERE reversal_of_id = ? LIMIT 1", id);
    }

    public boolean hasPostedEntries() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM journal_entries WHERE status = 'POSTED' LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check posted entries.", exception);
        }
    }

    /** The newest OPENING entry that has not been reversed (draft or posted), if any. */
    public Optional<JournalEntry> findLiveOpeningEntry() {
        String sql = """
                SELECT je.id FROM journal_entries je
                WHERE je.source_type = 'OPENING'
                  AND NOT EXISTS (SELECT 1 FROM journal_entries r WHERE r.reversal_of_id = je.id)
                ORDER BY je.id DESC LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            long id = resultSet.getLong("id");
            return findById(id);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the opening balances.", exception);
        }
    }

    public List<LedgerLine> ledgerLines(long accountId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT je.id AS entry_id, je.entry_no, je.entry_date, je.narration, jl.memo,
                       cc.code AS category_code, c.church_code, jl.debit, jl.credit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                LEFT JOIN collection_categories cc ON cc.id = jl.category_id
                LEFT JOIN churches c ON c.id = jl.church_id
                WHERE jl.account_id = ? AND je.status = 'POSTED' AND je.entry_date >= ? AND je.entry_date <= ?
                ORDER BY je.entry_date, je.posted_at, je.id, jl.line_no
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            statement.setDate(2, Date.valueOf(from));
            statement.setDate(3, Date.valueOf(to));
            List<LedgerLine> lines = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new LedgerLine(resultSet.getLong("entry_id"), resultSet.getString("entry_no"),
                            resultSet.getDate("entry_date").toLocalDate(), resultSet.getString("narration"),
                            resultSet.getString("memo"), resultSet.getString("category_code"),
                            resultSet.getString("church_code"), resultSet.getBigDecimal("debit"),
                            resultSet.getBigDecimal("credit")));
                }
            }
            return lines;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the account ledger.", exception);
        }
    }

    public BigDecimal netDebitBefore(long accountId, LocalDate date) {
        String sql = """
                SELECT COALESCE(SUM(jl.debit) - SUM(jl.credit), 0) AS net_debit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                WHERE jl.account_id = ? AND je.status = 'POSTED' AND je.entry_date < ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            statement.setDate(2, Date.valueOf(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                BigDecimal value = resultSet.next() ? resultSet.getBigDecimal("net_debit") : null;
                return value == null ? BigDecimal.ZERO : value;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the opening balance.", exception);
        }
    }

    private long insertHeader(Connection connection, JournalEntry entry, JournalStatus status, String entryNo,
                              Long postedBy, LocalDateTime postedAt) throws SQLException {
        String sql = """
                INSERT INTO journal_entries (entry_no, entry_date, fiscal_year, narration, source_type, source_id,
                                             status, reversal_of_id, prepared_by, prepared_at, posted_by, posted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, entryNo);
            statement.setDate(2, Date.valueOf(entry.entryDate()));
            statement.setInt(3, entry.fiscalYear());
            statement.setString(4, entry.narration());
            statement.setString(5, entry.sourceType().name());
            setNullableLong(statement, 6, entry.sourceId());
            statement.setString(7, status.name());
            setNullableLong(statement, 8, entry.reversalOfId());
            statement.setLong(9, entry.preparedBy());
            statement.setTimestamp(10, Timestamp.valueOf(entry.preparedAt()));
            setNullableLong(statement, 11, postedBy);
            if (postedAt == null) {
                statement.setNull(12, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(12, Timestamp.valueOf(postedAt));
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DatabaseException("Journal entry id was not generated.");
        }
    }

    private void insertLines(Connection connection, long entryId, List<JournalLine> lines) throws SQLException {
        String sql = """
                INSERT INTO journal_lines (entry_id, line_no, account_id, debit, credit, category_id, church_id, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalLine line : lines) {
                statement.setLong(1, entryId);
                statement.setInt(2, line.lineNo());
                statement.setLong(3, line.accountId());
                statement.setBigDecimal(4, line.debit());
                statement.setBigDecimal(5, line.credit());
                setNullableLong(statement, 6, line.categoryId());
                setNullableLong(statement, 7, line.churchId());
                statement.setString(8, line.memo());
                statement.executeUpdate();
            }
        }
    }

    private List<JournalLine> findLines(Connection connection, long entryId) throws SQLException {
        String sql = """
                SELECT id, line_no, account_id, debit, credit, category_id, church_id, memo
                FROM journal_lines WHERE entry_id = ? ORDER BY line_no
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entryId);
            List<JournalLine> lines = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new JournalLine(resultSet.getLong("id"), resultSet.getInt("line_no"),
                            resultSet.getLong("account_id"), resultSet.getBigDecimal("debit"),
                            resultSet.getBigDecimal("credit"), nullableLong(resultSet, "category_id"),
                            nullableLong(resultSet, "church_id"), resultSet.getString("memo")));
                }
            }
            return lines;
        }
    }

    private void requireBalanced(Connection connection, long entryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS line_count, COALESCE(SUM(debit), 0) AS debits, COALESCE(SUM(credit), 0) AS credits
                FROM journal_lines WHERE entry_id = ?
                """)) {
            statement.setLong(1, entryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt("line_count") < 2
                        || resultSet.getBigDecimal("debits").signum() == 0
                        || resultSet.getBigDecimal("debits").compareTo(resultSet.getBigDecimal("credits")) != 0) {
                    throw new JournalStateException("The entry is not balanced.");
                }
            }
        }
    }

    private int nextNumber(Connection connection, int fiscalYear) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO journal_entry_sequences (fiscal_year, last_number) VALUES (?, 0)")) {
            statement.setInt(1, fiscalYear);
            statement.executeUpdate();
        }
        int next;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_number FROM journal_entry_sequences WHERE fiscal_year = ? FOR UPDATE")) {
            statement.setInt(1, fiscalYear);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DatabaseException("Journal entry sequence is missing.");
                }
                next = resultSet.getInt("last_number") + 1;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE journal_entry_sequences SET last_number = ? WHERE fiscal_year = ?")) {
            statement.setInt(1, next);
            statement.setInt(2, fiscalYear);
            statement.executeUpdate();
        }
        return next;
    }

    private boolean exists(String sql, long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check the journal entry.", exception);
        }
    }

    private <T> T inTransaction(SqlWork<T> work, String failureMessage) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException(failureMessage, exception);
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=JournalRepositoryTest,AccountRepositoryTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/finance src/main/java/com/churchmanagement/exception/JournalStateException.java src/main/java/com/churchmanagement/repository/FiscalYearRepository.java src/main/java/com/churchmanagement/repository/AccountRepository.java src/main/java/com/churchmanagement/repository/JournalRepository.java src/test/java/com/churchmanagement/repository/JournalRepositoryTest.java src/test/java/com/churchmanagement/repository/AccountRepositoryTest.java
git commit -m "feat(finance): repositories for accounts, fiscal years and journal entries" -m "JournalRepository saves drafts with their lines in one transaction, and posts by locking the entry row, re-checking the lines balance in SQL, taking the next number from the per-year sequence under FOR UPDATE and marking the entry POSTED, so numbers never have gaps. insertPosted does the same for entries created already posted (reversals, documents). Also search, account ledger lines, balance before a date and the live opening entry." -m "AccountRepository covers the chart (CRUD, status, usage and child checks, posted balances); FiscalYearRepository creates years on demand." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Journal validator and money format

**Files:**
- Create: `dto/finance/JournalEntryRequest.java`, `dto/finance/JournalLineRequest.java`
- Create: `validation/JournalValidator.java`, `validation/JournalValidationException.java`
- Create: `util/MoneyFormat.java`
- Test: `src/test/java/com/churchmanagement/validation/JournalValidatorTest.java`, `src/test/java/com/churchmanagement/util/MoneyFormatTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountType`, `JournalSourceType` (Task 1).
- Produces:
  - `record JournalLineRequest(Long accountId, BigDecimal debit, BigDecimal credit, Long categoryId, Long churchId, String memo)` with `debitOrZero()`, `creditOrZero()`.
  - `record JournalEntryRequest(Long id, JournalSourceType sourceType, LocalDate entryDate, String narration, List<JournalLineRequest> lines)`.
  - `JournalValidator.validate(JournalEntryRequest request, Map<Long, Account> accounts, Set<Long> activeCategoryIds, LocalDate goLiveDate)` — throws `JournalValidationException`; constant `MAX_AMOUNT`.
  - `MoneyFormat.format(BigDecimal)` → `"1,234.50"` (null → `""`); `MoneyFormat.parse(String)` → `BigDecimal` scale 2 or `null` for blank; throws `IllegalArgumentException("Enter an amount like 1,250.00.")` for non-numbers.

- [ ] **Step 1: Write the failing tests**

`JournalValidatorTest.java`:

```java
package com.churchmanagement.validation;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalValidatorTest {
    private static final LocalDate GO_LIVE = LocalDate.of(2027, 1, 1);
    private static final Map<Long, Account> ACCOUNTS = Map.of(
            1L, account(1L, "1100", "Cash in Hand", AccountType.ASSET, false, Account.Status.ACTIVE),
            2L, account(2L, "4400", "Rent Income", AccountType.INCOME, false, Account.Status.ACTIVE),
            3L, account(3L, "1200", "Bank Accounts", AccountType.ASSET, true, Account.Status.ACTIVE),
            4L, account(4L, "1300", "Receivables", AccountType.ASSET, false, Account.Status.INACTIVE));
    private static final Set<Long> CATEGORIES = Set.of(10L);

    @Test
    void acceptsBalancedEntry() {
        assertDoesNotThrow(() -> validate(request(line(1L, "100.00", null, null), line(2L, null, "100.00", 10L))));
    }

    @Test
    void requiresNarration() {
        assertMessage("Enter a narration.", new JournalEntryRequest(null, JournalSourceType.JOURNAL, GO_LIVE, " ",
                List.of(line(1L, "1", null, null), line(2L, null, "1", 10L))));
    }

    @Test
    void rejectsDateBeforeGoLive() {
        assertMessage("Entries cannot be dated before the go-live date 2027-01-01.",
                new JournalEntryRequest(null, JournalSourceType.JOURNAL, LocalDate.of(2026, 12, 31), "Rent",
                        List.of(line(1L, "1", null, null), line(2L, null, "1", 10L))));
    }

    @Test
    void needsTwoLines() {
        assertMessage("An entry needs at least two lines.", request(line(1L, "100", null, null)));
    }

    @Test
    void rejectsMissingAccount() {
        assertMessage("Line 2: choose an account.", request(line(1L, "100", null, null), line(null, null, "100", 10L)));
    }

    @Test
    void rejectsGroupAccount() {
        assertMessage("Line 1: 1200 Bank Accounts is a group; choose an account under it.",
                request(line(3L, "100", null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void rejectsInactiveAccount() {
        assertMessage("Line 1: 1300 Receivables is inactive.",
                request(line(4L, "100", null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void lineNeedsExactlyOneSide() {
        assertMessage("Line 1: enter either a debit or a credit.",
                request(line(1L, "100", "100", null), line(2L, null, "100", 10L)));
        assertMessage("Line 1: enter either a debit or a credit.",
                request(line(1L, null, null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void rejectsNegativeAndThreeDecimalAmounts() {
        assertMessage("Line 1: amounts cannot be negative.",
                request(line(1L, "-5", null, null), line(2L, null, "5", 10L)));
        assertMessage("Line 1: use at most 2 decimal places.",
                request(line(1L, "1.005", null, null), line(2L, null, "1.005", 10L)));
    }

    @Test
    void requiresCategoryOnIncome() {
        assertMessage("Line 2: choose a category for 4400 Rent Income.",
                request(line(1L, "100", null, null), line(2L, null, "100", null)));
    }

    @Test
    void rejectsInactiveCategory() {
        assertMessage("Line 2: the selected category is not active.",
                request(line(1L, "100", null, null), line(2L, null, "100", 99L)));
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertMessage("Debits (100.00) and credits (99.99) do not balance.",
                request(line(1L, "100", null, null), line(2L, null, "99.99", 10L)));
    }

    private static void validate(JournalEntryRequest request) {
        JournalValidator.validate(request, ACCOUNTS, CATEGORIES, GO_LIVE);
    }

    private static void assertMessage(String expected, JournalEntryRequest request) {
        JournalValidationException exception = assertThrows(JournalValidationException.class, () -> validate(request));
        assertEquals(expected, exception.getMessage());
    }

    private static JournalEntryRequest request(JournalLineRequest... lines) {
        return new JournalEntryRequest(null, JournalSourceType.JOURNAL, GO_LIVE, "Rent for January",
                new ArrayList<>(List.of(lines)));
    }

    private static JournalLineRequest line(Long accountId, String debit, String credit, Long categoryId) {
        return new JournalLineRequest(accountId, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), categoryId, null, null);
    }

    private static Account account(long id, String code, String name, AccountType type, boolean group,
                                   Account.Status status) {
        return new Account(id, code, name, type, 1000L, group, CashFlowClass.OPERATING, null, null, status);
    }
}
```

`MoneyFormatTest.java`:

```java
package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyFormatTest {
    @Test
    void formatsWithGroupingAndTwoDecimals() {
        assertEquals("1,234,567.50", MoneyFormat.format(new BigDecimal("1234567.5")));
        assertEquals("0.00", MoneyFormat.format(BigDecimal.ZERO));
        assertEquals("-12.00", MoneyFormat.format(new BigDecimal("-12")));
        assertEquals("", MoneyFormat.format(null));
    }

    @Test
    void parsesTypedAmounts() {
        assertEquals(new BigDecimal("1250.00"), MoneyFormat.parse(" 1,250 "));
        assertEquals(new BigDecimal("0.50"), MoneyFormat.parse(".5"));
        assertNull(MoneyFormat.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("12a"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=JournalValidatorTest,MoneyFormatTest`
Expected: compilation FAILURE — `cannot find symbol: class JournalValidator`.

- [ ] **Step 3: Implement**

`dto/finance/JournalLineRequest.java`:

```java
package com.churchmanagement.dto.finance;

import java.math.BigDecimal;

/** A line as typed by the user; null amounts mean zero. */
public record JournalLineRequest(Long accountId, BigDecimal debit, BigDecimal credit, Long categoryId,
                                 Long churchId, String memo) {
    public BigDecimal debitOrZero() {
        return debit == null ? BigDecimal.ZERO : debit;
    }

    public BigDecimal creditOrZero() {
        return credit == null ? BigDecimal.ZERO : credit;
    }
}
```

`dto/finance/JournalEntryRequest.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;

import java.time.LocalDate;
import java.util.List;

/** A draft to create ({@code id == null}) or update. */
public record JournalEntryRequest(Long id, JournalSourceType sourceType, LocalDate entryDate, String narration,
                                  List<JournalLineRequest> lines) {
    public JournalEntryRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
```

`validation/JournalValidationException.java`:

```java
package com.churchmanagement.validation;

public class JournalValidationException extends RuntimeException {
    public JournalValidationException(String message) {
        super(message);
    }
}
```

`validation/JournalValidator.java`:

```java
package com.churchmanagement.validation;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.util.MoneyFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Double-entry rules every journal entry must satisfy before it is saved or posted. */
public final class JournalValidator {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
    private static final int MAX_NARRATION = 500;

    private JournalValidator() {
    }

    public static void validate(JournalEntryRequest request, Map<Long, Account> accounts, Set<Long> activeCategoryIds,
                                LocalDate goLiveDate) {
        if (request.narration() == null || request.narration().isBlank()) {
            throw new JournalValidationException("Enter a narration.");
        }
        if (request.narration().strip().length() > MAX_NARRATION) {
            throw new JournalValidationException("The narration cannot exceed 500 characters.");
        }
        if (request.entryDate() == null) {
            throw new JournalValidationException("Choose the entry date.");
        }
        if (request.entryDate().isBefore(goLiveDate)) {
            throw new JournalValidationException("Entries cannot be dated before the go-live date " + goLiveDate + ".");
        }
        if (request.lines().size() < 2) {
            throw new JournalValidationException("An entry needs at least two lines.");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (int index = 0; index < request.lines().size(); index++) {
            JournalLineRequest line = request.lines().get(index);
            String prefix = "Line " + (index + 1) + ": ";
            Account account = line.accountId() == null ? null : accounts.get(line.accountId());
            if (account == null) {
                throw new JournalValidationException(prefix + "choose an account.");
            }
            if (account.group()) {
                throw new JournalValidationException(prefix + account.displayText()
                        + " is a group; choose an account under it.");
            }
            if (!account.isActive()) {
                throw new JournalValidationException(prefix + account.displayText() + " is inactive.");
            }
            BigDecimal debit = line.debitOrZero();
            BigDecimal credit = line.creditOrZero();
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new JournalValidationException(prefix + "amounts cannot be negative.");
            }
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw new JournalValidationException(prefix + "enter either a debit or a credit.");
            }
            BigDecimal amount = debit.signum() > 0 ? debit : credit;
            if (amount.stripTrailingZeros().scale() > 2) {
                throw new JournalValidationException(prefix + "use at most 2 decimal places.");
            }
            if (amount.compareTo(MAX_AMOUNT) > 0) {
                throw new JournalValidationException(prefix + "the amount is too large.");
            }
            if (account.type().requiresCategory() && line.categoryId() == null) {
                throw new JournalValidationException(prefix + "choose a category for " + account.displayText() + ".");
            }
            if (line.categoryId() != null && !activeCategoryIds.contains(line.categoryId())) {
                throw new JournalValidationException(prefix + "the selected category is not active.");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new JournalValidationException("Debits (" + MoneyFormat.format(totalDebit) + ") and credits ("
                    + MoneyFormat.format(totalCredit) + ") do not balance.");
        }
    }
}
```

`util/MoneyFormat.java`:

```java
package com.churchmanagement.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Display and parsing of LKR amounts: "1,234.50". */
public final class MoneyFormat {
    private MoneyFormat() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Blank -> null; "1,250" -> 1250.00. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.strip().replace(",", ""));
            return value.scale() < 2 ? value.setScale(2) : value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter an amount like 1,250.00.");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=JournalValidatorTest,MoneyFormatTest`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/finance/JournalEntryRequest.java src/main/java/com/churchmanagement/dto/finance/JournalLineRequest.java src/main/java/com/churchmanagement/validation/JournalValidator.java src/main/java/com/churchmanagement/validation/JournalValidationException.java src/main/java/com/churchmanagement/util/MoneyFormat.java src/test/java/com/churchmanagement/validation/JournalValidatorTest.java src/test/java/com/churchmanagement/util/MoneyFormatTest.java
git commit -m "feat(finance): double-entry validation rules" -m "JournalValidator enforces the rules every entry must meet before it is saved or posted: a narration, a date on or after go-live, at least two lines, active posting (not group) accounts, exactly one positive side per line with at most two decimals, a category on income, expense and equity lines, and debits equal to credits. Messages name the line and account so the user knows what to fix. MoneyFormat formats and parses LKR amounts." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Finance settings, fiscal years, shared exception and activity codes

**Files:**
- Create: `service/FinanceException.java`, `service/FinanceSettings.java`, `service/FiscalYearService.java`
- Create: `src/test/java/com/churchmanagement/testsupport/FinanceFakes.java`
- Modify: `service/ActivityLogService.java` (constants + `logFinance`)
- Modify: `service/SystemSettingService.java` (validation of the two finance keys), `src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java`
- Test: `src/test/java/com/churchmanagement/service/FiscalYearServiceTest.java`

**Interfaces:**
- Consumes: `FiscalYearRepository`, `FiscalYear` (Tasks 1–2).
- Produces:
  - `class FinanceException extends RuntimeException` (`(String)`, `(String, Throwable)`).
  - `FinanceSettings` — constants `GO_LIVE_DATE_KEY`, `APPROVAL_REQUIRED_KEY`, `DEFAULT_GO_LIVE_DATE`; `LocalDate goLiveDate()`, `boolean approvalRequired()`; constructors `()` and `(SystemConfigurationCache)`.
  - `FiscalYearService(FiscalYearRepository)`: `FiscalYear forDate(LocalDate)`, `FiscalYear requireOpen(LocalDate)`.
  - `ActivityLogService`: constants `NAVIGATE_FINANCE`, `ACCOUNT_CREATED`, `ACCOUNT_UPDATED`, `ACCOUNT_ACTIVATED`, `ACCOUNT_DEACTIVATED`, `ACCOUNT_DELETED`, `JOURNAL_DRAFT_SAVED`, `JOURNAL_DRAFT_DELETED`, `JOURNAL_POSTED`, `JOURNAL_REVERSED`, `FINANCE_SETTINGS_UPDATED`; method `void logFinance(Long userId, String action, String recordId, String details)`.
  - Test support `FinanceFakes` with static nested fakes used by Tasks 5–7 and 13: `FakeAccountRepository`, `FakeJournalRepository`, `FakeFiscalYearRepository`, `FakeCategoryRepository`, `FakeFinanceSettings`, `RecordingActivityLog`, plus helpers `account(...)`, `category(...)`, `user(long id, String... permissions)`.

- [ ] **Step 1: Write the failing tests**

`FiscalYearServiceTest.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalYearServiceTest {
    @Test
    void createsMissingYearOpen() {
        FinanceFakes.FakeFiscalYearRepository repository = new FinanceFakes.FakeFiscalYearRepository();

        FiscalYear year = new FiscalYearService(repository).forDate(LocalDate.of(2027, 3, 4));

        assertEquals(2027, year.year());
        assertEquals(FiscalYear.Status.OPEN, year.status());
        assertEquals(LocalDate.of(2027, 12, 31), year.endDate());
    }

    @Test
    void closedYearIsRejected() {
        FinanceFakes.FakeFiscalYearRepository repository = new FinanceFakes.FakeFiscalYearRepository();
        repository.close(2027);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> new FiscalYearService(repository).requireOpen(LocalDate.of(2027, 6, 1)));

        assertEquals("The 2027 accounting year is closed.", exception.getMessage());
    }
}
```

In `SystemSettingServiceTest`, add to the fake default list (after `system.theme`):

```java
            add(setting("finance.go_live_date", "2027-01-01", "DATE", "FINANCE"));
            add(setting("finance.approval.required", "false", "BOOLEAN", "FINANCE"));
```

change `assertEquals(20, settings.size());` to `assertEquals(22, settings.size());`, and add:

```java
    @Test
    void rejectsInvalidFinanceGoLiveDate() {
        assertInvalid("finance.go_live_date", "01/01/2027", "Finance go-live date must be a date like 2027-01-01.");
    }

    @Test
    void rejectsInvalidFinanceApprovalFlag() {
        assertInvalid("finance.approval.required", "yes", "Finance approval setting must be true or false.");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=FiscalYearServiceTest,SystemSettingServiceTest`
Expected: compilation FAILURE — `package com.churchmanagement.testsupport.FinanceFakes does not exist` / `cannot find symbol: class FiscalYearService`.

- [ ] **Step 3: Implement**

`service/FinanceException.java`:

```java
package com.churchmanagement.service;

/** A finance rule was broken; the message is shown to the user as-is. */
public class FinanceException extends RuntimeException {
    public FinanceException(String message) {
        super(message);
    }

    public FinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`service/FinanceSettings.java`:

```java
package com.churchmanagement.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Read access to the finance settings in system_settings. */
public class FinanceSettings {
    public static final String GO_LIVE_DATE_KEY = "finance.go_live_date";
    public static final String APPROVAL_REQUIRED_KEY = "finance.approval.required";
    public static final LocalDate DEFAULT_GO_LIVE_DATE = LocalDate.of(2027, 1, 1);

    private final SystemConfigurationCache configurationCache;

    public FinanceSettings() {
        this(SystemConfigurationCache.getInstance());
    }

    public FinanceSettings(SystemConfigurationCache configurationCache) {
        this.configurationCache = configurationCache;
    }

    public LocalDate goLiveDate() {
        String value = configurationCache.getString(GO_LIVE_DATE_KEY);
        if (value == null || value.isBlank()) {
            return DEFAULT_GO_LIVE_DATE;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            return DEFAULT_GO_LIVE_DATE;
        }
    }

    public boolean approvalRequired() {
        return Boolean.parseBoolean(configurationCache.getString(APPROVAL_REQUIRED_KEY));
    }
}
```

`service/FiscalYearService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.FiscalYearRepository;

import java.time.LocalDate;

/** Calendar accounting years, created on first use. Closing is F6. */
public class FiscalYearService {
    private final FiscalYearRepository fiscalYearRepository;

    public FiscalYearService() {
        this(new FiscalYearRepository());
    }

    public FiscalYearService(FiscalYearRepository fiscalYearRepository) {
        this.fiscalYearRepository = fiscalYearRepository;
    }

    public FiscalYear forDate(LocalDate date) {
        int year = date.getYear();
        try {
            return fiscalYearRepository.findByYear(year).orElseGet(() -> {
                fiscalYearRepository.insertIfMissing(FiscalYear.open(year));
                return fiscalYearRepository.findByYear(year).orElse(FiscalYear.open(year));
            });
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the accounting year right now.", exception);
        }
    }

    public FiscalYear requireOpen(LocalDate date) {
        FiscalYear fiscalYear = forDate(date);
        if (fiscalYear.isClosed()) {
            throw new FinanceException("The " + fiscalYear.year() + " accounting year is closed.");
        }
        return fiscalYear;
    }
}
```

`ActivityLogService` — add constants next to the other navigation / category codes:

```java
    public static final String NAVIGATE_FINANCE = "NAVIGATE_FINANCE";
    public static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
    public static final String ACCOUNT_ACTIVATED = "ACCOUNT_ACTIVATED";
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String JOURNAL_DRAFT_SAVED = "JOURNAL_DRAFT_SAVED";
    public static final String JOURNAL_DRAFT_DELETED = "JOURNAL_DRAFT_DELETED";
    public static final String JOURNAL_POSTED = "JOURNAL_POSTED";
    public static final String JOURNAL_REVERSED = "JOURNAL_REVERSED";
    public static final String FINANCE_SETTINGS_UPDATED = "FINANCE_SETTINGS_UPDATED";
```

and a method next to `logOfficialSmsQueued`:

```java
    public void logFinance(Long userId, String action, String recordId, String details) {
        log(userId, action, "FINANCE", recordId, details);
    }
```

`SystemSettingService.validateValue` — add cases:

```java
            case "finance.go_live_date" -> requireIsoDate(normalized,
                    "Finance go-live date must be a date like 2027-01-01.");
            case "finance.approval.required" -> requireBoolean(normalized,
                    "Finance approval setting must be true or false.");
```

and the helper (import `java.time.LocalDate`, `java.time.format.DateTimeParseException`):

```java
    private void requireIsoDate(String value, String message) {
        try {
            LocalDate.parse(value == null ? "" : value);
        } catch (DateTimeParseException exception) {
            throw new SystemSettingException(message);
        }
    }
```

`src/test/java/com/churchmanagement/testsupport/FinanceFakes.java`:

```java
package com.churchmanagement.testsupport;

import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.FiscalYearRepository;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.FinanceSettings;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fakes for the finance services' tests. */
public final class FinanceFakes {
    private FinanceFakes() {
    }

    public static Account account(long id, String code, String name, AccountType type, Long parentId, boolean group) {
        return new Account(id, code, name, type, parentId, group, CashFlowClass.OPERATING, null, null,
                Account.Status.ACTIVE);
    }

    public static CollectionCategory category(long id, String code) {
        return new CollectionCategory(id, code, "Category " + code, null, CollectionCategory.Status.ACTIVE, null, null);
    }

    public static AuthenticatedUser user(long id, String... permissions) {
        return new AuthenticatedUser(id, "user" + id, "User " + id, 2L, "Clerk", List.of(permissions));
    }

    public static class FakeAccountRepository extends AccountRepository {
        public final Map<Long, Account> accounts = new LinkedHashMap<>();
        public final Map<Long, BigDecimal> netDebits = new HashMap<>();
        public final List<Long> usedIds = new ArrayList<>();
        private long nextId = 1000;

        public FakeAccountRepository() {
            super((DataSource) null);
        }

        public FakeAccountRepository with(Account account) {
            accounts.put(account.id(), account);
            return this;
        }

        @Override
        public List<Account> findAll() {
            return accounts.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<Account> findByCode(String code) {
            return accounts.values().stream().filter(account -> account.code().equals(code)).findFirst();
        }

        @Override
        public Optional<Account> findBySystemKey(String systemKey) {
            return accounts.values().stream().filter(account -> systemKey.equals(account.systemKey())).findFirst();
        }

        @Override
        public Account insert(Account account, LocalDateTime now) {
            Account saved = account.withId(nextId++);
            accounts.put(saved.id(), saved);
            return saved;
        }

        @Override
        public void update(Account account, LocalDateTime now) {
            accounts.put(account.id(), account);
        }

        @Override
        public void updateStatus(long id, Account.Status status, LocalDateTime now) {
            accounts.put(id, accounts.get(id).withStatus(status));
        }

        @Override
        public void delete(long id) {
            accounts.remove(id);
        }

        @Override
        public boolean isUsed(long id) {
            return usedIds.contains(id);
        }

        @Override
        public boolean hasChildren(long id) {
            return accounts.values().stream().anyMatch(account -> Long.valueOf(id).equals(account.parentId()));
        }

        @Override
        public boolean hasActiveChildren(long id) {
            return accounts.values().stream()
                    .anyMatch(account -> Long.valueOf(id).equals(account.parentId()) && account.isActive());
        }

        @Override
        public Map<Long, BigDecimal> postedNetDebits() {
            return netDebits;
        }
    }

    public static class FakeJournalRepository extends JournalRepository {
        public final Map<Long, JournalEntry> entries = new LinkedHashMap<>();
        public final List<LedgerLine> ledger = new ArrayList<>();
        public BigDecimal openingNetDebit = BigDecimal.ZERO;
        private final Map<Integer, Integer> numbers = new HashMap<>();
        private long nextId = 1;

        public FakeJournalRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<JournalEntry> findById(long id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
            return List.of();
        }

        @Override
        public long insertDraft(JournalEntry entry) {
            long id = nextId++;
            entries.put(id, copy(entry, id, null, JournalStatus.DRAFT, null, null));
            return id;
        }

        @Override
        public void updateDraft(JournalEntry entry) {
            JournalEntry existing = entries.get(entry.id());
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be edited.");
            }
            entries.put(entry.id(), copy(entry, entry.id(), null, JournalStatus.DRAFT, null, null));
        }

        @Override
        public void deleteDraft(long id) {
            JournalEntry existing = entries.get(id);
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be deleted.");
            }
            entries.remove(id);
        }

        @Override
        public String post(long id, long postedBy, LocalDateTime postedAt) {
            JournalEntry existing = entries.get(id);
            if (existing == null || existing.isPosted()) {
                throw new JournalStateException("Only draft entries can be posted.");
            }
            String entryNo = nextNumber(existing.fiscalYear());
            entries.put(id, copy(existing, id, entryNo, JournalStatus.POSTED, postedBy, postedAt));
            return entryNo;
        }

        @Override
        public PostedEntry insertPosted(JournalEntry entry, long postedBy, LocalDateTime postedAt) {
            long id = nextId++;
            String entryNo = nextNumber(entry.fiscalYear());
            entries.put(id, copy(entry, id, entryNo, JournalStatus.POSTED, postedBy, postedAt));
            return new PostedEntry(id, entryNo);
        }

        @Override
        public boolean isReversed(long id) {
            return entries.values().stream().anyMatch(entry -> Long.valueOf(id).equals(entry.reversalOfId()));
        }

        @Override
        public boolean hasPostedEntries() {
            return entries.values().stream().anyMatch(JournalEntry::isPosted);
        }

        @Override
        public Optional<JournalEntry> findLiveOpeningEntry() {
            return entries.values().stream()
                    .filter(entry -> entry.sourceType() == JournalSourceType.OPENING)
                    .filter(entry -> !isReversed(entry.id()))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<LedgerLine> ledgerLines(long accountId, LocalDate from, LocalDate to) {
            return ledger;
        }

        @Override
        public BigDecimal netDebitBefore(long accountId, LocalDate date) {
            return openingNetDebit;
        }

        private String nextNumber(int year) {
            int number = numbers.merge(year, 1, Integer::sum);
            return JournalRepository.formatEntryNo(year, number);
        }

        private static JournalEntry copy(JournalEntry entry, long id, String entryNo, JournalStatus status,
                                         Long postedBy, LocalDateTime postedAt) {
            return new JournalEntry(id, entryNo, entry.entryDate(), entry.fiscalYear(), entry.narration(),
                    entry.sourceType(), entry.sourceId(), status, entry.reversalOfId(), entry.preparedBy(),
                    entry.preparedAt(), postedBy, postedAt, entry.lines());
        }
    }

    public static class FakeFiscalYearRepository extends FiscalYearRepository {
        private final Map<Integer, FiscalYear> years = new HashMap<>();

        public FakeFiscalYearRepository() {
            super((DataSource) null);
        }

        public void close(int year) {
            FiscalYear open = FiscalYear.open(year);
            years.put(year, new FiscalYear(year, open.startDate(), open.endDate(), FiscalYear.Status.CLOSED));
        }

        @Override
        public Optional<FiscalYear> findByYear(int year) {
            return Optional.ofNullable(years.get(year));
        }

        @Override
        public void insertIfMissing(FiscalYear fiscalYear) {
            years.putIfAbsent(fiscalYear.year(), fiscalYear);
        }
    }

    public static class FakeCategoryRepository extends CollectionCategoryRepository {
        public final List<CollectionCategory> active = new ArrayList<>();

        public FakeCategoryRepository() {
            super((DataSource) null);
        }

        @Override
        public List<CollectionCategory> findActive() {
            return active;
        }
    }

    public static class FakeFinanceSettings extends FinanceSettings {
        public LocalDate goLiveDate = LocalDate.of(2027, 1, 1);
        public boolean approvalRequired;

        public FakeFinanceSettings() {
            super(null);
        }

        @Override
        public LocalDate goLiveDate() {
            return goLiveDate;
        }

        @Override
        public boolean approvalRequired() {
            return approvalRequired;
        }
    }

    public static class RecordingActivityLog extends ActivityLogService {
        public final List<String> actions = new ArrayList<>();

        public RecordingActivityLog() {
            super(null);
        }

        @Override
        public void logFinance(Long userId, String action, String recordId, String details) {
            actions.add(action);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=FiscalYearServiceTest,SystemSettingServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/FinanceException.java src/main/java/com/churchmanagement/service/FinanceSettings.java src/main/java/com/churchmanagement/service/FiscalYearService.java src/main/java/com/churchmanagement/service/ActivityLogService.java src/main/java/com/churchmanagement/service/SystemSettingService.java src/test/java/com/churchmanagement/testsupport/FinanceFakes.java src/test/java/com/churchmanagement/service/FiscalYearServiceTest.java src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java
git commit -m "feat(finance): settings, accounting years and finance activity codes" -m "FinanceSettings reads the go-live date (default 2027-01-01) and the approval setting; SystemSettingService validates both. FiscalYearService creates calendar years on first use and rejects work in a closed year. ActivityLogService gains the finance activity codes and logFinance. Test support FinanceFakes provides in-memory repositories and settings for the finance service tests." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: AccountService

**Files:**
- Create: `dto/finance/AccountRequest.java`, `dto/finance/AccountNode.java`
- Create: `service/AccountService.java`
- Test: `src/test/java/com/churchmanagement/service/AccountServiceTest.java`

**Interfaces:**
- Consumes: `AccountRepository`, `Account`, `AccountType`, `CashFlowClass` (Tasks 1–2), `FinanceException`, `ActivityLogService.logFinance` (Task 4).
- Produces:
  - `record AccountRequest(String code, String name, Long parentId, boolean group, CashFlowClass cashFlowClass, String description)`.
  - `record AccountNode(Account account, BigDecimal balance, List<AccountNode> children)`.
  - `AccountService(AccountRepository, ActivityLogService, Clock)` and `()`: `List<AccountNode> loadChart(boolean includeInactive)`, `List<Account> postingAccounts()` (active, non-group, code order), `List<Account> groups()` (active groups), `Account create(AccountRequest)`, `Account update(long id, AccountRequest)`, `void activate(long)`, `void deactivate(long)`, `void delete(long)`; static `String rangeText(Account group)` ("4001–4999").

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {
    private FinanceFakes.FakeAccountRepository repository;
    private FinanceFakes.RecordingActivityLog activityLog;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = new FinanceFakes.FakeAccountRepository()
                .with(FinanceFakes.account(1L, "1000", "Assets", AccountType.ASSET, null, true))
                .with(new Account(2L, "1100", "Cash in Hand", AccountType.ASSET, 1L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(new Account(3L, "1200", "Bank Accounts", AccountType.ASSET, 1L, true, CashFlowClass.CASH,
                        null, null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(4L, "4000", "Income", AccountType.INCOME, null, true))
                .with(FinanceFakes.account(5L, "4400", "Rent Income", AccountType.INCOME, 4L, false));
        activityLog = new FinanceFakes.RecordingActivityLog();
        service = new AccountService(repository, activityLog,
                Clock.fixed(Instant.parse("2027-01-05T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(FinanceFakes.user(1L, "finance.menu.view", "finance.accounts.manage"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createsBankAccountUnderBankGroupWithItsTypeAndCashFlowClass() {
        Account created = service.create(new AccountRequest("1201", " BOC Current ", 3L, false, null, null));

        assertEquals(AccountType.ASSET, created.type());
        assertEquals(CashFlowClass.CASH, created.cashFlowClass());
        assertEquals("BOC Current", created.name());
        assertEquals(List.of(ActivityLogServiceCodes.ACCOUNT_CREATED), activityLog.actions);
    }

    @Test
    void codeMustBeInsideTheGroupsRange() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1301", "Wrong", 3L, false, null, null)));

        assertEquals("Code 1301 is outside 1200 Bank Accounts (1201–1299).", exception.getMessage());
    }

    @Test
    void codeMustBeUnique() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1100", "Petty Cash", 1L, false, null, null)));

        assertEquals("Account code 1100 is already used.", exception.getMessage());
    }

    @Test
    void nameMustBeUniqueWithinTheGroup() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("4401", "rent income", 4L, false, null, null)));

        assertEquals("4000 Income already has an account named rent income.", exception.getMessage());
    }

    @Test
    void parentMustBeAGroup() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1101", "Petty Cash", 2L, false, null, null)));

        assertEquals("1100 Cash in Hand is not a group.", exception.getMessage());
    }

    @Test
    void subGroupsOnlyUnderTopGroupsWithRoundCodes() {
        assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1250", "Fixed Deposits", 3L, true, null, null)));
        Account group = service.create(new AccountRequest("1600", "Investments", 1L, true, CashFlowClass.INVESTING, null));
        assertTrue(group.group());
    }

    @Test
    void usedAccountKeepsCodeAndGroup() {
        repository.usedIds.add(5L);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.update(5L, new AccountRequest("4401", "Rent Income", 4L, false, null, null)));

        assertEquals("Code and group cannot change after the account has been used.", exception.getMessage());
        Account renamed = service.update(5L, new AccountRequest("4400", "Hall Rent", 4L, false, null, "Main hall"));
        assertEquals("Hall Rent", renamed.name());
    }

    @Test
    void systemAccountsCannotBeDeactivatedOrDeleted() {
        assertThrows(FinanceException.class, () -> service.deactivate(2L));
        assertThrows(FinanceException.class, () -> service.delete(2L));
    }

    @Test
    void groupWithActiveChildrenCannotBeDeactivated() {
        service.create(new AccountRequest("1201", "BOC Current", 3L, false, null, null));

        FinanceException exception = assertThrows(FinanceException.class, () -> service.deactivate(3L));

        assertEquals("Deactivate the accounts under 1200 Bank Accounts first.", exception.getMessage());
    }

    @Test
    void usedAccountCannotBeDeleted() {
        repository.usedIds.add(5L);

        FinanceException exception = assertThrows(FinanceException.class, () -> service.delete(5L));

        assertEquals("4400 Rent Income has entries and cannot be deleted; deactivate it instead.",
                exception.getMessage());
    }

    @Test
    void unusedAccountCanBeDeleted() {
        service.delete(5L);

        assertFalse(repository.accounts.containsKey(5L));
    }

    @Test
    void requiresManagePermission() {
        AuthContext.setCurrentUser(FinanceFakes.user(2L, "finance.menu.view"));

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1201", "BOC", 3L, false, null, null)));

        assertEquals("You do not have permission to manage accounts.", exception.getMessage());
    }

    @Test
    void chartShowsBalancesInNormalDirectionAndGroupTotals() {
        repository.netDebits.put(2L, new BigDecimal("1500.00"));
        repository.netDebits.put(5L, new BigDecimal("-400.00"));

        List<AccountNode> chart = service.loadChart(false);

        AccountNode assets = chart.getFirst();
        assertEquals(new BigDecimal("1500.00"), assets.balance());
        AccountNode income = chart.get(1);
        assertEquals(new BigDecimal("400.00"), income.balance());
        assertEquals("4400", income.children().getFirst().account().code());
    }

    @Test
    void postingAccountsExcludeGroupsAndInactive() {
        repository.updateStatus(5L, Account.Status.INACTIVE, null);

        assertEquals(List.of("1100"), service.postingAccounts().stream().map(Account::code).toList());
    }

    /** Keeps the test independent of how ActivityLogService names its constants' class. */
    private static final class ActivityLogServiceCodes {
        static final String ACCOUNT_CREATED = ActivityLogService.ACCOUNT_CREATED;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AccountServiceTest`
Expected: compilation FAILURE — `cannot find symbol: class AccountService`.

- [ ] **Step 3: Implement**

`dto/finance/AccountRequest.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.CashFlowClass;

/** Create or edit an account. A null cash-flow class means "same as the group". */
public record AccountRequest(String code, String name, Long parentId, boolean group, CashFlowClass cashFlowClass,
                             String description) {
}
```

`dto/finance/AccountNode.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;

import java.math.BigDecimal;
import java.util.List;

/** An account in the chart tree with its posted balance in its normal direction. */
public record AccountNode(Account account, BigDecimal balance, List<AccountNode> children) {
}
```

`service/AccountService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Chart of accounts maintenance. */
public class AccountService {
    private static final int MAX_NAME = 120;

    private final AccountRepository accountRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public AccountService() {
        this(new AccountRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public AccountService(AccountRepository accountRepository, ActivityLogService activityLogService, Clock clock) {
        this.accountRepository = accountRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<AccountNode> loadChart(boolean includeInactive) {
        require("finance.menu.view", "You do not have permission to view accounts.");
        try {
            List<Account> accounts = accountRepository.findAll().stream()
                    .filter(account -> includeInactive || account.isActive())
                    .toList();
            Map<Long, BigDecimal> netDebits = accountRepository.postedNetDebits();
            return accounts.stream()
                    .filter(Account::isTopGroup)
                    .map(top -> node(top, accounts, netDebits))
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the chart of accounts right now.", exception);
        }
    }

    public List<Account> postingAccounts() {
        try {
            return accountRepository.findAll().stream()
                    .filter(account -> !account.group() && account.isActive())
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load accounts right now.", exception);
        }
    }

    public List<Account> groups() {
        try {
            return accountRepository.findAll().stream()
                    .filter(account -> account.group() && account.isActive())
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load account groups right now.", exception);
        }
    }

    public Account create(AccountRequest request) {
        AuthenticatedUser user = requireManage();
        try {
            Account parent = requireParent(request.parentId());
            String code = normalizeCode(request.code());
            String name = normalizeName(request.name());
            validatePlacement(parent, code, request.group());
            if (accountRepository.findByCode(code).isPresent()) {
                throw new FinanceException("Account code " + code + " is already used.");
            }
            requireUniqueName(parent, name, null);
            Account account = new Account(null, code, name, parent.type(), parent.id(), request.group(),
                    request.cashFlowClass() == null ? parent.cashFlowClass() : request.cashFlowClass(), null,
                    blankToNull(request.description()), Account.Status.ACTIVE);
            Account saved = accountRepository.insert(account, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_CREATED,
                    String.valueOf(saved.id()), saved.displayText());
            return saved;
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the account right now.", exception);
        }
    }

    public Account update(long id, AccountRequest request) {
        AuthenticatedUser user = requireManage();
        try {
            Account existing = requireAccount(id);
            String code = normalizeCode(request.code());
            String name = normalizeName(request.name());
            Long parentId = existing.isTopGroup() ? null : request.parentId();
            boolean placementChanged = !code.equals(existing.code())
                    || (parentId != null && !parentId.equals(existing.parentId()));
            if (placementChanged) {
                if (existing.isTopGroup() || existing.isSystem() || accountRepository.isUsed(id)
                        || (existing.group() && accountRepository.hasChildren(id))) {
                    throw new FinanceException("Code and group cannot change after the account has been used.");
                }
                Account parent = requireParent(parentId);
                validatePlacement(parent, code, existing.group());
                if (accountRepository.findByCode(code).filter(other -> !other.id().equals(id)).isPresent()) {
                    throw new FinanceException("Account code " + code + " is already used.");
                }
            }
            if (!existing.isTopGroup()) {
                requireUniqueName(requireAccount(parentId == null ? existing.parentId() : parentId), name, id);
            }
            Account updated = new Account(id, code, name, existing.type(),
                    parentId == null ? existing.parentId() : parentId, existing.group(),
                    request.cashFlowClass() == null ? existing.cashFlowClass() : request.cashFlowClass(),
                    existing.systemKey(), blankToNull(request.description()), existing.status());
            accountRepository.update(updated, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_UPDATED, String.valueOf(id),
                    updated.displayText());
            return updated;
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void activate(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (!account.isTopGroup()) {
                Account parent = requireAccount(account.parentId());
                if (!parent.isActive()) {
                    throw new FinanceException("Activate " + parent.displayText() + " first.");
                }
            }
            accountRepository.updateStatus(id, Account.Status.ACTIVE, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_ACTIVATED, String.valueOf(id),
                    account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void deactivate(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (account.isTopGroup() || account.isSystem()) {
                throw new FinanceException(account.displayText() + " is a system account and cannot be deactivated.");
            }
            if (account.group() && accountRepository.hasActiveChildren(id)) {
                throw new FinanceException("Deactivate the accounts under " + account.displayText() + " first.");
            }
            accountRepository.updateStatus(id, Account.Status.INACTIVE, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_DEACTIVATED,
                    String.valueOf(id), account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void delete(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (account.isTopGroup() || account.isSystem()) {
                throw new FinanceException(account.displayText() + " is a system account and cannot be deleted.");
            }
            if (accountRepository.isUsed(id)) {
                throw new FinanceException(account.displayText()
                        + " has entries and cannot be deleted; deactivate it instead.");
            }
            if (account.group() && accountRepository.hasChildren(id)) {
                throw new FinanceException("Move or delete the accounts under " + account.displayText() + " first.");
            }
            accountRepository.delete(id);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_DELETED, String.valueOf(id),
                    account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to delete the account right now.", exception);
        }
    }

    /** "4001–4999" for a top group, "1201–1299" for a sub-group. */
    public static String rangeText(Account group) {
        String code = group.code();
        if (group.isTopGroup()) {
            return code.charAt(0) + "001–" + code.charAt(0) + "999";
        }
        return code.substring(0, 2) + "01–" + code.substring(0, 2) + "99";
    }

    private AccountNode node(Account account, List<Account> accounts, Map<Long, BigDecimal> netDebits) {
        List<AccountNode> children = accounts.stream()
                .filter(child -> account.id().equals(child.parentId()))
                .sorted(Comparator.comparing(Account::code))
                .map(child -> node(child, accounts, netDebits))
                .toList();
        BigDecimal balance;
        if (account.group()) {
            balance = children.stream().map(AccountNode::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            BigDecimal net = netDebits.getOrDefault(account.id(), BigDecimal.ZERO);
            balance = account.type().isDebitNormal() ? net : net.negate();
        }
        return new AccountNode(account, balance, new ArrayList<>(children));
    }

    private void validatePlacement(Account parent, String code, boolean group) {
        if (!parent.group()) {
            throw new FinanceException(parent.displayText() + " is not a group.");
        }
        if (!parent.isActive()) {
            throw new FinanceException(parent.displayText() + " is inactive.");
        }
        boolean inRange = parent.isTopGroup()
                ? code.charAt(0) == parent.code().charAt(0) && !code.equals(parent.code())
                : code.startsWith(parent.code().substring(0, 2)) && !code.equals(parent.code());
        if (!inRange) {
            throw new FinanceException("Code " + code + " is outside " + parent.displayText() + " ("
                    + rangeText(parent) + ").");
        }
        if (group && (!parent.isTopGroup() || !code.endsWith("00"))) {
            throw new FinanceException("A group must sit directly under a main group and have a code ending in 00.");
        }
    }

    private void requireUniqueName(Account parent, String name, Long exceptId) {
        boolean taken = accountRepository.findAll().stream()
                .anyMatch(account -> parent.id().equals(account.parentId())
                        && !account.id().equals(exceptId)
                        && account.name().equalsIgnoreCase(name));
        if (taken) {
            throw new FinanceException(parent.displayText() + " already has an account named " + name + ".");
        }
    }

    private Account requireParent(Long parentId) {
        if (parentId == null) {
            throw new FinanceException("Choose the group this account belongs to.");
        }
        return requireAccount(parentId);
    }

    private Account requireAccount(long id) {
        return accountRepository.findById(id).orElseThrow(() -> new FinanceException("The account could not be found."));
    }

    private static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.strip();
        if (!normalized.matches("\\d{4}")) {
            throw new FinanceException("Account code must be 4 digits.");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty()) {
            throw new FinanceException("Enter the account name.");
        }
        if (normalized.length() > MAX_NAME) {
            throw new FinanceException("The account name cannot exceed 120 characters.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AuthenticatedUser requireManage() {
        return require("finance.accounts.manage", "You do not have permission to manage accounts.");
    }

    private static AuthenticatedUser require(String permission, String message) {
        AuthenticatedUser user = AuthContext.getCurrentUser().orElseThrow(() -> new FinanceException(message));
        if (!new PermissionGuard(user).can(permission)) {
            throw new FinanceException(message);
        }
        return user;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AccountServiceTest`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/finance/AccountRequest.java src/main/java/com/churchmanagement/dto/finance/AccountNode.java src/main/java/com/churchmanagement/service/AccountService.java src/test/java/com/churchmanagement/service/AccountServiceTest.java
git commit -m "feat(finance): chart of accounts maintenance" -m "AccountService loads the chart as a tree with posted balances shown in each account's normal direction and group totals, and lets users with finance.accounts.manage create accounts (code inside the group's range, type and cash-flow class from the group, unique code and name within the group), edit them (code and group only while unused), deactivate, reactivate and delete unused ones. System accounts and main groups are protected. Every change is written to the activity log." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: JournalService

**Files:**
- Create: `dto/finance/AccountLedger.java`
- Create: `service/JournalService.java`
- Test: `src/test/java/com/churchmanagement/service/JournalServiceTest.java`

**Interfaces:**
- Consumes: `JournalRepository`, `AccountRepository`, `CollectionCategoryRepository`, `FiscalYearService`, `FinanceSettings`, `JournalValidator`, `ActivityLogService.logFinance`, `FinanceFakes`.
- Produces:
  - `record AccountLedger(Account account, BigDecimal openingBalance, List<AccountLedger.Row> rows, BigDecimal closingBalance)` with `record Row(LedgerLine line, BigDecimal balance)` (balances in the account's normal direction).
  - `JournalService(JournalRepository, AccountRepository, CollectionCategoryRepository, FiscalYearService, FinanceSettings, ActivityLogService, Clock)` and `()`:
    `JournalEntry saveDraft(JournalEntryRequest)`, `void deleteDraft(long)`, `JournalEntry post(long)`, `JournalEntry reverse(long id, LocalDate date, String reason)`, `List<JournalEntrySummary> search(JournalSearchCriteria)`, `Optional<JournalEntry> find(long)`, `AccountLedger ledger(long accountId, LocalDate from, LocalDate to)`, `boolean canPost(JournalEntry)`, `boolean canReverse(JournalEntry)`, package-private `JournalEntry postSystemEntry(JournalEntryRequest, JournalSourceType, Long sourceId)`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import com.churchmanagement.validation.JournalValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalServiceTest {
    private static final LocalDate DAY = LocalDate.of(2027, 1, 10);

    private FinanceFakes.FakeJournalRepository journalRepository;
    private FinanceFakes.FakeFiscalYearRepository fiscalYearRepository;
    private FinanceFakes.FakeFinanceSettings settings;
    private FinanceFakes.RecordingActivityLog activityLog;
    private JournalService service;

    @BeforeEach
    void setUp() {
        journalRepository = new FinanceFakes.FakeJournalRepository();
        fiscalYearRepository = new FinanceFakes.FakeFiscalYearRepository();
        settings = new FinanceFakes.FakeFinanceSettings();
        activityLog = new FinanceFakes.RecordingActivityLog();
        FinanceFakes.FakeAccountRepository accounts = new FinanceFakes.FakeAccountRepository()
                .with(new Account(1L, "1100", "Cash in Hand", AccountType.ASSET, 100L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(2L, "4400", "Rent Income", AccountType.INCOME, 400L, false));
        FinanceFakes.FakeCategoryRepository categories = new FinanceFakes.FakeCategoryRepository();
        categories.active.add(FinanceFakes.category(10L, "A"));
        service = new JournalService(journalRepository, accounts, categories, new FiscalYearService(fiscalYearRepository),
                settings, activityLog, Clock.fixed(Instant.parse("2027-01-10T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void savesDraftWithoutNumber() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));

        assertEquals(JournalStatus.DRAFT, draft.status());
        assertNull(draft.entryNo());
        assertEquals(7L, draft.preparedBy());
        assertEquals(2027, draft.fiscalYear());
        assertEquals(List.of(com.churchmanagement.service.ActivityLogService.JOURNAL_DRAFT_SAVED), activityLog.actions);
    }

    @Test
    void updatesDraft() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));

        JournalEntry updated = service.saveDraft(rentRequest(draft.id(), "750"));

        assertEquals(draft.id(), updated.id());
        assertEquals(new BigDecimal("750"), updated.totalDebit());
    }

    @Test
    void invalidDraftIsRejected() {
        assertThrows(JournalValidationException.class, () -> service.saveDraft(new JournalEntryRequest(null,
                JournalSourceType.JOURNAL, DAY, "Rent", List.of(line(1L, "500", null, null), line(2L, null, "400", 10L)))));
    }

    @Test
    void postAssignsSequentialNumbersPerYear() {
        JournalEntry first = service.post(service.saveDraft(rentRequest(null, "500")).id());
        JournalEntry second = service.post(service.saveDraft(rentRequest(null, "600")).id());

        assertEquals("JE-2027-00001", first.entryNo());
        assertEquals("JE-2027-00002", second.entryNo());
        assertEquals(JournalStatus.POSTED, second.status());
    }

    @Test
    void postedEntryCannotBeEditedOrDeleted() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        assertThrows(FinanceException.class, () -> service.saveDraft(rentRequest(posted.id(), "900")));
        assertThrows(FinanceException.class, () -> service.deleteDraft(posted.id()));
    }

    @Test
    void approvalOnPreparerCannotPost() {
        settings.approvalRequired = true;
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry", "finance.approve"));
        long id = service.saveDraft(rentRequest(null, "500")).id();

        FinanceException exception = assertThrows(FinanceException.class, () -> service.post(id));

        assertEquals("You prepared this entry; another approver must post it.", exception.getMessage());
    }

    @Test
    void approvalOnApproverPosts() {
        settings.approvalRequired = true;
        long id = service.saveDraft(rentRequest(null, "500")).id();
        AuthContext.setCurrentUser(FinanceFakes.user(8L, "finance.menu.view", "finance.approve"));

        assertEquals(JournalStatus.POSTED, service.post(id).status());
    }

    @Test
    void approvalOnEntryPermissionAloneCannotPost() {
        settings.approvalRequired = true;
        long id = service.saveDraft(rentRequest(null, "500")).id();
        AuthContext.setCurrentUser(FinanceFakes.user(9L, "finance.menu.view", "finance.entry"));

        assertThrows(FinanceException.class, () -> service.post(id));
    }

    @Test
    void closedYearIsRejected() {
        fiscalYearRepository.close(2027);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.saveDraft(rentRequest(null, "500")));

        assertEquals("The 2027 accounting year is closed.", exception.getMessage());
    }

    @Test
    void reverseSwapsSidesAndLinksOriginal() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        JournalEntry reversal = service.reverse(posted.id(), DAY.plusDays(1), "Wrong amount");

        assertEquals(JournalSourceType.REVERSAL, reversal.sourceType());
        assertEquals(posted.id(), reversal.reversalOfId());
        assertEquals(new BigDecimal("500"), reversal.lines().getFirst().credit());
        assertEquals(new BigDecimal("500"), reversal.lines().get(1).debit());
        assertEquals(Long.valueOf(10L), reversal.lines().get(1).categoryId());
        assertEquals("Reversal of JE-2027-00001 — Wrong amount", reversal.narration());
        assertTrue(reversal.isPosted());
    }

    @Test
    void entryCanBeReversedOnlyOnce() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());
        JournalEntry reversal = service.reverse(posted.id(), DAY, "Duplicate");

        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY, "Again"));
        assertThrows(FinanceException.class, () -> service.reverse(reversal.id(), DAY, "Undo"));
    }

    @Test
    void reversalNeedsReasonAndDateNotBeforeOriginal() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY, " "));
        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY.minusDays(1), "Early"));
    }

    @Test
    void draftCannotBeSavedWithoutEntryPermission() {
        AuthContext.setCurrentUser(FinanceFakes.user(3L, "finance.menu.view"));

        assertThrows(FinanceException.class, () -> service.saveDraft(rentRequest(null, "500")));
    }

    @Test
    void onlyJournalAndOpeningDraftsCanBeSaved() {
        assertThrows(FinanceException.class, () -> service.saveDraft(new JournalEntryRequest(null,
                JournalSourceType.RECEIPT, DAY, "Receipt", rentRequest(null, "1").lines())));
    }

    @Test
    void canPostAndCanReverseFollowPermissions() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));
        assertTrue(service.canPost(draft));
        settings.approvalRequired = true;
        assertFalse(service.canPost(draft));
    }

    @Test
    void ledgerRunsBalancesInNormalDirection() {
        journalRepository.openingNetDebit = new BigDecimal("-100.00");
        journalRepository.ledger.add(new LedgerLine(1L, "JE-2027-00001", DAY, "Rent", null, "A", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
        journalRepository.ledger.add(new LedgerLine(2L, "JE-2027-00002", DAY, "Refund", null, "A", null,
                new BigDecimal("50.00"), BigDecimal.ZERO));

        AccountLedger ledger = service.ledger(2L, DAY, DAY);

        assertEquals(new BigDecimal("100.00"), ledger.openingBalance());
        assertEquals(new BigDecimal("600.00"), ledger.rows().getFirst().balance());
        assertEquals(new BigDecimal("550.00"), ledger.closingBalance());
    }

    private static JournalEntryRequest rentRequest(Long id, String amount) {
        return new JournalEntryRequest(id, JournalSourceType.JOURNAL, DAY, "Hall rent",
                List.of(line(1L, amount, null, null), line(2L, null, amount, 10L)));
    }

    private static JournalLineRequest line(long accountId, String debit, String credit, Long categoryId) {
        return new JournalLineRequest(accountId, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), categoryId, null, null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=JournalServiceTest`
Expected: compilation FAILURE — `cannot find symbol: class JournalService`.

- [ ] **Step 3: Implement**

`dto/finance/AccountLedger.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;

import java.math.BigDecimal;
import java.util.List;

/** Posted movements on one account with running balances in the account's normal direction. */
public record AccountLedger(Account account, BigDecimal openingBalance, List<Row> rows, BigDecimal closingBalance) {
    public record Row(LedgerLine line, BigDecimal balance) {
    }
}
```

`service/JournalService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.validation.JournalValidator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The only way journal entries are saved, posted and reversed. */
public class JournalService {
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final FiscalYearService fiscalYearService;
    private final FinanceSettings financeSettings;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public JournalService() {
        this(new JournalRepository(), new AccountRepository(), new CollectionCategoryRepository(),
                new FiscalYearService(), new FinanceSettings(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public JournalService(JournalRepository journalRepository, AccountRepository accountRepository,
                          CollectionCategoryRepository categoryRepository, FiscalYearService fiscalYearService,
                          FinanceSettings financeSettings, ActivityLogService activityLogService, Clock clock) {
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.fiscalYearService = fiscalYearService;
        this.financeSettings = financeSettings;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public JournalEntry saveDraft(JournalEntryRequest request) {
        AuthenticatedUser user = require("finance.entry", "You do not have permission to enter journal entries.");
        if (request.sourceType() != JournalSourceType.JOURNAL && request.sourceType() != JournalSourceType.OPENING) {
            throw new FinanceException("Only journal and opening-balance entries can be saved as drafts.");
        }
        try {
            FiscalYear fiscalYear = validate(request);
            LocalDateTime now = LocalDateTime.now(clock);
            long id;
            if (request.id() == null) {
                id = journalRepository.insertDraft(toEntry(request, fiscalYear.year(), user.getUserId(), now, null));
            } else {
                JournalEntry existing = requireEntry(request.id());
                if (existing.isPosted()) {
                    throw new FinanceException("Posted entries cannot be edited; reverse them instead.");
                }
                if (existing.sourceType() != request.sourceType()) {
                    throw new FinanceException("This draft belongs to a different screen.");
                }
                journalRepository.updateDraft(new JournalEntry(existing.id(), null, request.entryDate(),
                        fiscalYear.year(), request.narration().strip(), existing.sourceType(), existing.sourceId(),
                        JournalStatus.DRAFT, null, existing.preparedBy(), existing.preparedAt(), null, null,
                        toLines(request.lines())));
                id = existing.id();
            }
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_DRAFT_SAVED, String.valueOf(id),
                    request.narration().strip());
            return requireEntry(id);
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the entry right now.", exception);
        }
    }

    public void deleteDraft(long id) {
        AuthenticatedUser user = require("finance.entry", "You do not have permission to delete journal entries.");
        try {
            JournalEntry existing = requireEntry(id);
            if (existing.isPosted()) {
                throw new FinanceException("Posted entries cannot be deleted; reverse them instead.");
            }
            journalRepository.deleteDraft(id);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_DRAFT_DELETED,
                    String.valueOf(id), existing.narration());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to delete the entry right now.", exception);
        }
    }

    public JournalEntry post(long id) {
        AuthenticatedUser user = currentUser("You do not have permission to post journal entries.");
        try {
            JournalEntry entry = requireEntry(id);
            if (entry.isPosted()) {
                throw new FinanceException("This entry is already posted.");
            }
            requirePostPermission(user, entry);
            validate(toRequest(entry));
            String entryNo = journalRepository.post(id, user.getUserId(), LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_POSTED, String.valueOf(id),
                    entryNo + " " + entry.narration());
            return requireEntry(id);
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the entry right now.", exception);
        }
    }

    public JournalEntry reverse(long id, LocalDate date, String reason) {
        AuthenticatedUser user = financeSettings.approvalRequired()
                ? require("finance.approve", "You do not have permission to reverse entries.")
                : require("finance.entry", "You do not have permission to reverse entries.");
        if (reason == null || reason.isBlank()) {
            throw new FinanceException("Enter the reason for the reversal.");
        }
        try {
            JournalEntry original = requireEntry(id);
            if (!original.isPosted()) {
                throw new FinanceException("Only posted entries can be reversed; delete the draft instead.");
            }
            if (original.sourceType() == JournalSourceType.REVERSAL) {
                throw new FinanceException("A reversal cannot be reversed; enter a new entry instead.");
            }
            if (journalRepository.isReversed(id)) {
                throw new FinanceException(original.entryNo() + " has already been reversed.");
            }
            if (date == null || date.isBefore(original.entryDate())) {
                throw new FinanceException("The reversal date cannot be before " + original.entryDate() + ".");
            }
            List<JournalLineRequest> swapped = original.lines().stream()
                    .map(line -> new JournalLineRequest(line.accountId(), line.credit(), line.debit(),
                            line.categoryId(), line.churchId(), line.memo()))
                    .toList();
            JournalEntryRequest request = new JournalEntryRequest(null, JournalSourceType.REVERSAL, date,
                    "Reversal of " + original.entryNo() + " — " + reason.strip(), swapped);
            FiscalYear fiscalYear = validate(request);
            LocalDateTime now = LocalDateTime.now(clock);
            PostedEntry posted = journalRepository.insertPosted(
                    toEntry(request, fiscalYear.year(), user.getUserId(), now, original.id()), user.getUserId(), now);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_REVERSED,
                    String.valueOf(original.id()), posted.entryNo() + " reverses " + original.entryNo());
            return requireEntry(posted.id());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to reverse the entry right now.", exception);
        }
    }

    public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
        require("finance.menu.view", "You do not have permission to view journal entries.");
        try {
            return journalRepository.search(criteria);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load journal entries right now.", exception);
        }
    }

    public Optional<JournalEntry> find(long id) {
        require("finance.menu.view", "You do not have permission to view journal entries.");
        try {
            return journalRepository.findById(id);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the entry right now.", exception);
        }
    }

    public AccountLedger ledger(long accountId, LocalDate from, LocalDate to) {
        require("finance.menu.view", "You do not have permission to view the ledger.");
        if (from == null || to == null || to.isBefore(from)) {
            throw new FinanceException("Choose a valid date range.");
        }
        try {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new FinanceException("The account could not be found."));
            boolean debitNormal = account.type().isDebitNormal();
            BigDecimal opening = normal(journalRepository.netDebitBefore(accountId, from), debitNormal);
            BigDecimal running = opening;
            List<AccountLedger.Row> rows = new ArrayList<>();
            for (LedgerLine line : journalRepository.ledgerLines(accountId, from, to)) {
                running = running.add(normal(line.debit().subtract(line.credit()), debitNormal));
                rows.add(new AccountLedger.Row(line, running));
            }
            return new AccountLedger(account, opening, rows, running);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the ledger right now.", exception);
        }
    }

    /** Whether the signed-in user may post this draft under the current approval setting. */
    public boolean canPost(JournalEntry entry) {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty() || entry.isPosted()) {
            return false;
        }
        PermissionGuard guard = new PermissionGuard(user.get());
        if (financeSettings.approvalRequired()) {
            return guard.can("finance.approve") && !Long.valueOf(user.get().getUserId()).equals(entry.preparedBy());
        }
        return guard.can("finance.entry");
    }

    public boolean canReverse(JournalEntry entry) {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty() || !entry.isPosted() || entry.sourceType() == JournalSourceType.REVERSAL) {
            return false;
        }
        return new PermissionGuard(user.get()).can(financeSettings.approvalRequired() ? "finance.approve" : "finance.entry");
    }

    /** For F2/F3 documents: validates and posts in one step (the document carries its own approval). */
    JournalEntry postSystemEntry(JournalEntryRequest request, JournalSourceType sourceType, Long sourceId) {
        AuthenticatedUser user = currentUser("Please sign in.");
        try {
            JournalEntryRequest typed = new JournalEntryRequest(null, sourceType, request.entryDate(),
                    request.narration(), request.lines());
            FiscalYear fiscalYear = validate(typed);
            LocalDateTime now = LocalDateTime.now(clock);
            JournalEntry entry = toEntry(typed, fiscalYear.year(), user.getUserId(), now, null);
            JournalEntry withSource = new JournalEntry(null, null, entry.entryDate(), entry.fiscalYear(),
                    entry.narration(), sourceType, sourceId, JournalStatus.DRAFT, null, entry.preparedBy(),
                    entry.preparedAt(), null, null, entry.lines());
            PostedEntry posted = journalRepository.insertPosted(withSource, user.getUserId(), now);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.JOURNAL_POSTED,
                    String.valueOf(posted.id()), posted.entryNo() + " " + sourceType);
            return requireEntry(posted.id());
        } catch (JournalStateException exception) {
            throw new FinanceException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the entry right now.", exception);
        }
    }

    private FiscalYear validate(JournalEntryRequest request) {
        Map<Long, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));
        Set<Long> categoryIds = categoryRepository.findActive().stream()
                .map(CollectionCategory::getId)
                .collect(Collectors.toSet());
        JournalValidator.validate(request, accounts, categoryIds, financeSettings.goLiveDate());
        return fiscalYearService.requireOpen(request.entryDate());
    }

    private void requirePostPermission(AuthenticatedUser user, JournalEntry entry) {
        PermissionGuard guard = new PermissionGuard(user);
        if (financeSettings.approvalRequired()) {
            if (!guard.can("finance.approve")) {
                throw new FinanceException("Approval is required; ask an approver to post this entry.");
            }
            if (Long.valueOf(user.getUserId()).equals(entry.preparedBy())) {
                throw new FinanceException("You prepared this entry; another approver must post it.");
            }
        } else if (!guard.can("finance.entry")) {
            throw new FinanceException("You do not have permission to post journal entries.");
        }
    }

    private JournalEntry requireEntry(long id) {
        return journalRepository.findById(id).orElseThrow(() -> new FinanceException("The entry could not be found."));
    }

    private static JournalEntry toEntry(JournalEntryRequest request, int fiscalYear, long userId, LocalDateTime now,
                                        Long reversalOfId) {
        return new JournalEntry(null, null, request.entryDate(), fiscalYear, request.narration().strip(),
                request.sourceType(), null, JournalStatus.DRAFT, reversalOfId, userId, now, null, null,
                toLines(request.lines()));
    }

    private static List<JournalLine> toLines(List<JournalLineRequest> lines) {
        List<JournalLine> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            JournalLineRequest line = lines.get(index);
            result.add(new JournalLine(null, index + 1, line.accountId(), line.debitOrZero(), line.creditOrZero(),
                    line.categoryId(), line.churchId(),
                    line.memo() == null || line.memo().isBlank() ? null : line.memo().strip()));
        }
        return result;
    }

    private static JournalEntryRequest toRequest(JournalEntry entry) {
        return new JournalEntryRequest(entry.id(), entry.sourceType(), entry.entryDate(), entry.narration(),
                entry.lines().stream()
                        .map(line -> new JournalLineRequest(line.accountId(), line.debit(), line.credit(),
                                line.categoryId(), line.churchId(), line.memo()))
                        .toList());
    }

    private static BigDecimal normal(BigDecimal netDebit, boolean debitNormal) {
        return debitNormal ? netDebit : netDebit.negate();
    }

    private static AuthenticatedUser currentUser(String message) {
        return AuthContext.getCurrentUser().orElseThrow(() -> new FinanceException(message));
    }

    private static AuthenticatedUser require(String permission, String message) {
        AuthenticatedUser user = currentUser(message);
        if (!new PermissionGuard(user).can(permission)) {
            throw new FinanceException(message);
        }
        return user;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=JournalServiceTest`
Expected: PASS (17 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/finance/AccountLedger.java src/main/java/com/churchmanagement/service/JournalService.java src/test/java/com/churchmanagement/service/JournalServiceTest.java
git commit -m "feat(finance): journal engine with drafts, posting, approval and reversals" -m "JournalService saves and updates drafts, deletes them, posts them (validator plus open-year check, then numbering in the repository transaction) and reverses posted entries with a new posted entry that swaps debits and credits, keeps categories and branches, and links back to the original. With approval required, only someone with finance.approve who did not prepare the entry can post; without it, finance.entry can post. Posted entries can never be edited or deleted, an entry can be reversed once, and a reversal cannot be reversed." -m "Also provides the account ledger with running balances in the account's normal direction, search, and postSystemEntry for documents in F2 and F3." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: OpeningBalanceService

**Files:**
- Create: `dto/finance/OpeningBalanceForm.java`
- Create: `service/OpeningBalanceService.java`
- Test: `src/test/java/com/churchmanagement/service/OpeningBalanceServiceTest.java`

**Interfaces:**
- Consumes: `JournalService.saveDraft`, `post`; `JournalRepository.findLiveOpeningEntry`; `AccountRepository.findAll`, `findBySystemKey("ACCUMULATED_FUND")`; `CollectionCategoryRepository.findActive`; `FinanceSettings.goLiveDate`.
- Produces:
  - `record OpeningBalanceForm(LocalDate goLiveDate, List<Account> balanceAccounts, List<CollectionCategory> categories, Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts, JournalEntry entry)` (`entry` null when nothing entered).
  - `OpeningBalanceService(JournalService, JournalRepository, AccountRepository, CollectionCategoryRepository, FinanceSettings)` and `()`: `OpeningBalanceForm load()`, `JournalEntry saveDraft(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts)`, `JournalEntry post()`; static `List<JournalLineRequest> buildLines(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts, Map<Long, Account> accounts, long accumulatedFundId)`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpeningBalanceServiceTest {
    private FinanceFakes.FakeJournalRepository journalRepository;
    private FinanceFakes.FakeAccountRepository accounts;
    private OpeningBalanceService service;
    private JournalService journalService;

    @BeforeEach
    void setUp() {
        journalRepository = new FinanceFakes.FakeJournalRepository();
        accounts = new FinanceFakes.FakeAccountRepository()
                .with(new Account(1L, "1100", "Cash in Hand", AccountType.ASSET, 100L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(2L, "2100", "Loans Payable", AccountType.LIABILITY, 200L, false))
                .with(new Account(3L, "3100", "Accumulated Fund", AccountType.EQUITY, 300L, false,
                        CashFlowClass.FINANCING, "ACCUMULATED_FUND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(4L, "4400", "Rent Income", AccountType.INCOME, 400L, false));
        FinanceFakes.FakeCategoryRepository categories = new FinanceFakes.FakeCategoryRepository();
        categories.active.add(FinanceFakes.category(10L, "A"));
        categories.active.add(FinanceFakes.category(11L, "B"));
        FinanceFakes.FakeFinanceSettings settings = new FinanceFakes.FakeFinanceSettings();
        journalService = new JournalService(journalRepository, accounts, categories,
                new FiscalYearService(new FinanceFakes.FakeFiscalYearRepository()), settings,
                new FinanceFakes.RecordingActivityLog(),
                Clock.fixed(Instant.parse("2027-01-02T09:00:00Z"), ZoneId.of("UTC")));
        service = new OpeningBalanceService(journalService, journalRepository, accounts, categories, settings);
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void buildsDebitsForAssetsAndCreditsForLiabilitiesAndFunds() {
        Map<Long, BigDecimal> accountAmounts = new LinkedHashMap<>();
        accountAmounts.put(1L, new BigDecimal("1000"));
        accountAmounts.put(2L, new BigDecimal("300"));
        Map<Long, BigDecimal> fundAmounts = new LinkedHashMap<>();
        fundAmounts.put(10L, new BigDecimal("800"));
        fundAmounts.put(11L, new BigDecimal("-100"));

        List<JournalLineRequest> lines = OpeningBalanceService.buildLines(accountAmounts, fundAmounts,
                accounts.accounts, 3L);

        assertEquals(new BigDecimal("1000"), lines.get(0).debit());
        assertEquals(new BigDecimal("300"), lines.get(1).credit());
        assertEquals(new BigDecimal("800"), lines.get(2).credit());
        assertEquals(Long.valueOf(10L), lines.get(2).categoryId());
        assertEquals(new BigDecimal("100"), lines.get(3).debit());
        assertEquals(Long.valueOf(11L), lines.get(3).categoryId());
    }

    @Test
    void zeroAmountsAreSkipped() {
        List<JournalLineRequest> lines = OpeningBalanceService.buildLines(Map.of(1L, BigDecimal.ZERO),
                Map.of(10L, BigDecimal.ZERO), accounts.accounts, 3L);

        assertEquals(0, lines.size());
    }

    @Test
    void savesOneOpeningDraftDatedOnGoLive() {
        JournalEntry draft = service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        JournalEntry again = service.saveDraft(Map.of(1L, new BigDecimal("1200")), Map.of(10L, new BigDecimal("1200")));

        assertEquals(draft.id(), again.id());
        assertEquals(JournalSourceType.OPENING, again.sourceType());
        assertEquals(LocalDate.of(2027, 1, 1), again.entryDate());
        assertEquals(1, journalRepository.entries.size());
    }

    @Test
    void unbalancedOpeningIsRejected() {
        assertThrows(RuntimeException.class,
                () -> service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("900"))));
    }

    @Test
    void postedOpeningCannotBeReplaced() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        service.post();

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.saveDraft(Map.of(1L, new BigDecimal("5")), Map.of(10L, new BigDecimal("5"))));

        assertEquals("Opening balances are already posted (JE-2027-00001). Reverse that entry to correct them.",
                exception.getMessage());
    }

    @Test
    void afterReversalANewOpeningCanBeEntered() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        JournalEntry posted = service.post();
        journalService.reverse(posted.id(), LocalDate.of(2027, 1, 1), "Wrong cash figure");

        JournalEntry fresh = service.saveDraft(Map.of(1L, new BigDecimal("900")), Map.of(10L, new BigDecimal("900")));

        assertEquals(JournalStatus.DRAFT, fresh.status());
    }

    @Test
    void loadShowsSavedAmountsByAccountAndCategory() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000"), 2L, new BigDecimal("200")),
                Map.of(10L, new BigDecimal("800")));

        OpeningBalanceForm form = service.load();

        assertEquals(0, new BigDecimal("1000").compareTo(form.accountAmounts().get(1L)));
        assertEquals(0, new BigDecimal("200").compareTo(form.accountAmounts().get(2L)));
        assertEquals(0, new BigDecimal("800").compareTo(form.fundAmounts().get(10L)));
        assertEquals(List.of("1100", "2100"), form.balanceAccounts().stream().map(Account::code).toList());
    }

    @Test
    void loadWithoutEntryHasNoEntry() {
        assertNull(service.load().entry());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OpeningBalanceServiceTest`
Expected: compilation FAILURE — `cannot find symbol: class OpeningBalanceService`.

- [ ] **Step 3: Implement**

`dto/finance/OpeningBalanceForm.java`:

```java
package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Opening balances: an amount per asset / liability account (positive = normal side) and an
 * Accumulated Fund amount per category. {@code entry} is the live OPENING entry, or null.
 */
public record OpeningBalanceForm(LocalDate goLiveDate, List<Account> balanceAccounts,
                                 List<CollectionCategory> categories, Map<Long, BigDecimal> accountAmounts,
                                 Map<Long, BigDecimal> fundAmounts, JournalEntry entry) {
}
```

`service/OpeningBalanceService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.repository.JournalRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The single OPENING entry: asset and liability balances and the Accumulated Fund per category. */
public class OpeningBalanceService {
    private static final String ACCUMULATED_FUND = "ACCUMULATED_FUND";

    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final FinanceSettings financeSettings;

    public OpeningBalanceService() {
        this(new JournalService(), new JournalRepository(), new AccountRepository(), new CollectionCategoryRepository(),
                new FinanceSettings());
    }

    public OpeningBalanceService(JournalService journalService, JournalRepository journalRepository,
                                 AccountRepository accountRepository, CollectionCategoryRepository categoryRepository,
                                 FinanceSettings financeSettings) {
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.financeSettings = financeSettings;
    }

    public OpeningBalanceForm load() {
        try {
            List<Account> balanceAccounts = accountRepository.findAll().stream()
                    .filter(account -> !account.group() && account.isActive())
                    .filter(account -> account.type() == AccountType.ASSET || account.type() == AccountType.LIABILITY)
                    .toList();
            Account fund = accumulatedFund();
            Map<Long, Account> accountsById = accountRepository.findAll().stream()
                    .collect(Collectors.toMap(Account::id, Function.identity()));
            Optional<JournalEntry> entry = journalRepository.findLiveOpeningEntry();
            Map<Long, BigDecimal> accountAmounts = new HashMap<>();
            Map<Long, BigDecimal> fundAmounts = new HashMap<>();
            entry.ifPresent(found -> {
                for (JournalLine line : found.lines()) {
                    BigDecimal netDebit = line.debit().subtract(line.credit());
                    if (line.accountId() == fund.id()) {
                        fundAmounts.merge(line.categoryId(), netDebit.negate(), BigDecimal::add);
                    } else {
                        Account account = accountsById.get(line.accountId());
                        BigDecimal amount = account != null && account.type().isDebitNormal()
                                ? netDebit : netDebit.negate();
                        accountAmounts.merge(line.accountId(), amount, BigDecimal::add);
                    }
                }
            });
            return new OpeningBalanceForm(financeSettings.goLiveDate(), balanceAccounts, categoryRepository.findActive(),
                    accountAmounts, fundAmounts, entry.orElse(null));
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the opening balances right now.", exception);
        }
    }

    public JournalEntry saveDraft(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts) {
        try {
            Optional<JournalEntry> live = journalRepository.findLiveOpeningEntry();
            if (live.isPresent() && live.get().isPosted()) {
                throw new FinanceException("Opening balances are already posted (" + live.get().entryNo()
                        + "). Reverse that entry to correct them.");
            }
            Map<Long, Account> accounts = accountRepository.findAll().stream()
                    .collect(Collectors.toMap(Account::id, Function.identity()));
            List<JournalLineRequest> lines = buildLines(accountAmounts, fundAmounts, accounts, accumulatedFund().id());
            return journalService.saveDraft(new JournalEntryRequest(live.map(JournalEntry::id).orElse(null),
                    JournalSourceType.OPENING, financeSettings.goLiveDate(),
                    "Opening balances at " + financeSettings.goLiveDate(), lines));
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the opening balances right now.", exception);
        }
    }

    public JournalEntry post() {
        try {
            JournalEntry live = journalRepository.findLiveOpeningEntry()
                    .orElseThrow(() -> new FinanceException("Save the opening balances before posting them."));
            if (live.isPosted()) {
                throw new FinanceException("Opening balances are already posted (" + live.entryNo() + ").");
            }
            return journalService.post(live.id());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to post the opening balances right now.", exception);
        }
    }

    /** Positive amounts sit on the account's normal side; negative ones on the other side. Zero is skipped. */
    static List<JournalLineRequest> buildLines(Map<Long, BigDecimal> accountAmounts, Map<Long, BigDecimal> fundAmounts,
                                               Map<Long, Account> accounts, long accumulatedFundId) {
        List<JournalLineRequest> lines = new ArrayList<>();
        accountAmounts.forEach((accountId, amount) -> {
            Account account = accounts.get(accountId);
            if (amount != null && amount.signum() != 0 && account != null) {
                lines.add(line(accountId, amount, account.type().isDebitNormal(), null));
            }
        });
        fundAmounts.forEach((categoryId, amount) -> {
            if (amount != null && amount.signum() != 0) {
                lines.add(line(accumulatedFundId, amount, false, categoryId));
            }
        });
        return lines;
    }

    private static JournalLineRequest line(long accountId, BigDecimal amount, boolean debitNormal, Long categoryId) {
        boolean debit = debitNormal == (amount.signum() > 0);
        BigDecimal value = amount.abs();
        return new JournalLineRequest(accountId, debit ? value : null, debit ? null : value, categoryId, null,
                categoryId == null ? null : "Accumulated Fund");
    }

    private Account accumulatedFund() {
        return accountRepository.findBySystemKey(ACCUMULATED_FUND)
                .orElseThrow(() -> new FinanceException("The Accumulated Fund account is missing from the chart."));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OpeningBalanceServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/finance/OpeningBalanceForm.java src/main/java/com/churchmanagement/service/OpeningBalanceService.java src/test/java/com/churchmanagement/service/OpeningBalanceServiceTest.java
git commit -m "feat(finance): opening balances as a single OPENING entry" -m "OpeningBalanceService builds the go-live OPENING entry from a balance per asset and liability account (debits for assets, credits for liabilities, the other side for negative amounts) and the Accumulated Fund per category. It reuses one draft until it is posted through the journal engine (same balance and approval rules), refuses a second one while a posted opening entry stands, and allows a fresh one after the posted entry is reversed. load() returns the amounts already entered." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Finance module in navigation

**Files:**
- Modify: `navigation/NavigationRegistry.java`
- Modify: `controller/RolePermissionController.java` (`MODULE_DISPLAY_ORDER`)
- Modify: `src/test/java/com/churchmanagement/navigation/NavigationRegistryTest.java`
- Create (placeholders replaced in Tasks 9–12): `view/finance-journal-view.fxml`, `view/finance-accounts-view.fxml`, `view/finance-opening-balances-view.fxml`, `view/finance-ledger-view.fxml`

**Interfaces:**
- Produces: module `finance` with items `finance.journal`, `finance.accounts`, `finance.opening-balances`, `finance.ledger` (all `finance.menu.view`, activity `NAVIGATE_FINANCE`).

- [ ] **Step 1: Update the failing test**

In `NavigationRegistryTest`:
- `modulesInTileOrderWithFixedShortcuts`: expect ids `List.of("dashboard", "collections", "branches", "messaging", "finance", "administration")` and shortcuts `List.of(1, 2, 3, 4, 5, 7)`.
- `itemIdsAreUniqueAndFindable`: `assertEquals(21, ids.size());`
- add:

```java
    @Test
    void financeScreensNeedFinanceMenuPermission() {
        for (String id : List.of("finance.journal", "finance.accounts", "finance.opening-balances", "finance.ledger")) {
            assertEquals(List.of("finance.menu.view"), NavigationRegistry.findItem(id).orElseThrow().permissionCodes());
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NavigationRegistryTest`
Expected: FAIL — module list does not contain `finance`.

- [ ] **Step 3: Implement**

In `NavigationRegistry.MODULES`, insert between the Messaging and Administration modules:

```java
            new NavModule("finance", "Finance", "fas-balance-scale", "Accounts, journals and opening balances", 5,
                    List.of(
                            item("finance.journal", "General Journal",
                                    "Enter, approve, post and reverse journal entries", "finance-journal-view.fxml",
                                    "fas-book", ActivityLogService.NAVIGATE_FINANCE, null, "finance.menu.view"),
                            item("finance.accounts", "Chart of Accounts",
                                    "Add, edit and deactivate accounts; see balances", "finance-accounts-view.fxml",
                                    "fas-sitemap", ActivityLogService.NAVIGATE_FINANCE, null, "finance.menu.view"),
                            item("finance.opening-balances", "Opening Balances",
                                    "Balances and funds at the go-live date", "finance-opening-balances-view.fxml",
                                    "fas-flag-checkered", ActivityLogService.NAVIGATE_FINANCE, null,
                                    "finance.menu.view"),
                            item("finance.ledger", "Account Ledger",
                                    "Every posted movement on one account with running balance",
                                    "finance-ledger-view.fxml", "fas-stream", ActivityLogService.NAVIGATE_FINANCE,
                                    null, "finance.menu.view"))),
```

In `RolePermissionController.MODULE_DISPLAY_ORDER`, add `"Finance",` after `"Reports",`.

Create each of the four views as a placeholder so the registry test's classpath check passes (Tasks 9–12 replace them), e.g. `finance-journal-view.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.layout.VBox?>

<VBox xmlns="http://javafx.com/javafx/21" xmlns:fx="http://javafx.com/fxml/1" styleClass="module-page"/>
```

(same content for `finance-accounts-view.fxml`, `finance-opening-balances-view.fxml`, `finance-ledger-view.fxml`).

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=NavigationRegistryTest,NavigationAccessTest,LandingScreenServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/navigation/NavigationRegistry.java src/main/java/com/churchmanagement/controller/RolePermissionController.java src/test/java/com/churchmanagement/navigation/NavigationRegistryTest.java src/main/resources/com/churchmanagement/view/finance-journal-view.fxml src/main/resources/com/churchmanagement/view/finance-accounts-view.fxml src/main/resources/com/churchmanagement/view/finance-opening-balances-view.fxml src/main/resources/com/churchmanagement/view/finance-ledger-view.fxml
git commit -m "feat(finance): Finance module in the navigation" -m "Adds the Finance tile (Ctrl+5) with General Journal, Chart of Accounts, Opening Balances and Account Ledger, all behind finance.menu.view, and lists the Finance permissions as their own group in Roles & Permissions. The four screens are placeholders until their tasks land." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Chart of Accounts screen

**Files:**
- Modify (replace): `src/main/resources/com/churchmanagement/view/finance-accounts-view.fxml`
- Create: `src/main/java/com/churchmanagement/controller/FinanceAccountsController.java`

**Interfaces:**
- Consumes: `AccountService.loadChart`, `groups`, `create`, `update`, `activate`, `deactivate`, `delete`, `rangeText`; `AccountNode`, `AccountRequest`, `Account`, `CashFlowClass`; `MoneyFormat.format`; existing `ProcessingDialog`, `DialogStyler`, `ButtonIconUtil`, `AuthContext`, `PermissionGuard`.

UI tasks have no unit test; verify by compile + the manual check.

- [ ] **Step 1: Replace the view**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.CheckBox?>
<?import javafx.scene.control.TreeTableColumn?>
<?import javafx.scene.control.TreeTableView?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="14" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.FinanceAccountsController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>
    <HBox alignment="CENTER_LEFT" spacing="10">
        <CheckBox fx:id="showInactiveCheckBox" onAction="#handleRefresh" text="Show inactive"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Button fx:id="addButton" onAction="#handleAdd" styleClass="primary-button" text="Add account"/>
        <Button fx:id="editButton" onAction="#handleEdit" text="Edit"/>
        <Button fx:id="statusButton" onAction="#handleToggleStatus" text="Deactivate"/>
        <Button fx:id="deleteButton" onAction="#handleDelete" text="Delete"/>
        <Button fx:id="refreshButton" onAction="#handleRefresh" text="Refresh"/>
    </HBox>
    <TreeTableView fx:id="accountTree" showRoot="false" VBox.vgrow="ALWAYS">
        <columns>
            <TreeTableColumn fx:id="codeColumn" prefWidth="110" text="Code"/>
            <TreeTableColumn fx:id="nameColumn" prefWidth="260" text="Name"/>
            <TreeTableColumn fx:id="typeColumn" prefWidth="110" text="Type"/>
            <TreeTableColumn fx:id="cashFlowColumn" prefWidth="110" text="Cash flow"/>
            <TreeTableColumn fx:id="balanceColumn" prefWidth="140" text="Balance"/>
            <TreeTableColumn fx:id="statusColumn" prefWidth="90" text="Status"/>
        </columns>
        <columnResizePolicy>
            <TreeTableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
        </columnResizePolicy>
    </TreeTableView>
</VBox>
```

- [ ] **Step 2: Create the controller**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.FinanceException;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;

import java.util.List;

public class FinanceAccountsController {
    @FXML private CheckBox showInactiveCheckBox;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button statusButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;
    @FXML private TreeTableView<AccountNode> accountTree;
    @FXML private TreeTableColumn<AccountNode, String> codeColumn;
    @FXML private TreeTableColumn<AccountNode, String> nameColumn;
    @FXML private TreeTableColumn<AccountNode, String> typeColumn;
    @FXML private TreeTableColumn<AccountNode, String> cashFlowColumn;
    @FXML private TreeTableColumn<AccountNode, String> balanceColumn;
    @FXML private TreeTableColumn<AccountNode, String> statusColumn;

    private final AccountService accountService = new AccountService();
    private boolean canManage;

    @FXML
    private void initialize() {
        canManage = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.accounts.manage"))
                .orElse(false);
        for (Button button : List.of(addButton, editButton, statusButton, deleteButton)) {
            button.setVisible(canManage);
            button.setManaged(canManage);
        }
        ButtonIconUtil.applyIcon(addButton, "fas-plus");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
        configureColumns();
        accountTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                updateButtons());
        accountTree.setRowFactory(tree -> {
            TreeTableRow<AccountNode> row = new TreeTableRow<>() {
                @Override
                protected void updateItem(AccountNode item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("muted-label");
                    if (!empty && item != null && !item.account().isActive()) {
                        getStyleClass().add("muted-label");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && canManage) {
                    handleEdit();
                }
            });
            return row;
        });
        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
        boolean includeInactive = showInactiveCheckBox.isSelected();
        ProcessingDialog.run("Chart of Accounts", "Loading accounts...",
                () -> accountService.loadChart(includeInactive),
                nodes -> {
                    TreeItem<AccountNode> root = new TreeItem<>();
                    nodes.forEach(node -> root.getChildren().add(treeItem(node)));
                    accountTree.setRoot(root);
                    updateButtons();
                },
                throwable -> showError(throwable.getMessage()));
    }

    @FXML
    private void handleAdd() {
        AccountNode selected = selectedNode();
        Account parent = selected == null ? null
                : selected.account().group() ? selected.account() : findParent(selected);
        showAccountDialog(null, parent);
    }

    @FXML
    private void handleEdit() {
        AccountNode selected = selectedNode();
        if (selected != null) {
            showAccountDialog(selected.account(), findParent(selected));
        }
    }

    @FXML
    private void handleToggleStatus() {
        AccountNode selected = selectedNode();
        if (selected == null) {
            return;
        }
        Account account = selected.account();
        run(account.isActive() ? "Deactivating account..." : "Activating account...", () -> {
            if (account.isActive()) {
                accountService.deactivate(account.id());
            } else {
                accountService.activate(account.id());
            }
        });
    }

    @FXML
    private void handleDelete() {
        AccountNode selected = selectedNode();
        if (selected == null) {
            return;
        }
        Alert confirm = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        confirm.setTitle("Delete account");
        confirm.setHeaderText("Delete " + selected.account().displayText() + "?");
        confirm.setContentText("Only accounts that have never been used can be deleted.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            run("Deleting account...", () -> accountService.delete(selected.account().id()));
        }
    }

    private void showAccountDialog(Account existing, Account parent) {
        boolean editing = existing != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Edit account" : "Add account");
        dialog.setHeaderText(editing ? existing.displayText() : "New account");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        ComboBox<Account> groupCombo = new ComboBox<>();
        groupCombo.getItems().setAll(accountService.groups());
        groupCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.displayText() + "  (" + AccountService.rangeText(account) + ")";
            }

            @Override
            public Account fromString(String text) {
                return null;
            }
        });
        groupCombo.setMaxWidth(Double.MAX_VALUE);
        if (parent != null) {
            groupCombo.getItems().stream().filter(group -> group.id().equals(parent.id())).findFirst()
                    .ifPresent(groupCombo::setValue);
        }
        CheckBox groupCheckBox = new CheckBox("This is a group (it holds other accounts)");
        TextField codeField = new TextField(editing ? existing.code() : "");
        codeField.setPromptText("4 digits, e.g. 1201");
        TextField nameField = new TextField(editing ? existing.name() : "");
        ComboBox<CashFlowClass> cashFlowCombo = new ComboBox<>();
        cashFlowCombo.getItems().setAll(CashFlowClass.values());
        cashFlowCombo.setPromptText("Same as group");
        cashFlowCombo.setMaxWidth(Double.MAX_VALUE);
        TextField descriptionField = new TextField(editing && existing.description() != null ? existing.description() : "");

        if (editing) {
            groupCheckBox.setSelected(existing.group());
            groupCheckBox.setDisable(true);
            cashFlowCombo.setValue(existing.cashFlowClass());
            if (existing.isTopGroup()) {
                groupCombo.setDisable(true);
                codeField.setDisable(true);
            }
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER_LEFT);
        grid.add(DialogStyler.fieldLabel("Group"), 0, 0);
        grid.add(groupCombo, 1, 0);
        grid.add(groupCheckBox, 1, 1);
        grid.add(DialogStyler.fieldLabel("Code"), 0, 2);
        grid.add(codeField, 1, 2);
        grid.add(DialogStyler.fieldLabel("Name"), 0, 3);
        grid.add(nameField, 1, 3);
        grid.add(DialogStyler.fieldLabel("Cash flow"), 0, 4);
        grid.add(cashFlowCombo, 1, 4);
        grid.add(DialogStyler.fieldLabel("Description"), 0, 5);
        grid.add(descriptionField, 1, 5);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Account group = groupCombo.getValue();
            AccountRequest request = new AccountRequest(codeField.getText(), nameField.getText(),
                    group == null ? null : group.id(), groupCheckBox.isSelected(), cashFlowCombo.getValue(),
                    descriptionField.getText());
            try {
                if (editing) {
                    accountService.update(existing.id(), request);
                } else {
                    accountService.create(request);
                }
                dialog.close();
                handleRefresh();
            } catch (FinanceException exception) {
                showError(exception.getMessage());
            }
        });
        dialog.showAndWait();
    }

    private void configureColumns() {
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue().account().code()));
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue().account().name()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getValue().account().group() ? "Group" : label(cell.getValue().getValue().account().type().name())));
        cashFlowColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                label(cell.getValue().getValue().account().cashFlowClass().name())));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                MoneyFormat.format(cell.getValue().getValue().balance())));
        balanceColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getValue().account().isActive() ? "Active" : "Inactive"));
    }

    private TreeItem<AccountNode> treeItem(AccountNode node) {
        TreeItem<AccountNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        node.children().forEach(child -> item.getChildren().add(treeItem(child)));
        return item;
    }

    private void updateButtons() {
        AccountNode selected = selectedNode();
        boolean none = selected == null;
        editButton.setDisable(none);
        deleteButton.setDisable(none || selected.account().isSystem() || selected.account().isTopGroup());
        statusButton.setDisable(none || selected.account().isSystem() || selected.account().isTopGroup());
        statusButton.setText(!none && !selected.account().isActive() ? "Activate" : "Deactivate");
    }

    private AccountNode selectedNode() {
        TreeItem<AccountNode> item = accountTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private Account findParent(AccountNode node) {
        TreeItem<AccountNode> item = accountTree.getSelectionModel().getSelectedItem();
        if (item == null || item.getParent() == null || item.getParent().getValue() == null) {
            return null;
        }
        return item.getParent().getValue().account();
    }

    private void run(String message, Runnable action) {
        ProcessingDialog.run("Chart of Accounts", message, action::run, this::handleRefresh,
                throwable -> showError(throwable.getMessage()));
    }

    private static String label(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Chart of Accounts");
        alert.setHeaderText("Chart of Accounts");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: succeeds. (`ProcessingDialog.run(String, String, ThrowingRunnable, Runnable, Consumer<Throwable>)` is the void overload; `action::run` adapts a `Runnable`.)

- [ ] **Step 4: Manual check**

As Admin: Finance › Chart of Accounts shows the five groups expanded with the seeded accounts; select "1200 Bank Accounts" → Add account → code 1201, name "BOC Current" → Save → it appears under 1200 with cash flow "Cash"; code 1301 under 1200 is rejected with the range message; Deactivate / Activate toggles; Delete removes an unused account; system accounts (1100, 3100, 4100–4300) cannot be deactivated or deleted; a user without `finance.accounts.manage` sees no edit buttons.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/finance-accounts-view.fxml src/main/java/com/churchmanagement/controller/FinanceAccountsController.java
git commit -m "feat(finance): Chart of Accounts screen" -m "A tree of the chart by group with code, name, type, cash-flow class, posted balance and status, and (with finance.accounts.manage) Add account, Edit, Deactivate / Activate and Delete. Adding from a selected group preselects it and the dialog shows each group's code range. Inactive accounts are muted and hidden unless 'Show inactive' is ticked." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: General Journal screen and entry dialog

**Files:**
- Modify (replace): `src/main/resources/com/churchmanagement/view/finance-journal-view.fxml`
- Create: `src/main/java/com/churchmanagement/controller/FinanceJournalController.java`
- Create: `src/main/java/com/churchmanagement/controller/JournalEntryDialog.java`

**Interfaces:**
- Consumes: `JournalService` (`search`, `find`, `saveDraft`, `deleteDraft`, `post`, `reverse`, `canPost`, `canReverse`); `AccountService.postingAccounts`; `CollectionCategoryService.findActive`; `ChurchRepository.findOperationalChurches`; `MoneyFormat`; `ComboBoxUtil.makeSearchable`.
- Produces: `JournalEntryDialog.show(JournalEntry entry, Runnable onChanged)` — `entry == null` opens a new JOURNAL entry; posted entries open read-only. Used again by Task 12.

- [ ] **Step 1: Replace the view**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.DatePicker?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.Pagination?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="14" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.FinanceJournalController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>
    <HBox alignment="CENTER_LEFT" spacing="10">
        <Label text="From"/>
        <DatePicker fx:id="fromDatePicker" prefWidth="140"/>
        <Label text="To"/>
        <DatePicker fx:id="toDatePicker" prefWidth="140"/>
        <ComboBox fx:id="statusComboBox" prefWidth="120"/>
        <ComboBox fx:id="sourceComboBox" prefWidth="150"/>
        <TextField fx:id="searchField" HBox.hgrow="ALWAYS" onAction="#handleSearch" promptText="Entry no or narration"/>
        <Button fx:id="searchButton" onAction="#handleSearch" styleClass="primary-button" text="Search"/>
        <Region/>
        <Button fx:id="newButton" onAction="#handleNew" styleClass="primary-button" text="New entry"/>
    </HBox>
    <TableView fx:id="entryTable" VBox.vgrow="ALWAYS">
        <columns>
            <TableColumn fx:id="entryNoColumn" prefWidth="130" text="Entry No"/>
            <TableColumn fx:id="dateColumn" prefWidth="100" text="Date"/>
            <TableColumn fx:id="narrationColumn" prefWidth="300" text="Narration"/>
            <TableColumn fx:id="sourceColumn" prefWidth="110" text="Source"/>
            <TableColumn fx:id="totalColumn" prefWidth="130" text="Amount"/>
            <TableColumn fx:id="statusColumn" prefWidth="90" text="Status"/>
            <TableColumn fx:id="preparedByColumn" prefWidth="130" text="Prepared by"/>
            <TableColumn fx:id="postedByColumn" prefWidth="130" text="Posted by"/>
        </columns>
        <columnResizePolicy>
            <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
        </columnResizePolicy>
    </TableView>
    <HBox alignment="CENTER_LEFT" spacing="10" styleClass="table-pagination-bar">
        <Label styleClass="pagination-label" text="Entries per page"/>
        <ComboBox fx:id="itemsPerPageComboBox" prefWidth="76" styleClass="items-per-page-combo"/>
        <Label fx:id="paginationSummaryLabel" styleClass="pagination-summary"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Pagination fx:id="entryPagination" maxHeight="48"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create `JournalEntryDialog`**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.service.FinanceSettings;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Create, edit, post, delete and reverse a journal entry. Posted entries open read-only. */
public final class JournalEntryDialog {
    private final JournalService journalService = new JournalService();
    private final List<Account> accounts;
    private final Map<Long, Account> accountsById;
    private final List<CollectionCategory> categories;
    private final List<Church> churches;
    private final List<LineRow> rows = new ArrayList<>();
    private final VBox linesBox = new VBox(6);
    private final Label totalsLabel = new Label();
    private final Runnable onChanged;
    private JournalEntry entry;
    private boolean readOnly;
    private DatePicker datePicker;
    private TextField narrationField;

    private JournalEntryDialog(JournalEntry entry, Runnable onChanged) {
        this.entry = entry;
        this.onChanged = onChanged;
        this.accounts = new AccountService().postingAccounts();
        this.accountsById = accounts.stream().collect(Collectors.toMap(Account::id, Function.identity()));
        this.categories = new CollectionCategoryService().findActive();
        this.churches = new ChurchRepository().findOperationalChurches();
        this.readOnly = entry != null && (entry.isPosted() || (entry.sourceType() != JournalSourceType.JOURNAL));
    }

    public static void show(JournalEntry entry, Runnable onChanged) {
        new JournalEntryDialog(entry, onChanged).open();
    }

    private void open() {
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Journal entry");
        dialog.setHeaderText(entry == null ? "New journal entry"
                : entry.isPosted() ? entry.entryNo() + " (posted)" : "Draft journal entry");

        datePicker = new DatePicker(entry == null ? LocalDate.now() : entry.entryDate());
        narrationField = new TextField(entry == null ? "" : entry.narration());
        narrationField.setPrefColumnCount(50);
        GridPane header = new GridPane();
        header.setHgap(12);
        header.setVgap(8);
        header.add(DialogStyler.fieldLabel("Date"), 0, 0);
        header.add(datePicker, 1, 0);
        header.add(DialogStyler.fieldLabel("Narration"), 0, 1);
        header.add(narrationField, 1, 1);

        HBox columnHeader = new HBox(6, fixed("Account", 260), fixed("Debit", 110), fixed("Credit", 110),
                fixed("Category", 130), fixed("Branch", 170), fixed("Memo", 170));
        columnHeader.getStyleClass().add("field-label");
        if (entry == null) {
            addRow(null);
            addRow(null);
        } else {
            entry.lines().forEach(this::addRow);
        }
        Button addLineButton = new Button("Add line");
        addLineButton.setOnAction(event -> addRow(null));
        addLineButton.setVisible(!readOnly);
        addLineButton.setManaged(!readOnly);

        ScrollPane linesScroll = new ScrollPane(linesBox);
        linesScroll.setFitToWidth(true);
        linesScroll.setPrefViewportHeight(280);
        VBox content = new VBox(12, header, columnHeader, linesScroll, new HBox(12, addLineButton, totalsLabel));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(1040);

        ButtonType saveType = new ButtonType("Save draft", ButtonBar.ButtonData.LEFT);
        ButtonType postType = new ButtonType("Post", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteType = new ButtonType("Delete draft", ButtonBar.ButtonData.LEFT);
        ButtonType reverseType = new ButtonType("Reverse…", ButtonBar.ButtonData.OTHER);
        boolean canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        if (!readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(saveType);
        }
        if (entry != null && !entry.isPosted() && !readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(deleteType);
        }
        boolean approvalRequired = new FinanceSettings().approvalRequired();
        if (!readOnly && (entry == null ? canEnter && !approvalRequired : journalService.canPost(entry))) {
            dialog.getDialogPane().getButtonTypes().add(postType);
        }
        if (entry != null && journalService.canReverse(entry)) {
            dialog.getDialogPane().getButtonTypes().add(reverseType);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        setEditable(!readOnly);

        action(dialog, saveType, () -> {
            entry = journalService.saveDraft(request());
            dialog.setHeaderText("Draft journal entry (saved)");
        });
        action(dialog, postType, () -> {
            if (canEnter) {
                entry = journalService.saveDraft(request()); // keep the approver's own edits, if any
            }
            entry = journalService.post(entry.id());
            info("Posted as " + entry.entryNo() + ".");
            dialog.close();
        });
        action(dialog, deleteType, () -> {
            journalService.deleteDraft(entry.id());
            dialog.close();
        });
        action(dialog, reverseType, () -> {
            TextInputDialog reasonDialog = DialogStyler.apply(new TextInputDialog());
            reasonDialog.setTitle("Reverse " + entry.entryNo());
            reasonDialog.setHeaderText("The reversal is posted today with debits and credits swapped.");
            reasonDialog.setContentText("Reason:");
            reasonDialog.showAndWait().ifPresent(reason -> {
                JournalEntry reversal = journalService.reverse(entry.id(), LocalDate.now(), reason);
                info("Reversed by " + reversal.entryNo() + ".");
                dialog.close();
            });
        });
        updateTotals();
        dialog.showAndWait();
    }

    private void action(Dialog<ButtonType> dialog, ButtonType type, Runnable work) {
        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        if (button == null) {
            return;
        }
        button.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try {
                work.run();
                onChanged.run();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                error(exception.getMessage());
            } catch (RuntimeException exception) {
                error(exception.getMessage() == null ? "Action failed. Please try again." : exception.getMessage());
            }
        });
    }

    private JournalEntryRequest request() {
        List<JournalLineRequest> lines = new ArrayList<>();
        for (LineRow row : rows) {
            if (row.isBlank()) {
                continue;
            }
            lines.add(new JournalLineRequest(
                    row.account.getValue() == null ? null : row.account.getValue().id(),
                    MoneyFormat.parse(row.debit.getText()), MoneyFormat.parse(row.credit.getText()),
                    row.category.getValue() == null ? null : row.category.getValue().getId(),
                    row.branch.getValue() == null ? null : row.branch.getValue().getId(),
                    row.memo.getText()));
        }
        return new JournalEntryRequest(entry == null ? null : entry.id(), JournalSourceType.JOURNAL,
                datePicker.getValue(), narrationField.getText(), lines);
    }

    private void addRow(JournalLine line) {
        LineRow row = new LineRow(line);
        rows.add(row);
        linesBox.getChildren().add(row.node);
        row.setEditable(!readOnly);
        updateTotals();
    }

    private void setEditable(boolean editable) {
        datePicker.setDisable(!editable);
        narrationField.setEditable(editable);
        rows.forEach(row -> row.setEditable(editable));
    }

    private void updateTotals() {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (LineRow row : rows) {
            debit = debit.add(row.amount(row.debit));
            credit = credit.add(row.amount(row.credit));
        }
        BigDecimal difference = debit.subtract(credit);
        totalsLabel.getStyleClass().remove("error-label");
        if (difference.signum() == 0) {
            totalsLabel.setText("Debits " + MoneyFormat.format(debit) + "   Credits " + MoneyFormat.format(credit)
                    + "   Balanced");
        } else {
            totalsLabel.setText("Debits " + MoneyFormat.format(debit) + "   Credits " + MoneyFormat.format(credit)
                    + "   Out of balance by " + MoneyFormat.format(difference.abs()));
            totalsLabel.getStyleClass().add("error-label");
        }
    }

    private static Label fixed(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        return label;
    }

    private void info(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Journal entry");
        alert.setHeaderText("Journal entry");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void error(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Journal entry");
        alert.setHeaderText("Journal entry");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** One editable line: account, debit, credit, category, branch, memo, remove. */
    private final class LineRow {
        private final ComboBox<Account> account = new ComboBox<>();
        private final TextField debit = new TextField();
        private final TextField credit = new TextField();
        private final ComboBox<CollectionCategory> category = new ComboBox<>(FXCollections.observableArrayList(categories));
        private final ComboBox<Church> branch = new ComboBox<>();
        private final TextField memo = new TextField();
        private final Button remove = new Button("✕");
        private final HBox node;

        private LineRow(JournalLine line) {
            account.setItems(new FilteredList<>(FXCollections.observableArrayList(accounts), item -> true));
            ComboBoxUtil.makeSearchable(account, Account::displayText);
            account.setPrefWidth(260);
            ComboBoxUtil.makeChurchSearchable(branch, FXCollections.observableArrayList(churches));
            branch.setPromptText("(none)");
            branch.setPrefWidth(170);
            category.setPrefWidth(130);
            category.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(CollectionCategory value) {
                    return value == null ? "" : value.displayText();
                }

                @Override
                public CollectionCategory fromString(String text) {
                    return null;
                }
            });
            debit.setPrefWidth(110);
            credit.setPrefWidth(110);
            debit.setAlignment(Pos.CENTER_RIGHT);
            credit.setAlignment(Pos.CENTER_RIGHT);
            memo.setPrefWidth(170);
            HBox.setHgrow(memo, Priority.ALWAYS);
            if (line != null) {
                account.setValue(accountsById.get(line.accountId()));
                debit.setText(line.debit().signum() > 0 ? MoneyFormat.format(line.debit()) : "");
                credit.setText(line.credit().signum() > 0 ? MoneyFormat.format(line.credit()) : "");
                categories.stream().filter(item -> item.getId().equals(line.categoryId())).findFirst()
                        .ifPresent(category::setValue);
                churches.stream().filter(item -> item.getId().equals(line.churchId())).findFirst()
                        .ifPresent(branch::setValue);
                memo.setText(line.memo() == null ? "" : line.memo());
            }
            account.valueProperty().addListener((observable, oldValue, newValue) -> refreshCategory());
            debit.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
            credit.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
            remove.setOnAction(event -> {
                rows.remove(this);
                linesBox.getChildren().remove(node);
                updateTotals();
            });
            node = new HBox(6, account, debit, credit, category, branch, memo, remove);
            node.setAlignment(Pos.CENTER_LEFT);
            refreshCategory();
        }

        private void refreshCategory() {
            boolean required = account.getValue() != null && account.getValue().type().requiresCategory();
            category.setPromptText(required ? "Required" : "(none)");
        }

        private BigDecimal amount(TextField field) {
            try {
                BigDecimal value = MoneyFormat.parse(field.getText());
                return value == null ? BigDecimal.ZERO : value;
            } catch (IllegalArgumentException exception) {
                return BigDecimal.ZERO;
            }
        }

        private boolean isBlank() {
            return account.getValue() == null && debit.getText().isBlank() && credit.getText().isBlank();
        }

        private void setEditable(boolean editable) {
            account.setDisable(!editable);
            category.setDisable(!editable);
            branch.setDisable(!editable);
            debit.setEditable(editable);
            credit.setEditable(editable);
            memo.setEditable(editable);
            remove.setVisible(editable);
        }
    }
}
```

Note: `JournalService` errors (`FinanceException`, `JournalValidationException`) are both `RuntimeException`s with user messages and are shown by `action(...)`.

- [ ] **Step 3: Create `FinanceJournalController`**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.Locale;

public class FinanceJournalController {
    private static final String ALL = "All";

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<String> sourceComboBox;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button newButton;
    @FXML private TableView<JournalEntrySummary> entryTable;
    @FXML private TableColumn<JournalEntrySummary, String> entryNoColumn;
    @FXML private TableColumn<JournalEntrySummary, String> dateColumn;
    @FXML private TableColumn<JournalEntrySummary, String> narrationColumn;
    @FXML private TableColumn<JournalEntrySummary, String> sourceColumn;
    @FXML private TableColumn<JournalEntrySummary, String> totalColumn;
    @FXML private TableColumn<JournalEntrySummary, String> statusColumn;
    @FXML private TableColumn<JournalEntrySummary, String> preparedByColumn;
    @FXML private TableColumn<JournalEntrySummary, String> postedByColumn;
    @FXML private ComboBox<Integer> itemsPerPageComboBox;
    @FXML private Label paginationSummaryLabel;
    @FXML private Pagination entryPagination;

    private final JournalService journalService = new JournalService();
    private final ObservableList<JournalEntrySummary> entries = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        boolean canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        newButton.setVisible(canEnter);
        newButton.setManaged(canEnter);
        ButtonIconUtil.applyIcon(newButton, "fas-plus");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today);
        statusComboBox.getItems().setAll(ALL, "Draft", "Posted");
        statusComboBox.setValue(ALL);
        sourceComboBox.getItems().add(ALL);
        for (JournalSourceType type : JournalSourceType.values()) {
            sourceComboBox.getItems().add(label(type.name()));
        }
        sourceComboBox.setValue(ALL);
        configureTable();
        handleSearch();
    }

    @FXML
    private void handleSearch() {
        JournalSearchCriteria criteria = new JournalSearchCriteria(fromDatePicker.getValue(), toDatePicker.getValue(),
                switch (statusComboBox.getValue()) {
                    case "Draft" -> JournalStatus.DRAFT;
                    case "Posted" -> JournalStatus.POSTED;
                    default -> null;
                },
                ALL.equals(sourceComboBox.getValue()) ? null
                        : JournalSourceType.valueOf(sourceComboBox.getValue().toUpperCase(Locale.ROOT).replace(' ', '_')),
                searchField.getText());
        ProcessingDialog.run("General Journal", "Loading entries...",
                () -> journalService.search(criteria),
                entries::setAll,
                throwable -> showError(throwable.getMessage()));
    }

    @FXML
    private void handleNew() {
        JournalEntryDialog.show(null, this::handleSearch);
    }

    private void open(JournalEntrySummary summary) {
        try {
            journalService.find(summary.id()).ifPresent(entry -> JournalEntryDialog.show(entry, this::handleSearch));
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void configureTable() {
        TablePaginationUtil.configure(entryTable, entries, entryPagination, itemsPerPageComboBox,
                paginationSummaryLabel, "entries");
        entryNoColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().entryNo() == null ? "Draft" : cell.getValue().entryNo()));
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().entryDate().toString()));
        narrationColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().narration()));
        sourceColumn.setCellValueFactory(cell -> new SimpleStringProperty(label(cell.getValue().sourceType().name())));
        totalColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyFormat.format(cell.getValue().total())));
        totalColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(label(cell.getValue().status().name())));
        preparedByColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().preparedByName()));
        postedByColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().postedByName() == null ? "" : cell.getValue().postedByName()));
        entryTable.setRowFactory(table -> {
            TableRow<JournalEntrySummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });
    }

    private static String label(String enumName) {
        String text = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("General Journal");
        alert.setHeaderText("General Journal");
        alert.setContentText(message == null ? "Unable to load entries." : message);
        alert.showAndWait();
    }
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: succeeds. (Check `TablePaginationUtil.configure(TableView, ObservableList, Pagination, ComboBox<Integer>, Label, String)` exists — it is the overload `SmsLogController` uses.)

- [ ] **Step 5: Manual check**

As Admin with approval off: New entry → date in 2027 (after setting the go-live date to today in Task 13 if testing before 2027, or temporarily via the setting), lines Dr 1100 Cash 500 / Cr 4400 Rent Income 500 with category A → the totals line reads "Balanced" → Post → "Posted as JE-…"; the list shows it; double-click opens read-only with **Reverse…** → reason → "Reversed by JE-…". An unbalanced entry shows the red difference and Post reports the validator message; an income line without a category names the line. Turn approval on: the preparer sees no Post button; another user with `finance.approve` can post.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/finance-journal-view.fxml src/main/java/com/churchmanagement/controller/FinanceJournalController.java src/main/java/com/churchmanagement/controller/JournalEntryDialog.java
git commit -m "feat(finance): General Journal screen and entry dialog" -m "The General Journal lists entries by date range, status, source and text with paging; New entry and double-click open the entry dialog. The dialog edits date, narration and lines (searchable account, debit, credit, category marked Required for income, expense and equity accounts, optional branch, memo) with live totals and an 'Out of balance by' warning, and offers Save draft, Post, Delete draft and Reverse according to the entry's state, the user's permissions and the approval setting. Posted and non-journal entries open read-only." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Opening Balances screen

**Files:**
- Modify (replace): `src/main/resources/com/churchmanagement/view/finance-opening-balances-view.fxml`
- Create: `src/main/java/com/churchmanagement/controller/FinanceOpeningBalancesController.java`

**Interfaces:**
- Consumes: `OpeningBalanceService.load`, `saveDraft`, `post`; `JournalService.canPost`, `canReverse`, `reverse`; `OpeningBalanceForm`; `MoneyFormat`.

- [ ] **Step 1: Replace the view**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.GridPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="14" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.FinanceOpeningBalancesController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>
    <Label fx:id="statusLabel" styleClass="report-card-title" wrapText="true"/>
    <ScrollPane fitToWidth="true" styleClass="transparent-scroll-pane" VBox.vgrow="ALWAYS">
        <HBox spacing="40">
            <VBox spacing="8">
                <Label styleClass="report-card-title" text="Assets and liabilities"/>
                <GridPane fx:id="accountGrid" hgap="12" vgap="6"/>
            </VBox>
            <VBox spacing="8">
                <Label styleClass="report-card-title" text="Accumulated Fund by category"/>
                <GridPane fx:id="fundGrid" hgap="12" vgap="6"/>
            </VBox>
        </HBox>
    </ScrollPane>
    <HBox alignment="CENTER_LEFT" spacing="10">
        <Label fx:id="totalsLabel"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Button fx:id="saveButton" onAction="#handleSave" text="Save draft"/>
        <Button fx:id="postButton" onAction="#handlePost" styleClass="primary-button" text="Post"/>
        <Button fx:id="reverseButton" onAction="#handleReverse" text="Reverse to correct"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create the controller**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.service.OpeningBalanceService;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class FinanceOpeningBalancesController {
    @FXML private Label statusLabel;
    @FXML private GridPane accountGrid;
    @FXML private GridPane fundGrid;
    @FXML private Label totalsLabel;
    @FXML private Button saveButton;
    @FXML private Button postButton;
    @FXML private Button reverseButton;

    private final OpeningBalanceService openingBalanceService = new OpeningBalanceService();
    private final JournalService journalService = new JournalService();
    private final Map<Account, TextField> accountFields = new LinkedHashMap<>();
    private final Map<Long, TextField> fundFields = new LinkedHashMap<>();
    private OpeningBalanceForm form;
    private boolean canEnter;

    @FXML
    private void initialize() {
        canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        load();
    }

    @FXML
    private void handleSave() {
        run("Saving opening balances...", () -> openingBalanceService.saveDraft(accountAmounts(), fundAmounts()));
    }

    @FXML
    private void handlePost() {
        run("Posting opening balances...", () -> {
            if (canEnter) {
                openingBalanceService.saveDraft(accountAmounts(), fundAmounts());
            }
            openingBalanceService.post();
        });
    }

    @FXML
    private void handleReverse() {
        JournalEntry entry = form.entry();
        TextInputDialog reasonDialog = DialogStyler.apply(new TextInputDialog());
        reasonDialog.setTitle("Reverse opening balances");
        reasonDialog.setHeaderText("This posts a reversal of " + entry.entryNo()
                + " so the opening balances can be entered again.");
        reasonDialog.setContentText("Reason:");
        reasonDialog.showAndWait().ifPresent(reason ->
                run("Reversing opening balances...", () -> journalService.reverse(entry.id(), entry.entryDate(), reason)));
    }

    private void load() {
        ProcessingDialog.run("Opening Balances", "Loading opening balances...",
                openingBalanceService::load,
                this::show,
                throwable -> showError(throwable.getMessage()));
    }

    private void show(OpeningBalanceForm loaded) {
        form = loaded;
        JournalEntry entry = loaded.entry();
        boolean posted = entry != null && entry.isPosted();
        statusLabel.setText("Go-live date " + loaded.goLiveDate() + " — "
                + (entry == null ? "Not entered" : posted ? "Posted " + entry.entryNo() : "Draft"));

        accountFields.clear();
        accountGrid.getChildren().clear();
        int row = 0;
        for (Account account : loaded.balanceAccounts()) {
            TextField field = amountField(loaded.accountAmounts().get(account.id()), posted);
            accountFields.put(account, field);
            accountGrid.add(new Label(account.displayText()), 0, row);
            accountGrid.add(field, 1, row++);
        }
        fundFields.clear();
        fundGrid.getChildren().clear();
        row = 0;
        for (CollectionCategory category : loaded.categories()) {
            TextField field = amountField(loaded.fundAmounts().get(category.getId()), posted);
            fundFields.put(category.getId(), field);
            fundGrid.add(new Label(category.displayText()), 0, row);
            fundGrid.add(field, 1, row++);
        }

        saveButton.setDisable(posted || !canEnter);
        postButton.setDisable(posted || !(entry == null ? canEnter : journalService.canPost(entry)));
        reverseButton.setVisible(posted && journalService.canReverse(entry));
        reverseButton.setManaged(reverseButton.isVisible());
        updateTotals();
    }

    private TextField amountField(BigDecimal amount, boolean readOnly) {
        TextField field = new TextField(amount == null || amount.signum() == 0 ? "" : MoneyFormat.format(amount));
        field.setPrefWidth(150);
        field.setAlignment(Pos.CENTER_RIGHT);
        field.setEditable(!readOnly);
        field.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
        return field;
    }

    private void updateTotals() {
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        for (Map.Entry<Account, TextField> entry : accountFields.entrySet()) {
            BigDecimal amount = amount(entry.getValue());
            if (entry.getKey().type() == AccountType.ASSET) {
                assets = assets.add(amount);
            } else {
                liabilities = liabilities.add(amount);
            }
        }
        BigDecimal funds = fundFields.values().stream().map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = assets.subtract(liabilities).subtract(funds);
        totalsLabel.getStyleClass().remove("error-label");
        totalsLabel.setText("Assets " + MoneyFormat.format(assets) + "   Liabilities " + MoneyFormat.format(liabilities)
                + "   Funds " + MoneyFormat.format(funds)
                + (difference.signum() == 0 ? "   Balanced" : "   Difference " + MoneyFormat.format(difference)));
        if (difference.signum() != 0) {
            totalsLabel.getStyleClass().add("error-label");
        }
    }

    private BigDecimal amount(TextField field) {
        try {
            BigDecimal value = MoneyFormat.parse(field.getText());
            return value == null ? BigDecimal.ZERO : value;
        } catch (IllegalArgumentException exception) {
            return BigDecimal.ZERO;
        }
    }

    private Map<Long, BigDecimal> accountAmounts() {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        accountFields.forEach((account, field) -> amounts.put(account.id(), MoneyFormat.parse(field.getText())));
        return amounts;
    }

    private Map<Long, BigDecimal> fundAmounts() {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        fundFields.forEach((categoryId, field) -> amounts.put(categoryId, MoneyFormat.parse(field.getText())));
        return amounts;
    }

    private void run(String message, Runnable action) {
        ProcessingDialog.run("Opening Balances", message, action::run, this::load,
                throwable -> showError(throwable.getMessage()));
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Opening Balances");
        alert.setHeaderText("Opening Balances");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: succeeds.

- [ ] **Step 4: Manual check**

Enter Cash in Hand 100,000, Loans Payable 20,000, Category A 50,000, Category C 30,000 → totals "Balanced" → Save draft → reopen the screen: values kept, status "Draft" → Post → "Posted JE-2027-00001", fields read-only, **Reverse to correct** visible for an approver → reverse → screen shows "Not entered" and accepts new values. An unbalanced Post shows the debits/credits message.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/finance-opening-balances-view.fxml src/main/java/com/churchmanagement/controller/FinanceOpeningBalancesController.java
git commit -m "feat(finance): Opening Balances screen" -m "One form for the go-live balances: an amount per asset and liability account and the Accumulated Fund per category, with live totals and the difference, Save draft and Post. Once posted the form is read-only and an approver can 'Reverse to correct', after which the balances can be entered again." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Account Ledger screen

**Files:**
- Modify (replace): `src/main/resources/com/churchmanagement/view/finance-ledger-view.fxml`
- Create: `src/main/java/com/churchmanagement/controller/FinanceLedgerController.java`

**Interfaces:**
- Consumes: `AccountService.postingAccounts`; `JournalService.ledger`, `find`; `AccountLedger`; `JournalEntryDialog.show`; `MoneyFormat`; `ComboBoxUtil.makeSearchable`.

- [ ] **Step 1: Replace the view**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.DatePicker?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="14" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.FinanceLedgerController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>
    <HBox alignment="CENTER_LEFT" spacing="10">
        <Label text="Account"/>
        <ComboBox fx:id="accountComboBox" prefWidth="320"/>
        <Label text="From"/>
        <DatePicker fx:id="fromDatePicker" prefWidth="140"/>
        <Label text="To"/>
        <DatePicker fx:id="toDatePicker" prefWidth="140"/>
        <Button fx:id="showButton" onAction="#handleShow" styleClass="primary-button" text="Show"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Label fx:id="openingLabel"/>
    </HBox>
    <TableView fx:id="ledgerTable" VBox.vgrow="ALWAYS">
        <columns>
            <TableColumn fx:id="dateColumn" prefWidth="100" text="Date"/>
            <TableColumn fx:id="entryNoColumn" prefWidth="130" text="Entry No"/>
            <TableColumn fx:id="narrationColumn" prefWidth="260" text="Narration"/>
            <TableColumn fx:id="memoColumn" prefWidth="160" text="Memo"/>
            <TableColumn fx:id="categoryColumn" prefWidth="80" text="Category"/>
            <TableColumn fx:id="branchColumn" prefWidth="80" text="Branch"/>
            <TableColumn fx:id="debitColumn" prefWidth="120" text="Debit"/>
            <TableColumn fx:id="creditColumn" prefWidth="120" text="Credit"/>
            <TableColumn fx:id="balanceColumn" prefWidth="130" text="Balance"/>
        </columns>
        <columnResizePolicy>
            <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
        </columnResizePolicy>
    </TableView>
    <HBox alignment="CENTER_RIGHT">
        <Label fx:id="closingLabel" styleClass="report-card-title"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create the controller**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.entity.Account;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public class FinanceLedgerController {
    @FXML private ComboBox<Account> accountComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button showButton;
    @FXML private Label openingLabel;
    @FXML private TableView<AccountLedger.Row> ledgerTable;
    @FXML private TableColumn<AccountLedger.Row, String> dateColumn;
    @FXML private TableColumn<AccountLedger.Row, String> entryNoColumn;
    @FXML private TableColumn<AccountLedger.Row, String> narrationColumn;
    @FXML private TableColumn<AccountLedger.Row, String> memoColumn;
    @FXML private TableColumn<AccountLedger.Row, String> categoryColumn;
    @FXML private TableColumn<AccountLedger.Row, String> branchColumn;
    @FXML private TableColumn<AccountLedger.Row, String> debitColumn;
    @FXML private TableColumn<AccountLedger.Row, String> creditColumn;
    @FXML private TableColumn<AccountLedger.Row, String> balanceColumn;
    @FXML private Label closingLabel;

    private final JournalService journalService = new JournalService();

    @FXML
    private void initialize() {
        List<Account> accounts = new AccountService().postingAccounts();
        accountComboBox.setItems(new FilteredList<>(FXCollections.observableArrayList(accounts), account -> true));
        ComboBoxUtil.makeSearchable(accountComboBox, Account::displayText);
        accountComboBox.setPromptText("Select account...");
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(LocalDate.of(today.getYear(), 1, 1));
        toDatePicker.setValue(today);
        ButtonIconUtil.applyIcon(showButton, "fas-search");
        configureTable();
    }

    @FXML
    private void handleShow() {
        Account account = accountComboBox.getValue();
        if (account == null) {
            showError("Select an account.");
            return;
        }
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        ProcessingDialog.run("Account Ledger", "Loading ledger...",
                () -> journalService.ledger(account.id(), from, to),
                ledger -> {
                    openingLabel.setText("Opening balance " + MoneyFormat.format(ledger.openingBalance()));
                    ledgerTable.getItems().setAll(ledger.rows());
                    closingLabel.setText("Closing balance " + MoneyFormat.format(ledger.closingBalance()));
                },
                throwable -> showError(throwable.getMessage()));
    }

    private void configureTable() {
        text(dateColumn, row -> row.line().entryDate().toString());
        text(entryNoColumn, row -> row.line().entryNo());
        text(narrationColumn, row -> row.line().narration());
        text(memoColumn, row -> row.line().memo());
        text(categoryColumn, row -> row.line().categoryCode());
        text(branchColumn, row -> row.line().churchCode());
        money(debitColumn, row -> row.line().debit());
        money(creditColumn, row -> row.line().credit());
        money(balanceColumn, AccountLedger.Row::balance);
        ledgerTable.setRowFactory(table -> {
            TableRow<AccountLedger.Row> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    journalService.find(row.getItem().line().entryId())
                            .ifPresent(entry -> JournalEntryDialog.show(entry, this::handleShow));
                }
            });
            return row;
        });
    }

    private static void text(TableColumn<AccountLedger.Row, String> column, Function<AccountLedger.Row, String> value) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                value.apply(cell.getValue()) == null ? "" : value.apply(cell.getValue())));
    }

    private static void money(TableColumn<AccountLedger.Row, String> column,
                              Function<AccountLedger.Row, BigDecimal> value) {
        column.setCellValueFactory(cell -> {
            BigDecimal amount = value.apply(cell.getValue());
            return new SimpleStringProperty(amount == null || amount.signum() == 0 && column.getText().matches("Debit|Credit")
                    ? "" : MoneyFormat.format(amount));
        });
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Account Ledger");
        alert.setHeaderText("Account Ledger");
        alert.setContentText(message == null ? "Unable to load the ledger." : message);
        alert.showAndWait();
    }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: succeeds.

- [ ] **Step 4: Manual check**

Select 1100 Cash in Hand, 2027-01-01 to today → opening balance, each posted line with running balance, closing balance; reversed entries show both the original and the reversal; double-click opens the entry read-only.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/finance-ledger-view.fxml src/main/java/com/churchmanagement/controller/FinanceLedgerController.java
git commit -m "feat(finance): Account Ledger screen" -m "Pick an account and a date range to see the opening balance, every posted line (date, entry number, narration, memo, category, branch, debit, credit) with a running balance in the account's normal direction, and the closing balance. Double-click opens the entry read-only. PDF and Excel books come in F4." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Finance settings tab

**Files:**
- Create: `service/FinanceSettingsService.java`
- Modify: `controller/SettingsController.java`, `view/settings-view.fxml` (new "Finance" tab after "SMS")
- Test: `src/test/java/com/churchmanagement/service/FinanceSettingsServiceTest.java`

**Interfaces:**
- Consumes: `SystemSettingService.updateSettings(List<UpdateSystemSettingRequest>)`, `JournalRepository.hasPostedEntries`, `FinanceSettings`, `ActivityLogService.logFinance`.
- Produces: `FinanceSettingsService(SystemSettingService, JournalRepository, FinanceSettings, ActivityLogService)` and `()`: `void update(LocalDate goLiveDate, boolean approvalRequired)`, `boolean goLiveLocked()`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.dto.SystemSettingDto;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceSettingsServiceTest {
    @Test
    void savesBothSettingsWhileNothingIsPosted() {
        RecordingSettings settingsService = new RecordingSettings();
        FinanceSettingsService service = new FinanceSettingsService(settingsService,
                new FinanceFakes.FakeJournalRepository(), new FinanceFakes.FakeFinanceSettings(),
                new FinanceFakes.RecordingActivityLog());

        service.update(LocalDate.of(2026, 11, 1), true);

        assertEquals(List.of("finance.go_live_date=2026-11-01", "finance.approval.required=true"), settingsService.saved);
    }

    @Test
    void goLiveDateIsLockedOncePosted() {
        FinanceFakes.FakeJournalRepository journal = new FinanceFakes.FakeJournalRepository();
        journal.insertPosted(new JournalEntry(null, null, LocalDate.of(2027, 1, 1), 2027, "x", JournalSourceType.JOURNAL,
                null, JournalStatus.DRAFT, null, 1L, LocalDateTime.now(), null, null, List.of()), 1L, LocalDateTime.now());
        RecordingSettings settingsService = new RecordingSettings();
        FinanceSettingsService service = new FinanceSettingsService(settingsService, journal,
                new FinanceFakes.FakeFinanceSettings(), new FinanceFakes.RecordingActivityLog());

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.update(LocalDate.of(2027, 2, 1), false));

        assertEquals("The go-live date cannot change after entries have been posted.", exception.getMessage());
        service.update(LocalDate.of(2027, 1, 1), true);
        assertEquals(List.of("finance.go_live_date=2027-01-01", "finance.approval.required=true"), settingsService.saved);
    }

    private static class RecordingSettings extends SystemSettingService {
        private final List<String> saved = new ArrayList<>();

        private RecordingSettings() {
            super(null, null, null);
        }

        @Override
        public List<SystemSettingDto> updateSettings(List<UpdateSystemSettingRequest> requests) {
            requests.forEach(request -> saved.add(request.getSettingKey() + "=" + request.getSettingValue()));
            return List.of();
        }
    }
}
```

(Check `SystemSettingService` has a 3-argument constructor `(SystemSettingRepository, SystemConfigurationCache, ActivityLogService)` — `SystemSettingServiceTest.serviceWithDefaults` uses it — and that `UpdateSystemSettingRequest` has `getSettingKey()` / `getSettingValue()`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=FinanceSettingsServiceTest`
Expected: compilation FAILURE — `cannot find symbol: class FinanceSettingsService`.

- [ ] **Step 3: Implement the service**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthContext;

import java.time.LocalDate;
import java.util.List;

/** Saves the finance settings; the go-live date is locked once any entry is posted. */
public class FinanceSettingsService {
    private final SystemSettingService systemSettingService;
    private final JournalRepository journalRepository;
    private final FinanceSettings financeSettings;
    private final ActivityLogService activityLogService;

    public FinanceSettingsService() {
        this(new SystemSettingService(), new JournalRepository(), new FinanceSettings(), new ActivityLogService());
    }

    public FinanceSettingsService(SystemSettingService systemSettingService, JournalRepository journalRepository,
                                  FinanceSettings financeSettings, ActivityLogService activityLogService) {
        this.systemSettingService = systemSettingService;
        this.journalRepository = journalRepository;
        this.financeSettings = financeSettings;
        this.activityLogService = activityLogService;
    }

    public boolean goLiveLocked() {
        try {
            return journalRepository.hasPostedEntries();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to check posted entries right now.", exception);
        }
    }

    public void update(LocalDate goLiveDate, boolean approvalRequired) {
        if (goLiveDate == null) {
            throw new FinanceException("Choose the go-live date.");
        }
        if (!goLiveDate.equals(financeSettings.goLiveDate()) && goLiveLocked()) {
            throw new FinanceException("The go-live date cannot change after entries have been posted.");
        }
        systemSettingService.updateSettings(List.of(
                new UpdateSystemSettingRequest(FinanceSettings.GO_LIVE_DATE_KEY, goLiveDate.toString()),
                new UpdateSystemSettingRequest(FinanceSettings.APPROVAL_REQUIRED_KEY, String.valueOf(approvalRequired))));
        activityLogService.logFinance(AuthContext.getCurrentUser().map(user -> user.getUserId()).orElse(null),
                ActivityLogService.FINANCE_SETTINGS_UPDATED, null,
                "go_live_date: " + goLiveDate + ", approval_required: " + approvalRequired);
    }
}
```

- [ ] **Step 4: Add the Finance tab**

`settings-view.fxml` — after the closing `</Tab>` of the "SMS" tab:

```xml
        <Tab closable="false" text="Finance">
            <ScrollPane fitToWidth="true" styleClass="transparent-scroll-pane">
                <content>
            <VBox spacing="14" styleClass="settings-page">
                <padding>
                    <Insets bottom="8" left="2" right="2" top="12"/>
                </padding>
                <GridPane hgap="14" vgap="10" styleClass="settings-form-card">
                    <Label text="Go-live date" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
                    <DatePicker fx:id="financeGoLiveDatePicker" prefWidth="200" GridPane.columnIndex="1" GridPane.rowIndex="0"/>
                    <Label fx:id="financeGoLiveNoteLabel" styleClass="muted-label" wrapText="true"
                           GridPane.columnIndex="1" GridPane.rowIndex="1"/>
                    <Label text="Approval" GridPane.columnIndex="0" GridPane.rowIndex="2"/>
                    <CheckBox fx:id="financeApprovalCheckBox" text="Require approval before posting"
                              GridPane.columnIndex="1" GridPane.rowIndex="2"/>
                    <Label styleClass="muted-label" wrapText="true"
                           text="When ticked, an entry is posted by an approver who did not prepare it."
                           GridPane.columnIndex="1" GridPane.rowIndex="3"/>
                </GridPane>
                <HBox spacing="10" styleClass="settings-action-bar">
                    <Button fx:id="saveFinanceSettingsButton" onAction="#saveFinanceSettings" styleClass="primary-button" text="Save Finance"/>
                </HBox>
            </VBox>
                </content>
            </ScrollPane>
        </Tab>
```

(add `<?import javafx.scene.control.DatePicker?>` if missing).

`SettingsController` — fields:

```java
    @FXML private DatePicker financeGoLiveDatePicker;
    @FXML private Label financeGoLiveNoteLabel;
    @FXML private CheckBox financeApprovalCheckBox;
    @FXML private Button saveFinanceSettingsButton;
    private final FinanceSettingsService financeSettingsService = new FinanceSettingsService();
```

in `applySettings(...)` (where the SMS values are set):

```java
        financeGoLiveDatePicker.setValue(parseDate(values.get(FinanceSettings.GO_LIVE_DATE_KEY)));
        financeApprovalCheckBox.setSelected(Boolean.parseBoolean(values.get(FinanceSettings.APPROVAL_REQUIRED_KEY)));
        refreshFinanceLock();
```

and new methods:

```java
    @FXML
    private void saveFinanceSettings() {
        LocalDate goLive = financeGoLiveDatePicker.getValue();
        boolean approval = financeApprovalCheckBox.isSelected();
        ProcessingDialog.run("Save Settings", "Saving finance settings...",
                () -> financeSettingsService.update(goLive, approval),
                () -> {
                    refreshFinanceLock();
                    showInfo("Settings updated", "Finance settings updated successfully.");
                },
                throwable -> showValidationError(processingMessage(throwable)));
    }

    private void refreshFinanceLock() {
        boolean locked;
        try {
            locked = financeSettingsService.goLiveLocked();
        } catch (RuntimeException exception) {
            locked = false;
        }
        financeGoLiveDatePicker.setDisable(locked);
        financeGoLiveNoteLabel.setText(locked
                ? "Locked: entries have been posted."
                : "The books accept entries from this date. It locks after the first entry is posted.");
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? FinanceSettings.DEFAULT_GO_LIVE_DATE : LocalDate.parse(value.strip());
        } catch (java.time.format.DateTimeParseException exception) {
            return FinanceSettings.DEFAULT_GO_LIVE_DATE;
        }
    }
```

Imports: `com.churchmanagement.service.FinanceSettings`, `com.churchmanagement.service.FinanceSettingsService`, `javafx.scene.control.DatePicker`, `java.time.LocalDate` (if missing). If `showInfo` / `showValidationError` / `processingMessage` have other names in `SettingsController`, use the ones `saveSmsSettings` uses.

- [ ] **Step 5: Test and compile**

Run: `mvn -q test -Dtest=FinanceSettingsServiceTest,SystemSettingServiceTest` then `mvn -q compile`
Expected: PASS; compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/churchmanagement/service/FinanceSettingsService.java src/main/java/com/churchmanagement/controller/SettingsController.java src/main/resources/com/churchmanagement/view/settings-view.fxml src/test/java/com/churchmanagement/service/FinanceSettingsServiceTest.java
git commit -m "feat(finance): Finance settings tab" -m "Settings gets a Finance tab with the go-live date and 'Require approval before posting'. The go-live date is locked, with a note, once any entry has been posted; FinanceSettingsService enforces the same rule and logs every change." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: User manuals (EN + SI)

**Files:**
- Modify: `docs/user-manual.md`, `docs/user-manual-si.md` (new chapter `## 19. Finance` at the end, and a Table of Contents entry)

- [ ] **Step 1: English chapter**

Append to `docs/user-manual.md` and add `19. [Finance](#19-finance)` to the Table of Contents:

```markdown
---

## 19. Finance

The Finance module keeps the church's double-entry books. Open it from **Home › Finance** (Ctrl+5).

### 19.1 Key ideas

- Every financial event is a **journal entry**: debits must equal credits.
- An entry is first a **draft** (can be edited or deleted). When it is **posted** it gets a number such as **JE-2027-00001** and can never be changed — mistakes are corrected with a **reversal**.
- Income, expense and Accumulated Fund lines carry a **category** (A, B, C …) so each category's income and spending can be reported. Cash and bank lines do not.
- The books start on the **go-live date** (1 January 2027). Entries cannot be dated earlier.

### 19.2 Chart of Accounts

**Finance › Chart of Accounts** shows all accounts grouped as Assets (1xxx), Liabilities (2xxx), Equity (3xxx), Income (4xxx) and Expenses (5xxx), with each account's balance.

To add an account (needs *Manage accounts*): select the group (e.g. **1200 Bank Accounts**), click **Add account**, enter a 4-digit code inside the group's range shown in the dialog (e.g. 1201) and a name (e.g. *BOC Current Account*), then **Save**. Tick *This is a group* to create a new group (code ending in 00, directly under a main group).

Accounts that have been used cannot change code or group and cannot be deleted — **Deactivate** them instead. Cash in Hand, Accumulated Fund, Tithes, Offerings and Other Donations are system accounts and stay active.

### 19.3 General Journal

**Finance › General Journal** lists entries by date, status and source. Click **New entry**, choose the date, type a narration and add lines: account, debit **or** credit, category (required for income, expense and Accumulated Fund lines), optional branch and memo. The bottom line shows the totals and warns *Out of balance by …* until debits equal credits.

- **Save draft** keeps the entry for later.
- **Post** records it permanently and gives it a number.
- **Reverse…** (on a posted entry) asks for a reason and posts a new entry with debits and credits swapped.

**Approval:** if *Require approval before posting* is ticked in **Settings › Finance**, the person who prepared an entry cannot post it; someone with *Approve finance entries* must post it.

### 19.4 Opening Balances

**Finance › Opening Balances** records the position on the go-live date: the balance of each asset and liability account (cash, bank, buildings, loans …) and the **Accumulated Fund for each category**. The totals must balance (Assets − Liabilities = Funds). **Save draft**, then **Post**. To correct posted opening balances, use **Reverse to correct** and enter them again.

### 19.5 Account Ledger

**Finance › Account Ledger** shows, for one account and date range, the opening balance, every posted line with a running balance, and the closing balance. Double-click a line to open its entry.

### 19.6 Settings and permissions

**Settings › Finance:** go-live date (locked after the first posting) and *Require approval before posting*.

| Action | Permission |
|--------|------------|
| Open Finance and view | `finance.menu.view` |
| Prepare entries and opening balances | `finance.entry` |
| Post when approval is required; reverse | `finance.approve` |
| Add, edit, deactivate accounts | `finance.accounts.manage` |
| Close accounting years (later release) | `finance.year.close` |
```

- [ ] **Step 2: Sinhala chapter**

Append to `docs/user-manual-si.md` and add `19. [Finance](#19-finance)` to its Table of Contents:

```markdown
---

## 19. Finance

Finance මොඩියුලය පල්ලියේ ද්විත්ව-ප්‍රවේශ (double-entry) ගිණුම් පොත් තබා ගනී. එය **Home › Finance** (Ctrl+5) මගින් විවෘත කරන්න.

### 19.1 මූලික අදහස්

- සෑම මූල්‍ය සිදුවීමක්ම **journal entry** එකකි: හර (debit) එකතුව බැර (credit) එකතුවට සමාන විය යුතුය.
- ප්‍රවේශයක් මුලින් **draft** එකකි (සංස්කරණය හෝ මකා දැමිය හැක). එය **post** කළ විට **JE-2027-00001** වැනි අංකයක් ලැබෙන අතර කිසිදා වෙනස් කළ නොහැක — වැරදි **reversal** මගින් නිවැරදි කෙරේ.
- ආදායම්, වියදම් සහ Accumulated Fund පේළිවලට **category** (A, B, C …) එකක් ඇත; එවිට එක් එක් category එකේ ආදායම සහ වියදම වාර්තා කළ හැක. මුදල් සහ බැංකු පේළිවලට category නැත.
- ගිණුම් පොත් **go-live date** (2027 ජනවාරි 1) සිට ආරම්භ වේ. ඊට පෙර දිනයකින් ප්‍රවේශ කළ නොහැක.

### 19.2 Chart of Accounts

**Finance › Chart of Accounts** සියලු ගිණුම් Assets (1xxx), Liabilities (2xxx), Equity (3xxx), Income (4xxx) සහ Expenses (5xxx) ලෙස කාණ්ඩ කර, එක් එක් ගිණුමේ ශේෂය සමඟ පෙන්වයි.

ගිණුමක් එක් කිරීමට (*Manage accounts* අවසරය අවශ්‍යයි): කාණ්ඩය තෝරන්න (උදා. **1200 Bank Accounts**), **Add account** ඔබන්න, සංවාදයේ පෙන්වන පරාසය තුළ ඉලක්කම් 4ක කේතයක් (උදා. 1201) සහ නමක් (උදා. *BOC Current Account*) ඇතුළත් කර **Save** ඔබන්න. නව කාණ්ඩයක් සෑදීමට *This is a group* ලකුණු කරන්න (00 න් අවසන් වන කේතයක්, ප්‍රධාන කාණ්ඩයක් යටතේ කෙලින්ම).

භාවිත කළ ගිණුම්වල කේතය හෝ කාණ්ඩය වෙනස් කළ නොහැකි අතර මකා දැමිය නොහැක — ඒ වෙනුවට **Deactivate** කරන්න. Cash in Hand, Accumulated Fund, Tithes, Offerings සහ Other Donations පද්ධති ගිණුම් වන අතර සක්‍රියව පවතී.

### 19.3 General Journal

**Finance › General Journal** දිනය, තත්ත්වය සහ මූලාශ්‍රය අනුව ප්‍රවේශ ලැයිස්තුගත කරයි. **New entry** ඔබා, දිනය තෝරා, විස්තරයක් (narration) ලියා පේළි එක් කරන්න: ගිණුම, හර **හෝ** බැර, category (ආදායම්, වියදම් සහ Accumulated Fund පේළි සඳහා අනිවාර්ය), අවශ්‍ය නම් ශාඛාව සහ සටහන. පහළ පේළිය එකතු පෙන්වන අතර හර සහ බැර සමාන වන තුරු *Out of balance by …* ලෙස අනතුරු අඟවයි.

- **Save draft** ප්‍රවේශය පසුව සඳහා තබා ගනී.
- **Post** එය ස්ථිරව සටහන් කර අංකයක් ලබා දෙයි.
- **Reverse…** (post කළ ප්‍රවේශයක) හේතුවක් අසා, හර සහ බැර මාරු කළ නව ප්‍රවේශයක් post කරයි.

**අනුමැතිය:** **Settings › Finance** හි *Require approval before posting* ලකුණු කර ඇත්නම්, ප්‍රවේශය සකස් කළ පුද්ගලයාට එය post කළ නොහැක; *Approve finance entries* අවසරය ඇති වෙනත් අයෙකු post කළ යුතුය.

### 19.4 Opening Balances

**Finance › Opening Balances** go-live දිනයේ තත්ත්වය සටහන් කරයි: එක් එක් වත්කම් සහ වගකීම් ගිණුමේ ශේෂය (මුදල්, බැංකු, ගොඩනැගිලි, ණය …) සහ **එක් එක් category එකේ Accumulated Fund**. එකතු සමතුලිත විය යුතුය (Assets − Liabilities = Funds). **Save draft** කර පසුව **Post** කරන්න. post කළ opening balances නිවැරදි කිරීමට **Reverse to correct** භාවිත කර නැවත ඇතුළත් කරන්න.

### 19.5 Account Ledger

**Finance › Account Ledger** එක් ගිණුමක් සහ දින පරාසයක් සඳහා ආරම්භක ශේෂය, ධාවන ශේෂය සහිත සෑම post කළ පේළියක්ම සහ අවසාන ශේෂය පෙන්වයි. ප්‍රවේශය විවෘත කිරීමට පේළියක් මත දෙවරක් ක්ලික් කරන්න.

### 19.6 සැකසුම් සහ අවසර

**Settings › Finance:** go-live date (පළමු post කිරීමෙන් පසු අගුළු දමයි) සහ *Require approval before posting*.

| ක්‍රියාව | අවසරය |
|--------|------------|
| Finance විවෘත කර බැලීම | `finance.menu.view` |
| ප්‍රවේශ සහ opening balances සකස් කිරීම | `finance.entry` |
| අනුමැතිය අවශ්‍ය විට post කිරීම; reverse කිරීම | `finance.approve` |
| ගිණුම් එක් කිරීම, සංස්කරණය, අක්‍රිය කිරීම | `finance.accounts.manage` |
| ගිණුම් වර්ෂ වසා දැමීම (පසු නිකුතුවක) | `finance.year.close` |
```

- [ ] **Step 3: Commit**

```bash
git add docs/user-manual.md docs/user-manual-si.md
git commit -m "docs(manual): Finance chapter (EN + SI)" -m "Adds chapter 19 Finance to both manuals: key ideas (double entry, draft and posted entries, reversals, categories, go-live), adding accounts in the Chart of Accounts, the General Journal with optional approval, Opening Balances, the Account Ledger, Finance settings and the finance permissions." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 15: Final verification

- [ ] **Step 1:** Check the database host is reachable, then `mvn -q test`. Expected: all tests pass (460 before + the new finance tests).
- [ ] **Step 2:** Start the app on the primary machine against the dev database; confirm Flyway applies V43; `SELECT code, name FROM accounts ORDER BY code` lists the 35 seeded accounts; Admin has the five finance permissions; Settings › Finance shows 2027-01-01.
- [ ] **Step 3:** Manual checks from Tasks 9–13, as Admin and as a clerk (`finance.menu.view`, `finance.entry`) with approval off and on. To post before 2027, temporarily set the go-live date to today in Settings › Finance (only while nothing is posted), and set it back to 2027-01-01 by reversing test entries — or use a disposable database.
- [ ] **Step 4:** Report results; integration via superpowers:finishing-a-development-branch (keep the feature branch).
