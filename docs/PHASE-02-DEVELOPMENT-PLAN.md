# Phase 02 — Development Plan

**Project:** Church Management System  
**Prepared:** 2026-10-15  
**Baseline:** Phase 01 (receipts, reports, SMS, backup, users/roles) — latest migration `V39`

---

## 1. Delivery Order

| Milestone | Scope | Requirement | Est. Effort |
|-----------|-------|-------------|-------------|
| **M1** (first) | Auto next code for Region / Branch | 05a | 1–2 days |
| **M1** (first) | Collection Categories (A–E), seeding, year lock, Category filter in Receipt Collection Report | 01 | 1.5–2 weeks |
| **M1** (first) | Official SMS messaging to selected branches | 05b | 3–4 days |
| **M2** | Finance & Accounting module + 5 core reports + Category-wise Income & Expenditure | 02 | 6–8 weeks |
| **M3** | Branch Documents register | 03 | 1 week |
| **M3** | Member services, attendance, fees, payments, reports | 04 | 3–4 weeks |
| **M3** | SMS messaging to selected members (needs the member list from 04) | 05b | 1–2 days |

**Why this order:** 01 and 05 come first, as requested. Finance (02) comes next because it uses the categories from 01 as accounting funds. Members (04) comes after Finance so that fee payments can post to the ledger as expenses.

Each milestone ships as its own release: Flyway migrations, a backup before upgrade, deployment to both machines, and user-manual updates (EN + SI).

---

## 2. Requirement 05 — Existing Feature Optimizations (M1)

### 05a. Auto next code
- **Create Region:** pre-fill the next `R###` code (current max + 1, e.g. `R009`).
- **Create Branch:** pre-fill the next 4-digit code (current max + 1, e.g. `0112`).
- The field stays editable. Uniqueness is still validated on save.
- New method: `RegionService.suggestNextCode()` / `ChurchService.suggestNextCode()`, unit tested (empty table, gaps, non-numeric legacy codes).

### 05b. Official messaging (replaces the "send test SMS" box with a proper screen)
- New **Send Message** screen, opened from Settings → Modem, or from its own menu item if permissions allow.
- **Recipients:** multi-select branches with a region filter and "select all in region". Members are added in M3.
- Recipient preview shows the name and mobile, and flags missing or invalid numbers.
- Message box shows a character and SMS-part counter.
- Messages go through the existing **SMS queue** (`V39`) and are logged in `sms_logs` with type `OFFICIAL_MESSAGE`, so delivery status, the SMS log screen and resend all keep working.
- New permission `SEND_OFFICIAL_SMS`. Every send is recorded in the activity log.
- The existing "send test SMS" stays for modem diagnostics.

---

## 3. Requirement 01 — Collection Categories (M1)

### 3.1 Concept
A **Category** (A, B, C, … with any name) is assigned per **branch × collection type × accounting year**. Each receipt line (Tithes / Offerings / Others) belongs to exactly one category. In M2 these categories become **funds** in the accounting module (standard fund accounting for non-profits). Expenditures are tagged with a category, which gives a per-category income and expenditure balance alongside the normal financial statements.

### 3.2 Data model (migration `V40__collection_categories.sql`)
| Table | Key columns |
|-------|-------------|
| `collection_categories` | `id`, `code` (A, B…), `name`, `description`, `status` |
| `church_category_assignments` | `church_id`, `collection_type`, `category_id`, `accounting_year`. Unique on (church, type, year). |
| `receipt_items` (alter) | add `category_id`: the category is **snapshotted when the receipt is created**, so history never changes |

### 3.3 Business rules
1. **Accounting year = calendar year (1 Jan – 31 Dec).**
2. An assignment for (branch, year) **cannot be changed once any receipt exists** for that branch in that year, including cancelled receipts. The UI shows it as locked, with the reason.
3. **To change a category:** set it up for the new year before the first receipt of that year is entered. Assignments for future years can be edited freely.
4. **Carry forward:** if a branch has no assignment for the new year when its first receipt is entered, the previous year's assignment is copied automatically.
5. A new branch must have all three collection types assigned before receipts can be issued. Default: **A** (pre-selected on the branch form).
6. Categories that are in use cannot be deleted, only deactivated.

### 3.4 Seed data (from the client's list)
| Branches | Tithes | Offerings | Others |
|----------|--------|-----------|--------|
| General list (0003 … 0109, plus 0049, 0110, 0111) | A | A | A |
| 0013, 0037, 0053, 0087, 0097, 0105 (in the general list) | A | **E** | A |
| 0001 Katunayake, 0002 Seeduwa | D | D | D |
| 0010 Kandy, 0050 Kachchirawa, 0051 Ganemulla, 0057 Meerigama | B | A | B |
| **All other branches** (not in the lists above) | C | C | C |

- Seeded for accounting year **2026**. Existing 2026 receipt items are back-filled with their category (confirmed).
- The seed matches branches by `church_code` at run time, not by a fixed list. 0111 Blackfool was added through the system, so it is picked up if it exists. Any branch that matches no list falls back to **C**.

### 3.5 UI
- **Category master** screen: add, edit and deactivate categories.
- **Branch form → "Categories" tab:** one category per collection type per year, with a year selector and lock indicator.
- **Receipt Collection Report:** new **Category** quick filter (All / A / B / …). When a category is selected, only receipt lines in that category are included and totals are recalculated. It works together with the existing Except List.
- Category shown in the receipt history detail and in report exports.

### 3.6 Tests
Lock rule (receipt exists / doesn't exist / cancelled receipt), carry-forward, category snapshot on receipt creation, report filter combined with the Except List.

---

## 4. Requirement 02 — Finance & Accounting Module (M2)

### 4.1 Principles
- **Double-entry bookkeeping.** Every transaction is a balanced journal entry (debits = credits), and this is enforced in the service layer and with a DB check.
- **Accounting year:** 1 Jan – 31 Dec, with statuses `OPEN` and `CLOSED`.
- **Posted entries are immutable.** Corrections are made with reversing entries, never by editing or deleting.
- **Funds:** categories from 01 are a dimension on every income and expense line.
- Full audit trail: user, timestamp, source document.

### 4.2 Data model
| Table | Purpose |
|-------|---------|
| `fiscal_years` | year, start/end, status, closed_by/at |
| `accounts` | Chart of Accounts (code, name, type: ASSET / LIABILITY / EQUITY / INCOME / EXPENSE, parent, cash-flow class: OPERATING / INVESTING / FINANCING / CASH) |
| `journal_entries` | number, date, narration, source_type (RECEIPT, RECEIPT_CANCEL, PAYMENT_VOUCHER, RECEIPT_VOUCHER, CONTRA, JOURNAL, OPENING, CLOSING), source_id, status |
| `journal_lines` | entry, account, debit, credit, `category_id` (fund), `church_id` (optional) |
| `payment_vouchers` / `receipt_vouchers` | payee/payer, method (cash/bank/cheque), cheque no, approved by, printed |

A standard church Chart of Accounts is seeded and can be edited: Cash in Hand, Bank accounts, Fixed Assets (Land & Buildings…), Loans Payable, Accumulated Fund, Tithes/Offerings/Other Income, standard expense heads.

### 4.3 Functions
1. **Chart of Accounts** maintenance.
2. **Opening balances** for go-live (see Q8).
3. **Auto-posting from receipts.** Issuing a receipt posts Dr Cash in Hand / Cr Tithes, Offerings or Other Income, tagged with the category and branch. Cancelling a receipt posts the reversal.
4. **Payment Voucher** (expenditure): category is required, branch is optional, voucher is printable.
5. **Receipt Voucher** for non-collection income (rent, interest, grants, loan received).
6. **Contra** entries: cash deposited to bank, withdrawals, transfers between banks.
7. **General Journal** for adjustments, depreciation, accruals and transfers between funds.
8. **Year-end closing:** close income and expense into the Accumulated Fund (per category), lock the year, and carry balance-sheet balances forward. Only an admin can reopen a closed year, and this is logged.
9. **Permissions:** view, enter, approve and close-year are separate.

### 4.4 Reports (PDF via Jasper, Excel via POI, date range / year, comparison with previous year)
| Report | Notes |
|--------|-------|
| **Trial Balance** | Opening, period movements and closing for each account. Must balance. |
| **Income Statement** (Income & Expenditure) | Income − Expenses = Surplus / (Deficit) |
| **Balance Sheet** (Statement of Financial Position) | Assets = Liabilities + Equity (Accumulated Fund) |
| **Cash Flow Statement** | **Direct method** from cash/bank movements, classified as Operating / Investing / Financing by account class |
| **Statement of Changes in Equity** | Opening Accumulated Fund, surplus, transfers and closing, with **one column per category (fund)** |
| **Category-wise Income & Expenditure** (client-specific) | For each category: opening balance + income − expenditure = closing balance, for any date range |
| Supporting | General Ledger, Cash Book, Bank Book, Voucher register |

### 4.5 Tests
Balanced-entry enforcement, receipt auto-posting and reversal, year close/carry-forward, TB = BS identity, cash-flow closing cash = BS cash, category balance roll-forward.

---

## 5. Requirement 03 — Branch Documents Register (M3)

No file uploads. This is a reference register only.

| Field | Notes |
|-------|-------|
| Branch, Document Type | Types configurable: Deed, Land Registry, Loan, Survey Plan, Agreement, Other |
| Title, Official Reference No. | e.g. deed number, folio number, loan account number |
| Storage Reference | Physical location, e.g. "Head Office safe – File 12" |
| Document Date, Expiry / Due Date | Optional. Dashboard reminder for upcoming expiries. |
| Key Details | Type-specific fields. Deed: notary, deed no., land extent, plan/lot no. Loan: lender, amount, rate, term, instalment, end date. Free text for Other. |
| Notes, Status (Active / Archived) | |

UI: a "Documents" tab on the branch, a global search screen, and a PDF/Excel register export. Permissions: view and manage.

---

## 6. Requirement 04 — Member Services & Attendance (M3)

### 6.1 Data model
| Table | Key columns |
|-------|-------------|
| `members` | name*, mobile*, position, NIC, status |
| `member_fee_plans` | member, **weekly fee**, threshold % (default 50), effective_from / effective_to |
| `member_assignments` | member, branch, **weekday***, travel method, from location, km, **weight % (integer, ≥ 1)**, effective_from / effective_to |
| `member_attendance` | member, assignment, **exact date** |
| `member_fee_payments` | member, period_from, period_to, total amount, paid date, reference |

### 6.2 Rules
- If the same branch is attended on two weekdays, it is two assignments, each with its own weight.
- The active assignments of a member **must total exactly 100%**. Weights are whole numbers, minimum 1%. Validated on save.
- Changes are **effective-dated**: a change closes the old assignments and starts new ones, so past weeks are calculated with the rules that applied then.
- **Week** = the same week definition as receipts (`WeekUtil` + week-identifier-day setting).
- **Weekly attendance %** = sum of the weights of the assignments attended in that week.
- **Eligible fee** (confirmed):
  - weekly % **≥ threshold** → **full weekly fee**
  - weekly % **< threshold** → **weekly fee × weekly %**
- **Settings** (Settings screen, stored in `system_settings`):
  - `MEMBER_FEE_THRESHOLD_PERCENT`: default **50**
  - `MEMBER_FEE_BELOW_THRESHOLD_RULE`: `PRORATE` (fee × %, default) or `ZERO` (no fee)
  - The threshold can also be overridden per member on the fee plan (Q11).
- Fee payments can optionally post to Finance as a Payment Voucher (expense, category required).

### 6.3 Screens & reports
- Member list and form, assignment editor with a live total-% indicator, fee plan.
- **Attendance entry:** pick a date, see the members assigned on that weekday, and tick who attended (bulk entry).
- **Attendance calendar** per member: month view coloured by attended / missed / not scheduled.
- **Attendance Report** (PDF/Excel): member → week → exact date & weekday → %, eligible fee.
- **Fee Report** (PDF/Excel): member → week → %, eligible fee.
- **Paid periods:** weeks that fall in a recorded payment period are highlighted and marked **PAID** in both reports.

---

## 7. Cross-cutting
- Flyway migrations from `V40`. Each feature gets its own migration and a matching permission migration (same pattern as `V24`–`V26`).
- Activity logging for every create, update, delete, lock and year-close.
- Manual JDBC repositories, following the existing patterns (no ORM).
- JUnit tests for services and calculations. Manual UI testing on both machines.
- User manual (EN + SI) updated per milestone.

---

## 8. Confirmed Decisions (2026-10-15)
- 0111 Blackfool added through the system.
- All branches not in the client's lists: Tithes, Offerings, Others → **Category C**.
- 2026 receipts are back-filled with categories.
- Fee: at or above the threshold → full fee; below → fee × % (configurable setting).
- Accounting go-live: **1 Jan 2027**, with opening balances.

## 9. Open Questions for the Client

| # | Topic | Question | Proposed default |
|---|-------|----------|------------------|
| Q1 | Categories | What are the full names of A–E? | Seed as "Category A"… (renamable) |
| Q4 | Seeding | Confirm 0049 Handala, 0110 Jaffna and 0111 Blackfool belong to the general list (Tithes/Others = A, Offerings = A). | Yes |
| Q5 | Categories | Does "Others" mean the receipt's **Other Donations** type? | Yes |
| Q6 | Finance | Cash basis or accrual? Is there an auditor format to follow? | Double-entry, mainly cash basis, with accrual possible through journals |
| Q7 | Finance | Are collections banked? How many bank accounts are there? Is bank reconciliation needed? | Cash in Hand + bank accounts, contra entries, reconciliation later |
| Q8 | Finance | Who will supply the opening balances at 1 Jan 2027 go-live (assets, loans, bank/cash, balance per category)? | Treasurer provides by mid-Dec 2026 |
| Q9 | Finance | Is a voucher approval step (prepared → approved) required? | Yes, optional approval |
| Q11 | Members | Is the threshold global or per member? | Global default 50%, per-member override |
| Q12 | Members | Does attendance on a day other than the assigned weekday count? | No. Only the assigned weekday in that week. |
| Q13 | Members | Can a member be assigned to the same branch & weekday twice? Can members be inactive for some weeks (leave)? | No duplicates; leave weeks excluded from fee |
| Q14 | Documents | Are expiry/renewal reminders wanted on the dashboard? | Yes |
| Q15 | SMS | Is there a maximum message length or a required signature/footer for official messages? | Configurable footer setting |
