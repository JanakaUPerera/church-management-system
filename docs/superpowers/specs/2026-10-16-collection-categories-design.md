# Collection Categories — Design

**Requirement:** Phase 02 / 01 (Milestone M1)
**Date:** 2026-10-16
**Status:** Approved in brainstorming, pending spec review
**Related:** [PHASE-02-DEVELOPMENT-PLAN.md](../../PHASE-02-DEVELOPMENT-PLAN.md) §3, [CATEGORY-ASSIGNMENTS.md](../../CATEGORY-ASSIGNMENTS.md)

---

## 1. Goal

Every receipt line (Tithes / Offerings / Other Donations) belongs to exactly one **collection category** (A, B, C, …). The category is assigned per **branch × collection type × accounting year** and is **stored on the receipt line when the receipt is created**, so history never changes. In M2 these categories become funds in the accounting module.

## 2. Decisions

| # | Decision |
|---|----------|
| D1 | Accounting year = calendar year (1 Jan – 31 Dec). |
| D2 | A receipt's accounting year is the year of its **entry date** (`receipt_datetime`). |
| D3 | A **correction receipt** (`corrected_from_receipt_id`) uses the **original receipt's accounting year**, and therefore that year's categories. |
| D4 | The year lock and the receipt's category both use `receipts.accounting_year` (a stored column), not a date computed at query time. |
| D5 | Blocking cancel/correct for a **closed** accounting year is **deferred to M2** (when `fiscal_years` exists). M1 keeps the existing 7-day cancellation window unchanged. |
| D6 | The category is snapshotted on `receipt_items.category_id` (approach A). Query-time derivation and effective-dated assignments were rejected. |
| D7 | Category is **not** shown in the receipt history detail or on the printed receipt. |
| D8 | Editing a branch's assignments uses the existing `church.update` permission. |

## 3. Data model — migration `V40__collection_categories.sql`

### 3.1 New tables

**`collection_categories`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK auto | |
| `code` | VARCHAR(10) NOT NULL UNIQUE | Upper case, e.g. `A` |
| `name` | VARCHAR(100) NOT NULL | |
| `description` | VARCHAR(255) NULL | |
| `status` | ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NULL | |

**`church_category_assignments`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK auto | |
| `church_id` | BIGINT NOT NULL FK → `churches.id` | |
| `collection_type` | ENUM('OFFERTORY','TITHES','OTHER_DONATIONS') NOT NULL | Same values as `receipt_items` |
| `category_id` | BIGINT NOT NULL FK → `collection_categories.id` | |
| `accounting_year` | SMALLINT NOT NULL | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NULL | |

Unique key `(church_id, collection_type, accounting_year)`.

### 3.2 Altered tables

- **`receipts`**: add `accounting_year SMALLINT NOT NULL` (added nullable, back-filled, then made NOT NULL). Index `(church_id, accounting_year)` for the lock check.
- **`receipt_items`**: add `category_id BIGINT NOT NULL` with FK → `collection_categories.id` (added nullable, back-filled, then made NOT NULL).

### 3.3 Seed and back-fill (same migration)

1. Insert categories A–E named "Category A" … "Category E" (status ACTIVE). Names and codes can be edited later in the Category master.
2. Insert 2026 assignments for **every** branch, matched by `church_code` at run time, following [CATEGORY-ASSIGNMENTS.md](../../CATEGORY-ASSIGNMENTS.md):
   - General list → A / A / A
   - 0013, 0037, 0053, 0087, 0097, 0105 → Tithes A, Offerings **E**, Others A
   - 0001, 0002 → D / D / D
   - 0010, 0050, 0051, 0057 → Tithes B, Offerings A, Others B
   - Any existing branch not in a list → C / C / C. Matching uses `church_code` at run time, so 0111 Blackfool is picked up by the general list if it exists. Branches created after the migration get their assignments from the branch form (R5).
3. `receipts.accounting_year = YEAR(receipt_datetime)`, except correction receipts, which take the original's value.
4. `receipt_items.category_id` = the branch's assignment for (collection type, **2026**). Any receipts entered before 2026 also use the 2026 assignment.
5. Make both new columns NOT NULL.

### 3.4 Permissions (same migration)

Add `category.menu.view`, `category.create`, `category.update` and `category.delete`, and grant them to the Admin role the same way earlier permission migrations (V24–V26) do.

## 4. Business rules

| # | Rule |
|---|------|
| R1 | **Lock:** assignments for (branch, year) cannot be changed once any receipt for that branch has `accounting_year = year`, **including cancelled receipts**. The UI shows the lock and the reason. |
| R2 | Assignments for unlocked years, including future years, can be edited freely. |
| R3 | **Carry-forward:** when a receipt needs (branch, year) and no assignment exists, the branch's **most recent earlier year** is copied into that year (all three types), in the receipt's transaction, and logged. |
| R4 | If no earlier year exists either, the receipt is rejected with "Categories are not assigned for this branch. Assign them in the branch's Categories tab." |
| R5 | A new branch must have all three types assigned. The branch form saves the current-year assignments together with the branch. The default is **A** for all three types. |
| R6 | A category that is in use (referenced by any assignment or receipt line) can be neither deleted nor deactivated. Both actions are available only for an unused category. |
| R7 | An inactive category cannot be chosen for new or edited assignments. (Because of R6, an inactive category is never referenced by an assignment.) |
| R8 | Category codes are required, unique and stored in upper case. Names are required. |

## 5. Components

### 5.1 Category master
- `entity/CollectionCategory`, `repository/CollectionCategoryRepository`, `service/CollectionCategoryService`, `validation/CollectionCategoryValidator`.
- Service: `findAll`, `findActive`, `search`, `create`, `update`, `activate`, `deactivate`, `delete`.
- `delete` throws "Category is in use and cannot be deleted." and `deactivate` throws "Category is in use and cannot be deactivated." when the category is referenced (R6). `activate` is always allowed.

### 5.2 Assignments
- `entity/CategoryAssignment`, `repository/CategoryAssignmentRepository`, `service/CategoryAssignmentService`.
- `findForChurch(churchId, year)` → a `ChurchCategoryAssignments` DTO containing the three category ids, `locked`, `lockReason`, and `carriedForwardFrom` (an Integer, set when no rows exist yet and the values shown are the ones that would be carried forward).
- `saveAssignments(churchId, year, Map<CollectionType, Long>, userId)` rejects a locked year (R1), an inactive category (R7) or a missing type.
- `assignInitial(churchId, year, Map<CollectionType, Long>, connection)` is used when a branch is created (R5); a null map means A for every type.
- `resolveForReceipt(churchId, year, connection)` → `ResolvedCategories(Map<CollectionType, Long> categoryIds, Integer carriedForwardFrom)`. It locks the branch's assignment rows (`SELECT … FOR UPDATE`), applies R3 and R4, and runs on the caller's connection. It doesn't write to the activity log. `ReceiptService` logs the carry-forward **after** the receipt commits, so a receipt that rolls back never leaves a false log entry.

### 5.3 Receipt creation (`ReceiptService.createReceipt`)
- After `validateCorrectionReceipt`, work out `accountingYear`: the original's `accounting_year` for a correction, otherwise `receiptDateTime.getYear()`.
- Call `resolveForReceipt(church, accountingYear, connection)` and set `receipt.accountingYear` and `item.categoryId` before inserting.
- A failure uses the existing rollback and `logCreateFailed` path.

### 5.4 Branch creation (`ChurchService.create`)
- Accepts the chosen categories (one per type) and saves the branch and its current-year assignments in one transaction.

### 5.5 Activity log
New actions:
- `CATEGORY_CREATED`, `CATEGORY_UPDATED`, `CATEGORY_ACTIVATED`, `CATEGORY_DEACTIVATED`, `CATEGORY_DELETED`.
- `CATEGORY_ASSIGNMENTS_UPDATED`, with the old and new value for each type.
- `CATEGORY_ASSIGNMENTS_CARRIED_FORWARD` (from year → to year, branch code).

## 6. UI

### 6.1 Category master screen
- `category-view.fxml` and `CategoryController`, following the Region screen: table (Code, Name, Description, Status, In use), search, Create/Update dialog, Activate/Deactivate, Delete.
- "In use" = the number of branches assigned to the category for the current year.
- Menu item in the same group as Regions and Branches, controlled by `category.menu.view`. Each button is controlled by its own permission.

### 6.2 Branch dialog
- The dialog content becomes a `TabPane` with two tabs: **Details** (the existing fields, unchanged) and **Categories**.
- **Categories tab:**
  - A year selector. It lists the years that have assignments, plus the current year and next year. The default is the current year.
  - Three combos (Tithes, Offerings, Other Donations) listing active categories. For an inactive category that is already assigned, the combo shows it marked "(inactive)".
  - **Create mode:** current year only, with A pre-selected for all three types.
  - **Locked year:** the combos are disabled and a 🔒 label shows the lock reason.
  - **Year with no rows:** a note reads "Not set — will carry forward from {year} on first receipt", with the values pre-filled. Saving makes them explicit.
- Changed assignments are saved when **Update** is clicked, through `saveAssignments`.

### 6.3 Receipt Collection Report
- A new **Category** combo (All / one entry per category), next to the Except List button. It appears only for `RECEIPT_COLLECTION`.
- `ReportSearchCriteria.categoryId`. When it is set, `AND ri.category_id = ?` is added next to `appendExclusionFilter`, in both the row query and the summary-totals query. Totals recalculate, and receipts with no matching lines drop out.
- The PDF export shows "Category: {code} - {name}" in its existing header filter line when a category is selected. Like the other filters, it is left out when none is selected. The Excel export has no filter header today, so its rows and totals are filtered but its layout is unchanged. There is no per-row category column.
- A change to the filter is recorded through the existing `logFilterChanged`.

## 7. Testing

Service-level unit tests with fake repositories, in the style of `RegionServiceTest` and `ChurchServiceTest`:

- **Lock (R1):** unlocked when there is no receipt; locked by an active receipt; locked by a cancelled receipt only; a different year is not locked.
- **Future year (R2):** editable while the current year is locked.
- **Carry-forward (R3):** copies from the previous year; copies from an older year when a year was skipped; logs the copy.
- **No assignment (R4):** the receipt is rejected and nothing is saved.
- **Snapshot:** each receipt line gets the category for its type. A later change to a future year's assignment does not affect existing lines.
- **Correction (D3):** a correction entered in January for a December original uses the original's year.
- **New branch (R5):** saving creates three assignments; the default is A.
- **Category master (R6–R8):** delete and deactivate are blocked when in use; an inactive category is rejected in assignments; a duplicate code is rejected; the code is upper-cased.
- **Report:** the Category filter combined with the Except List gives the correct rows and totals (`ReportServiceTest`).

## 8. Out of scope (M1)

- Category filter on reports other than the Receipt Collection Report.
- Category in the receipt history detail and on printed receipts.
- Closed-year blocking (M2).
- Category-wise Income & Expenditure (M2).
- User-manual updates (EN + SI) are part of the M1 release, but are tracked separately from this design.
