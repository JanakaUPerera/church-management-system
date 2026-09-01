# Receipt Week Boundary — Configurable Identifier Day

Date: 2026-09-01
Status: Approved (design), pending implementation plan

## Problem

The system currently defines a "week" as Monday–Sunday, and receipt entry
defaults to the *previous* week's Monday (a one-week lag), because churches
submit money on a Monday for the week that just ended the day before.
Presenting a Monday-start-of-week picker at receipt entry time — right when
the user is thinking about "the Monday I'm submitting on" — creates
confusion: the Monday they're standing on is not the Monday the form wants.

## Decision

Redefine the week around the **submission day** as its identifier, rather
than around the start of the collection period:

- Week boundaries: the identifier day (default **Monday**) is the *last*
  day of the week; the week spans `(identifier − 6 days)` through
  `identifier`. With the default, that's **Tuesday → Monday**.
- Receipt-entry default ("current submission week"): the identifier day
  on or before today — i.e. **no lag**. If today *is* the identifier day,
  the default is today.
- The identifier day is a new configurable system setting, defaulting to
  Monday (reproducing the above). Setting it to Sunday reproduces the
  *original* Monday–Sunday week exactly, so the old behavior remains
  reachable, just no longer hardcoded.

## Non-goals

- No migration or reinterpretation of historical receipts. Existing
  `week_start_date`/`week_end_date` values are left exactly as stored;
  each receipt remains internally consistent (`end = start + 6`) under
  whatever boundary was in effect when it was created. Reports spanning
  the cutover date will show a one-day seam in historical data — expected
  and accurate, since it reflects how the organization actually operated
  at the time.
- No DB schema changes: no new/renamed columns, no new constraints. The
  existing `week_start_date` (earlier boundary) / `week_end_date`
  (later boundary, now the identifier) columns keep their current roles
  and every existing report/repository SQL filter on them is untouched.

## Design

### A. `WeekUtil` — generalized around a configurable identifier day

`WeekUtil` stays a pure, dependency-free static utility. Callers resolve
the configured `DayOfWeek` and pass it in; `WeekUtil` does no config
lookups itself.

Replace the current hardcoded-Monday/Sunday API:

```
getPreviousWeekMonday(today)
getCurrentWeekMonday(today)
getPreviousWeekSunday(today)
isMonday(date)
isWeekStartMonday(date)
isWeekEndSunday(date)
getSundayForMonday(monday)
isBackWeek(selectedWeekStart, today)
isCurrentSubmissionWeek(selectedWeekStart, today)   // appears unused — verify at implementation time
```

with a generic, identifier-day-parameterized API:

- `currentIdentifier(LocalDate today, DayOfWeek identifierDay)` →
  `today.with(TemporalAdjusters.previousOrSame(identifierDay))`. This is
  "the most recently completed (or completing-today) week" — used both as
  the receipt-entry default and as the cutoff for "future weeks" / "back
  week" comparisons everywhere in the app.
- `weekStartFor(LocalDate identifierDate)` → `identifierDate.minusDays(6)`.
- `isIdentifierDay(LocalDate date, DayOfWeek identifierDay)` →
  `date != null && date.getDayOfWeek() == identifierDay`.
- `isWeekStartDay(LocalDate date, DayOfWeek identifierDay)` →
  `date != null && date.getDayOfWeek() == identifierDay.plus(1)` (the day
  6 days before the identifier day, cyclically).
- `isBackWeek(LocalDate selectedIdentifier, LocalDate today, DayOfWeek identifierDay)`
  → `selectedIdentifier != null && selectedIdentifier.isBefore(currentIdentifier(today, identifierDay))`.
- `parseIdentifierDay(String settingValue)` → parses the system-setting
  string (`DayOfWeek.valueOf(...)`, case-insensitive) to a `DayOfWeek`,
  defaulting to `MONDAY` for null/blank/unparseable input. This is the
  single place default-and-fallback logic lives, so five-plus call sites
  don't each re-implement the Monday fallback.

All existing callers of the removed methods are updated to the new API
(see sections C and D). Any test or call site still referencing a removed
method by name is a signal that call site was missed.

### B. New system setting

- Key: `receipt.week.identifier.day`
- Category: `RECEIPT`
- Value domain: one of the seven `DayOfWeek` enum names
  (`MONDAY`…`SUNDAY`), case-insensitive on read, stored uppercase
- Default: `MONDAY`
- Editable: `true`

Seeded via a new Flyway migration, following the existing insert-if-absent
pattern used for `receipt.late.reason.required`
(`V30__receipt_late_reason_setting.sql`):

```sql
INSERT INTO system_settings (setting_key, setting_value, setting_type, category, description, is_editable)
SELECT 'receipt.week.identifier.day', 'MONDAY', 'ENUM', 'RECEIPT',
       'Day of week that identifies/ends a submission week (week = identifier day minus 6 days, through identifier day)', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'receipt.week.identifier.day'
);
```

(Exact next Flyway version number to be picked at implementation time —
note the branch already has in-flight migration numbering changes.)

`SystemSettingService.validateValue` gains a case validating this key
against the seven day names, using the existing `requireOneOf(...)`
helper (same shape as `receipt.default.language`'s ENGLISH/SINHALA/TAMIL
check).

`SettingsController` gains a `ComboBox<String>` (`weekIdentifierDayComboBox`
or similar) populated with the seven day names, following the exact
pattern of `receiptLanguageComboBox` (load, display, save, default-value
fallback to `"MONDAY"`).

Every consumer resolves the setting the same way `ReceiptService` already
resolves `receipt.allow.back.week`:

```java
DayOfWeek identifierDay = WeekUtil.parseIdentifierDay(
        configurationCache.getString("receipt.week.identifier.day"));
```

### C. Receipt creation

- **`weekStartDate` / `weekEndDate`** columns and DTO fields (`Receipt`,
  `CreateReceiptRequest`, `ReceiptResponseDto`) keep their current roles:
  start = earlier boundary date, end = later/identifier date. Nothing
  renamed at the DB or DTO layer.
- **`ReceiptEntryController`**: `weekStartDatePicker` is retargeted to the
  identifier date — restricted to the configured identifier day via a
  generalized `DatePickerUtil.enableDayOfWeekOnly(picker, DayOfWeek)`
  (replacing `enableMondaysOnly`), defaulting to
  `WeekUtil.currentIdentifier(today, identifierDay)`. On submit, this
  picker's value becomes `request.weekEndDate`, and
  `request.weekStartDate = WeekUtil.weekStartFor(pickedValue)`. The
  existing read-only `weekEndDateLabel` becomes the computed **start**
  boundary label. FXML copy is updated (e.g. picker label → "Week Ending
  / Submission Date", computed label → "Week Start Date") so the field
  names on screen match what's actually being picked vs. computed —
  this label clarity is the crux of the fix.
  `DatePickerUtil.restrictToRange` for `churchServiceDatePicker` is
  updated to derive its bounds from the (now swapped) start/end sources.
- **`ReceiptValidator.validateForCreate`** gains a `DayOfWeek identifierDay`
  parameter. Header validation changes:
  - `weekEnd` (the picked value) must satisfy `isIdentifierDay(weekEnd, identifierDay)`
    (replaces "week start must be Monday").
  - `weekEnd` must not be after `currentIdentifier(today, identifierDay)`
    (replaces the old "future weeks" check, which compared `weekStart`
    against `getPreviousWeekMonday`).
  - `weekStart` must still equal `weekEnd.minusDays(6)` (unchanged check,
    just the source values have swapped roles).
  - Church-service-date-within-week and late-reason-required logic are
    unchanged in shape, driven by the new `isBackWeek` call.
- **`ReceiptService`**: resolves `identifierDay` once per
  `createReceipt`/`validateReceiptBeforeConfirmation` call from
  `configurationCache`, threads it into `ReceiptValidator.validateForCreate`
  and into every `WeekUtil.isBackWeek(...)` call (`lateSubmission`
  computation in both methods).
- `ReceiptRepository` uniqueness/correction/search logic is untouched — it
  already just compares whatever `weekStartDate` values are passed in, and
  those continue to be internally consistent (`end = start + 6`).

### D. Reports / Dashboard / Submission Status — consistent picker, unchanged SQL

`ReportsController`, `DashboardHomeController`, and
`SubmissionStatusController`'s week filters switch to picking the
identifier day too, using the same `enableDayOfWeekOnly` helper and the
same `currentIdentifier`-based default — so the whole app uses one
picking convention ("click the Monday you're thinking of").

At the point each screen's controller/service builds its
criteria/request object, the picked identifier date is converted with
`WeekUtil.weekStartFor(...)` **before** being handed to the existing
service/repository methods:

- `ReportService.defaultCriteria` / `quickRange("This Week"/"Previous Week")`:
  compute the identifier date first (`currentIdentifier`, or
  `currentIdentifier(...).minusWeeks(1)` for "Previous Week"), then set
  `criteria.weekStartDate = weekStartFor(identifierDate)`. `ReportRepository`
  keeps filtering `r.week_start_date = ?` unmodified.
- `DashboardService.defaultWeeklyRange` / `loadWeeklyDashboard`: same
  conversion; `DashboardRepository` SQL unmodified.
- `SubmissionStatusService.defaultWeekStart` / `safeWeekStart`: same
  conversion; `SubmissionStatusRepository` SQL unmodified.

`DashboardService.isCurrentCalendarWeekRange` — flagged in the earlier
codebase study as a private, duplicated inline reimplementation of the
Monday-lookup — is replaced with a call through the shared
`currentIdentifier` path instead of staying a second source of truth.

Each of these three services validates the *picked* value the same way
`ReceiptValidator` does (must be the identifier day; end = start + 6),
using the generalized `WeekUtil` checks instead of the hardcoded
Monday/Sunday ones currently in `DashboardService.validateWeek`,
`SubmissionStatusService.safeWeekStart`, and `ReportService.normalizeAndValidate`.

### E. Testing

- `WeekUtilTest` (new/updated): default-day (MONDAY) behavior reproducing
  today's Tue–Mon examples; a non-default configured day (e.g. WEDNESDAY)
  to prove genericity; `parseIdentifierDay` fallback behavior for
  null/blank/garbage input.
- `ReceiptServiceTest`, `ReceiptValidator`-adjacent tests: updated for the
  new picked-field semantics (identifier in `weekEndDate`) and the
  back-week/late-submission boundary under the new default-day math.
- `ReportServiceTest`, `DashboardServiceTest`, `SubmissionStatusServiceTest`:
  updated for the new default/quick-range computation and validation
  messages.
- `SystemSettingServiceTest`: new case for `receipt.week.identifier.day`
  validation (accepts the seven day names, rejects anything else).
- Existing tests referencing removed `WeekUtil` methods by name are the
  signal for any missed call site.
