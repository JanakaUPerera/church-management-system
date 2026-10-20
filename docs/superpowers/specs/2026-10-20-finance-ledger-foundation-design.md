# Finance F1 — Ledger Foundation — Design

**Requirement:** Phase 02 / 02 Finance & Accounting (Milestone M2), sub-project F1
**Date:** 2026-10-20
**Status:** Approved in brainstorming, pending spec review
**Related:** [PHASE-02-DEVELOPMENT-PLAN.md](../../PHASE-02-DEVELOPMENT-PLAN.md) §4, [module navigation design](2026-10-20-module-navigation-design.md)

---

## 1. M2 roadmap

The Finance module is built as six sub-projects. Each gets its own spec → plan → branch → merge.

| # | Sub-project | Delivers | Depends on |
|---|-------------|----------|------------|
| **F1** | **Ledger foundation** (this spec) | Fiscal years, Chart of Accounts with account creation, journal engine, General Journal, opening balances, Account Ledger view, Finance module + permissions | — |
| F2 | All income | Receipt auto-posting (Dr Cash / Cr income, category + branch) and reversal on cancel; **Receipt Vouchers** for other income (rent, interest, grants, head-office donations, loans received) | F1 |
| F3 | Expenses and transfers | Payment Vouchers, Contra entries, printing | F1 |
| F4 | Books and Trial Balance | Trial Balance, General Ledger, Cash Book, Bank Book, voucher register (PDF / Excel) | F1 |
| F5 | Financial statements | Income & Expenditure, Balance Sheet, Cash Flow (direct), Changes in Equity per category, Category-wise Income & Expenditure, previous-year comparison | F4 |
| F6 | Year-end close | Close income/expense to the Accumulated Fund per category, lock year, carry forward, admin reopen | F1, F5 |

Client answers recorded during brainstorming (2026-10-20):
- No existing chart of accounts — seed a standard church chart with grouped 4-digit codes, fully editable (answers part of Q6).
- All collections are held at **head office**: one Cash in Hand account; the branch is a tag on lines (answers part of Q7).
- Approval is **optional**, controlled by a setting (answers Q9).
- Accounting go-live **1 Jan 2027** (already confirmed); earlier receipts are not posted, opening balances cover them.

## 2. Decisions

| # | Decision |
|---|----------|
| D1 | **One journal** for every financial event (`journal_entries` / `journal_lines`). Documents (receipts, vouchers) point to their entry via `source_type` / `source_id`. All reports read the journal only. |
| D2 | Posted entries are **immutable**. Corrections are reversing entries. Drafts can be edited or deleted. |
| D3 | Approval is optional (`finance.approval.required`, default `false`). On: posting needs `finance.approve` and the poster cannot be the preparer. Off: `finance.entry` can post. |
| D4 | Accounts are **group** accounts (organise only) or **posting** accounts (take entries). Type follows the code's first digit: 1 Asset, 2 Liability, 3 Equity, 4 Income, 5 Expense. |
| D5 | **Category (fund) is required** on lines whose account type is INCOME, EXPENSE or EQUITY; optional otherwise. **Branch** is optional on every line. |
| D6 | One pooled **Cash in Hand** account; bank accounts are added by the user under group 1200. |
| D7 | Entry numbers `JE-<year>-<5 digits>` are assigned **at posting**, per fiscal year, without gaps. Drafts have no number. |
| D8 | Calendar fiscal years, created automatically (OPEN) the first time an entry is dated in them. Nothing can be saved or posted into a CLOSED year. Closing is F6. |
| D9 | No entry may be dated before `finance.go_live_date` (default 2027-01-01). The go-live date cannot be changed once any entry is posted. |
| D10 | Opening balances are a single **OPENING** entry dated on the go-live date. It can only be corrected by reversing it and posting a new one. |
| D11 | Amounts are `DECIMAL(15,2)`, LKR only, no currency field. |
| D12 | Only one reversal per entry; reversing a reversal is not allowed (re-enter instead). |

## 3. Data model — migration `V43__finance_ledger_foundation.sql`

### 3.1 `fiscal_years`

| Column | Type | Notes |
|--------|------|-------|
| `year` | INT PK | e.g. 2027 |
| `start_date`, `end_date` | DATE NOT NULL | 1 Jan / 31 Dec |
| `status` | VARCHAR(10) NOT NULL DEFAULT 'OPEN' | OPEN / CLOSED |
| `closed_by` | BIGINT NULL FK users | set by F6 |
| `closed_at` | DATETIME NULL | |

### 3.2 `accounts`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK auto | |
| `code` | CHAR(4) NOT NULL UNIQUE | digits only |
| `name` | VARCHAR(120) NOT NULL | unique among siblings |
| `type` | VARCHAR(10) NOT NULL | ASSET / LIABILITY / EQUITY / INCOME / EXPENSE |
| `parent_id` | BIGINT NULL FK accounts | null only for the five top groups |
| `is_group` | BOOLEAN NOT NULL | |
| `cash_flow_class` | VARCHAR(10) NOT NULL | CASH / OPERATING / INVESTING / FINANCING (used by F5) |
| `system_key` | VARCHAR(40) NULL UNIQUE | stable handle for code that must find an account even if renamed |
| `description` | VARCHAR(255) NULL | |
| `status` | VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / INACTIVE |
| `created_at`, `updated_at` | DATETIME | |

**Code ranges:** a top group `X000` holds codes `X001–X999`; a sub-group `XY00` holds `XY01–XY99`. A child's type equals its parent's type. Top groups and system-keyed accounts cannot be deleted, re-typed or re-parented (they can be renamed).

### 3.3 `journal_entries`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK auto | |
| `entry_no` | VARCHAR(20) NULL UNIQUE | assigned at posting |
| `entry_date` | DATE NOT NULL | |
| `fiscal_year` | INT NOT NULL FK fiscal_years | from `entry_date` |
| `narration` | VARCHAR(500) NOT NULL | |
| `source_type` | VARCHAR(20) NOT NULL | F1: OPENING, JOURNAL, REVERSAL. Reserved: RECEIPT, RECEIPT_CANCEL, RECEIPT_VOUCHER, PAYMENT_VOUCHER, CONTRA, CLOSING |
| `source_id` | BIGINT NULL | document id for document-driven entries |
| `status` | VARCHAR(10) NOT NULL | DRAFT / POSTED |
| `reversal_of_id` | BIGINT NULL UNIQUE FK journal_entries | set on the reversing entry (UNIQUE enforces one reversal) |
| `prepared_by`, `prepared_at` | BIGINT FK users, DATETIME | |
| `posted_by`, `posted_at` | BIGINT NULL FK users, DATETIME NULL | |
| `updated_at` | DATETIME | |

Indexes: (`entry_date`), (`status`), (`source_type`, `source_id`).

### 3.4 `journal_lines`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK auto | |
| `entry_id` | BIGINT NOT NULL FK journal_entries ON DELETE CASCADE | cascade only matters for draft deletion |
| `line_no` | INT NOT NULL | |
| `account_id` | BIGINT NOT NULL FK accounts | posting account |
| `debit`, `credit` | DECIMAL(15,2) NOT NULL DEFAULT 0 | `CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))` |
| `category_id` | BIGINT NULL FK collection_categories | required by D5 (service-enforced) |
| `church_id` | BIGINT NULL FK churches | |
| `memo` | VARCHAR(255) NULL | |

Indexes: (`account_id`), (`category_id`), (`church_id`).

### 3.5 Entry number sequence

`journal_entry_sequences(fiscal_year INT PK, last_number INT NOT NULL)`, incremented with `SELECT … FOR UPDATE` inside the posting transaction (same pattern as receipt numbers).

### 3.6 Settings and permissions

- `system_settings`: `finance.go_live_date` = `2027-01-01` (DATE, category FINANCE); `finance.approval.required` = `false` (BOOLEAN, category FINANCE).
- `permissions` (module `Finance`): `finance.menu.view` (open the Finance module and view), `finance.entry` (prepare entries / opening balances), `finance.approve` (post when approval is on, reverse), `finance.accounts.manage` (create / edit / deactivate accounts), `finance.year.close` (F6). All granted to Admin.

### 3.7 Seeded chart of accounts

G = group. Cash-flow class in brackets.

| Code | Name | Type | Notes |
|------|------|------|-------|
| 1000 | Assets | G ASSET | |
| 1100 | Cash in Hand | ASSET (CASH) | `CASH_IN_HAND` |
| 1200 | Bank Accounts | G ASSET (CASH) | user adds 1201–1299 |
| 1300 | Receivables | ASSET (OPERATING) | |
| 1400 | Advances & Deposits | ASSET (OPERATING) | |
| 1500 | Fixed Assets | G ASSET (INVESTING) | |
| 1510 | Land & Buildings | ASSET (INVESTING) | |
| 1520 | Vehicles | ASSET (INVESTING) | |
| 1530 | Furniture & Equipment | ASSET (INVESTING) | |
| 2000 | Liabilities | G LIABILITY | |
| 2100 | Loans Payable | LIABILITY (FINANCING) | |
| 2200 | Payables | LIABILITY (OPERATING) | |
| 3000 | Equity | G EQUITY | |
| 3100 | Accumulated Fund | EQUITY (FINANCING) | `ACCUMULATED_FUND` |
| 4000 | Income | G INCOME | |
| 4100 | Tithes | INCOME (OPERATING) | `TITHES_INCOME` |
| 4200 | Offerings | INCOME (OPERATING) | `OFFERINGS_INCOME` |
| 4300 | Other Donations | INCOME (OPERATING) | `OTHER_DONATIONS_INCOME` |
| 4400 | Rent Income | INCOME (OPERATING) | |
| 4500 | Interest Income | INCOME (OPERATING) | |
| 4600 | Grants | INCOME (OPERATING) | |
| 4900 | Other Income | INCOME (OPERATING) | |
| 5000 | Expenses | G EXPENSE | |
| 5100 | Salaries & Allowances | EXPENSE (OPERATING) | |
| 5200 | Pastoral & Ministry Expenses | EXPENSE (OPERATING) | |
| 5300 | Utilities | EXPENSE (OPERATING) | |
| 5400 | Repairs & Maintenance | EXPENSE (OPERATING) | |
| 5500 | Travel & Transport | EXPENSE (OPERATING) | |
| 5600 | Printing & Stationery | EXPENSE (OPERATING) | |
| 5700 | Charity & Welfare | EXPENSE (OPERATING) | |
| 5800 | Events & Conventions | EXPENSE (OPERATING) | |
| 5850 | Bank Charges | EXPENSE (OPERATING) | |
| 5900 | Depreciation | EXPENSE (OPERATING) | |
| 5950 | Member Fees | EXPENSE (OPERATING) | used by M3 fee payments |
| 5990 | Miscellaneous Expenses | EXPENSE (OPERATING) | |

New accounts default to their parent's cash-flow class (editable).

## 4. Services

All follow the existing pattern: constructor injection with a no-arg default constructor, permission checks via `AuthContext` + `PermissionGuard`, activity logging, `RuntimeException` subclasses with user-facing messages.

### 4.1 `AccountService`

- `List<AccountNode> loadChart()` — tree in code order with each posting account's posted balance (debit − credit, shown in the account's normal direction: debit for ASSET/EXPENSE, credit for LIABILITY/EQUITY/INCOME; groups show the sum of their children).
- `Account create(CreateAccountRequest)` — requires `finance.accounts.manage`; parent must be an active group; code 4 digits, unique, inside the parent's range (§3.2); type = parent's type; name unique among siblings; cash-flow class defaults to parent's.
- `Account update(long id, UpdateAccountRequest)` — name, description and cash-flow class always; code / parent only while the account has no journal lines and is not system-keyed or a top group.
- `deactivate(long id)` / `activate(long id)` — a group cannot be deactivated while it has active children; a posting account can always be deactivated (it then cannot be used on new lines; history is kept).
- No delete for accounts used on any line; unused, non-system accounts can be deleted.
- Activity codes: `ACCOUNT_CREATED`, `ACCOUNT_UPDATED`, `ACCOUNT_ACTIVATED`, `ACCOUNT_DEACTIVATED`, `ACCOUNT_DELETED`.

### 4.2 `FiscalYearService`

- `FiscalYear forDate(LocalDate date)` — returns the year, creating it OPEN if missing.
- `requireOpen(LocalDate date)` — throws "The <year> accounting year is closed." when CLOSED.

### 4.3 `JournalService`

- `JournalEntry saveDraft(JournalEntryRequest)` (create or update a DRAFT; `finance.entry`).
- `deleteDraft(long id)` (`finance.entry`; drafts only).
- `JournalEntry post(long id)` — validates again inside one transaction, assigns `entry_no`, sets POSTED. Permission per D3.
- `JournalEntry reverse(long id, LocalDate date, String reason)` — creates and posts a REVERSAL entry with debits and credits swapped, same categories / branches, narration "Reversal of JE-… — <reason>"; requires `finance.approve` when approval is on, else `finance.entry`; original must be POSTED, not a reversal, not already reversed; reversal date ≥ original date and in an open year.
- `List<JournalEntrySummary> search(JournalSearchCriteria)` — date range, status, source type, entry no, account.
- Package-private `postSystemEntry(JournalEntryRequest, String sourceType, Long sourceId)` for F2/F3 document posting (validates and posts in one step, ignoring the approval setting because the source document carries its own approval).

**Validation (`JournalValidator`, pure, unit-tested):**
1. Narration required (≤ 500).
2. At least two lines.
3. Each line: exactly one of debit / credit > 0, max 2 decimals, ≤ 9,999,999,999,999.99.
4. Each account exists, is a posting account and is ACTIVE.
5. Category present for INCOME / EXPENSE / EQUITY accounts, and the category exists.
6. Σ debit = Σ credit (exact decimal compare).
7. Entry date ≥ go-live date; fiscal year OPEN.

Messages name the line ("Line 3: choose a category for 4100 Tithes.").

Activity codes: `JOURNAL_DRAFT_SAVED`, `JOURNAL_DRAFT_DELETED`, `JOURNAL_POSTED`, `JOURNAL_REVERSED`.

### 4.4 `OpeningBalanceService`

- `OpeningBalanceForm load()` — all active ASSET and LIABILITY posting accounts, and one Accumulated Fund row per active category, with any existing draft amounts.
- `saveDraft(OpeningBalanceForm)` / `post()` — builds a single OPENING entry dated on the go-live date: asset balances as debits, liability balances as credits, Accumulated Fund per category as credit (or debit if negative). Rejects a second OPENING entry while one is POSTED and not reversed. Posting follows D3.

### 4.5 `FinanceSettingsService`

Reads / updates `finance.go_live_date` (rejected once any entry is POSTED) and `finance.approval.required`, through the existing `SystemSettingService` validation (new cases for these keys).

## 5. Screens (Finance module)

Registry module `finance` — title **Finance**, icon `fas-balance-scale`, description "Accounts, journals and opening balances", shortcut **5**, placed between Messaging and Administration. Items (all need `finance.menu.view`):

| Item id | Title | View |
|---------|-------|------|
| `finance.journal` | General Journal | `finance-journal-view.fxml` |
| `finance.accounts` | Chart of Accounts | `finance-accounts-view.fxml` |
| `finance.opening-balances` | Opening Balances | `finance-opening-balances-view.fxml` |
| `finance.ledger` | Account Ledger | `finance-ledger-view.fxml` |

- **General Journal:** filter bar (date range, status, source, search) and a table (Entry No / "Draft", date, narration, source, total, status, prepared by, posted by). **New entry** / double-click opens the **entry editor dialog**: date, narration, a line grid (account — searchable combo of active posting accounts showing "code name"; debit; credit; category — enabled and required for INCOME/EXPENSE/EQUITY; branch; memo), add/remove line, live totals and a red "Out of balance by X" line. Buttons by state and permission: **Save draft**, **Post**, **Delete draft**; for posted entries **Reverse…** (asks date and reason). Posted entries open read-only. The source document of non-journal entries is shown read-only.
- **Chart of Accounts:** `TreeTableView` (code, name, type, cash-flow class, balance, status). Buttons with `finance.accounts.manage`: **Add account** (parent preselected from the selected group), **Edit**, **Deactivate / Activate**, **Delete** (unused only). Inactive accounts in muted style; "Show inactive" toggle.
- **Opening Balances:** banner with go-live date and status (Not entered / Draft / Posted JE-…); two tables (Assets & Liabilities; Accumulated Fund by category), live totals, difference; **Save draft**, **Post**; after posting read-only with **Reverse to correct** (needs approve rights).
- **Account Ledger:** account combo + date range → opening balance, each posted line (date, entry no, narration, memo, category, branch, debit, credit, running balance), closing balance. Double-click opens the entry read-only.
- **Settings → Finance** section: go-live date (disabled once anything is posted, with a note) and **Require approval before posting** checkbox.

Number formatting `#,##0.00`; amounts right-aligned; all lists paged with the existing `TablePaginationUtil`.

## 6. Tests

- **`JournalValidatorTest`:** every rule in §4.3 including exact decimal balance, 3-decimal rejection, group / inactive account, missing category per type, date before go-live.
- **`JournalServiceTest`** (fake repositories): save/update/delete draft; post assigns sequential numbers per year; posted cannot be edited or deleted; approval on → preparer cannot post, approver can; approval off → enterer can post; reverse swaps sides and links; second reversal and reversal-of-reversal rejected; closed year rejected; activity entries written.
- **`AccountServiceTest`:** code range and uniqueness, type from parent, sibling-name uniqueness, edit limits after use, system/top-group protection, group deactivation with active children, delete only unused.
- **`OpeningBalanceServiceTest`:** builds the right debit/credit sides, negative fund balance, unbalanced rejected, one live OPENING entry only.
- **`FiscalYearServiceTest`:** auto-create, closed year rejected.
- **Repository tests** (RecordingDataSource): insert/update SQL and parameters, sequence `FOR UPDATE`, balance query.
- **`NavigationRegistryTest`** updated (finance module, shortcut 5, 21 items); **`SystemSettingServiceTest`** for the two finance settings.
- **Manual:** as Admin and as a clerk with approval on and off: create a bank account 1201, enter and post opening balances, post a journal, reverse it, view the ledger.

## 7. Documentation

User manual EN + SI: new chapter "Finance" covering the module, Chart of Accounts (adding accounts, groups and codes), General Journal (draft, post, approval, reversal), Opening Balances, Account Ledger, Finance settings and permissions.

## 8. Out of scope (later sub-projects)

Receipt auto-posting and Receipt Vouchers (F2); Payment Vouchers and Contra (F3); PDF/Excel books and Trial Balance (F4); statements (F5); year-end close and reopen (F6); bank reconciliation; budgets; multi-currency.
