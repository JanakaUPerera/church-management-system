# Receipt Week Boundary — Configurable Identifier Day Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redefine the receipt "week" around a configurable identifier day (default Monday, the submission day) instead of the hardcoded Monday-start/Sunday-end week, so receipt entry defaults to the current, un-lagged submission week — and apply the same picking convention to the Reports, Dashboard, and Submission Status week filters.

**Architecture:** A single pure utility (`WeekUtil`) is generalized to take an explicit `DayOfWeek identifierDay` parameter instead of hardcoding Monday/Sunday. A new system setting (`receipt.week.identifier.day`, default `MONDAY`) drives it everywhere via the existing `SystemConfigurationCache`. `week_start_date`/`week_end_date` columns and all report/repository SQL keep their existing roles (start = earlier boundary, end = later boundary) — only what value the app computes for them changes. Every week-picking screen (receipt entry, reports, dashboard, submission status) shows a picker for the *identifier* day and derives the earlier boundary from it via `WeekUtil.weekStartFor(...)`.

**Tech Stack:** Java 21, JavaFX, JUnit 5, Flyway (MySQL migrations), Maven.

**Spec:** [docs/superpowers/specs/2026-09-01-receipt-week-boundary-config-design.md](../specs/2026-09-01-receipt-week-boundary-config-design.md)

## Global Constraints

- Week boundaries: `identifier day` (default **MONDAY**) is the *last* day of the week; the week spans `(identifier − 6 days)` through `identifier`.
- Receipt-entry / report / dashboard / submission-status default = the identifier day on or before today (`WeekUtil.currentIdentifier`) — **no lag**.
- New system setting `receipt.week.identifier.day` (category `RECEIPT`, ENUM of the 7 `DayOfWeek` names, default `MONDAY`, editable) drives every consumer via `WeekUtil.parseIdentifierDay(configurationCache.getString("receipt.week.identifier.day"))`.
- `week_start_date` / `week_end_date` DB columns, and all existing `ReportRepository` / `ReceiptRepository` / `DashboardRepository` / `SubmissionStatusRepository` SQL, keep their current roles unchanged — start = earlier boundary, end = later boundary. No schema changes.
- No migration or reinterpretation of historical receipts.
- Every service gains its `SystemConfigurationCache` dependency by **adding a new trailing-parameter constructor overload** and making the existing (shorter) constructor delegate to it via `SystemConfigurationCache.getInstance()` — this keeps every existing test's constructor call compiling unchanged (confirmed safe: `SystemConfigurationCache.getString(...)` never touches the database unless `reload()`/`loadSettings()` is called, which none of these services do).
- Every controller resolves the identifier day itself via a private `resolveIdentifierDay()` helper reading `SystemConfigurationCache.getInstance().getString("receipt.week.identifier.day")` through `WeekUtil.parseIdentifierDay(...)` — same pattern already used in services, no new shared abstraction.
- Copy convention used everywhere a picker/label pair exists: the picker's label reads **"Week Ending Date"**, the computed read-only label reads **"Week Start Date"**.

---

### Task 1: `WeekUtil` — generalize around a configurable identifier day

**Files:**
- Modify: `src/main/java/com/churchmanagement/util/WeekUtil.java`
- Test: `src/test/java/com/churchmanagement/util/WeekUtilTest.java` (new file)

**Interfaces:**
- Produces: `WeekUtil.currentIdentifier(LocalDate today, DayOfWeek identifierDay)`, `WeekUtil.weekStartFor(LocalDate identifierDate)`, `WeekUtil.isIdentifierDay(LocalDate date, DayOfWeek identifierDay)`, `WeekUtil.isWeekStartDay(LocalDate date, DayOfWeek identifierDay)`, `WeekUtil.isBackWeek(LocalDate selectedIdentifier, LocalDate today, DayOfWeek identifierDay)`, `WeekUtil.parseIdentifierDay(String settingValue)`, `WeekUtil.displayName(DayOfWeek dayOfWeek)` — every later task consumes these exact names/signatures.

- [ ] **Step 1: Write the failing test file**

```java
package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekUtilTest {

    @Test
    void currentIdentifierIsTodayWhenTodayIsTheIdentifierDay() {
        LocalDate monday = LocalDate.of(2026, 5, 18);

        assertEquals(monday, WeekUtil.currentIdentifier(monday, DayOfWeek.MONDAY));
    }

    @Test
    void currentIdentifierIsTheMostRecentIdentifierDayOnOtherDays() {
        LocalDate sunday = LocalDate.of(2026, 5, 24);

        assertEquals(LocalDate.of(2026, 5, 18), WeekUtil.currentIdentifier(sunday, DayOfWeek.MONDAY));
    }

    @Test
    void currentIdentifierSupportsANonDefaultDay() {
        LocalDate wednesday = LocalDate.of(2026, 5, 20);

        assertEquals(LocalDate.of(2026, 5, 15), WeekUtil.currentIdentifier(wednesday, DayOfWeek.FRIDAY));
    }

    @Test
    void weekStartForIsSixDaysBeforeTheIdentifier() {
        assertEquals(LocalDate.of(2026, 5, 12), WeekUtil.weekStartFor(LocalDate.of(2026, 5, 18)));
    }

    @Test
    void weekStartForReturnsNullForNullInput() {
        assertNull(WeekUtil.weekStartFor(null));
    }

    @Test
    void isIdentifierDayMatchesOnlyTheConfiguredDay() {
        assertTrue(WeekUtil.isIdentifierDay(LocalDate.of(2026, 5, 18), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isIdentifierDay(LocalDate.of(2026, 5, 17), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isIdentifierDay(null, DayOfWeek.MONDAY));
    }

    @Test
    void isWeekStartDayMatchesSixDaysBeforeTheIdentifierDay() {
        assertTrue(WeekUtil.isWeekStartDay(LocalDate.of(2026, 5, 12), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isWeekStartDay(LocalDate.of(2026, 5, 11), DayOfWeek.MONDAY));
    }

    @Test
    void isBackWeekIsTrueOnlyBeforeTheCurrentIdentifier() {
        LocalDate today = LocalDate.of(2026, 5, 18);

        assertFalse(WeekUtil.isBackWeek(LocalDate.of(2026, 5, 18), today, DayOfWeek.MONDAY));
        assertTrue(WeekUtil.isBackWeek(LocalDate.of(2026, 5, 11), today, DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isBackWeek(null, today, DayOfWeek.MONDAY));
    }

    @Test
    void parseIdentifierDayDefaultsToMondayForBlankOrInvalidInput() {
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay(null));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay(""));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay("  "));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay("NOT_A_DAY"));
    }

    @Test
    void parseIdentifierDayParsesAConfiguredDayCaseInsensitively() {
        assertEquals(DayOfWeek.WEDNESDAY, WeekUtil.parseIdentifierDay("wednesday"));
        assertEquals(DayOfWeek.SUNDAY, WeekUtil.parseIdentifierDay("SUNDAY"));
    }

    @Test
    void displayNameFormatsAsFullEnglishDayName() {
        assertEquals("Monday", WeekUtil.displayName(DayOfWeek.MONDAY));
        assertEquals("Tuesday", WeekUtil.displayName(DayOfWeek.TUESDAY));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `mvn -q -Dtest=WeekUtilTest test`
Expected: FAIL — `WeekUtil` has no `currentIdentifier`/`weekStartFor`/`isIdentifierDay`/`isWeekStartDay`/`isBackWeek(3-arg)`/`parseIdentifierDay`/`displayName` members yet.

- [ ] **Step 3: Rewrite `WeekUtil`**

Replace the full file content:

```java
package com.churchmanagement.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class WeekUtil {
    public static final DayOfWeek DEFAULT_IDENTIFIER_DAY = DayOfWeek.MONDAY;

    private WeekUtil() {
    }

    /**
     * The identifier date of the week containing {@code today} — the most
     * recent occurrence of {@code identifierDay} on or before {@code today}.
     * This is "the current submission week" with no lag: if today already
     * is the identifier day, today is returned.
     */
    public static LocalDate currentIdentifier(LocalDate today, DayOfWeek identifierDay) {
        return today.with(TemporalAdjusters.previousOrSame(identifierDay));
    }

    /** The earlier boundary of the week ending on {@code identifierDate} (6 days before it). */
    public static LocalDate weekStartFor(LocalDate identifierDate) {
        return identifierDate == null ? null : identifierDate.minusDays(6);
    }

    /** True if {@code date} falls on the configured identifier day (the week's last day). */
    public static boolean isIdentifierDay(LocalDate date, DayOfWeek identifierDay) {
        return date != null && date.getDayOfWeek() == identifierDay;
    }

    /** True if {@code date} falls on the week's first day (6 days before the identifier day). */
    public static boolean isWeekStartDay(LocalDate date, DayOfWeek identifierDay) {
        return date != null && date.getDayOfWeek() == identifierDay.plus(1);
    }

    /** True if the selected week's identifier date is before the current submission week's identifier. */
    public static boolean isBackWeek(LocalDate selectedIdentifier, LocalDate today, DayOfWeek identifierDay) {
        return selectedIdentifier != null && selectedIdentifier.isBefore(currentIdentifier(today, identifierDay));
    }

    /** Parses the {@code receipt.week.identifier.day} setting value, defaulting to Monday. */
    public static DayOfWeek parseIdentifierDay(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            return DEFAULT_IDENTIFIER_DAY;
        }
        try {
            return DayOfWeek.valueOf(settingValue.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DEFAULT_IDENTIFIER_DAY;
        }
    }

    /** Full English display name for a day of week, e.g. {@code MONDAY} -&gt; {@code "Monday"}. */
    public static String displayName(DayOfWeek dayOfWeek) {
        return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
```

Note: this removes `getPreviousWeekMonday`, `getCurrentWeekMonday`, `getPreviousWeekSunday`, `isMonday`, `isWeekStartMonday`, `isWeekEndSunday`, `getSundayForMonday`, the 2-arg `isBackWeek`, and `isCurrentSubmissionWeek`. The codebase will not compile again until Tasks 4-13 update every caller — that's expected and resolved by the end of this plan.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=WeekUtilTest test`
Expected: PASS (the rest of the module will still fail to *compile* until later tasks — that's fine, `-Dtest=WeekUtilTest` compiles only what it needs for this class... if your Maven setup compiles the whole module first and fails, skip full verification here and rely on Task 14's final full-suite run; visually confirm `WeekUtilTest` methods are logically correct instead).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/util/WeekUtil.java src/test/java/com/churchmanagement/util/WeekUtilTest.java
git commit -m "feat(receipt): generalize WeekUtil around a configurable identifier day

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `DatePickerUtil` — day-of-week-parameterized restrictions

**Files:**
- Modify: `src/main/java/com/churchmanagement/util/DatePickerUtil.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `DatePickerUtil.enableDayOfWeekOnly(DatePicker, DayOfWeek)`, `DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates(DatePicker, DayOfWeek)` — consumed by Tasks 7, 9, 11, 13.

There is no `DatePickerUtilTest` in this codebase (JavaFX controls aren't unit-tested here) — this task is verified by inspection and by the controller tasks that consume it.

- [ ] **Step 1: Add the two new methods, keep the old Monday-only ones for now**

Add these two methods to `DatePickerUtil` (after `enableMondaysOnlyAndDisableFutureDates`, before `restrictToRange`):

```java
    public static void enableDayOfWeekOnly(DatePicker datePicker, DayOfWeek allowedDay) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.getDayOfWeek() != allowedDay);
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.getDayOfWeek() != allowedDay) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void enableDayOfWeekOnlyAndDisableFutureDates(DatePicker datePicker, DayOfWeek allowedDay) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.getDayOfWeek() != allowedDay || date.isAfter(LocalDate.now()));
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && (newValue.getDayOfWeek() != allowedDay || newValue.isAfter(LocalDate.now()))) {
                datePicker.setValue(oldValue);
            }
        });
    }
```

(`enableMondaysOnly`, `enableMondaysOnlyAndDisableFutureDates`, `disableFutureDates`, `restrictToRange`, `applySystemDateFormat`, and the private `isMonday` helper stay untouched for now — their remaining callers are migrated in Tasks 7, 9, 11, 13, and the now-dead Monday-only methods are removed in Task 13's cleanup step.)

- [ ] **Step 2: Compile-check**

Run: `mvn -q -Dtest=WeekUtilTest compile`
Expected: compiles (this file has no test of its own; correctness is confirmed visually and by later controller tasks).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/churchmanagement/util/DatePickerUtil.java
git commit -m "feat(receipt): add day-of-week-parameterized DatePicker restrictions

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: New system setting — `receipt.week.identifier.day`

**Files:**
- Create: `src/main/resources/db/migration/V38__receipt_week_identifier_day_setting.sql`
- Modify: `src/main/java/com/churchmanagement/service/SystemSettingService.java`
- Test: `src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java`

**Interfaces:**
- Produces: the `receipt.week.identifier.day` row in `system_settings` (default `MONDAY`), and validation for it in `SystemSettingService`. Consumed by every service/controller task from Task 6 onward via `SystemConfigurationCache.getString("receipt.week.identifier.day")`.

- [ ] **Step 1: Write the failing test cases**

In `SystemSettingServiceTest`, change the `loadsSettings` test's expected count from 18 to 19:

```java
    @Test
    void loadsSettings() {
        AuthContext.setCurrentUser(settingsAdmin());
        SystemSettingService service = serviceWithDefaults(new TestSystemConfigurationCache());

        List<SystemSettingDto> settings = service.loadSettings();

        assertEquals(19, settings.size());
        assertEquals("organization.name", settings.getFirst().getSettingKey());
    }
```

Add a new test after `rejectsInvalidLateReasonRequiredFlag`:

```java
    @Test
    void rejectsInvalidWeekIdentifierDay() {
        assertInvalid("receipt.week.identifier.day", "FUNDAY",
                "Week identifier day must be MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, or SUNDAY.");
    }
```

In `FakeSystemSettingRepository`'s constructor, add the new row right after `receipt.default.language`:

```java
            add(setting("receipt.default.language", "ENGLISH", "ENUM", "RECEIPT"));
            add(setting("receipt.week.identifier.day", "MONDAY", "ENUM", "RECEIPT"));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=SystemSettingServiceTest test`
Expected: FAIL — `loadsSettings` sees 18 (fixture already has the new row from Step 1, but validation for the new key doesn't exist yet) and `rejectsInvalidWeekIdentifierDay` fails because `updateSettings` doesn't reject `"FUNDAY"` for that key (falls through the `default -> {}` branch, no exception thrown).

- [ ] **Step 3: Add validation in `SystemSettingService.validateValue`**

In `src/main/java/com/churchmanagement/service/SystemSettingService.java`, add a case right after `receipt.default.language`:

```java
            case "receipt.default.language" -> requireOneOf(normalized,
                    "Receipt default language must be ENGLISH, SINHALA, or TAMIL.",
                    "ENGLISH", "SINHALA", "TAMIL");
            case "receipt.week.identifier.day" -> requireOneOf(normalized,
                    "Week identifier day must be MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, or SUNDAY.",
                    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
```

- [ ] **Step 4: Create the Flyway migration**

```sql
INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'receipt.week.identifier.day', 'MONDAY', 'ENUM', 'RECEIPT',
       'Day of week that identifies/ends a submission week (the week runs from 6 days before this day through this day)',
       TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'receipt.week.identifier.day'
);
```

Save as `src/main/resources/db/migration/V38__receipt_week_identifier_day_setting.sql`. (If another migration has claimed `V38` by the time this task runs, use `git log --oneline -- src/main/resources/db/migration` / `ls src/main/resources/db/migration` to find the actual next free version and rename the file accordingly — the content stays the same.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=SystemSettingServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V38__receipt_week_identifier_day_setting.sql \
        src/main/java/com/churchmanagement/service/SystemSettingService.java \
        src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java
git commit -m "feat(receipt): add receipt.week.identifier.day system setting

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Settings UI — expose the week identifier day

**Files:**
- Modify: `src/main/java/com/churchmanagement/controller/SettingsController.java`
- Modify: `src/main/resources/com/churchmanagement/view/settings-view.fxml`

**Interfaces:**
- Consumes: the `receipt.week.identifier.day` setting key from Task 3.
- Produces: nothing consumed by later tasks — this is a leaf UI task. Verified manually (no controller tests exist in this codebase).

- [ ] **Step 1: Add the FXML control**

In `settings-view.fxml`, in the RECEIPT settings `GridPane` (the same one holding `receiptLanguageComboBox`), add a new row after the `Default Receipt Language` row (row index 4), then bump the `Save Receipt` button's row usage isn't index-based so no other change is needed:

```xml
                    <Label text="Default Receipt Language" GridPane.columnIndex="0" GridPane.rowIndex="4"/>
                    <ComboBox fx:id="receiptLanguageComboBox" prefWidth="180" GridPane.columnIndex="1" GridPane.rowIndex="4"/>
                    <Label fx:id="receiptLanguageErrorLabel" styleClass="error-label" GridPane.columnIndex="2" GridPane.rowIndex="4"/>

                    <Label text="Week Identifier Day" GridPane.columnIndex="0" GridPane.rowIndex="5"/>
                    <ComboBox fx:id="weekIdentifierDayComboBox" prefWidth="180" GridPane.columnIndex="1" GridPane.rowIndex="5"/>
                </GridPane>
```

(Replace the existing three lines ending in `GridPane.rowIndex="4"/>` plus the closing `</GridPane>` with the block above — i.e. insert the new `Label`/`ComboBox` pair before the existing `</GridPane>` closing tag.)

- [ ] **Step 2: Wire it up in `SettingsController`**

Add the field declaration next to `receiptLanguageComboBox`:

```java
    @FXML private ComboBox<String> receiptLanguageComboBox;
    @FXML private ComboBox<String> weekIdentifierDayComboBox;
```

In `initialize()`, populate its items next to the language combo box:

```java
        receiptLanguageComboBox.getItems().setAll("ENGLISH", "SINHALA", "TAMIL");
        weekIdentifierDayComboBox.getItems().setAll(
                "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
```

In `saveReceiptSettings()`, add the new setting to the save list:

```java
    @FXML
    private void saveReceiptSettings() {
        saveSettings(List.of(
                request("receipt.number.prefix", receiptPrefixField.getText()),
                request("receipt.sequence.padding", receiptPaddingField.getText()),
                request("receipt.allow.back.week", String.valueOf(receiptAllowBackWeekCheckBox.isSelected())),
                request("receipt.late.reason.required", String.valueOf(receiptLateReasonRequiredCheckBox.isSelected())),
                request("receipt.default.language", receiptLanguageComboBox.getValue()),
                request("receipt.week.identifier.day", weekIdentifierDayComboBox.getValue())
        ));
    }
```

In `applySettings(...)`, load the value with a `MONDAY` fallback:

```java
        receiptLanguageComboBox.setValue(defaultValue(values.get("receipt.default.language"), "ENGLISH"));
        weekIdentifierDayComboBox.setValue(defaultValue(values.get("receipt.week.identifier.day"), "MONDAY"));
```

- [ ] **Step 2: Manual verification**

Run the app (see the `run` skill if available), open Settings → Receipt, confirm "Week Identifier Day" shows a dropdown defaulting to `MONDAY`, and that saving persists a different value and reloads correctly after navigating away and back.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/SettingsController.java \
        src/main/resources/com/churchmanagement/view/settings-view.fxml
git commit -m "feat(settings): expose the week identifier day setting in Settings UI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: `ReceiptValidator` — validate around the identifier day

**Files:**
- Modify: `src/main/java/com/churchmanagement/validation/ReceiptValidator.java`

**Interfaces:**
- Consumes: `WeekUtil.isIdentifierDay`, `WeekUtil.isWeekStartDay`, `WeekUtil.currentIdentifier`, `WeekUtil.displayName` (Task 1).
- Produces: `ReceiptValidator.validateForCreate(CreateReceiptRequest, LocalDate today, boolean lateSubmission, boolean lateSubmissionReasonRequired, DayOfWeek identifierDay)` — a new 5th parameter. Consumed by Task 6 (`ReceiptService`).

There's no standalone `ReceiptValidatorTest` — this class is exercised entirely through `ReceiptServiceTest` (Task 6), so this task's correctness is verified there. Do not run tests standalone after this task; the module won't compile again until Task 6 updates `ReceiptService`.

- [ ] **Step 1: Rewrite `ReceiptValidator`**

Replace the full file content:

```java
package com.churchmanagement.validation;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.util.WeekUtil;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ReceiptValidator {
    private ReceiptValidator() {
    }

    public static List<String> validateForCreate(CreateReceiptRequest request, LocalDate today, boolean lateSubmission,
                                                 boolean lateSubmissionReasonRequired, DayOfWeek identifierDay) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Church is required.");
            errors.add("Week end date is required.");
            errors.add("Date of the church service is required.");
            errors.add("Submitted by name is required.");
            errors.add("At least one collection item is required.");
            return errors;
        }

        validateHeader(request, today, lateSubmission, lateSubmissionReasonRequired, identifierDay, errors);
        validateItems(request.getItems(), errors);
        return errors;
    }

    private static void validateHeader(CreateReceiptRequest request, LocalDate today, boolean lateSubmission,
                                       boolean lateSubmissionReasonRequired, DayOfWeek identifierDay,
                                       List<String> errors) {
        if (request.getChurchId() == null) {
            errors.add("Church is required.");
        }

        LocalDate weekStart = request.getWeekStartDate();
        LocalDate weekEnd = request.getWeekEndDate();
        if (weekEnd == null) {
            errors.add("Week end date is required.");
        } else {
            if (!WeekUtil.isIdentifierDay(weekEnd, identifierDay)) {
                errors.add("Week end date must be a " + WeekUtil.displayName(identifierDay) + ".");
            }
            if (weekEnd.isAfter(WeekUtil.currentIdentifier(today, identifierDay))) {
                errors.add("Future weeks are not allowed.");
            }
        }

        if (weekStart == null || !WeekUtil.isWeekStartDay(weekStart, identifierDay)) {
            errors.add("Week start date must be a " + WeekUtil.displayName(identifierDay.plus(1)) + ".");
        }

        if (weekStart != null && weekEnd != null && !weekEnd.equals(weekStart.plusDays(6))) {
            errors.add("Week end date must be 6 days after week start date.");
        }

        LocalDate churchServiceDate = request.getChurchServiceDate();
        if (churchServiceDate == null) {
            errors.add("Date of the church service is required.");
        } else if (churchServiceDate.isAfter(today)) {
            errors.add("Date of the church service cannot be in the future.");
        } else if (weekStart != null && weekEnd != null
                && (churchServiceDate.isBefore(weekStart) || churchServiceDate.isAfter(weekEnd))) {
            errors.add("Date of the church service must be within the selected week.");
        }

        if (request.getSubmittedByName() == null || request.getSubmittedByName().isBlank()) {
            errors.add("Submitted by name is required.");
        }

        if (lateSubmission && lateSubmissionReasonRequired
                && (request.getLateSubmissionReason() == null || request.getLateSubmissionReason().isBlank())) {
            errors.add("Late submission reason is required for back week receipts.");
        }
    }

    private static void validateItems(List<ReceiptItemDto> items, List<String> errors) {
        if (items == null || items.isEmpty()) {
            errors.add("At least one collection item is required.");
            return;
        }

        Set<CollectionType> seenTypes = EnumSet.noneOf(CollectionType.class);
        for (ReceiptItemDto item : items) {
            if (item == null || item.getCollectionType() == null || !seenTypes.add(item.getCollectionType())) {
                errors.add("Duplicate collection type is not allowed.");
            }

            if (item == null || item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be greater than zero.");
            } else if (item.getAmount().stripTrailingZeros().scale() > 2) {
                errors.add("Amount must have a maximum of two decimal places.");
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/churchmanagement/validation/ReceiptValidator.java
git commit -m "feat(receipt): validate week end/start against the configured identifier day

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: `ReceiptService` — thread the identifier day through, fix the late-submission field

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/ReceiptService.java`
- Test: `src/test/java/com/churchmanagement/service/ReceiptServiceTest.java`

**Interfaces:**
- Consumes: `ReceiptValidator.validateForCreate(..., DayOfWeek)` (Task 5), `WeekUtil.isBackWeek(LocalDate, LocalDate, DayOfWeek)` / `WeekUtil.parseIdentifierDay` (Task 1).
- Produces: nothing new consumed elsewhere — `ReceiptService`'s public API is unchanged.

**Important correctness fix:** the identifier moved from `weekStartDate` to `weekEndDate`. Every `WeekUtil.isBackWeek(...)` call in this class must read `request.getWeekEndDate()` (not `getWeekStartDate()`).

- [ ] **Step 1: Update `ReceiptService`**

Add the import:

```java
import java.time.DayOfWeek;
```

In `createReceipt(CreateReceiptRequest request, boolean printOriginal)`, change:

```java
        LocalDate today = LocalDate.now(clock);
        boolean lateSubmission = request != null && WeekUtil.isBackWeek(request.getWeekStartDate(), today);
        enforceBackWeekSetting(lateSubmission);
        validateRequest(request, today, lateSubmission);
```

to:

```java
        LocalDate today = LocalDate.now(clock);
        DayOfWeek identifierDay = resolveIdentifierDay();
        boolean lateSubmission = request != null && WeekUtil.isBackWeek(request.getWeekEndDate(), today, identifierDay);
        enforceBackWeekSetting(lateSubmission);
        validateRequest(request, today, lateSubmission, identifierDay);
```

In `validateReceiptBeforeConfirmation(CreateReceiptRequest request)`, apply the same change:

```java
        LocalDate today = LocalDate.now(clock);
        DayOfWeek identifierDay = resolveIdentifierDay();
        boolean lateSubmission = request != null && WeekUtil.isBackWeek(request.getWeekEndDate(), today, identifierDay);
        enforceBackWeekSetting(lateSubmission);
        validateRequest(request, today, lateSubmission, identifierDay);
```

Update `validateRequest` and add `resolveIdentifierDay`:

```java
    private void validateRequest(CreateReceiptRequest request, LocalDate today, boolean lateSubmission,
                                 DayOfWeek identifierDay) {
        List<String> errors = ReceiptValidator.validateForCreate(request, today, lateSubmission,
                isLateSubmissionReasonRequired(), identifierDay);
        if (!errors.isEmpty()) {
            throw new ReceiptException(String.join("\n", errors));
        }
    }

    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(configurationCache.getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 2: Rewrite `ReceiptServiceTest`**

The fixed clock is `2026-05-18T09:00:00Z` (a Monday). Under the new default identifier day (Monday), the current submission week is now `[2026-05-12, 2026-05-18]` (Tuesday–Monday) instead of the old `[2026-05-11, 2026-05-17]`, and "back week" (one week behind) is `[2026-05-05, 2026-05-11]`.

Replace the constants block:

```java
    private static final LocalDate CURRENT_WEEK_START = LocalDate.of(2026, 5, 12);
    private static final LocalDate CURRENT_WEEK_END = LocalDate.of(2026, 5, 18);
    private static final LocalDate BACK_WEEK_START = LocalDate.of(2026, 5, 5);
    private static final LocalDate BACK_WEEK_END = LocalDate.of(2026, 5, 11);
```

Rename `createValidReceiptForNormalPreviousWeek` to `createValidReceiptForCurrentSubmissionWeek` (body unchanged, still calls `validRequest()`).

Replace `rejectFutureWeek`:

```java
    @Test
    void rejectFutureWeek() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 19));
        request.setWeekEndDate(LocalDate.of(2026, 5, 25));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Future weeks are not allowed."));
        assertEquals(0, receiptNumberGeneratorService.generateCount);
    }
```

Replace `rejectWeekStartThatIsNotMonday` with `rejectWeekEndThatIsNotMonday`:

```java
    @Test
    void rejectWeekEndThatIsNotMonday() {
        CreateReceiptRequest request = validRequest();
        request.setWeekEndDate(LocalDate.of(2026, 5, 17));
        request.setWeekStartDate(LocalDate.of(2026, 5, 11));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week end date must be a Monday."));
    }
```

Replace `rejectWeekEndThatIsNotSunday` with `rejectWeekStartThatIsNotTuesday`:

```java
    @Test
    void rejectWeekStartThatIsNotTuesday() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 13));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week start date must be a Tuesday."));
    }
```

Replace `rejectWeekEndNotEqualToStartPlusSixDays`:

```java
    @Test
    void rejectWeekEndNotEqualToStartPlusSixDays() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 5));
        request.setWeekEndDate(LocalDate.of(2026, 5, 18));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week end date must be 6 days after week start date."));
    }
```

In the church-service-date test, replace every `NORMAL_WEEK_START`/`NORMAL_WEEK_END` reference with `CURRENT_WEEK_START`/`CURRENT_WEEK_END` (same test bodies, only the constant names change):

```java
    @Test
    void rejectChurchServiceDateOutsideWeek() {
        CreateReceiptRequest request = validRequest();
        request.setChurchServiceDate(CURRENT_WEEK_START.minusDays(1));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Date of the church service must be within the selected week."));
    }
```

(Find and replace every other remaining `NORMAL_WEEK_START` / `NORMAL_WEEK_END` occurrence in the file — including inside `validRequest()` and `cancelledReceiptForCorrection()` — with `CURRENT_WEEK_START` / `CURRENT_WEEK_END`. The `BACK_WEEK_START` / `BACK_WEEK_END` occurrences in `allowBackWeekReceiptAndMarkAsLate`, the unnamed-reason test, and `rejectBackWeekReceiptWithoutLateSubmissionReasonWhenSettingIsEnabled` need no value changes beyond the constant redefinition already made above — those three tests' bodies are otherwise unchanged.)

- [ ] **Step 3: Run the tests**

Run: `mvn -q -Dtest=ReceiptServiceTest test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/churchmanagement/service/ReceiptService.java \
        src/test/java/com/churchmanagement/service/ReceiptServiceTest.java
git commit -m "feat(receipt): resolve the identifier day for receipt creation and validation

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: `ReceiptEntryController` — pick the identifier day, compute the start

**Files:**
- Modify: `src/main/java/com/churchmanagement/controller/ReceiptEntryController.java`
- Modify: `src/main/resources/com/churchmanagement/view/receipt-entry-view.fxml`

**Interfaces:**
- Consumes: `WeekUtil.currentIdentifier`, `WeekUtil.weekStartFor`, `WeekUtil.isBackWeek(3-arg)`, `WeekUtil.parseIdentifierDay` (Task 1); `DatePickerUtil.enableDayOfWeekOnly` (Task 2).

Verified manually (no controller tests in this codebase) — this is the screen the whole feature is about, so give it a careful manual pass.

- [ ] **Step 1: Update imports and add the resolver helper**

Add imports:

```java
import com.churchmanagement.service.SystemConfigurationCache;
import java.time.DayOfWeek;
```

Add a private helper near the other private helpers:

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(
                SystemConfigurationCache.getInstance().getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 2: Update `initialize()` and `clearForm()`**

Change both occurrences of:

```java
        weekStartDatePicker.setValue(WeekUtil.getPreviousWeekMonday(LocalDate.now()));
```

to:

```java
        weekStartDatePicker.setValue(WeekUtil.currentIdentifier(LocalDate.now(), resolveIdentifierDay()));
```

- [ ] **Step 3: Update `configureControls()`**

Change:

```java
        DatePickerUtil.enableMondaysOnly(weekStartDatePicker);
        DatePickerUtil.restrictToRange(churchServiceDatePicker, weekStartDatePicker::getValue,
                () -> WeekUtil.getSundayForMonday(weekStartDatePicker.getValue()));
```

to:

```java
        DatePickerUtil.enableDayOfWeekOnly(weekStartDatePicker, resolveIdentifierDay());
        DatePickerUtil.restrictToRange(churchServiceDatePicker,
                () -> WeekUtil.weekStartFor(weekStartDatePicker.getValue()),
                weekStartDatePicker::getValue);
```

- [ ] **Step 4: Update `updateWeekState()`**

Replace the full method:

```java
    private void updateWeekState() {
        LocalDate identifier = weekStartDatePicker.getValue();
        LocalDate start = WeekUtil.weekStartFor(identifier);
        weekEndDateLabel.setText(dateTimeFormatter.formatDate(start));

        LocalDate serviceDate = churchServiceDatePicker.getValue();
        if (start != null && identifier != null
                && (serviceDate == null || serviceDate.isBefore(start) || serviceDate.isAfter(identifier))) {
            churchServiceDatePicker.setValue(identifier);
        }

        boolean late = identifier != null && WeekUtil.isBackWeek(identifier, LocalDate.now(), resolveIdentifierDay());
        lateSubmissionLabel.setText(late ? "YES" : "NO");
        lateSubmissionLabel.getStyleClass().removeAll("status-active", "status-inactive");
        lateSubmissionLabel.getStyleClass().add(late ? "status-inactive" : "status-active");
        lateReasonLabel.setVisible(late);
        lateReasonLabel.setManaged(late);
        lateSubmissionReasonArea.setVisible(late);
        lateSubmissionReasonArea.setManaged(late);
        lateSubmissionReasonArea.setDisable(!late);
        if (!late) {
            lateSubmissionReasonArea.clear();
        }
    }
```

- [ ] **Step 5: Update `buildRequest()`**

Replace:

```java
        LocalDate weekStart = weekStartDatePicker.getValue();
        request.setChurchId(selectedChurch == null ? null : selectedChurch.getId());
        request.setWeekStartDate(weekStart);
        request.setWeekEndDate(WeekUtil.getSundayForMonday(weekStart));
```

with:

```java
        LocalDate weekIdentifier = weekStartDatePicker.getValue();
        request.setChurchId(selectedChurch == null ? null : selectedChurch.getId());
        request.setWeekEndDate(weekIdentifier);
        request.setWeekStartDate(WeekUtil.weekStartFor(weekIdentifier));
```

- [ ] **Step 6: Update `applyCorrectionReceipt(...)`**

Change:

```java
        weekStartDatePicker.setValue(receipt.getWeekStartDate());
```

to:

```java
        weekStartDatePicker.setValue(receipt.getWeekEndDate());
```

- [ ] **Step 7: Update `summaryGrid(...)`**

Change:

```java
                summaryRow("Late submission:", WeekUtil.isBackWeek(request.getWeekStartDate(), LocalDate.now()) ? "YES" : "NO")
```

to:

```java
                summaryRow("Late submission:", WeekUtil.isBackWeek(request.getWeekEndDate(), LocalDate.now(), resolveIdentifierDay()) ? "YES" : "NO")
```

- [ ] **Step 8: Update `receipt-entry-view.fxml` copy**

Change:

```xml
              <Label text="Week Start Date" GridPane.columnIndex="0" GridPane.rowIndex="1" />
              <DatePicker fx:id="weekStartDatePicker" prefWidth="280" GridPane.columnIndex="1" GridPane.rowIndex="1" />
              <Label text="Week End Date" GridPane.columnIndex="2" GridPane.rowIndex="1" />
              <Label fx:id="weekEndDateLabel" styleClass="value-label" text="-" GridPane.columnIndex="3" GridPane.rowIndex="1" />
```

to:

```xml
              <Label text="Week Ending Date" GridPane.columnIndex="0" GridPane.rowIndex="1" />
              <DatePicker fx:id="weekStartDatePicker" prefWidth="280" GridPane.columnIndex="1" GridPane.rowIndex="1" />
              <Label text="Week Start Date" GridPane.columnIndex="2" GridPane.rowIndex="1" />
              <Label fx:id="weekEndDateLabel" styleClass="value-label" text="-" GridPane.columnIndex="3" GridPane.rowIndex="1" />
```

(`fx:id`s are unchanged — only the on-screen label text flips to match what each control now actually shows.)

- [ ] **Step 9: Manual verification**

Run the app, open Receipt Entry: confirm the "Week Ending Date" picker only allows Mondays (or the configured day) and future dates are blocked, defaults to today if today is a Monday, the "Week Start Date" label shows 6 days earlier, "Date of church service" is restricted to that range, late-submission indicator and reason field toggle correctly for a back week, and a full create + a corrected-receipt re-creation both work.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/ReceiptEntryController.java \
        src/main/resources/com/churchmanagement/view/receipt-entry-view.fxml
git commit -m "feat(receipt): pick the week-ending (identifier) date in receipt entry

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: `DashboardService` — configurable week validation and defaults

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/DashboardService.java`
- Test: `src/test/java/com/churchmanagement/service/DashboardServiceTest.java`

**Interfaces:**
- Consumes: `WeekUtil.currentIdentifier`, `WeekUtil.weekStartFor`, `WeekUtil.isIdentifierDay`, `WeekUtil.isWeekStartDay`, `WeekUtil.isBackWeek(3-arg)`, `WeekUtil.parseIdentifierDay`, `WeekUtil.displayName` (Task 1).
- Produces: `DashboardService.defaultWeeklyRange()` now returns `(weekStart, identifier)` instead of `(monday, monday+6)`. Consumed by Task 9 (`DashboardHomeController`).

**Important correctness fix:** the identifier moved from `weekStartDate` to `weekEndDate` — `applyWeeklyPermissions`'s `WeekUtil.isBackWeek` call must read `weekly.getWeekEndDate()`.

- [ ] **Step 1: Add the `SystemConfigurationCache` dependency**

`SystemConfigurationCache` lives in the same `com.churchmanagement.service` package as `DashboardService`, so no import is needed — just add the field and constructors:

```java
    private final DashboardRepository dashboardRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final SystemConfigurationCache configurationCache;

    public DashboardService() {
        this(new DashboardRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public DashboardService(DashboardRepository dashboardRepository, ActivityLogService activityLogService, Clock clock) {
        this(dashboardRepository, activityLogService, clock, SystemConfigurationCache.getInstance());
    }

    public DashboardService(DashboardRepository dashboardRepository, ActivityLogService activityLogService, Clock clock,
                            SystemConfigurationCache configurationCache) {
        this.dashboardRepository = dashboardRepository;
        this.activityLogService = activityLogService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.configurationCache = configurationCache;
    }
```

Add the import at the top of the file instead:

```java
import java.time.DayOfWeek;
```

- [ ] **Step 2: Update `defaultWeeklyStart`, `defaultWeeklyRange`, `loadWeeklyDashboard`'s null-end fallback**

Replace:

```java
    public WeeklyDashboardDto loadWeeklyDashboard(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        AuthenticatedUser user = currentUser();
        PermissionGuard guard = new PermissionGuard(user);
        LocalDate safeWeekStart = defaultWeeklyStart(weekStartDate);
        LocalDate safeWeekEnd = weekEndDate == null ? WeekUtil.getSundayForMonday(safeWeekStart) : weekEndDate;
        validateWeek(safeWeekStart, safeWeekEnd);
```

with:

```java
    public WeeklyDashboardDto loadWeeklyDashboard(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        AuthenticatedUser user = currentUser();
        PermissionGuard guard = new PermissionGuard(user);
        LocalDate safeWeekStart = defaultWeeklyStart(weekStartDate);
        LocalDate safeWeekEnd = weekEndDate == null ? safeWeekStart.plusDays(6) : weekEndDate;
        validateWeek(safeWeekStart, safeWeekEnd);
```

Replace:

```java
    public DateRange defaultWeeklyRange() {
        LocalDate weekStart = WeekUtil.getCurrentWeekMonday(LocalDate.now(clock));
        return new DateRange(weekStart, WeekUtil.getSundayForMonday(weekStart));
    }
```

with:

```java
    public DateRange defaultWeeklyRange() {
        LocalDate identifier = WeekUtil.currentIdentifier(LocalDate.now(clock), resolveIdentifierDay());
        return new DateRange(WeekUtil.weekStartFor(identifier), identifier);
    }
```

- [ ] **Step 3: Update `validateWeek`, `isCurrentCalendarWeekRange`, `applyWeeklyPermissions`**

Replace:

```java
    private void validateWeek(LocalDate weekStartDate, LocalDate weekEndDate) {
        if (!WeekUtil.isWeekStartMonday(weekStartDate)) {
            throw new DashboardException("Week start date must be a Monday.");
        }
        if (!WeekUtil.isWeekEndSunday(weekEndDate)) {
            throw new DashboardException("Week end date must be a Sunday.");
        }
        if (!weekEndDate.equals(weekStartDate.plusDays(6))) {
            throw new DashboardException("Week end date must be 6 days after week start date.");
        }
    }
```

with:

```java
    private void validateWeek(LocalDate weekStartDate, LocalDate weekEndDate) {
        DayOfWeek identifierDay = resolveIdentifierDay();
        if (!WeekUtil.isWeekStartDay(weekStartDate, identifierDay)) {
            throw new DashboardException("Week start date must be a " + WeekUtil.displayName(identifierDay.plus(1)) + ".");
        }
        if (!WeekUtil.isIdentifierDay(weekEndDate, identifierDay)) {
            throw new DashboardException("Week end date must be a " + WeekUtil.displayName(identifierDay) + ".");
        }
        if (!weekEndDate.equals(weekStartDate.plusDays(6))) {
            throw new DashboardException("Week end date must be 6 days after week start date.");
        }
    }
```

Replace:

```java
    private boolean isCurrentCalendarWeekRange(LocalDate weekStartDate, LocalDate weekEndDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return weekStartDate != null && weekEndDate != null
                && weekStartDate.equals(currentWeekStart)
                && weekEndDate.equals(WeekUtil.getSundayForMonday(currentWeekStart));
    }
```

with:

```java
    private boolean isCurrentCalendarWeekRange(LocalDate weekStartDate, LocalDate weekEndDate) {
        LocalDate currentIdentifier = WeekUtil.currentIdentifier(LocalDate.now(clock), resolveIdentifierDay());
        return weekStartDate != null && weekEndDate != null
                && weekEndDate.equals(currentIdentifier)
                && weekStartDate.equals(WeekUtil.weekStartFor(currentIdentifier));
    }
```

In `applyWeeklyPermissions`, replace:

```java
        weekly.setLateSubmissionsVisible(receiptView
                && WeekUtil.isBackWeek(weekly.getWeekStartDate(), LocalDate.now(clock)));
```

with:

```java
        weekly.setLateSubmissionsVisible(receiptView
                && WeekUtil.isBackWeek(weekly.getWeekEndDate(), LocalDate.now(clock), resolveIdentifierDay()));
```

Add the resolver helper near the other private helpers:

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(configurationCache.getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 4: Rewrite `DashboardServiceTest`**

The fixed clock is `2026-06-02T04:30:00Z` in `Asia/Colombo` = local `2026-06-02` (a Tuesday). Under the new default identifier day (Monday), `currentIdentifier` is unchanged at `2026-06-01`, but the default *range* becomes `[2026-05-26, 2026-06-01]` (Tuesday–Monday) instead of the old `[2026-06-01, 2026-06-07]`.

Replace the fixture receipts list:

```java
        private final List<ReceiptRow> receipts = List.of(
                new ReceiptRow(1L, 1L, 1L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 27),
                        true, false, "OFFERTORY", new BigDecimal("1000.00")),
                new ReceiptRow(2L, 2L, 1L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 6, 2),
                        true, true, "OFFERTORY", new BigDecimal("500.00")),
                new ReceiptRow(3L, 3L, 2L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 26),
                        true, false, "TITHES", new BigDecimal("500.00")),
                new ReceiptRow(4L, 4L, 2L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        false, false, "OTHER_DONATIONS", new BigDecimal("900.00")),
                new ReceiptRow(5L, 1L, 1L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                        true, false, "TITHES", new BigDecimal("300.00"))
        );
```

Replace `defaultWeeklyDateIsCurrentWeek`:

```java
    @Test
    void defaultWeeklyDateIsCurrentWeek() {
        DashboardService.DateRange range = dashboardService.defaultWeeklyRange();
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(null, null, null);

        assertEquals(LocalDate.of(2026, 5, 26), range.dateFrom());
        assertEquals(LocalDate.of(2026, 6, 1), range.dateTo());
        assertEquals(range.dateFrom(), weekly.getWeekStartDate());
        assertEquals(range.dateTo(), weekly.getWeekEndDate());
        assertTrue(weekly.isTodaysReceiptsTotalVisible());
        assertFalse(weekly.isWeekCollectionVisible());
        assertEquals(ActivityLogService.DASHBOARD_WEEKLY_VIEWED, activityLogService.lastAction);
    }
```

Leave `defaultTrendingDateIsCurrentMonthStartToToday` and `quickRangesAreCalculatedFromToday` unchanged (neither depends on week-identifier logic).

Replace `cancelledReceiptsExcludedFromAllTotals`:

```java
    @Test
    void cancelledReceiptsExcludedFromAllTotals() {
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 25), null);
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 6, 2), null);

        assertEquals(1, weekly.getCompletedRegions());
        assertEquals(2, weekly.getTotalRegions());
        assertEquals(new BigDecimal("1.0"), BigDecimal.valueOf(weekly.getRegionSubmissionProgress().getFirst().getProgress()));
        assertEquals(new BigDecimal("500.00"), weekly.getTodaysReceiptsTotal());
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "OFFERTORY"));
        assertEquals(new BigDecimal("1500.00"), pointValue(weekly.getCollectionTypeWeeklyTotals(), "OFFERTORY"));
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeeklyTotals(), "OTHER_DONATIONS"));
        assertEquals(new BigDecimal("1500.00"), regionCollectionValue(
                weekly.getRegionWiseWeeklyCollection(), "North", "OFFERTORY"));
        assertEquals(new BigDecimal("500.00"), regionCollectionValue(
                weekly.getRegionWiseWeeklyCollection(), "South", "TITHES"));
        assertEquals(new BigDecimal("2000.00"), pointValue(trending.getTotalCollectionTrend(), "2026-05-19"));

        WeeklyDashboardDto filteredWeekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 25), 2L);
        assertEquals(new BigDecimal("1500.00"), pointValue(filteredWeekly.getTopWeeklyRegionCollections(), "North"));
        assertEquals(new BigDecimal("500.00"), pointValue(filteredWeekly.getTopWeeklyRegionCollections(), "South"));
    }
```

Replace `pendingChurchesCalculatedFromActiveReceiptsOnly`:

```java
    @Test
    void pendingChurchesCalculatedFromActiveReceiptsOnly() {
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1), null);

        assertEquals(0, weekly.getSubmittedChurches());
        assertEquals(4, weekly.getPendingChurches());
        assertTrue(weekly.isTodaysReceiptsTotalVisible());
        assertFalse(weekly.isWeekCollectionVisible());
        assertEquals(new BigDecimal("1000.00"), pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "OFFERTORY"));
        assertEquals(new BigDecimal("500.00"), pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "TITHES"));
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeeklyTotals(), "OFFERTORY"));
    }
```

Replace `trendGroupingIsWeeklyForShortRanges`:

```java
    @Test
    void trendGroupingIsWeeklyForShortRanges() {
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 6, 2), null);

        assertEquals("WEEKLY", trending.getGroupingMode());
        assertEquals("WEEKLY", dashboardRepository.lastGroupingMode);
        assertEquals("2026-05-19", trending.getTotalCollectionTrend().getFirst().getLabel());
    }
```

Replace `trendGroupingStaysWeeklyForRangesOverNinetyDays`:

```java
    @Test
    void trendGroupingStaysWeeklyForRangesOverNinetyDays() {
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 2), null);

        assertEquals("WEEKLY", trending.getGroupingMode());
        assertEquals("WEEKLY", dashboardRepository.lastGroupingMode);
        assertTrue(trending.getTotalCollectionTrend().stream().anyMatch(point -> "2026-05-19".equals(point.getLabel())));
    }
```

Leave `invalidDateRangesAreRejected` unchanged — its third assertion (`loadWeeklyDashboard(LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 31), null)`) is still invalid under the new rule (weekEnd `2026-05-31` is a Sunday, not the Monday identifier day) so `DashboardException` is still thrown.

- [ ] **Step 5: Run the tests**

Run: `mvn -q -Dtest=DashboardServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/churchmanagement/service/DashboardService.java \
        src/test/java/com/churchmanagement/service/DashboardServiceTest.java
git commit -m "feat(dashboard): validate and default weeks around the identifier day

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: `DashboardHomeController` — pick the identifier day

**Files:**
- Modify: `src/main/java/com/churchmanagement/controller/DashboardHomeController.java`

**Interfaces:**
- Consumes: `WeekUtil.weekStartFor` (Task 1); `DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates` (Task 2); `DashboardService.defaultWeeklyRange()` now returning `(start, identifier)` (Task 8).

- [ ] **Step 1: Add the resolver helper**

Add near the other private helpers (imports `WeekUtil` and `DayOfWeek` are already present per the earlier study — `SystemConfigurationCache` needs importing):

```java
import com.churchmanagement.service.SystemConfigurationCache;
import java.time.DayOfWeek;
```

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(
                SystemConfigurationCache.getInstance().getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 2: Update `configureFilters()`**

Change:

```java
        DatePickerUtil.enableMondaysOnlyAndDisableFutureDates(weeklyWeekDatePicker);
        DatePickerUtil.disableFutureDates(trendingDateFromPicker);
        DatePickerUtil.disableFutureDates(trendingDateToPicker);
        weeklyWeekDatePicker.setValue(weeklyRange.dateFrom());
```

to:

```java
        DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates(weeklyWeekDatePicker, resolveIdentifierDay());
        DatePickerUtil.disableFutureDates(trendingDateFromPicker);
        DatePickerUtil.disableFutureDates(trendingDateToPicker);
        weeklyWeekDatePicker.setValue(weeklyRange.dateTo());
```

- [ ] **Step 3: Update `shiftWeeklyDate` and `loadWeekly`**

Change:

```java
    private void shiftWeeklyDate(int weeks) {
        LocalDate selectedWeek = weeklyWeekDatePicker.getValue();
        if (selectedWeek == null) {
            selectedWeek = dashboardService.defaultWeeklyRange().dateFrom();
        }
        weeklyWeekDatePicker.setValue(selectedWeek.plusWeeks(weeks));
        loadWeekly(true);
    }
```

to:

```java
    private void shiftWeeklyDate(int weeks) {
        LocalDate selectedWeek = weeklyWeekDatePicker.getValue();
        if (selectedWeek == null) {
            selectedWeek = dashboardService.defaultWeeklyRange().dateTo();
        }
        weeklyWeekDatePicker.setValue(selectedWeek.plusWeeks(weeks));
        loadWeekly(true);
    }
```

In `loadWeekly(boolean filterChanged)`, change:

```java
            Region region = weeklyRegionComboBox.getValue();
            WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(
                    weeklyWeekDatePicker.getValue(),
                    weeklyWeekDatePicker.getValue() == null ? null : weeklyWeekDatePicker.getValue().plusDays(6),
                    region == null ? null : region.getId());
```

to:

```java
            Region region = weeklyRegionComboBox.getValue();
            LocalDate identifier = weeklyWeekDatePicker.getValue();
            LocalDate weekStart = identifier == null ? null : WeekUtil.weekStartFor(identifier);
            WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(weekStart, identifier,
                    region == null ? null : region.getId());
```

- [ ] **Step 4: Manual verification**

Run the app, open Dashboard → Weekly Data tab: confirm the week picker only allows Mondays (or the configured day), Previous/Next Week buttons still step by 7 days correctly, and the weekly cards/charts populate for both the default week and a manually-picked earlier week.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/DashboardHomeController.java
git commit -m "feat(dashboard): pick the week-ending (identifier) date on the weekly dashboard

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: `SubmissionStatusService` — configurable week validation and defaults

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/SubmissionStatusService.java`
- Test: `src/test/java/com/churchmanagement/service/SubmissionStatusServiceTest.java`

**Interfaces:**
- Consumes: `WeekUtil.currentIdentifier`, `WeekUtil.weekStartFor`, `WeekUtil.isWeekStartDay`, `WeekUtil.parseIdentifierDay`, `WeekUtil.displayName` (Task 1).
- Produces: `SubmissionStatusService.defaultWeekIdentifier()` (new method) alongside the existing `defaultWeekStart()` (now returns the *start* boundary instead of the Monday identifier). Consumed by Task 11 (`SubmissionStatusController`).

- [ ] **Step 1: Add the `SystemConfigurationCache` dependency**

```java
    private final SubmissionStatusRepository submissionStatusRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final SystemConfigurationCache configurationCache;

    public SubmissionStatusService() {
        this(new SubmissionStatusRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public SubmissionStatusService(SubmissionStatusRepository submissionStatusRepository,
                                   ActivityLogService activityLogService, Clock clock) {
        this(submissionStatusRepository, activityLogService, clock, SystemConfigurationCache.getInstance());
    }

    public SubmissionStatusService(SubmissionStatusRepository submissionStatusRepository,
                                   ActivityLogService activityLogService, Clock clock,
                                   SystemConfigurationCache configurationCache) {
        this.submissionStatusRepository = submissionStatusRepository;
        this.activityLogService = activityLogService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.configurationCache = configurationCache;
    }
```

Add the import:

```java
import java.time.DayOfWeek;
```

(`SystemConfigurationCache` is in the same package, no import needed.)

- [ ] **Step 2: Update `defaultWeekStart`, add `defaultWeekIdentifier`, update `safeWeekStart`**

Replace:

```java
    public LocalDate defaultWeekStart() {
        return WeekUtil.getCurrentWeekMonday(LocalDate.now(clock));
    }
```

with:

```java
    public LocalDate defaultWeekStart() {
        return WeekUtil.weekStartFor(defaultWeekIdentifier());
    }

    public LocalDate defaultWeekIdentifier() {
        return WeekUtil.currentIdentifier(LocalDate.now(clock), resolveIdentifierDay());
    }
```

Replace:

```java
    private LocalDate safeWeekStart(LocalDate weekStartDate) {
        LocalDate safeWeekStart = weekStartDate == null ? defaultWeekStart() : weekStartDate;
        if (!WeekUtil.isWeekStartMonday(safeWeekStart)) {
            throw new SubmissionStatusException("Week Start Date must be a Monday.");
        }
        return safeWeekStart;
    }
```

with:

```java
    private LocalDate safeWeekStart(LocalDate weekStartDate) {
        LocalDate safeWeekStart = weekStartDate == null ? defaultWeekStart() : weekStartDate;
        DayOfWeek identifierDay = resolveIdentifierDay();
        if (!WeekUtil.isWeekStartDay(safeWeekStart, identifierDay)) {
            throw new SubmissionStatusException("Week Start Date must be a " + WeekUtil.displayName(identifierDay.plus(1)) + ".");
        }
        return safeWeekStart;
    }

    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(configurationCache.getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 3: Rewrite `SubmissionStatusServiceTest`**

The fixed clock is `2026-06-02T04:30:00Z` in `Asia/Colombo` = local `2026-06-02` (a Tuesday). `defaultWeekIdentifier()` is `2026-06-01` (unchanged value, previously returned by the old `defaultWeekStart()`); the new `defaultWeekStart()` now returns `2026-05-26` (the actual start boundary).

Replace every occurrence of `LocalDate.of(2026, 5, 25)` used as a query argument (`loadWeeklyStatus`, `loadSubmissionTotals`, `loadWeeklySummary` calls) with `LocalDate.of(2026, 5, 26)` — this affects `submittedPendingAndCancelledStatusesAreCalculated`, `totalsUseActiveReceiptsOnlyAndExcludeCancelled`, `lateSubmissionDisplayComesFromActiveSubmission`, and `regionAndStatusFilteringAreApplied`. None of these tests' other assertions change.

Replace the fixture's first two `ReceiptRow` entries' `weekStartDate` (leave the third, `R-12`, unchanged — it represents an unrelated older week and is never queried directly):

```java
        private final List<ReceiptRow> receipts = List.of(
                new ReceiptRow(10L, 1L, 1L, "R-10", LocalDate.of(2026, 5, 26),
                        LocalDateTime.of(2026, 6, 2, 9, 0), "ACTIVE", true,
                        new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("200.00")),
                new ReceiptRow(11L, 3L, 2L, "R-11", LocalDate.of(2026, 5, 26),
                        LocalDateTime.of(2026, 6, 1, 9, 0), "CANCELLED", false,
                        new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                new ReceiptRow(12L, 3L, 2L, "R-12", LocalDate.of(2026, 5, 18),
                        LocalDateTime.of(2026, 5, 20, 9, 0), "ACTIVE", false,
                        new BigDecimal("900.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        );
```

Replace `defaultCurrentWeekSupportsPreviousAndNextNavigationMath`:

```java
    @Test
    void defaultCurrentWeekSupportsPreviousAndNextNavigationMath() {
        LocalDate defaultWeek = service.defaultWeekIdentifier();

        assertEquals(LocalDate.of(2026, 6, 1), defaultWeek);
        assertEquals(LocalDate.of(2026, 5, 25), defaultWeek.minusWeeks(1));
        assertEquals(LocalDate.of(2026, 6, 8), defaultWeek.plusWeeks(1));
    }
```

Add a new test after it:

```java
    @Test
    void defaultWeekStartReturnsSixDaysBeforeTheIdentifier() {
        assertEquals(LocalDate.of(2026, 5, 26), service.defaultWeekStart());
    }
```

Replace `nonMondayWeekStartIsRejected`:

```java
    @Test
    void nonTuesdayWeekStartIsRejected() {
        assertThrows(SubmissionStatusService.SubmissionStatusException.class,
                () -> service.loadWeeklyStatus(LocalDate.of(2026, 5, 25), null, "ALL"));
    }
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q -Dtest=SubmissionStatusServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/SubmissionStatusService.java \
        src/test/java/com/churchmanagement/service/SubmissionStatusServiceTest.java
git commit -m "feat(submission-status): validate and default weeks around the identifier day

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: `SubmissionStatusController` — pick the identifier day

**Files:**
- Modify: `src/main/java/com/churchmanagement/controller/SubmissionStatusController.java`
- Modify: `src/main/resources/com/churchmanagement/view/submission-status-view.fxml`

**Interfaces:**
- Consumes: `WeekUtil.weekStartFor` (Task 1); `DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates` (Task 2); `SubmissionStatusService.defaultWeekIdentifier()` (Task 10).

- [ ] **Step 1: Add the resolver helper and imports**

```java
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.WeekUtil;
import java.time.DayOfWeek;
```

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(
                SystemConfigurationCache.getInstance().getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 2: Replace every `defaultWeekStart()` picker-seeding call with `defaultWeekIdentifier()`**

In `initialize()`, `handlePreviousWeek()`, `handleNextWeek()`, and `handleRefresh()`, replace `submissionStatusService.defaultWeekStart()` with `submissionStatusService.defaultWeekIdentifier()` everywhere it seeds or falls back the picker's value (4 occurrences):

```java
        weekStartDatePicker.setValue(submissionStatusService.defaultWeekIdentifier());
```

```java
    @FXML
    private void handlePreviousWeek() {
        LocalDate current = weekStartDatePicker.getValue();
        weekStartDatePicker.setValue((current == null ? submissionStatusService.defaultWeekIdentifier() : current).minusWeeks(1));
    }

    @FXML
    private void handleNextWeek() {
        LocalDate current = weekStartDatePicker.getValue();
        LocalDate nextWeek = (current == null ? submissionStatusService.defaultWeekIdentifier() : current).plusWeeks(1);
        if (!nextWeek.isAfter(LocalDate.now())) {
            weekStartDatePicker.setValue(nextWeek);
        }
    }
```

- [ ] **Step 3: Convert the picker's identifier value to the start boundary before every service call**

In `handleSearch()`, change:

```java
        LocalDate weekStart = weekStartDatePicker.getValue();
```

to:

```java
        LocalDate weekStart = WeekUtil.weekStartFor(weekStartDatePicker.getValue());
```

In `refreshDashboard(boolean logFilterChange)`, apply the same change:

```java
            LocalDate weekStart = WeekUtil.weekStartFor(weekStartDatePicker.getValue());
```

- [ ] **Step 4: Restrict the picker to the identifier day**

Change:

```java
        DatePickerUtil.enableMondaysOnlyAndDisableFutureDates(weekStartDatePicker);
```

to:

```java
        DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates(weekStartDatePicker, resolveIdentifierDay());
```

- [ ] **Step 5: Update `submission-status-view.fxml` copy**

Change:

```xml
        <Label text="Week Start Date" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
        <DatePicker fx:id="weekStartDatePicker" maxWidth="Infinity" GridPane.columnIndex="1" GridPane.rowIndex="0"/>
```

to:

```xml
        <Label text="Week Ending Date" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
        <DatePicker fx:id="weekStartDatePicker" maxWidth="Infinity" GridPane.columnIndex="1" GridPane.rowIndex="0"/>
```

- [ ] **Step 6: Manual verification**

Run the app, open Receipts → Submission Status: confirm the week picker only allows Mondays (or the configured day), Previous/Next Week buttons work, and the table/summary/totals populate correctly for the default week.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/SubmissionStatusController.java \
        src/main/resources/com/churchmanagement/view/submission-status-view.fxml
git commit -m "feat(submission-status): pick the week-ending (identifier) date

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: `ReportService` — configurable week validation and defaults

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/ReportService.java`

**Interfaces:**
- Consumes: `WeekUtil.currentIdentifier`, `WeekUtil.weekStartFor`, `WeekUtil.isWeekStartDay`, `WeekUtil.parseIdentifierDay`, `WeekUtil.displayName` (Task 1).
- Produces: `ReportService.defaultWeekIdentifier()` (new method). Consumed by Task 13 (`ReportsController`).

No test changes: `ReportServiceTest`'s `criteria()` helper always sets `weekStartDate` explicitly to `LocalDate.of(2026, 6, 1)` (a Monday, still valid under the new default), no test exercises `quickRange("This Week"/"Previous Week")` or asserts the Monday-validation message, and the existing 6-arg constructor keeps compiling via the new delegating overload — this task is verified by the full-suite run in Task 14.

- [ ] **Step 1: Add the `SystemConfigurationCache` dependency**

```java
    private final ReportRepository reportRepository;
    private final ActivityLogService activityLogService;
    private final ReportPdfExporter pdfExporter;
    private final ReportExcelExporter excelExporter;
    private final PrinterService printerService;
    private final Clock clock;
    private final SystemConfigurationCache configurationCache;

    public ReportService() {
        this(new ReportRepository(), new ActivityLogService(), new ReportPdfExporter(),
                new ReportExcelExporter(), new MockPrinterService(), Clock.systemDefaultZone());
    }

    public ReportService(ReportRepository reportRepository, ActivityLogService activityLogService,
                         ReportPdfExporter pdfExporter, ReportExcelExporter excelExporter,
                         PrinterService printerService, Clock clock) {
        this(reportRepository, activityLogService, pdfExporter, excelExporter, printerService, clock,
                SystemConfigurationCache.getInstance());
    }

    public ReportService(ReportRepository reportRepository, ActivityLogService activityLogService,
                         ReportPdfExporter pdfExporter, ReportExcelExporter excelExporter,
                         PrinterService printerService, Clock clock, SystemConfigurationCache configurationCache) {
        this.reportRepository = reportRepository;
        this.activityLogService = activityLogService;
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
        this.printerService = printerService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.configurationCache = configurationCache;
    }
```

Add the import:

```java
import java.time.DayOfWeek;
```

(`SystemConfigurationCache` is in the same package, no import needed.)

- [ ] **Step 2: Update `defaultCriteria`, add `defaultWeekIdentifier`, update `quickRange`**

Replace:

```java
        criteria.setWeekStartDate(WeekUtil.getCurrentWeekMonday(today));
        return criteria;
    }
```

with:

```java
        criteria.setWeekStartDate(WeekUtil.weekStartFor(WeekUtil.currentIdentifier(today, resolveIdentifierDay())));
        return criteria;
    }

    public LocalDate defaultWeekIdentifier() {
        return WeekUtil.currentIdentifier(LocalDate.now(clock), resolveIdentifierDay());
    }
```

Replace `quickRange`:

```java
    public DateRange quickRange(String quickFilter) {
        LocalDate today = LocalDate.now(clock);
        return switch (quickFilter) {
            case "This Week" -> {
                LocalDate monday = WeekUtil.getCurrentWeekMonday(today);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "Previous Week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "Quarter" -> {
                int quarterStart = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                yield new DateRange(LocalDate.of(today.getYear(), quarterStart, 1), today);
            }
            case "Year" -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
            default -> new DateRange(today.withDayOfMonth(1), today);
        };
    }
```

with:

```java
    public DateRange quickRange(String quickFilter) {
        LocalDate today = LocalDate.now(clock);
        DayOfWeek identifierDay = resolveIdentifierDay();
        return switch (quickFilter) {
            case "This Week" -> {
                LocalDate identifier = WeekUtil.currentIdentifier(today, identifierDay);
                yield new DateRange(WeekUtil.weekStartFor(identifier), identifier);
            }
            case "Previous Week" -> {
                LocalDate identifier = WeekUtil.currentIdentifier(today, identifierDay).minusWeeks(1);
                yield new DateRange(WeekUtil.weekStartFor(identifier), identifier);
            }
            case "Quarter" -> {
                int quarterStart = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                yield new DateRange(LocalDate.of(today.getYear(), quarterStart, 1), today);
            }
            case "Year" -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
            default -> new DateRange(today.withDayOfMonth(1), today);
        };
    }
```

(`TemporalAdjusters` import may now be unused in this file — remove it if no other method references it; check with a quick search for `TemporalAdjusters` before deleting the import.)

- [ ] **Step 3: Update `normalizeAndValidate`**

Replace:

```java
            if (!WeekUtil.isWeekStartMonday(safe.getWeekStartDate())) {
                throw new ReportException("Week Start Date must be Monday.");
            }
```

with:

```java
            DayOfWeek identifierDay = resolveIdentifierDay();
            if (!WeekUtil.isWeekStartDay(safe.getWeekStartDate(), identifierDay)) {
                throw new ReportException("Week Start Date must be " + WeekUtil.displayName(identifierDay.plus(1)) + ".");
            }
```

Add the resolver helper near the other private helpers:

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(configurationCache.getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/churchmanagement/service/ReportService.java
git commit -m "feat(reports): validate and default weeks around the identifier day

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: `ReportsController` — pick the identifier day, clean up dead `DatePickerUtil` methods

**Files:**
- Modify: `src/main/java/com/churchmanagement/controller/ReportsController.java`
- Modify: `src/main/resources/com/churchmanagement/view/reports-view.fxml`
- Modify: `src/main/java/com/churchmanagement/util/DatePickerUtil.java`

**Interfaces:**
- Consumes: `WeekUtil.weekStartFor` (Task 1); `DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates` (Task 2); `ReportService.defaultWeekIdentifier()` (Task 12).

This is the last consumer of `DatePickerUtil.enableMondaysOnly` / `enableMondaysOnlyAndDisableFutureDates` (Tasks 7, 9, 11 already migrated `ReceiptEntryController`, `DashboardHomeController`, `SubmissionStatusController` off them) — remove the now-dead methods here.

- [ ] **Step 1: Add the resolver helper and imports**

```java
import com.churchmanagement.service.SystemConfigurationCache;
import java.time.DayOfWeek;
```

```java
    private DayOfWeek resolveIdentifierDay() {
        return WeekUtil.parseIdentifierDay(
                SystemConfigurationCache.getInstance().getString("receipt.week.identifier.day"));
    }
```

- [ ] **Step 2: Update `configureFilters()`**

Change:

```java
        DatePickerUtil.enableMondaysOnlyAndDisableFutureDates(weekStartDatePicker);
```

to:

```java
        DatePickerUtil.enableDayOfWeekOnlyAndDisableFutureDates(weekStartDatePicker, resolveIdentifierDay());
```

- [ ] **Step 3: Update `handleClear()`, `criteriaForAction()`, `applyQuickDate()`**

Change:

```java
        weekStartDatePicker.setValue(defaults.getWeekStartDate());
```

to:

```java
        weekStartDatePicker.setValue(reportService.defaultWeekIdentifier());
```

Change:

```java
        criteria.setWeekStartDate(weekStartDatePicker.getValue());
```

to:

```java
        criteria.setWeekStartDate(WeekUtil.weekStartFor(weekStartDatePicker.getValue()));
```

Change:

```java
        if ("This Week".equals(option) || "Previous Week".equals(option)) {
            weekStartDatePicker.setValue(range.dateFrom());
        }
```

to:

```java
        if ("This Week".equals(option) || "Previous Week".equals(option)) {
            weekStartDatePicker.setValue(range.dateTo());
        }
```

- [ ] **Step 4: Rename and update the computed-label method**

Change:

```java
    private void updateWeekEndDate() {
        weekEndDateLabel.setText(dateTimeFormatter.formatDate(WeekUtil.getSundayForMonday(weekStartDatePicker.getValue())));
    }
```

to:

```java
    private void updateWeekStartLabel() {
        weekEndDateLabel.setText(dateTimeFormatter.formatDate(WeekUtil.weekStartFor(weekStartDatePicker.getValue())));
    }
```

Update its two call sites in `configureFilters()`:

```java
        weekStartDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> updateWeekStartLabel());
        ...
        updateWeekStartLabel();
```

- [ ] **Step 5: Update `reports-view.fxml` copy**

Change:

```xml
                            <Label fx:id="weekStartLabel" styleClass="field-label" text="Week" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
                            <DatePicker fx:id="weekStartDatePicker" maxWidth="Infinity" GridPane.columnIndex="1" GridPane.rowIndex="0"/>
                            <Label fx:id="weekEndLabel" styleClass="field-label" text="Week End" GridPane.columnIndex="2" GridPane.rowIndex="0"/>
                            <Label fx:id="weekEndDateLabel" styleClass="value-label" text="-" GridPane.columnIndex="3" GridPane.rowIndex="0"/>
```

to:

```xml
                            <Label fx:id="weekStartLabel" styleClass="field-label" text="Week Ending Date" GridPane.columnIndex="0" GridPane.rowIndex="0"/>
                            <DatePicker fx:id="weekStartDatePicker" maxWidth="Infinity" GridPane.columnIndex="1" GridPane.rowIndex="0"/>
                            <Label fx:id="weekEndLabel" styleClass="field-label" text="Week Start Date" GridPane.columnIndex="2" GridPane.rowIndex="0"/>
                            <Label fx:id="weekEndDateLabel" styleClass="value-label" text="-" GridPane.columnIndex="3" GridPane.rowIndex="0"/>
```

- [ ] **Step 6: Remove the now-dead `DatePickerUtil` methods**

First confirm no callers remain:

Run: `grep -rn "enableMondaysOnly\b\|enableMondaysOnlyAndDisableFutureDates" src/main/java`
Expected: no matches (Tasks 7, 9, 11, and this task have migrated every caller).

Then remove `enableMondaysOnly`, `enableMondaysOnlyAndDisableFutureDates`, and the private `isMonday` helper from `src/main/java/com/churchmanagement/util/DatePickerUtil.java` (leave `disableFutureDates`, `restrictToRange`, `applySystemDateFormat`, and the two new `enableDayOfWeekOnly*` methods from Task 2 in place).

- [ ] **Step 7: Manual verification**

Run the app, open Reports: confirm the week-based report types' picker only allows Mondays (or the configured day), "This Week"/"Previous Week" quick-date options set it correctly, the computed "Week Start Date" label updates, and generating/exporting a weekly report still works end to end.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/ReportsController.java \
        src/main/resources/com/churchmanagement/view/reports-view.fxml \
        src/main/java/com/churchmanagement/util/DatePickerUtil.java
git commit -m "feat(reports): pick the week-ending (identifier) date, remove dead Monday-only helpers

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 14: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: PASS, zero failures. If anything unrelated to this change was already failing on `refactor/receipt-design` before this plan started, confirm it was already failing (e.g. via `git stash` + rerun) before treating it as this plan's responsibility.

- [ ] **Step 2: Search for any remaining references to removed APIs**

Run: `grep -rn "getPreviousWeekMonday\|getCurrentWeekMonday\|getPreviousWeekSunday\|getSundayForMonday\|isWeekStartMonday\|isWeekEndSunday\|isCurrentSubmissionWeek\|WeekUtil.isMonday" src/main src/test`
Expected: no matches. If any turn up, fix that call site using the equivalent new `WeekUtil` API from Task 1 and re-run Step 1.

- [ ] **Step 3: Manual smoke test checklist**

With the app running against a dev database that has migration `V38` applied:
1. Settings → Receipt: confirm "Week Identifier Day" defaults to Monday and can be changed and saved.
2. Receipt Entry: create a receipt for the current (un-lagged) week — confirm it's not flagged late.
3. Receipt Entry: pick a week two weeks back — confirm it's flagged late and (if configured) requires a reason.
4. Dashboard → Weekly Data: confirm the default week matches receipt entry's default week.
5. Reports → a weekly report type: confirm "This Week" and "Previous Week" quick filters produce the expected receipts.
6. Submission Status: confirm the default week and Previous/Next Week navigation match the same week receipts were just filed under.
7. Settings → Receipt: change "Week Identifier Day" to a non-Monday value (e.g. Wednesday), restart the app, and repeat steps 2-6 to confirm every screen picks up the new day consistently.

- [ ] **Step 4: Final commit (if Step 2 required fixes)**

```bash
git add -A
git commit -m "fix(receipt): clean up remaining references to removed WeekUtil methods

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

(Skip this step if Step 2 found nothing to fix.)
