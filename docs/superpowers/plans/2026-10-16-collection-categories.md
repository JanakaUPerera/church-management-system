# Collection Categories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every receipt line a collection category (A, B, C, …) that is assigned per branch × collection type × accounting year, stored on the line when the receipt is created, and filterable in the Receipt Collection Report.

**Architecture:**
- Migration V40 adds `collection_categories` and `church_category_assignments`, plus `receipts.accounting_year` and `receipt_items.category_id`, and seeds the 2026 data.
- A new `CategoryAssignmentService` owns the year lock, carry-forward and resolution rules. `ReceiptService` calls it inside its existing transaction, and `ChurchService` calls it when a branch is created.
- UI: a Category master screen (modelled on the Region screen), a Categories tab in the branch dialog (its own `ChurchCategoryPane` class), and a Category combo next to the Except List.

**Tech Stack:** Java 21, JavaFX 21 (FXML), manual JDBC (no ORM), MySQL 8, Flyway, JUnit 5 (`mvn test`), JasperReports (PDF export).

**Spec:** `docs/superpowers/specs/2026-10-16-collection-categories-design.md` (read it before starting).

## Global Constraints

- Accounting year = calendar year. A receipt's year = `receiptDateTime.getYear()`. A correction receipt uses the **original** receipt's `accounting_year`.
- The lock counts **all** receipts, including cancelled ones, with `accounting_year = year` for the branch.
- Carry-forward copies the branch's **most recent earlier year**. If no earlier year exists, the receipt fails with exactly: `Categories are not assigned for this branch. Assign them in the branch's Categories tab.`
- The default category for a new branch is code `A`, for all three types. (Existing branches missing from the client list still get `C` in the V40 seed.)
- A category that is in use (any assignment or receipt line) can be **neither deleted nor deactivated**.
- Collection types are `CollectionType.OFFERTORY`, `TITHES` and `OTHER_DONATIONS` (DB enum values of the same names). UI labels: "Offerings", "Tithes", "Other Donations".
- Permissions: `category.menu.view`, `category.create`, `category.update`, `category.delete`. Branch assignments use the existing `church.update`.
- The category is **not** shown in receipt history or on the printed receipt.
- No new third-party dependencies.
- Follow the existing style: constructor injection with a no-arg production constructor, a nested `XxxException extends RuntimeException` per service, `DatabaseException` wrapped into the service exception with a friendly message, and fakes (subclasses with a `null` DataSource) instead of mocking libraries.
- Commits: one per task, conventional-commit subject plus a detailed body, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## File Map

| File | Responsibility |
|------|----------------|
| `src/main/resources/db/migration/V40__collection_categories.sql` | Schema, seed, back-fill, permissions |
| `entity/CollectionCategory.java` | Category entity |
| `entity/CategoryAssignment.java` | Assignment record (church, type, category, year) |
| `dto/ChurchCategoryAssignments.java` | One branch-year view for the UI (ids, lock, carry-forward source) |
| `repository/CollectionCategoryRepository.java` | Category CRUD and usage queries |
| `repository/CategoryAssignmentRepository.java` | Assignment queries. Every method takes a `Connection` |
| `validation/CollectionCategoryValidator.java` | Code, name and status validation |
| `service/CollectionCategoryService.java` | Category master rules (R6, R8) |
| `service/CategoryAssignmentService.java` | Lock, carry-forward, resolution, defaults (R1–R5, R7) |
| `service/ActivityLogService.java` (modify) | New action constants and log methods |
| `entity/Receipt.java`, `entity/ReceiptItem.java`, `repository/ReceiptRepository.java`, `service/ReceiptService.java` (modify) | Store `accounting_year` and `category_id` |
| `repository/ChurchRepository.java`, `service/ChurchService.java` (modify) | Save the branch and its initial assignments in one transaction |
| `controller/CategoryController.java`, `view/category-view.fxml` | Category master screen |
| `controller/DashboardController.java`, `view/dashboard-view.fxml` (modify) | "Categories" menu item |
| `controller/ChurchCategoryPane.java` | Branch dialog Categories tab |
| `controller/ChurchController.java` (modify) | Wraps the dialog in a TabPane |
| `dto/report/ReportSearchCriteria.java`, `repository/ReportRepository.java`, `service/ReportService.java`, `reports/export/ReportPdfExporter.java`, `controller/ReportsController.java`, `view/reports-view.fxml` (modify) | Category filter |
| `src/test/java/com/churchmanagement/testsupport/RecordingDataSource.java` | Shared test DataSource: records SQL and parameters, and commit/rollback |

All Java paths are under `src/main/java/com/churchmanagement/` and all FXML under `src/main/resources/com/churchmanagement/`, unless a full path is shown.

---

### Task 1: Migration V40 (schema, seed, back-fill, permissions)

**Files:**
- Create: `src/main/resources/db/migration/V40__collection_categories.sql`

**Interfaces:**
- Produces:
  - Tables `collection_categories(id, code, name, description, status, created_at, updated_at)` and `church_category_assignments(id, church_id, collection_type, category_id, accounting_year, created_at, updated_at)`.
  - Columns `receipts.accounting_year SMALLINT NOT NULL` and `receipt_items.category_id BIGINT NOT NULL`.
  - Four `category.*` permissions granted to Admin.

- [ ] **Step 1: Write the migration**

```sql
-- Phase 02 / requirement 01: collection categories.
-- A category is assigned per church x collection type x accounting year and
-- is stored on each receipt line when the receipt is created.

CREATE TABLE collection_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_collection_categories_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO collection_categories (code, name, status, created_at) VALUES
    ('A', 'Category A', 'ACTIVE', CURRENT_TIMESTAMP),
    ('B', 'Category B', 'ACTIVE', CURRENT_TIMESTAMP),
    ('C', 'Category C', 'ACTIVE', CURRENT_TIMESTAMP),
    ('D', 'Category D', 'ACTIVE', CURRENT_TIMESTAMP),
    ('E', 'Category E', 'ACTIVE', CURRENT_TIMESTAMP);

CREATE TABLE church_category_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    church_id BIGINT NOT NULL,
    collection_type ENUM('OFFERTORY','TITHES','OTHER_DONATIONS') NOT NULL,
    category_id BIGINT NOT NULL,
    accounting_year SMALLINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_church_category_assignment (church_id, collection_type, accounting_year),
    KEY idx_church_category_assignments_category (category_id),
    CONSTRAINT fk_church_category_assignments_church
        FOREIGN KEY (church_id) REFERENCES churches (id),
    CONSTRAINT fk_church_category_assignments_category
        FOREIGN KEY (category_id) REFERENCES collection_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2026 assignments, matched by church_code at run time (docs/CATEGORY-ASSIGNMENTS.md).
-- Columns: church_code, tithes, offerings, others. Churches not listed get C.
INSERT INTO church_category_assignments (church_id, collection_type, category_id, accounting_year, created_at)
SELECT c.id, t.collection_type, cat.id, 2026, CURRENT_TIMESTAMP
FROM churches c
CROSS JOIN (
    SELECT 'TITHES' AS collection_type UNION ALL SELECT 'OFFERTORY' UNION ALL SELECT 'OTHER_DONATIONS'
) t
LEFT JOIN (
              SELECT '0001' AS church_code, 'D' AS tithes, 'D' AS offerings, 'D' AS others
    UNION ALL SELECT '0002', 'D', 'D', 'D'
    UNION ALL SELECT '0003', 'A', 'A', 'A'
    UNION ALL SELECT '0004', 'A', 'A', 'A'
    UNION ALL SELECT '0006', 'A', 'A', 'A'
    UNION ALL SELECT '0007', 'A', 'A', 'A'
    UNION ALL SELECT '0008', 'A', 'A', 'A'
    UNION ALL SELECT '0009', 'A', 'A', 'A'
    UNION ALL SELECT '0010', 'B', 'A', 'B'
    UNION ALL SELECT '0011', 'A', 'A', 'A'
    UNION ALL SELECT '0012', 'A', 'A', 'A'
    UNION ALL SELECT '0013', 'A', 'E', 'A'
    UNION ALL SELECT '0014', 'A', 'A', 'A'
    UNION ALL SELECT '0015', 'A', 'A', 'A'
    UNION ALL SELECT '0016', 'A', 'A', 'A'
    UNION ALL SELECT '0017', 'A', 'A', 'A'
    UNION ALL SELECT '0018', 'A', 'A', 'A'
    UNION ALL SELECT '0019', 'A', 'A', 'A'
    UNION ALL SELECT '0020', 'A', 'A', 'A'
    UNION ALL SELECT '0022', 'A', 'A', 'A'
    UNION ALL SELECT '0023', 'A', 'A', 'A'
    UNION ALL SELECT '0024', 'A', 'A', 'A'
    UNION ALL SELECT '0025', 'A', 'A', 'A'
    UNION ALL SELECT '0026', 'A', 'A', 'A'
    UNION ALL SELECT '0027', 'A', 'A', 'A'
    UNION ALL SELECT '0028', 'A', 'A', 'A'
    UNION ALL SELECT '0029', 'A', 'A', 'A'
    UNION ALL SELECT '0030', 'A', 'A', 'A'
    UNION ALL SELECT '0031', 'A', 'A', 'A'
    UNION ALL SELECT '0034', 'A', 'A', 'A'
    UNION ALL SELECT '0035', 'A', 'A', 'A'
    UNION ALL SELECT '0036', 'A', 'A', 'A'
    UNION ALL SELECT '0037', 'A', 'E', 'A'
    UNION ALL SELECT '0038', 'A', 'A', 'A'
    UNION ALL SELECT '0039', 'A', 'A', 'A'
    UNION ALL SELECT '0041', 'A', 'A', 'A'
    UNION ALL SELECT '0042', 'A', 'A', 'A'
    UNION ALL SELECT '0043', 'A', 'A', 'A'
    UNION ALL SELECT '0044', 'A', 'A', 'A'
    UNION ALL SELECT '0045', 'A', 'A', 'A'
    UNION ALL SELECT '0046', 'A', 'A', 'A'
    UNION ALL SELECT '0047', 'A', 'A', 'A'
    UNION ALL SELECT '0049', 'A', 'A', 'A'
    UNION ALL SELECT '0050', 'B', 'A', 'B'
    UNION ALL SELECT '0051', 'B', 'A', 'B'
    UNION ALL SELECT '0053', 'A', 'E', 'A'
    UNION ALL SELECT '0054', 'A', 'A', 'A'
    UNION ALL SELECT '0055', 'A', 'A', 'A'
    UNION ALL SELECT '0056', 'A', 'A', 'A'
    UNION ALL SELECT '0057', 'B', 'A', 'B'
    UNION ALL SELECT '0058', 'A', 'A', 'A'
    UNION ALL SELECT '0064', 'A', 'A', 'A'
    UNION ALL SELECT '0069', 'A', 'A', 'A'
    UNION ALL SELECT '0070', 'A', 'A', 'A'
    UNION ALL SELECT '0071', 'A', 'A', 'A'
    UNION ALL SELECT '0072', 'A', 'A', 'A'
    UNION ALL SELECT '0074', 'A', 'A', 'A'
    UNION ALL SELECT '0075', 'A', 'A', 'A'
    UNION ALL SELECT '0078', 'A', 'A', 'A'
    UNION ALL SELECT '0082', 'A', 'A', 'A'
    UNION ALL SELECT '0084', 'A', 'A', 'A'
    UNION ALL SELECT '0086', 'A', 'A', 'A'
    UNION ALL SELECT '0087', 'A', 'E', 'A'
    UNION ALL SELECT '0088', 'A', 'A', 'A'
    UNION ALL SELECT '0095', 'A', 'A', 'A'
    UNION ALL SELECT '0096', 'A', 'A', 'A'
    UNION ALL SELECT '0097', 'A', 'E', 'A'
    UNION ALL SELECT '0100', 'A', 'A', 'A'
    UNION ALL SELECT '0101', 'A', 'A', 'A'
    UNION ALL SELECT '0102', 'A', 'A', 'A'
    UNION ALL SELECT '0103', 'A', 'A', 'A'
    UNION ALL SELECT '0104', 'A', 'A', 'A'
    UNION ALL SELECT '0105', 'A', 'E', 'A'
    UNION ALL SELECT '0106', 'A', 'A', 'A'
    UNION ALL SELECT '0107', 'A', 'A', 'A'
    UNION ALL SELECT '0108', 'A', 'A', 'A'
    UNION ALL SELECT '0109', 'A', 'A', 'A'
    UNION ALL SELECT '0110', 'A', 'A', 'A'
    UNION ALL SELECT '0111', 'A', 'A', 'A'
) s ON s.church_code = c.church_code
JOIN collection_categories cat ON cat.code = COALESCE(
    CASE t.collection_type
        WHEN 'TITHES' THEN s.tithes
        WHEN 'OFFERTORY' THEN s.offerings
        ELSE s.others
    END, 'C');

-- Accounting year of each receipt: its entry year; a correction takes its original's year.
ALTER TABLE receipts
    ADD COLUMN accounting_year SMALLINT NULL AFTER receipt_datetime;

UPDATE receipts SET accounting_year = YEAR(receipt_datetime);

UPDATE receipts corrected
JOIN receipts original ON original.id = corrected.corrected_from_receipt_id
SET corrected.accounting_year = YEAR(original.receipt_datetime);

ALTER TABLE receipts
    MODIFY accounting_year SMALLINT NOT NULL,
    ADD INDEX idx_receipts_church_accounting_year (church_id, accounting_year);

-- Category of each existing receipt line: the church's 2026 assignment
-- (receipts entered before 2026, if any, also use 2026).
ALTER TABLE receipt_items
    ADD COLUMN category_id BIGINT NULL AFTER collection_type;

UPDATE receipt_items ri
JOIN receipts r ON r.id = ri.receipt_id
JOIN church_category_assignments a
    ON a.church_id = r.church_id
   AND a.collection_type = ri.collection_type
   AND a.accounting_year = 2026
SET ri.category_id = a.category_id;

ALTER TABLE receipt_items
    MODIFY category_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_receipt_items_category
        FOREIGN KEY (category_id) REFERENCES collection_categories (id);

-- Permissions.
INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'category.menu.view' permission_code, 'Categories' module_name, 'View Categories menu' permission_description
    UNION ALL SELECT 'category.create', 'Categories', 'Create collection categories'
    UNION ALL SELECT 'category.update', 'Categories', 'Edit collection categories'
    UNION ALL SELECT 'category.delete', 'Categories', 'Activate, deactivate or delete collection categories'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('category.menu.view', 'category.create', 'category.update', 'category.delete')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
```

- [ ] **Step 2: Apply it to a development database (never production)**

Back up the dev database first (Backup & Restore screen, or `mysqldump`). Start the app with `mvn javafx:run`; `MigrationRunner` applies V40 on startup. If the startup log shows a migration error, fix the SQL. Before re-running, restore the backup (Flyway marks a failed MySQL migration in `flyway_schema_history`).

- [ ] **Step 3: Verify the data with SQL**

Run against the dev DB:

```sql
SELECT code, name FROM collection_categories ORDER BY code;          -- expect A..E
SELECT COUNT(*) FROM church_category_assignments WHERE accounting_year = 2026;
                                                                      -- expect 3 x (SELECT COUNT(*) FROM churches)
SELECT c.church_code, a.collection_type, cat.code
FROM church_category_assignments a
JOIN churches c ON c.id = a.church_id
JOIN collection_categories cat ON cat.id = a.category_id
WHERE c.church_code IN ('0001','0010','0013','0003') ORDER BY 1, 2;
-- expect 0001 D/D/D; 0010 OFFERTORY A, TITHES B, OTHER_DONATIONS B;
--        0013 OFFERTORY E, others A; 0003 all A
SELECT COUNT(*) FROM receipt_items WHERE category_id IS NULL;         -- expect 0
SELECT accounting_year, COUNT(*) FROM receipts GROUP BY 1;
```

Compare the category totals with the Summary table in `docs/CATEGORY-ASSIGNMENTS.md` (A: 73/71/73, B: 4/0/4, D: 2/2/2, E: 0/6/0; C covers any extra branches).

- [ ] **Step 4: Run the existing test suite**

Run: `mvn test`
Expected: BUILD SUCCESS. The migration doesn't touch Java, so nothing should change.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V40__collection_categories.sql
git commit   # message: "feat(db): add collection categories schema, 2026 seed and back-fill (V40)" + detailed body
```

---

### Task 2: Shared test DataSource and activity-log methods

**Files:**
- Create: `src/test/java/com/churchmanagement/testsupport/RecordingDataSource.java`
- Modify: `service/ActivityLogService.java` (constants next to `REGION_*`; methods next to `logRegionAction`)

**Interfaces:**
- Produces:
  - `RecordingDataSource` with: `List<String> sqls()`, `String lastSql()`, `List<Object> lastValues()`, `boolean committed()`, `boolean rolledBack()`. Every connection and statement is a proxy; `executeQuery` returns an empty ResultSet and `executeUpdate` returns 1.
  - `ActivityLogService` constants:
    - `CATEGORY_CREATED`, `CATEGORY_UPDATED`, `CATEGORY_ACTIVATED`, `CATEGORY_DEACTIVATED`, `CATEGORY_DELETED`
    - `CATEGORY_ASSIGNMENTS_UPDATED`, `CATEGORY_ASSIGNMENTS_CARRIED_FORWARD`
    - `NAVIGATE_CATEGORIES`
  - `ActivityLogService` methods:
    - `void logCategoryAction(long userId, String action, String categoryCode)`
    - `void logCategoryAssignmentsUpdated(long userId, String churchCode, int year, String oldValue, String newValue)`
    - `void logCategoryAssignmentsCarriedForward(long userId, String churchCode, int fromYear, int toYear)`

- [ ] **Step 1: Create the test helper**

```java
package com.churchmanagement.testsupport;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A DataSource for unit tests. It records each prepared SQL string, the parameters bound to the last
 * statement, and whether the connection was committed or rolled back. Queries return no rows.
 */
public class RecordingDataSource implements DataSource {
    private final List<String> sqls = new ArrayList<>();
    private final List<Object> lastValues = new ArrayList<>();
    private boolean autoCommit = true;
    private boolean committed;
    private boolean rolledBack;

    public List<String> sqls() {
        return sqls;
    }

    public String lastSql() {
        return sqls.isEmpty() ? null : sqls.getLast();
    }

    public List<Object> lastValues() {
        return lastValues;
    }

    public boolean committed() {
        return committed;
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    @Override
    public Connection getConnection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> {
                        sqls.add((String) args[0]);
                        lastValues.clear();
                        yield statementProxy();
                    }
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> {
                        autoCommit = (boolean) args[0];
                        yield null;
                    }
                    case "commit" -> {
                        committed = true;
                        yield null;
                    }
                    case "rollback" -> {
                        rolledBack = true;
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private PreparedStatement statementProxy() {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                        while (lastValues.size() < index) {
                            lastValues.add(null);
                        }
                        lastValues.set(index - 1, args[1]);
                        return null;
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("executeQuery".equals(name) || "getGeneratedKeys".equals(name)) {
                        return emptyResultSet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> "next".equals(method.getName()) ? false : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
```

- [ ] **Step 2: Add the activity-log constants and methods**

In `ActivityLogService`, add next to the `REGION_*` constants:

```java
    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_ACTIVATED = "CATEGORY_ACTIVATED";
    public static final String CATEGORY_DEACTIVATED = "CATEGORY_DEACTIVATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";
    public static final String CATEGORY_ASSIGNMENTS_UPDATED = "CATEGORY_ASSIGNMENTS_UPDATED";
    public static final String CATEGORY_ASSIGNMENTS_CARRIED_FORWARD = "CATEGORY_ASSIGNMENTS_CARRIED_FORWARD";
```

Add next to `NAVIGATE_CHURCHES`:

```java
    public static final String NAVIGATE_CATEGORIES = "NAVIGATE_CATEGORIES";
```

Add after `logChurchUpdated(...)`:

```java
    public void logCategoryAction(long userId, String action, String categoryCode) {
        log(userId, action, "Category code: " + categoryCode);
    }

    public void logCategoryAssignmentsUpdated(long userId, String churchCode, int year, String oldValue,
                                              String newValue) {
        log(userId, CATEGORY_ASSIGNMENTS_UPDATED, "Church", churchCode, oldValue, newValue,
                "Collection categories for " + year + ", church code: " + churchCode);
    }

    public void logCategoryAssignmentsCarriedForward(long userId, String churchCode, int fromYear, int toYear) {
        log(userId, CATEGORY_ASSIGNMENTS_CARRIED_FORWARD,
                fromYear + " collection categories carried forward to " + toYear + ", church code: " + churchCode);
    }
```

- [ ] **Step 3: Compile and run the tests**

Run: `mvn -q test`
Expected: BUILD SUCCESS (nothing uses the new code yet).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/churchmanagement/testsupport/RecordingDataSource.java src/main/java/com/churchmanagement/service/ActivityLogService.java
git commit   # "test: add shared RecordingDataSource; feat(activity): category log actions"
```

---

### Task 3: Category master (entity, repository, validator, service)

**Files:**
- Create: `entity/CollectionCategory.java`, `repository/CollectionCategoryRepository.java`, `validation/CollectionCategoryValidator.java`, `service/CollectionCategoryService.java`
- Test: `src/test/java/com/churchmanagement/service/CollectionCategoryServiceTest.java`

**Interfaces:**
- Consumes: `ActivityLogService.logCategoryAction` and the `CATEGORY_*` constants (Task 2).
- Produces:
  - `CollectionCategory`: `Long getId()`, `String getCode()`, `String getName()`, `String getDescription()`, `Status getStatus()` (enum `ACTIVE`/`INACTIVE`), `boolean isActive()`, `String displayText()` (`"A - Category A"`), plus setters and the constructor `(Long id, String code, String name, String description, Status status, LocalDateTime createdAt, LocalDateTime updatedAt)`.
  - `CollectionCategoryRepository(DataSource)` methods:
    - `List<CollectionCategory> findAll()`, `findActive()`, `search(String)`
    - `Optional<CollectionCategory> findById(long)`, `findByCode(String)`
    - `boolean existsByCode(String)`, `existsByCodeAndIdNot(String, long)`, `isInUse(long id)`
    - `Map<Long, Integer> countChurchesByCategory(int year)`
    - `CollectionCategory save(CollectionCategory)`, `update(CollectionCategory)`
    - `void updateStatus(long, Status, LocalDateTime)`, `void delete(long)`
  - `CollectionCategoryService` methods:
    - `findAll()`, `findActive()`, `search(String)`, `Map<Long, Integer> churchCountsForYear(int year)`
    - `create(String code, String name, String description, Status status, long userId)`
    - `update(long id, String code, String name, String description, Status status, long userId)`
    - `activate(long id, long userId)`, `deactivate(long id, long userId)`, `delete(long id, long userId)`
    - Nested `CollectionCategoryException`.

- [ ] **Step 1: Write the failing tests**

```java
package com.churchmanagement.service;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.repository.CollectionCategoryRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionCategoryServiceTest {
    private final FakeCategoryRepository repository = new FakeCategoryRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final CollectionCategoryService service = new CollectionCategoryService(repository, activityLogService,
            Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("UTC")));

    @Test
    void createNormalisesCodeToUpperCase() {
        CollectionCategory category = service.create(" f ", " Building Fund ", " For the new hall ",
                CollectionCategory.Status.ACTIVE, 1L);

        assertEquals("F", category.getCode());
        assertEquals("Building Fund", category.getName());
        assertEquals("For the new hall", category.getDescription());
        assertEquals(ActivityLogService.CATEGORY_CREATED, activityLogService.actions.getFirst());
    }

    @Test
    void createRejectsDuplicateCode() {
        service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create("a", "Another", null, CollectionCategory.Status.ACTIVE, 1L));

        assertEquals("A category with this code already exists.", exception.getMessage());
    }

    @Test
    void createRejectsMissingCodeAndName() {
        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create(" ", " ", null, CollectionCategory.Status.ACTIVE, 1L));

        assertTrue(exception.getMessage().contains("Category code is required."));
        assertTrue(exception.getMessage().contains("Category name is required."));
    }

    @Test
    void createRejectsCodeWithSymbols() {
        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create("A-1", "Bad", null, CollectionCategory.Status.ACTIVE, 1L));

        assertTrue(exception.getMessage().contains("Category code may only contain letters and digits (max 10)."));
    }

    @Test
    void updateRenamesCategory() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        CollectionCategory updated = service.update(category.getId(), "A", "General Fund", null,
                CollectionCategory.Status.ACTIVE, 1L);

        assertEquals("General Fund", updated.getName());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_UPDATED));
    }

    @Test
    void deleteUnusedCategory() {
        CollectionCategory category = service.create("F", "Spare", null, CollectionCategory.Status.ACTIVE, 1L);

        service.delete(category.getId(), 1L);

        assertTrue(repository.findById(category.getId()).isEmpty());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_DELETED));
    }

    @Test
    void deleteCategoryInUseIsRejected() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);
        repository.inUseIds.add(category.getId());

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.delete(category.getId(), 1L));

        assertEquals("Category is in use and cannot be deleted.", exception.getMessage());
        assertTrue(repository.findById(category.getId()).isPresent());
    }

    @Test
    void deactivateCategoryInUseIsRejected() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);
        repository.inUseIds.add(category.getId());

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.deactivate(category.getId(), 1L));

        assertEquals("Category is in use and cannot be deactivated.", exception.getMessage());
        assertEquals(CollectionCategory.Status.ACTIVE, repository.findById(category.getId()).orElseThrow().getStatus());
    }

    @Test
    void deactivateCategory() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        service.deactivate(category.getId(), 1L);

        assertEquals(CollectionCategory.Status.INACTIVE, repository.findById(category.getId()).orElseThrow().getStatus());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_DEACTIVATED));
    }

    private static class FakeCategoryRepository extends CollectionCategoryRepository {
        private final List<CollectionCategory> categories = new ArrayList<>();
        private final Set<Long> inUseIds = new HashSet<>();
        private long nextId = 1L;

        private FakeCategoryRepository() {
            super((DataSource) null);
        }

        @Override
        public List<CollectionCategory> findAll() {
            return List.copyOf(categories);
        }

        @Override
        public Optional<CollectionCategory> findById(long id) {
            return categories.stream().filter(category -> category.getId() == id).findFirst();
        }

        @Override
        public boolean existsByCode(String code) {
            return categories.stream().anyMatch(category -> category.getCode().equals(code));
        }

        @Override
        public boolean existsByCodeAndIdNot(String code, long id) {
            return categories.stream().anyMatch(category -> category.getCode().equals(code) && category.getId() != id);
        }

        @Override
        public boolean isInUse(long id) {
            return inUseIds.contains(id);
        }

        @Override
        public CollectionCategory save(CollectionCategory category) {
            category.setId(nextId++);
            categories.add(category);
            return category;
        }

        @Override
        public CollectionCategory update(CollectionCategory category) {
            CollectionCategory existing = findById(category.getId()).orElseThrow();
            existing.setCode(category.getCode());
            existing.setName(category.getName());
            existing.setDescription(category.getDescription());
            existing.setStatus(category.getStatus());
            existing.setUpdatedAt(category.getUpdatedAt());
            return existing;
        }

        @Override
        public void updateStatus(long id, CollectionCategory.Status status, LocalDateTime updatedAt) {
            findById(id).orElseThrow().setStatus(status);
        }

        @Override
        public void delete(long id) {
            categories.removeIf(category -> category.getId() == id);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> actions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logCategoryAction(long userId, String action, String categoryCode) {
            actions.add(action);
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=CollectionCategoryServiceTest`
Expected: COMPILATION ERROR, "cannot find symbol CollectionCategory / CollectionCategoryService".

- [ ] **Step 3: Create the entity**

```java
package com.churchmanagement.entity;

import java.time.LocalDateTime;

public class CollectionCategory {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public CollectionCategory() {
    }

    public CollectionCategory(Long id, String code, String name, String description, Status status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** "A - Category A", as shown in combos and report headers. */
    public String displayText() {
        return code + " - " + name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 4: Create the validator**

```java
package com.churchmanagement.validation;

import com.churchmanagement.entity.CollectionCategory;

import java.util.ArrayList;
import java.util.List;

public final class CollectionCategoryValidator {
    private CollectionCategoryValidator() {
    }

    public static List<String> validateForCreateOrUpdate(String code, String name, String description,
                                                         CollectionCategory.Status status) {
        List<String> errors = new ArrayList<>();

        if (code == null || code.isBlank()) {
            errors.add("Category code is required.");
        } else if (!code.matches("[A-Z0-9]{1,10}")) {
            errors.add("Category code may only contain letters and digits (max 10).");
        }

        if (name == null || name.isBlank()) {
            errors.add("Category name is required.");
        } else if (name.length() > 100) {
            errors.add("Category name must be 100 characters or fewer.");
        }

        if (description != null && description.length() > 255) {
            errors.add("Description must be 255 characters or fewer.");
        }

        if (status == null) {
            errors.add("Status must be ACTIVE or INACTIVE.");
        }

        return errors;
    }
}
```

- [ ] **Step 5: Create the repository**

```java
package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
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

public class CollectionCategoryRepository {
    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, description, status, created_at, updated_at
            FROM collection_categories
            """;

    private final DataSource dataSource;

    public CollectionCategoryRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public CollectionCategoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<CollectionCategory> findAll() {
        return query(SELECT_COLUMNS + " ORDER BY code", List.of(), "Unable to load categories.");
    }

    public List<CollectionCategory> findActive() {
        return query(SELECT_COLUMNS + " WHERE status = 'ACTIVE' ORDER BY code", List.of(),
                "Unable to load categories.");
    }

    public List<CollectionCategory> search(String searchText) {
        String term = "%" + searchText + "%";
        return query(SELECT_COLUMNS + " WHERE code LIKE ? OR name LIKE ? ORDER BY code", List.of(term, term),
                "Unable to search categories.");
    }

    public Optional<CollectionCategory> findById(long id) {
        return query(SELECT_COLUMNS + " WHERE id = ?", List.of(id), "Unable to load category.").stream().findFirst();
    }

    public Optional<CollectionCategory> findByCode(String code) {
        return query(SELECT_COLUMNS + " WHERE code = ?", List.of(code), "Unable to load category.").stream().findFirst();
    }

    public boolean existsByCode(String code) {
        return exists("SELECT 1 FROM collection_categories WHERE code = ?", List.of(code));
    }

    public boolean existsByCodeAndIdNot(String code, long id) {
        return exists("SELECT 1 FROM collection_categories WHERE code = ? AND id <> ?", List.of(code, id));
    }

    /** True when any assignment or receipt line references the category. */
    public boolean isInUse(long id) {
        return exists("""
                SELECT 1 FROM church_category_assignments WHERE category_id = ?
                UNION ALL
                SELECT 1 FROM receipt_items WHERE category_id = ?
                LIMIT 1
                """, List.of(id, id));
    }

    /** category id -> number of churches assigned to it (any collection type) in {@code year}. */
    public Map<Long, Integer> countChurchesByCategory(int year) {
        String sql = """
                SELECT category_id, COUNT(DISTINCT church_id) church_count
                FROM church_category_assignments
                WHERE accounting_year = ?
                GROUP BY category_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            Map<Long, Integer> counts = new HashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getLong("category_id"), resultSet.getInt("church_count"));
                }
            }
            return counts;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count category usage.", exception);
        }
    }

    public CollectionCategory save(CollectionCategory category) {
        String sql = """
                INSERT INTO collection_categories (code, name, description, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getCode());
            statement.setString(2, category.getName());
            setNullableString(statement, 3, category.getDescription());
            statement.setString(4, category.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(category.getCreatedAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    category.setId(keys.getLong(1));
                }
            }
            return category;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save category.", exception);
        }
    }

    public CollectionCategory update(CollectionCategory category) {
        String sql = """
                UPDATE collection_categories
                SET code = ?, name = ?, description = ?, status = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getCode());
            statement.setString(2, category.getName());
            setNullableString(statement, 3, category.getDescription());
            statement.setString(4, category.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(category.getUpdatedAt()));
            statement.setLong(6, category.getId());
            statement.executeUpdate();
            return category;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update category.", exception);
        }
    }

    public void updateStatus(long id, CollectionCategory.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE collection_categories SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update category status.", exception);
        }
    }

    public void delete(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM collection_categories WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete category.", exception);
        }
    }

    private List<CollectionCategory> query(String sql, List<Object> parameters, String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            List<CollectionCategory> categories = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    categories.add(map(resultSet));
                }
            }
            return categories;
        } catch (SQLException exception) {
            throw new DatabaseException(errorMessage, exception);
        }
    }

    private boolean exists(String sql, List<Object> parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check category.", exception);
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private CollectionCategory map(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new CollectionCategory(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                CollectionCategory.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime());
    }
}
```

- [ ] **Step 6: Create the service**

```java
package com.churchmanagement.service;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.validation.CollectionCategoryValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CollectionCategoryService {
    private final CollectionCategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public CollectionCategoryService() {
        this(new CollectionCategoryRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public CollectionCategoryService(CollectionCategoryRepository categoryRepository,
                                     ActivityLogService activityLogService, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<CollectionCategory> findAll() {
        try {
            return categoryRepository.findAll();
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    public List<CollectionCategory> findActive() {
        try {
            return categoryRepository.findActive();
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    public List<CollectionCategory> search(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return findAll();
        }
        try {
            return categoryRepository.search(searchText.strip());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to search categories right now. Please try again later.", exception);
        }
    }

    public Map<Long, Integer> churchCountsForYear(int year) {
        try {
            return categoryRepository.countChurchesByCategory(year);
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load category usage right now.", exception);
        }
    }

    public CollectionCategory create(String code, String name, String description,
                                     CollectionCategory.Status status, long userId) {
        CollectionCategory category = normalize(code, name, description, status);
        validate(category);
        try {
            if (categoryRepository.existsByCode(category.getCode())) {
                throw new CollectionCategoryException("A category with this code already exists.");
            }
            category.setCreatedAt(LocalDateTime.now(clock));
            CollectionCategory saved = categoryRepository.save(category);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_CREATED, saved.getCode());
            return saved;
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to save category right now. Please try again later.", exception);
        }
    }

    public CollectionCategory update(long id, String code, String name, String description,
                                     CollectionCategory.Status status, long userId) {
        CollectionCategory category = normalize(code, name, description, status);
        category.setId(id);
        validate(category);
        try {
            categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            if (categoryRepository.existsByCodeAndIdNot(category.getCode(), id)) {
                throw new CollectionCategoryException("A category with this code already exists.");
            }
            category.setUpdatedAt(LocalDateTime.now(clock));
            CollectionCategory updated = categoryRepository.update(category);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_UPDATED, updated.getCode());
            return updated;
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category right now. Please try again later.", exception);
        }
    }

    public void activate(long id, long userId) {
        updateStatus(id, CollectionCategory.Status.ACTIVE, userId, ActivityLogService.CATEGORY_ACTIVATED);
    }

    public void deactivate(long id, long userId) {
        try {
            if (categoryRepository.isInUse(id)) {
                throw new CollectionCategoryException("Category is in use and cannot be deactivated.");
            }
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category status right now. Please try again later.", exception);
        }
        updateStatus(id, CollectionCategory.Status.INACTIVE, userId, ActivityLogService.CATEGORY_DEACTIVATED);
    }

    public void delete(long id, long userId) {
        try {
            CollectionCategory category = categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            if (categoryRepository.isInUse(id)) {
                throw new CollectionCategoryException("Category is in use and cannot be deleted.");
            }
            categoryRepository.delete(id);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_DELETED, category.getCode());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to delete category right now. Please try again later.", exception);
        }
    }

    private void updateStatus(long id, CollectionCategory.Status status, long userId, String action) {
        try {
            CollectionCategory category = categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            categoryRepository.updateStatus(id, status, LocalDateTime.now(clock));
            activityLogService.logCategoryAction(userId, action, category.getCode());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category status right now. Please try again later.", exception);
        }
    }

    private CollectionCategory normalize(String code, String name, String description, CollectionCategory.Status status) {
        String normalizedCode = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        String normalizedName = name == null ? "" : name.strip();
        String normalizedDescription = description == null || description.isBlank() ? null : description.strip();
        return new CollectionCategory(null, normalizedCode, normalizedName, normalizedDescription, status, null, null);
    }

    private void validate(CollectionCategory category) {
        List<String> errors = CollectionCategoryValidator.validateForCreateOrUpdate(
                category.getCode(), category.getName(), category.getDescription(), category.getStatus());
        if (!errors.isEmpty()) {
            throw new CollectionCategoryException(String.join("\n", errors));
        }
    }

    public static class CollectionCategoryException extends RuntimeException {
        public CollectionCategoryException(String message) {
            super(message);
        }

        public CollectionCategoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=CollectionCategoryServiceTest`
Expected: PASS (9 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/churchmanagement/entity/CollectionCategory.java src/main/java/com/churchmanagement/repository/CollectionCategoryRepository.java src/main/java/com/churchmanagement/validation/CollectionCategoryValidator.java src/main/java/com/churchmanagement/service/CollectionCategoryService.java src/test/java/com/churchmanagement/service/CollectionCategoryServiceTest.java
git commit   # "feat(categories): collection category master service"
```

---

### Task 4: Category assignment service (lock, carry-forward, resolution)

**Files:**
- Create: `entity/CategoryAssignment.java`, `dto/ChurchCategoryAssignments.java`, `repository/CategoryAssignmentRepository.java`, `service/CategoryAssignmentService.java`
- Test: `src/test/java/com/churchmanagement/service/CategoryAssignmentServiceTest.java`

**Interfaces:**
- Consumes:
  - `CollectionCategoryRepository.findById(long)` and `findByCode(String)` (Task 3).
  - `ActivityLogService.logCategoryAssignmentsUpdated` (Task 2).
  - `RecordingDataSource` (Task 2), in the tests.
- Produces:
  - `record CategoryAssignment(long churchId, CollectionType collectionType, long categoryId, int accountingYear)`.
  - `record ChurchCategoryAssignments(int year, Map<CollectionType, Long> categoryIds, boolean locked, String lockReason, Integer carriedForwardFrom)`.
  - `record CategoryAssignmentService.ResolvedCategories(Map<CollectionType, Long> categoryIds, Integer carriedForwardFrom)`.
  - `CategoryAssignmentRepository(DataSource)`. Every method takes a `Connection`:
    - `List<CategoryAssignment> findByChurchAndYear(long churchId, int year, Connection)` and `findByChurchAndYearForUpdate(…)`
    - `OptionalInt findLatestYearBefore(long churchId, int year, Connection)`
    - `List<Integer> findYears(long churchId, Connection)`
    - `int countReceiptsForYear(long churchId, int year, Connection)`
    - `void upsert(long churchId, int year, CollectionType type, long categoryId, LocalDateTime now, Connection)`
  - `CategoryAssignmentService(CategoryAssignmentRepository, CollectionCategoryRepository, ActivityLogService, Clock, DataSource)` methods:
    - `ChurchCategoryAssignments findForChurch(long churchId, int year)`
    - `List<Integer> selectableYears(long churchId)`
    - `Long defaultCategoryId()`
    - `void saveAssignments(long churchId, String churchCode, int year, Map<CollectionType, Long> categoryIds, long userId)`
    - `void assignInitial(long churchId, int year, Map<CollectionType, Long> categoryIdsOrNull, Connection)`
    - `ResolvedCategories resolveForReceipt(long churchId, int year, Connection)`
    - Nested `CategoryAssignmentException`.
    - Constant `NOT_ASSIGNED_MESSAGE = "Categories are not assigned for this branch. Assign them in the branch's Categories tab."`

- [ ] **Step 1: Write the failing tests**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.repository.CategoryAssignmentRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryAssignmentServiceTest {
    private static final long CHURCH = 10L;
    private static final long A = 1L;
    private static final long C = 3L;
    private static final long E = 5L;
    private static final long INACTIVE = 9L;

    private final FakeAssignmentRepository assignments = new FakeAssignmentRepository();
    private final FakeCategoryRepository categories = new FakeCategoryRepository();
    private final FakeActivityLogService activityLog = new FakeActivityLogService();
    private final RecordingDataSource dataSource = new RecordingDataSource();
    private final CategoryAssignmentService service = new CategoryAssignmentService(assignments, categories,
            activityLog, Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("UTC")), dataSource);

    @Test
    void yearWithoutReceiptsIsUnlocked() {
        assignAll(2026, A);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2026);

        assertFalse(result.locked());
        assertEquals(A, result.categoryIds().get(CollectionType.TITHES));
    }

    @Test
    void yearWithReceiptsIsLockedWithReason() {
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 14);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2026);

        assertTrue(result.locked());
        assertEquals("Locked: 14 receipt(s) exist for 2026.", result.lockReason());
    }

    @Test
    void cancelledReceiptsAlsoLockTheYear() {
        // countReceiptsForYear counts every status; the repository SQL has no status filter (see Step 4).
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 1);

        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2026, allOf(C), 1L));

        assertEquals("Categories for 2026 are locked because receipts already exist.", exception.getMessage());
    }

    @Test
    void futureYearStaysEditableWhileCurrentYearIsLocked() {
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 5);

        service.saveAssignments(CHURCH, "0012", 2027, allOf(C), 1L);

        assertEquals(C, assignments.get(2027, CollectionType.OFFERTORY));
        assertTrue(dataSource.committed());
        assertEquals(1, activityLog.assignmentUpdates);
    }

    @Test
    void saveRejectsInactiveCategory() {
        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2027, allOf(INACTIVE), 1L));

        assertEquals("Category Z is inactive and cannot be assigned.", exception.getMessage());
    }

    @Test
    void saveRejectsMissingCollectionType() {
        Map<CollectionType, Long> partial = new HashMap<>(allOf(A));
        partial.remove(CollectionType.OTHER_DONATIONS);

        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2027, partial, 1L));

        assertEquals("Choose a category for Other Donations.", exception.getMessage());
    }

    @Test
    void resolveUsesExistingAssignments() {
        assignAll(2026, A);
        assignments.put(2026, CollectionType.OFFERTORY, E);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2026, null);

        assertEquals(E, resolved.categoryIds().get(CollectionType.OFFERTORY));
        assertEquals(A, resolved.categoryIds().get(CollectionType.TITHES));
        assertNull(resolved.carriedForwardFrom());
    }

    @Test
    void resolveCarriesForwardFromPreviousYear() {
        assignAll(2026, A);
        assignments.put(2026, CollectionType.OFFERTORY, E);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2027, null);

        assertEquals(2026, resolved.carriedForwardFrom());
        assertEquals(E, assignments.get(2027, CollectionType.OFFERTORY));
        assertEquals(A, assignments.get(2027, CollectionType.TITHES));
    }

    @Test
    void resolveCarriesForwardFromOlderYearWhenAYearWasSkipped() {
        assignAll(2025, C);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2027, null);

        assertEquals(2025, resolved.carriedForwardFrom());
        assertEquals(C, assignments.get(2027, CollectionType.TITHES));
    }

    @Test
    void resolveWithoutAnyAssignmentIsRejected() {
        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.resolveForReceipt(CHURCH, 2026, null));

        assertEquals(CategoryAssignmentService.NOT_ASSIGNED_MESSAGE, exception.getMessage());
    }

    @Test
    void findForChurchShowsCarryForwardPreviewForEmptyYear() {
        assignAll(2026, A);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2027);

        assertEquals(2026, result.carriedForwardFrom());
        assertEquals(A, result.categoryIds().get(CollectionType.TITHES));
        assertFalse(result.locked());
    }

    @Test
    void assignInitialDefaultsToCategoryA() {
        service.assignInitial(CHURCH, 2026, null, null);

        for (CollectionType type : CollectionType.values()) {
            assertEquals(A, assignments.get(2026, type));
        }
    }

    @Test
    void selectableYearsIncludeCurrentAndNextYear() {
        assignAll(2025, A);

        assertEquals(List.of(2025, 2026, 2027), service.selectableYears(CHURCH));
    }

    private void assignAll(int year, long categoryId) {
        for (CollectionType type : CollectionType.values()) {
            assignments.put(year, type, categoryId);
        }
    }

    private static Map<CollectionType, Long> allOf(long categoryId) {
        Map<CollectionType, Long> map = new HashMap<>();
        for (CollectionType type : CollectionType.values()) {
            map.put(type, categoryId);
        }
        return map;
    }

    private static class FakeAssignmentRepository extends CategoryAssignmentRepository {
        private final List<CategoryAssignment> rows = new ArrayList<>();
        private final Map<Integer, Integer> receiptCounts = new HashMap<>();

        private FakeAssignmentRepository() {
            super((DataSource) null);
        }

        void put(int year, CollectionType type, long categoryId) {
            rows.removeIf(row -> row.accountingYear() == year && row.collectionType() == type);
            rows.add(new CategoryAssignment(CHURCH, type, categoryId, year));
        }

        Long get(int year, CollectionType type) {
            return rows.stream()
                    .filter(row -> row.accountingYear() == year && row.collectionType() == type)
                    .map(CategoryAssignment::categoryId)
                    .findFirst().orElse(null);
        }

        @Override
        public List<CategoryAssignment> findByChurchAndYear(long churchId, int year, Connection connection) {
            return rows.stream().filter(row -> row.churchId() == churchId && row.accountingYear() == year).toList();
        }

        @Override
        public List<CategoryAssignment> findByChurchAndYearForUpdate(long churchId, int year, Connection connection) {
            return findByChurchAndYear(churchId, year, connection);
        }

        @Override
        public OptionalInt findLatestYearBefore(long churchId, int year, Connection connection) {
            return rows.stream()
                    .filter(row -> row.churchId() == churchId && row.accountingYear() < year)
                    .mapToInt(CategoryAssignment::accountingYear)
                    .max();
        }

        @Override
        public List<Integer> findYears(long churchId, Connection connection) {
            return rows.stream().map(CategoryAssignment::accountingYear).distinct().sorted().toList();
        }

        @Override
        public int countReceiptsForYear(long churchId, int year, Connection connection) {
            return receiptCounts.getOrDefault(year, 0);
        }

        @Override
        public void upsert(long churchId, int year, CollectionType type, long categoryId, LocalDateTime now,
                           Connection connection) {
            put(year, type, categoryId);
        }
    }

    private static class FakeCategoryRepository extends CollectionCategoryRepository {
        private final List<CollectionCategory> categories = List.of(
                category(A, "A", CollectionCategory.Status.ACTIVE),
                category(C, "C", CollectionCategory.Status.ACTIVE),
                category(E, "E", CollectionCategory.Status.ACTIVE),
                category(INACTIVE, "Z", CollectionCategory.Status.INACTIVE));

        private FakeCategoryRepository() {
            super((DataSource) null);
        }

        private static CollectionCategory category(long id, String code, CollectionCategory.Status status) {
            return new CollectionCategory(id, code, "Category " + code, null, status, LocalDateTime.now(), null);
        }

        @Override
        public Optional<CollectionCategory> findById(long id) {
            return categories.stream().filter(category -> category.getId() == id).findFirst();
        }

        @Override
        public Optional<CollectionCategory> findByCode(String code) {
            return categories.stream().filter(category -> category.getCode().equals(code)).findFirst();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private int assignmentUpdates;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logCategoryAssignmentsUpdated(long userId, String churchCode, int year, String oldValue,
                                                  String newValue) {
            assignmentUpdates++;
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=CategoryAssignmentServiceTest`
Expected: COMPILATION ERROR, "cannot find symbol CategoryAssignmentService".

- [ ] **Step 3: Create the record types**

`entity/CategoryAssignment.java`:

```java
package com.churchmanagement.entity;

import com.churchmanagement.enums.CollectionType;

public record CategoryAssignment(long churchId, CollectionType collectionType, long categoryId, int accountingYear) {
}
```

`dto/ChurchCategoryAssignments.java`:

```java
package com.churchmanagement.dto;

import com.churchmanagement.enums.CollectionType;

import java.util.Map;

/**
 * A branch's categories for one accounting year, as shown in the branch dialog.
 * When the year has no rows yet, {@code categoryIds} holds the values that would be
 * carried forward and {@code carriedForwardFrom} names their year.
 */
public record ChurchCategoryAssignments(int year, Map<CollectionType, Long> categoryIds, boolean locked,
                                        String lockReason, Integer carriedForwardFrom) {
    public ChurchCategoryAssignments {
        categoryIds = Map.copyOf(categoryIds);
    }
}
```

- [ ] **Step 4: Create the repository**

```java
package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Every method runs on the caller's connection so callers control the transaction. */
public class CategoryAssignmentRepository {
    private static final String SELECT_BY_CHURCH_AND_YEAR = """
            SELECT church_id, collection_type, category_id, accounting_year
            FROM church_category_assignments
            WHERE church_id = ? AND accounting_year = ?
            """;

    private final DataSource dataSource;

    public CategoryAssignmentRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public CategoryAssignmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public List<CategoryAssignment> findByChurchAndYear(long churchId, int year, Connection connection) {
        return queryAssignments(SELECT_BY_CHURCH_AND_YEAR, churchId, year, connection);
    }

    /** Locks the rows (and, under InnoDB REPEATABLE READ, the gap when there are none). */
    public List<CategoryAssignment> findByChurchAndYearForUpdate(long churchId, int year, Connection connection) {
        return queryAssignments(SELECT_BY_CHURCH_AND_YEAR + " FOR UPDATE", churchId, year, connection);
    }

    public OptionalInt findLatestYearBefore(long churchId, int year, Connection connection) {
        String sql = """
                SELECT MAX(accounting_year) latest_year
                FROM church_category_assignments
                WHERE church_id = ? AND accounting_year < ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int latest = resultSet.getInt("latest_year");
                    return resultSet.wasNull() ? OptionalInt.empty() : OptionalInt.of(latest);
                }
            }
            return OptionalInt.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category years.", exception);
        }
    }

    public List<Integer> findYears(long churchId, Connection connection) {
        String sql = """
                SELECT DISTINCT accounting_year
                FROM church_category_assignments
                WHERE church_id = ?
                ORDER BY accounting_year
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            List<Integer> years = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    years.add(resultSet.getInt("accounting_year"));
                }
            }
            return years;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category years.", exception);
        }
    }

    /** Counts receipts of every status (cancelled ones too): any receipt locks the year. */
    public int countReceiptsForYear(long churchId, int year, Connection connection) {
        String sql = "SELECT COUNT(*) receipt_count FROM receipts WHERE church_id = ? AND accounting_year = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("receipt_count") : 0;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count receipts.", exception);
        }
    }

    public void upsert(long churchId, int year, CollectionType type, long categoryId, LocalDateTime now,
                       Connection connection) {
        String sql = """
                INSERT INTO church_category_assignments
                    (church_id, collection_type, category_id, accounting_year, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), updated_at = VALUES(created_at)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setString(2, type.name());
            statement.setLong(3, categoryId);
            statement.setInt(4, year);
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save category assignment.", exception);
        }
    }

    private List<CategoryAssignment> queryAssignments(String sql, long churchId, int year, Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            List<CategoryAssignment> assignments = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assignments.add(new CategoryAssignment(
                            resultSet.getLong("church_id"),
                            CollectionType.valueOf(resultSet.getString("collection_type")),
                            resultSet.getLong("category_id"),
                            resultSet.getInt("accounting_year")));
                }
            }
            return assignments;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category assignments.", exception);
        }
    }
}
```

- [ ] **Step 5: Create the service**

```java
package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.CategoryAssignmentRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CategoryAssignmentService {
    public static final String NOT_ASSIGNED_MESSAGE =
            "Categories are not assigned for this branch. Assign them in the branch's Categories tab.";
    static final String DEFAULT_CATEGORY_CODE = "A";

    private final CategoryAssignmentRepository assignmentRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final DataSource dataSource;

    public CategoryAssignmentService() {
        this(new CategoryAssignmentRepository(), new CollectionCategoryRepository(), new ActivityLogService(),
                Clock.systemDefaultZone(), DatabaseConfig.getDataSource());
    }

    public CategoryAssignmentService(CategoryAssignmentRepository assignmentRepository,
                                     CollectionCategoryRepository categoryRepository,
                                     ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this.assignmentRepository = assignmentRepository;
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.dataSource = dataSource;
    }

    public ChurchCategoryAssignments findForChurch(long churchId, int year) {
        try (Connection connection = dataSource.getConnection()) {
            List<CategoryAssignment> rows = assignmentRepository.findByChurchAndYear(churchId, year, connection);
            int receiptCount = assignmentRepository.countReceiptsForYear(churchId, year, connection);
            Integer carriedForwardFrom = null;
            if (rows.isEmpty()) {
                OptionalInt previousYear = assignmentRepository.findLatestYearBefore(churchId, year, connection);
                if (previousYear.isPresent()) {
                    carriedForwardFrom = previousYear.getAsInt();
                    rows = assignmentRepository.findByChurchAndYear(churchId, carriedForwardFrom, connection);
                }
            }
            boolean locked = receiptCount > 0;
            return new ChurchCategoryAssignments(year, toMap(rows), locked,
                    locked ? "Locked: " + receiptCount + " receipt(s) exist for " + year + "." : null,
                    carriedForwardFrom);
        } catch (SQLException | DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    /** Years that have assignments, plus the current and the next year, in ascending order. */
    public List<Integer> selectableYears(long churchId) {
        int currentYear = LocalDate.now(clock).getYear();
        try (Connection connection = dataSource.getConnection()) {
            TreeSet<Integer> years = new TreeSet<>(assignmentRepository.findYears(churchId, connection));
            years.add(currentYear);
            years.add(currentYear + 1);
            return List.copyOf(years);
        } catch (SQLException | DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load category years right now.", exception);
        }
    }

    /** Id of the default category (A) when it exists and is active; null otherwise (the user then picks one). */
    public Long defaultCategoryId() {
        try {
            return categoryRepository.findByCode(DEFAULT_CATEGORY_CODE)
                    .filter(CollectionCategory::isActive)
                    .map(CollectionCategory::getId)
                    .orElse(null);
        } catch (DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load categories right now.", exception);
        }
    }

    public void saveAssignments(long churchId, String churchCode, int year, Map<CollectionType, Long> categoryIds,
                                long userId) {
        validateSelection(categoryIds);
        Connection connection = null;
        boolean previousAutoCommit = true;
        Map<CollectionType, Long> previous;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            previous = toMap(assignmentRepository.findByChurchAndYearForUpdate(churchId, year, connection));
            if (assignmentRepository.countReceiptsForYear(churchId, year, connection) > 0) {
                throw new CategoryAssignmentException(
                        "Categories for " + year + " are locked because receipts already exist.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            for (CollectionType type : CollectionType.values()) {
                assignmentRepository.upsert(churchId, year, type, categoryIds.get(type), now, connection);
            }
            connection.commit();
        } catch (CategoryAssignmentException exception) {
            rollback(connection);
            throw exception;
        } catch (SQLException | DatabaseException exception) {
            rollback(connection);
            throw new CategoryAssignmentException("Unable to save categories right now. Please try again later.", exception);
        } finally {
            close(connection, previousAutoCommit);
        }
        activityLogService.logCategoryAssignmentsUpdated(userId, churchCode, year, describe(previous),
                describe(categoryIds));
    }

    /** Saves a new branch's first assignments on the caller's transaction; null means the default category (A) for all types. */
    public void assignInitial(long churchId, int year, Map<CollectionType, Long> categoryIds, Connection connection) {
        Map<CollectionType, Long> selection = categoryIds == null ? defaultSelection() : categoryIds;
        validateSelection(selection);
        LocalDateTime now = LocalDateTime.now(clock);
        for (CollectionType type : CollectionType.values()) {
            assignmentRepository.upsert(churchId, year, type, selection.get(type), now, connection);
        }
    }

    /**
     * The category for each collection type of a receipt in {@code year}, on the receipt's transaction.
     * Carries the most recent earlier year forward when the year has no assignments yet.
     */
    public ResolvedCategories resolveForReceipt(long churchId, int year, Connection connection) {
        List<CategoryAssignment> rows = assignmentRepository.findByChurchAndYearForUpdate(churchId, year, connection);
        Map<CollectionType, Long> categoryIds = new EnumMap<>(CollectionType.class);
        categoryIds.putAll(toMap(rows));
        if (categoryIds.size() == CollectionType.values().length) {
            return new ResolvedCategories(categoryIds, null);
        }

        OptionalInt previousYear = assignmentRepository.findLatestYearBefore(churchId, year, connection);
        if (previousYear.isEmpty()) {
            throw new CategoryAssignmentException(NOT_ASSIGNED_MESSAGE);
        }
        Map<CollectionType, Long> source = toMap(
                assignmentRepository.findByChurchAndYear(churchId, previousYear.getAsInt(), connection));
        LocalDateTime now = LocalDateTime.now(clock);
        for (CollectionType type : CollectionType.values()) {
            if (categoryIds.containsKey(type)) {
                continue;
            }
            Long categoryId = source.get(type);
            if (categoryId == null) {
                throw new CategoryAssignmentException(NOT_ASSIGNED_MESSAGE);
            }
            assignmentRepository.upsert(churchId, year, type, categoryId, now, connection);
            categoryIds.put(type, categoryId);
        }
        return new ResolvedCategories(categoryIds, previousYear.getAsInt());
    }

    private Map<CollectionType, Long> defaultSelection() {
        Long defaultId = defaultCategoryId();
        if (defaultId == null) {
            throw new CategoryAssignmentException(
                    "Default category " + DEFAULT_CATEGORY_CODE + " is not available. Choose the branch's categories.");
        }
        Map<CollectionType, Long> selection = new EnumMap<>(CollectionType.class);
        for (CollectionType type : CollectionType.values()) {
            selection.put(type, defaultId);
        }
        return selection;
    }

    private void validateSelection(Map<CollectionType, Long> categoryIds) {
        for (CollectionType type : CollectionType.values()) {
            Long categoryId = categoryIds == null ? null : categoryIds.get(type);
            if (categoryId == null) {
                throw new CategoryAssignmentException("Choose a category for " + type.getDisplayLabel() + ".");
            }
            CollectionCategory category;
            try {
                category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new CategoryAssignmentException("Selected category could not be found."));
            } catch (DatabaseException exception) {
                throw new CategoryAssignmentException("Unable to check categories right now.", exception);
            }
            if (!category.isActive()) {
                throw new CategoryAssignmentException(
                        "Category " + category.getCode() + " is inactive and cannot be assigned.");
            }
        }
    }

    private static Map<CollectionType, Long> toMap(List<CategoryAssignment> rows) {
        return rows.stream().collect(Collectors.toMap(CategoryAssignment::collectionType,
                CategoryAssignment::categoryId, (left, right) -> left, () -> new EnumMap<>(CollectionType.class)));
    }

    private String describe(Map<CollectionType, Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return "(not set)";
        }
        StringBuilder text = new StringBuilder();
        for (CollectionType type : CollectionType.values()) {
            Long id = categoryIds.get(type);
            String code = id == null ? "-" : categoryRepository.findById(id).map(CollectionCategory::getCode).orElse("?");
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(type.getDisplayLabel()).append('=').append(code);
        }
        return text.toString();
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure is more useful to the caller.
        }
    }

    private void close(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(previousAutoCommit);
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while closing.
        }
    }

    public record ResolvedCategories(Map<CollectionType, Long> categoryIds, Integer carriedForwardFrom) {
    }

    public static class CategoryAssignmentException extends RuntimeException {
        public CategoryAssignmentException(String message) {
            super(message);
        }

        public CategoryAssignmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=CategoryAssignmentServiceTest`
Expected: PASS (13 tests). If `selectableYearsIncludeCurrentAndNextYear` fails, check that the clock is 2026.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/churchmanagement/entity/CategoryAssignment.java src/main/java/com/churchmanagement/dto/ChurchCategoryAssignments.java src/main/java/com/churchmanagement/repository/CategoryAssignmentRepository.java src/main/java/com/churchmanagement/service/CategoryAssignmentService.java src/test/java/com/churchmanagement/service/CategoryAssignmentServiceTest.java
git commit   # "feat(categories): per-year branch assignments with lock and carry-forward"
```

---

### Task 5: Store accounting year and category on new receipts

**Files:**
- Modify: `entity/Receipt.java`, `entity/ReceiptItem.java`, `repository/ReceiptRepository.java` (the 3 receipt SELECTs at ~lines 70, 98, 125; `mapReceipt`; `insertReceipt`; `insertReceiptItem`), `service/ReceiptService.java`
- Test: `src/test/java/com/churchmanagement/service/ReceiptServiceTest.java`

**Interfaces:**
- Consumes: `CategoryAssignmentService.resolveForReceipt(long, int, Connection)`, `ResolvedCategories`, `CategoryAssignmentException` and `NOT_ASSIGNED_MESSAGE` (Task 4); `ActivityLogService.logCategoryAssignmentsCarriedForward` (Task 2).
- Produces:
  - `Receipt.getAccountingYear()/setAccountingYear(Integer)` and `ReceiptItem.getCategoryId()/setCategoryId(Long)`.
  - A new `ReceiptService` constructor with the 10th parameter `CategoryAssignmentService categoryAssignmentService`.

- [ ] **Step 1: Write the failing tests**

In `ReceiptServiceTest`:

1. Add the field `private FakeCategoryAssignmentService categoryAssignmentService;`.
2. In `setUp()`, before `receiptService = ...`, add `categoryAssignmentService = new FakeCategoryAssignmentService();`.
3. Change **every** `new ReceiptService(…, configurationCache)` call (lines ~68, 93, 106, 121, 138) to pass `, categoryAssignmentService` as an extra last argument.

Add these tests:

```java
    @Test
    void receiptLinesGetCategoryForTheirCollectionType() {
        receiptService.createReceipt(validRequest());

        assertEquals(2026, receiptRepository.lastReceipt.getAccountingYear());
        assertEquals(2026, categoryAssignmentService.requestedYear);
        for (ReceiptItem item : receiptRepository.insertedItems) {
            assertEquals(FakeCategoryAssignmentService.CATEGORY_IDS.get(item.getCollectionType()), item.getCategoryId());
        }
    }

    @Test
    void correctionUsesOriginalReceiptAccountingYear() {
        Receipt original = new Receipt();
        original.setId(55L);
        original.setChurchId(10L);
        original.setStatus(ReceiptStatus.CANCELLED);
        original.setWeekStartDate(validRequest().getWeekStartDate());
        original.setAccountingYear(2025);
        receiptRepository.correctionReceipt = original;
        CreateReceiptRequest request = validRequest();
        request.setCorrectedFromReceiptId(55L);

        receiptService.createReceipt(request);

        assertEquals(2025, receiptRepository.lastReceipt.getAccountingYear());
        assertEquals(2025, categoryAssignmentService.requestedYear);
    }

    @Test
    void receiptIsRejectedWhenBranchHasNoCategories() {
        categoryAssignmentService.notAssigned = true;

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(validRequest()));

        assertEquals(CategoryAssignmentService.NOT_ASSIGNED_MESSAGE, exception.getMessage());
        assertTrue(receiptRepository.insertedReceipts.isEmpty());
        assertTrue(dataSource.connection.rolledBack);
    }

    @Test
    void carryForwardIsLoggedAfterCommit() {
        categoryAssignmentService.carriedForwardFrom = 2025;

        receiptService.createReceipt(validRequest());

        assertTrue(activityLogService.carriedForward);
    }
```

> **Executor note:** use the test class's existing request builder. If it isn't named `validRequest()`, find the helper that the existing "create receipt" tests use, and reuse it with the same church id (10L) and week. Also confirm that the fixed clock is in 2026 (`fixedClock()`). If it isn't, change the expected years to match its year. Read the tests near line 150 before writing these.

Add the fake (next to the other fakes):

```java
    private static class FakeCategoryAssignmentService extends CategoryAssignmentService {
        private static final Map<CollectionType, Long> CATEGORY_IDS = Map.of(
                CollectionType.TITHES, 1L, CollectionType.OFFERTORY, 5L, CollectionType.OTHER_DONATIONS, 1L);
        private Integer requestedYear;
        private boolean notAssigned;
        private Integer carriedForwardFrom;

        private FakeCategoryAssignmentService() {
            super(null, null, null, null, null);
        }

        @Override
        public ResolvedCategories resolveForReceipt(long churchId, int year, Connection connection) {
            requestedYear = year;
            if (notAssigned) {
                throw new CategoryAssignmentException(NOT_ASSIGNED_MESSAGE);
            }
            return new ResolvedCategories(CATEGORY_IDS, carriedForwardFrom);
        }
    }
```

In the existing `FakeActivityLogService`, add:

```java
        private boolean carriedForward;

        @Override
        public void logCategoryAssignmentsCarriedForward(long userId, String churchCode, int fromYear, int toYear) {
            carriedForward = true;
        }
```

Add the imports `java.util.Map` and `com.churchmanagement.enums.CollectionType` if they are missing. `FakeDataSource.connection` and `FakeConnection.rolledBack` are private fields of nested classes, so the test can read them.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=ReceiptServiceTest`
Expected: COMPILATION ERROR. The 10-argument constructor and `setAccountingYear`/`getCategoryId` don't exist yet.

- [ ] **Step 3: Add the entity fields**

In `Receipt.java`, add the field after `receiptDateTime`, with its accessors:

```java
    private Integer accountingYear;

    public Integer getAccountingYear() {
        return accountingYear;
    }

    public void setAccountingYear(Integer accountingYear) {
        this.accountingYear = accountingYear;
    }
```

In `ReceiptItem.java`, add the field after `collectionType`, with its accessors:

```java
    private Long categoryId;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
```

- [ ] **Step 4: Update the repository SQL**

In `ReceiptRepository`:

- In each of the three SELECTs whose results go to `mapReceipt` (`findActiveReceiptForChurchAndWeekForUpdate`, `findReceiptByIdForUpdate`, and the one at ~line 125), change `receipt_datetime, submitted_by_name,` to `receipt_datetime, accounting_year, submitted_by_name,`.
- In `mapReceipt`, after `setReceiptDateTime`, add:

```java
        receipt.setAccountingYear(resultSet.getInt("accounting_year"));
```

- In `insertReceipt`, change the column list to end `corrected_from_receipt_id, created_at, accounting_year`. Make the VALUES list 15 `?` long, and after `statement.setTimestamp(14, …)` add:

```java
            statement.setInt(15, receipt.getAccountingYear());
```

- Replace `insertReceiptItem`'s SQL and its bindings with:

```java
        String sql = """
                INSERT INTO receipt_items (receipt_id, collection_type, category_id, amount, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            statement.setString(2, item.getCollectionType().name());
            statement.setLong(3, item.getCategoryId());
            statement.setBigDecimal(4, item.getAmount());
            setNullableString(statement, 5, item.getNote());
            statement.setTimestamp(6, Timestamp.valueOf(item.getCreatedAt()));
            statement.executeUpdate();
```

- [ ] **Step 5: Wire the category into `ReceiptService`**

1. Add the field `private final CategoryAssignmentService categoryAssignmentService;`.
2. The existing 9-argument constructor (the one ending in `SystemConfigurationCache configurationCache`) becomes a delegate:

```java
    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ReceiptPrintService receiptPrintService,
                          ReceiptSmsNotificationService receiptSmsNotificationService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource,
                          SystemConfigurationCache configurationCache) {
        this(receiptRepository, churchRepository, receiptNumberGeneratorService, receiptPrintService,
                receiptSmsNotificationService, activityLogService, clock, dataSource, configurationCache,
                new CategoryAssignmentService());
    }

    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ReceiptPrintService receiptPrintService,
                          ReceiptSmsNotificationService receiptSmsNotificationService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource,
                          SystemConfigurationCache configurationCache,
                          CategoryAssignmentService categoryAssignmentService) {
        // existing assignments, plus:
        this.categoryAssignmentService = categoryAssignmentService;
    }
```

   Move the body of the old constructor into the new one.
3. Make `validateCorrectionReceipt` return the corrected receipt: change its return type to `Receipt`, return `null` where it currently does `return;`, and `return correctedReceipt;` at the end.
4. In `createReceipt`, replace the block from `validateCorrectionReceipt(request, church, connection);` to the item-insert loop with:

```java
            Receipt correctedReceipt = validateCorrectionReceipt(request, church, connection);

            LocalDateTime now = LocalDateTime.now(clock);
            int accountingYear = correctedReceipt == null ? now.getYear() : correctedReceipt.getAccountingYear();
            CategoryAssignmentService.ResolvedCategories categories =
                    resolveCategories(church.getId(), accountingYear, connection);
            Receipt receipt = buildReceipt(request, church, currentUser.getUserId(), lateSubmission, now);
            receipt.setAccountingYear(accountingYear);
            receipt.setReceiptNo(receiptNumberGeneratorService.generateReceiptNumber(connection));

            long receiptId = receiptRepository.insertReceipt(receipt, connection);
            for (ReceiptItem item : items) {
                item.setCreatedAt(now);
                item.setCategoryId(categories.categoryIds().get(item.getCollectionType()));
                receiptRepository.insertReceiptItem(receiptId, item, connection);
            }

            connection.commit();
            receiptCommitted = true;
            if (categories.carriedForwardFrom() != null) {
                activityLogService.logCategoryAssignmentsCarriedForward(currentUser.getUserId(),
                        church.getChurchCode(), categories.carriedForwardFrom(), accountingYear);
            }
```

   Keep the existing code after `receiptCommitted = true;` (print, SMS, response) unchanged.
5. Add the helper. It turns the assignment error into a `ReceiptException` so that the message reaches the user instead of the generic "Unable to save receipt":

```java
    private CategoryAssignmentService.ResolvedCategories resolveCategories(long churchId, int accountingYear,
                                                                          Connection connection) {
        try {
            return categoryAssignmentService.resolveForReceipt(churchId, accountingYear, connection);
        } catch (CategoryAssignmentService.CategoryAssignmentException exception) {
            throw new ReceiptException(exception.getMessage(), exception);
        }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=ReceiptServiceTest`
Expected: PASS (existing tests plus 4 new ones).

- [ ] **Step 7: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/churchmanagement/entity/Receipt.java src/main/java/com/churchmanagement/entity/ReceiptItem.java src/main/java/com/churchmanagement/repository/ReceiptRepository.java src/main/java/com/churchmanagement/service/ReceiptService.java src/test/java/com/churchmanagement/service/ReceiptServiceTest.java
git commit   # "feat(receipts): store accounting year and category on each receipt line"
```

---

### Task 6: New branches get their first category assignments

**Files:**
- Modify: `repository/ChurchRepository.java` (`save`), `service/ChurchService.java`
- Test: `src/test/java/com/churchmanagement/service/ChurchServiceTest.java`

**Interfaces:**
- Consumes: `CategoryAssignmentService.assignInitial(long, int, Map, Connection)` (Task 4); `RecordingDataSource` (Task 2).
- Produces:
  - `ChurchRepository.save(Church, Connection)`.
  - The constructor `ChurchService(ChurchRepository, RegionRepository, ActivityLogService, Clock, CategoryAssignmentService, DataSource)`.
  - `ChurchService.create(String churchCode, String churchName, Long regionId, Church.Status status, String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition, String authorizedPersonPositionOther, String smsMobileNumber, ReceiptLanguage receiptLanguage, Map<CollectionType, Long> categoryIds, long userId)`. `categoryIds == null` means category A for all types.

- [ ] **Step 1: Write the failing tests**

In `ChurchServiceTest`:

- Replace the `churchService` initialiser with:

```java
    private final FakeCategoryAssignmentService categoryAssignmentService = new FakeCategoryAssignmentService();
    private final ChurchService churchService = new ChurchService(
            churchRepository,
            regionRepository,
            activityLogService,
            Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneId.of("UTC")),
            categoryAssignmentService,
            new RecordingDataSource()
    );
```

- Add the tests:

```java
    @Test
    void createChurchAssignsDefaultCategoriesForCurrentYear() {
        Church church = churchService.create("0120", "New Church", 1L, Church.Status.ACTIVE,
                null, null, null, VALID_SMS_MOBILE, 1L);

        assertEquals(church.getId(), categoryAssignmentService.churchId);
        assertEquals(2026, categoryAssignmentService.year);
        assertNull(categoryAssignmentService.categoryIds);
    }

    @Test
    void createChurchPassesChosenCategories() {
        Map<CollectionType, Long> chosen = Map.of(
                CollectionType.TITHES, 1L, CollectionType.OFFERTORY, 5L, CollectionType.OTHER_DONATIONS, 1L);

        churchService.create("0120", "New Church", 1L, Church.Status.ACTIVE, null, null, null,
                VALID_SMS_MOBILE, ReceiptLanguage.ENGLISH, chosen, 1L);

        assertEquals(chosen, categoryAssignmentService.categoryIds);
    }

    @Test
    void createChurchFailsWhenCategoriesCannotBeAssigned() {
        categoryAssignmentService.failWith = "Default category A is not available. Choose the branch's categories.";

        ChurchService.ChurchException exception = assertThrows(ChurchService.ChurchException.class,
                () -> churchService.create("0120", "New Church", 1L, Church.Status.ACTIVE,
                        null, null, null, VALID_SMS_MOBILE, 1L));

        assertEquals("Default category A is not available. Choose the branch's categories.", exception.getMessage());
    }
```

- In `FakeChurchRepository`, add an override so that the transactional save path works:

```java
        @Override
        public Church save(Church church, Connection connection) {
            return save(church);
        }
```

- Add the fake:

```java
    private static class FakeCategoryAssignmentService extends CategoryAssignmentService {
        private Long churchId;
        private Integer year;
        private Map<CollectionType, Long> categoryIds;
        private String failWith;

        private FakeCategoryAssignmentService() {
            super(null, null, null, null, null);
        }

        @Override
        public void assignInitial(long churchId, int year, Map<CollectionType, Long> categoryIds,
                                  Connection connection) {
            if (failWith != null) {
                throw new CategoryAssignmentException(failWith);
            }
            this.churchId = churchId;
            this.year = year;
            this.categoryIds = categoryIds;
        }
    }
```

- Add the imports: `com.churchmanagement.enums.CollectionType`, `com.churchmanagement.testsupport.RecordingDataSource`, `java.sql.Connection`, `java.util.Map`, and the static `assertNull`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=ChurchServiceTest`
Expected: COMPILATION ERROR. The 6-argument constructor doesn't exist yet.

- [ ] **Step 3: Split `ChurchRepository.save`**

Replace `save(Church church)` with a delegate. The new overload keeps the existing SQL and bindings exactly; only the connection handling changes:

```java
    public Church save(Church church) {
        try (Connection connection = dataSource.getConnection()) {
            return save(church, connection);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save church.", exception);
        }
    }

    public Church save(Church church, Connection connection) {
        String sql = """
                INSERT INTO churches (
                    church_code, church_name, region_id, status, authorized_person_name,
                    authorized_person_position, authorized_person_position_other, sms_mobile_number,
                    receipt_language, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // ... the existing setX(...) calls and generated-key handling, unchanged ...
            return church;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save church.", exception);
        }
    }
```

- [ ] **Step 4: Update `ChurchService`**

- Add the fields `private final CategoryAssignmentService categoryAssignmentService;` and `private final DataSource dataSource;`.
- Constructors:

```java
    public ChurchService() {
        this(new ChurchRepository(), new RegionRepository(), new ActivityLogService(), Clock.systemDefaultZone(),
                new CategoryAssignmentService(), DatabaseConfig.getDataSource());
    }

    public ChurchService(ChurchRepository churchRepository, RegionRepository regionRepository,
                         ActivityLogService activityLogService, Clock clock,
                         CategoryAssignmentService categoryAssignmentService, DataSource dataSource) {
        this.churchRepository = churchRepository;
        this.regionRepository = regionRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.categoryAssignmentService = categoryAssignmentService;
        this.dataSource = dataSource;
    }
```

   Remove the old 4-argument constructor. Run `grep -rn "new ChurchService(" src` to confirm that only `ChurchServiceTest` and the no-arg calls use it.
- Change the existing 10-argument `create(..., ReceiptLanguage receiptLanguage, long userId)` to delegate:

```java
    public Church create(String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber,
                         ReceiptLanguage receiptLanguage, long userId) {
        return create(churchCode, churchName, regionId, status, authorizedPersonName, authorizedPersonPosition,
                authorizedPersonPositionOther, smsMobileNumber, receiptLanguage, null, userId);
    }
```

- Add the new full overload:

```java
    public Church create(String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber,
                         ReceiptLanguage receiptLanguage, Map<CollectionType, Long> categoryIds, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status, authorizedPersonName,
                authorizedPersonPosition, authorizedPersonPositionOther, smsMobileNumber, receiptLanguage);
        validate(church);

        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            ensureActiveRegion(church.getRegionId());
            if (churchRepository.existsByChurchCode(church.getChurchCode())) {
                throw new ChurchException("A church with this code already exists.");
            }

            church.setCreatedAt(LocalDateTime.now(clock));
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Church savedChurch = churchRepository.save(church, connection);
            categoryAssignmentService.assignInitial(savedChurch.getId(), LocalDate.now(clock).getYear(),
                    categoryIds, connection);
            connection.commit();
            activityLogService.logChurchAction(userId, ActivityLogService.CHURCH_CREATED, savedChurch.getChurchCode());
            return savedChurch;
        } catch (ChurchException exception) {
            rollback(connection);
            throw exception;
        } catch (CategoryAssignmentService.CategoryAssignmentException exception) {
            rollback(connection);
            throw new ChurchException(exception.getMessage(), exception);
        } catch (SQLException | DatabaseException exception) {
            rollback(connection);
            throw new ChurchException("Unable to save church right now. Please try again later.", exception);
        } finally {
            close(connection, previousAutoCommit);
        }
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure is more useful to the caller.
        }
    }

    private void close(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(previousAutoCommit);
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while closing.
        }
    }
```

- Imports: `com.churchmanagement.config.DatabaseConfig`, `com.churchmanagement.enums.CollectionType`, `javax.sql.DataSource`, `java.sql.Connection`, `java.sql.SQLException`, `java.time.LocalDate`, `java.util.Map`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=ChurchServiceTest`
Expected: PASS (all existing tests plus 3 new ones).

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS. If a compile error points at another `new ChurchService(4 args)`, switch it to the no-arg constructor (production) or the 6-argument constructor (test).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/churchmanagement/repository/ChurchRepository.java src/main/java/com/churchmanagement/service/ChurchService.java src/test/java/com/churchmanagement/service/ChurchServiceTest.java
git commit   # "feat(branches): save a new branch and its first categories in one transaction"
```

---

### Task 7: Category master screen and menu item

**Files:**
- Create: `src/main/resources/com/churchmanagement/view/category-view.fxml`, `controller/CategoryController.java`
- Modify: `controller/DashboardController.java` (fields ~line 153, handler ~line 475, `createMenuDefinitions` ~line 561, icons ~line 600), `src/main/resources/com/churchmanagement/view/dashboard-view.fxml` (after `churchesButton`, line 59)

**Interfaces:**
- Consumes: `CollectionCategoryService` (Task 3); `ActivityLogService.NAVIGATE_CATEGORIES` (Task 2).

This task is UI-only, so it is verified manually. The rules it calls are already covered by `CollectionCategoryServiceTest`.

- [ ] **Step 1: Create the FXML**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.Pagination?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.CategoryController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>

    <HBox alignment="CENTER_RIGHT" spacing="10">
        <Button fx:id="saveButton" onAction="#handleSave" styleClass="primary-button" text="New Category"/>
    </HBox>

    <HBox alignment="CENTER_RIGHT" spacing="10">
        <TextField fx:id="searchField" HBox.hgrow="ALWAYS" onAction="#handleSearch" promptText="Search by code or name"/>
        <Button fx:id="searchButton" onAction="#handleSearch" styleClass="primary-button" text="Search"/>
        <Button fx:id="refreshButton" onAction="#handleRefresh" text="Refresh"/>
    </HBox>

    <TableView fx:id="categoryTable" VBox.vgrow="ALWAYS">
        <columns>
            <TableColumn fx:id="codeColumn" prefWidth="90" text="Code"/>
            <TableColumn fx:id="nameColumn" prefWidth="220" text="Name"/>
            <TableColumn fx:id="descriptionColumn" prefWidth="260" text="Description"/>
            <TableColumn fx:id="statusColumn" prefWidth="110" text="Status"/>
            <TableColumn fx:id="inUseColumn" prefWidth="140" text="Branches (this year)"/>
            <TableColumn fx:id="actionColumn" prefWidth="120" text="Action"/>
        </columns>
        <columnResizePolicy>
            <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
        </columnResizePolicy>
    </TableView>

    <HBox alignment="CENTER_LEFT" spacing="10" styleClass="table-pagination-bar" VBox.vgrow="NEVER">
        <Label styleClass="pagination-label" text="Categories per page"/>
        <ComboBox fx:id="categoryItemsPerPageComboBox" prefWidth="76" styleClass="items-per-page-combo"/>
        <Label fx:id="categoryPaginationSummaryLabel" styleClass="pagination-summary" text="0 of 0 Categories"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Pagination fx:id="categoryPagination" maxHeight="48"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create the controller**

```java
package com.churchmanagement.controller;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CategoryController {
    private final CollectionCategoryService categoryService = new CollectionCategoryService();
    private final ObservableList<CollectionCategory> categories = FXCollections.observableArrayList();
    private Map<Long, Integer> churchCounts = new HashMap<>();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;

    @FXML private Button saveButton;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private TableView<CollectionCategory> categoryTable;
    @FXML private Pagination categoryPagination;
    @FXML private ComboBox<Integer> categoryItemsPerPageComboBox;
    @FXML private Label categoryPaginationSummaryLabel;
    @FXML private TableColumn<CollectionCategory, String> codeColumn;
    @FXML private TableColumn<CollectionCategory, String> nameColumn;
    @FXML private TableColumn<CollectionCategory, String> descriptionColumn;
    @FXML private TableColumn<CollectionCategory, String> statusColumn;
    @FXML private TableColumn<CollectionCategory, String> inUseColumn;
    @FXML private TableColumn<CollectionCategory, Void> actionColumn;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            return;
        }
        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("category.menu.view")) {
            setFormDisabled(true);
            return;
        }

        ButtonIconUtil.applyIcon(saveButton, "fas-plus");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
        configureTable();
        saveButton.setVisible(permissionGuard.can("category.create"));
        saveButton.setManaged(saveButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("category.update") || permissionGuard.can("category.delete"));
        refreshCategories();
    }

    @FXML
    private void handleSave() {
        showCategoryDialog(null);
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText();
        ProcessingDialog.run("Search Categories", "Searching categories...",
                () -> categoryService.search(searchText),
                categories::setAll,
                throwable -> showFriendlyError(throwable.getMessage() == null
                        ? "Unable to search categories." : throwable.getMessage()));
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        refreshCategories();
    }

    private void configureTable() {
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCode()));
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDescription() == null ? "" : cell.getValue().getDescription()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus().name()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                badge.getStyleClass().add("status-badge");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(status);
                badge.getStyleClass().removeAll("status-active", "status-inactive");
                badge.getStyleClass().add("ACTIVE".equals(status) ? "status-active" : "status-inactive");
                setGraphic(badge);
            }
        });
        inUseColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(churchCounts.getOrDefault(cell.getValue().getId(), 0))));
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button();
            private final Button statusButton = new Button();
            private final Button deleteButton = new Button();
            private final HBox actionBox = new HBox(6, editButton, statusButton, deleteButton);

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                actionBox.setAlignment(Pos.CENTER);
                editButton.getStyleClass().add("table-action-button");
                statusButton.getStyleClass().add("table-action-button");
                deleteButton.getStyleClass().add("table-action-button");
                ButtonIconUtil.applyTableActionIcon(editButton, "fas-edit", "Edit category");
                ButtonIconUtil.applyTableActionIcon(deleteButton, "fas-trash", "Delete category");
                editButton.setOnAction(event -> showCategoryDialog(getTableView().getItems().get(getIndex())));
                statusButton.setOnAction(event -> toggleStatus(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(event -> deleteCategory(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                CollectionCategory category = getTableView().getItems().get(getIndex());
                editButton.setVisible(permissionGuard.can("category.update"));
                editButton.setManaged(editButton.isVisible());
                boolean canDelete = permissionGuard.can("category.delete");
                statusButton.setVisible(canDelete);
                statusButton.setManaged(canDelete);
                deleteButton.setVisible(canDelete);
                deleteButton.setManaged(canDelete);
                ButtonIconUtil.applyTableActionIcon(statusButton,
                        category.isActive() ? "fas-toggle-off" : "fas-toggle-on",
                        category.isActive() ? "Deactivate category" : "Activate category");
                setGraphic(actionBox);
            }
        });

        TablePaginationUtil.configure(categoryTable, categories, categoryPagination, categoryItemsPerPageComboBox,
                categoryPaginationSummaryLabel, "categories");
    }

    private void refreshCategories() {
        try {
            churchCounts = categoryService.churchCountsForYear(LocalDate.now().getYear());
            categories.setAll(categoryService.findAll());
        } catch (CollectionCategoryService.CollectionCategoryException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void showCategoryDialog(CollectionCategory category) {
        boolean editing = category != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Update Category" : "Create Category");
        dialog.setHeaderText(editing ? "Update " + category.getCode() : "Create a new collection category");

        ButtonType saveButtonType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField codeField = new TextField();
        codeField.setPromptText("F");
        TextField nameField = new TextField();
        nameField.setPromptText("Building Fund");
        TextField descriptionField = new TextField();
        ComboBox<CollectionCategory.Status> statusComboBox = new ComboBox<>(FXCollections.observableArrayList(
                CollectionCategory.Status.ACTIVE, CollectionCategory.Status.INACTIVE));
        statusComboBox.setMaxWidth(Double.MAX_VALUE);
        if (editing) {
            codeField.setText(category.getCode());
            nameField.setText(category.getName());
            descriptionField.setText(category.getDescription() == null ? "" : category.getDescription());
            statusComboBox.setValue(category.getStatus());
        } else {
            statusComboBox.setValue(CollectionCategory.Status.ACTIVE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(260);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        grid.add(DialogStyler.fieldLabel("Code"), 0, 0);
        grid.add(codeField, 1, 0);
        grid.add(DialogStyler.fieldLabel("Name"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(DialogStyler.fieldLabel("Description"), 0, 2);
        grid.add(descriptionField, 1, 2);
        grid.add(DialogStyler.fieldLabel("Status"), 0, 3);
        grid.add(statusComboBox, 1, 3);
        dialog.getDialogPane().setContent(grid);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            submitButton.setDisable(true);
            ProcessingDialog.run(editing ? "Update Category" : "Create Category",
                    editing ? "Updating category..." : "Creating category...",
                    () -> {
                if (editing) {
                    categoryService.update(category.getId(), codeField.getText(), nameField.getText(),
                            descriptionField.getText(), statusComboBox.getValue(), currentUser.getUserId());
                } else {
                    categoryService.create(codeField.getText(), nameField.getText(), descriptionField.getText(),
                            statusComboBox.getValue(), currentUser.getUserId());
                }
                    },
                    () -> {
                refreshCategories();
                dialog.close();
                    },
                    throwable -> {
                submitButton.setDisable(false);
                showFriendlyError(throwable.getMessage());
                    });
        });

        dialog.showAndWait();
    }

    private void toggleStatus(CollectionCategory category) {
        if (category.isActive() && !confirm("Deactivate Category", "Deactivate " + category.getCode() + "?",
                "It can no longer be chosen for branch assignments.")) {
            return;
        }
        ProcessingDialog.run(category.isActive() ? "Deactivate Category" : "Activate Category",
                "Updating category...",
                () -> {
            if (category.isActive()) {
                categoryService.deactivate(category.getId(), currentUser.getUserId());
            } else {
                categoryService.activate(category.getId(), currentUser.getUserId());
            }
                },
                this::refreshCategories,
                throwable -> showFriendlyError(throwable.getMessage()));
    }

    private void deleteCategory(CollectionCategory category) {
        if (!confirm("Delete Category", "Delete " + category.getCode() + "?",
                "This cannot be undone.")) {
            return;
        }
        ProcessingDialog.run("Delete Category", "Deleting category...",
                () -> categoryService.delete(category.getId(), currentUser.getUserId()),
                this::refreshCategories,
                throwable -> showFriendlyError(throwable.getMessage()));
    }

    private boolean confirm(String title, String header, String content) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void setFormDisabled(boolean disabled) {
        saveButton.setDisable(disabled);
        searchField.setDisable(disabled);
        categoryTable.setDisable(disabled);
        categoryPagination.setDisable(disabled);
        categoryItemsPerPageComboBox.setDisable(disabled);
    }

    private void showFriendlyError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Category Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
```

> **Executor note:** confirm that `ProcessingDialog.run` has a supplier/consumer overload (title, message, supplier, Consumer<T> onSuccess, Consumer<Throwable> onFailure), as `RegionController.handleSearch` uses. It does as of this plan; match whatever signature `RegionController` uses.

- [ ] **Step 3: Add the menu item**

`dashboard-view.fxml`: add after the `churchesButton` line:

```xml
            <Button fx:id="categoriesButton" maxWidth="Infinity" onAction="#showCategories" styleClass="menu-button" text="Categories"/>
```

`DashboardController`:

```java
    @FXML
    private Button categoriesButton;
```

```java
    @FXML
    private void showCategories() {
        loadMenu(findMenu(categoriesButton));
    }
```

In `createMenuDefinitions()`, after the "Churches" entry:

```java
                new MenuDefinition("Categories", "/com/churchmanagement/view/category-view.fxml",
                        categoriesButton, ActivityLogService.NAVIGATE_CATEGORIES, "category.menu.view"),
```

Next to `setMenuIcon(regionsButton, "fas-map");`:

```java
        setMenuIcon(categoriesButton, "fas-tags");
```

Search `DashboardController` for any list that enumerates every menu button (for example a collapse or sidebar-toggle list, or a per-button visibility loop) and add `categoriesButton` to it: `grep -n "churchesButton" src/main/java/com/churchmanagement/controller/DashboardController.java`.

- [ ] **Step 4: Compile and run the tests**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Manual check**

Run `mvn javafx:run` and sign in as Admin (on the dev DB from Task 1). Then check:
- The **Categories** menu item shows A–E, with branch counts for this year (A ≈ 73).
- Create `F / Building Fund`; rename it; deactivate and activate it; delete it (succeeds because it's unused).
- Delete `A` fails with "Category is in use and cannot be deleted."; Deactivate `A` fails with "Category is in use and cannot be deactivated."
- Signed in as a role without `category.menu.view`, the menu item is hidden.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/category-view.fxml src/main/java/com/churchmanagement/controller/CategoryController.java src/main/java/com/churchmanagement/controller/DashboardController.java src/main/resources/com/churchmanagement/view/dashboard-view.fxml
git commit   # "feat(categories): Category master screen and menu item"
```

---

### Task 8: Categories tab in the branch dialog

**Files:**
- Create: `controller/ChurchCategoryPane.java`
- Modify: `controller/ChurchController.java` (`showChurchDialog`, ~lines 346–396)

**Interfaces:**
- Consumes:
  - `CollectionCategoryService.findAll()` (Task 3).
  - `CategoryAssignmentService.findForChurch`, `selectableYears`, `defaultCategoryId` and `saveAssignments` (Task 4).
  - The 11-argument `ChurchService.create(..., Map<CollectionType, Long> categoryIds, long userId)` (Task 6).
- Produces:
  - `ChurchCategoryPane(CollectionCategoryService, CategoryAssignmentService, Long churchIdOrNull, int currentYear)`, a `VBox`.
  - Its methods: `Map<CollectionType, Long> selectedCategoryIds()`, `int selectedYear()`, `boolean hasEditableChanges()`.

UI only, verified manually. The rules are covered by `CategoryAssignmentServiceTest`.

- [ ] **Step 1: Create the pane**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.service.CategoryAssignmentService;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.util.DialogStyler;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The branch dialog's Categories tab: one category per collection type for a chosen accounting year. */
public class ChurchCategoryPane extends VBox {
    private static final List<CollectionType> TYPES =
            List.of(CollectionType.TITHES, CollectionType.OFFERTORY, CollectionType.OTHER_DONATIONS);

    private final CategoryAssignmentService assignmentService;
    private final Long churchId;
    private final List<CollectionCategory> allCategories;
    private final ComboBox<Integer> yearComboBox = new ComboBox<>();
    private final Map<CollectionType, ComboBox<CollectionCategory>> combos = new EnumMap<>(CollectionType.class);
    private final Label statusLabel = new Label();
    private Map<CollectionType, Long> loadedIds = Map.of();
    private boolean locked;

    public ChurchCategoryPane(CollectionCategoryService categoryService, CategoryAssignmentService assignmentService,
                              Long churchId, int currentYear) {
        super(12);
        this.assignmentService = assignmentService;
        this.churchId = churchId;
        this.allCategories = categoryService.findAll();
        setPadding(new Insets(12, 0, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        grid.add(DialogStyler.fieldLabel("Accounting Year"), 0, 0);
        grid.add(yearComboBox, 1, 0);
        int row = 1;
        for (CollectionType type : TYPES) {
            ComboBox<CollectionCategory> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setCellFactory(list -> new CategoryCell());
            combo.setButtonCell(new CategoryCell());
            combos.put(type, combo);
            grid.add(DialogStyler.fieldLabel(type.getDisplayLabel()), 0, row);
            grid.add(combo, 1, row++);
        }
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("field-hint");
        getChildren().addAll(grid, statusLabel);

        if (churchId == null) {
            yearComboBox.setItems(FXCollections.observableArrayList(currentYear));
            yearComboBox.setValue(currentYear);
            yearComboBox.setDisable(true);
            Long defaultId = assignmentService.defaultCategoryId();
            Map<CollectionType, Long> defaults = new EnumMap<>(CollectionType.class);
            TYPES.forEach(type -> defaults.put(type, defaultId));
            showSelection(defaults, false);
            statusLabel.setText("Categories for " + currentYear + ". Default is A; change them if needed.");
        } else {
            yearComboBox.setItems(FXCollections.observableArrayList(assignmentService.selectableYears(churchId)));
            yearComboBox.valueProperty().addListener((obs, oldYear, newYear) -> {
                if (newYear != null) {
                    loadYear(newYear);
                }
            });
            yearComboBox.setValue(currentYear);
        }
    }

    public int selectedYear() {
        return yearComboBox.getValue();
    }

    public Map<CollectionType, Long> selectedCategoryIds() {
        Map<CollectionType, Long> ids = new EnumMap<>(CollectionType.class);
        combos.forEach((type, combo) -> {
            CollectionCategory selected = combo.getValue();
            ids.put(type, selected == null ? null : selected.getId());
        });
        return ids;
    }

    /** True when the shown year is editable and the selection differs from what is stored. */
    public boolean hasEditableChanges() {
        return !locked && !Objects.equals(loadedIds, selectedCategoryIds());
    }

    private void loadYear(int year) {
        ChurchCategoryAssignments assignments = assignmentService.findForChurch(churchId, year);
        locked = assignments.locked();
        loadedIds = assignments.carriedForwardFrom() == null ? assignments.categoryIds() : Map.of();
        showSelection(assignments.categoryIds(), locked);
        if (locked) {
            statusLabel.setText("🔒 " + assignments.lockReason()
                    + " Set up a future year to change categories.");
        } else if (assignments.carriedForwardFrom() != null) {
            statusLabel.setText("Not set. Will carry forward from " + assignments.carriedForwardFrom()
                    + " on the first receipt. Click Update to save these now.");
        } else if (assignments.categoryIds().isEmpty()) {
            statusLabel.setText("Not set. Choose a category for each collection type.");
        } else {
            statusLabel.setText("Editable until the first receipt of " + year + " is entered.");
        }
    }

    private void showSelection(Map<CollectionType, Long> categoryIds, boolean disable) {
        for (CollectionType type : TYPES) {
            ComboBox<CollectionCategory> combo = combos.get(type);
            Long selectedId = categoryIds.get(type);
            // Active categories, plus the assigned one if it is somehow inactive (defensive; R6 normally prevents it).
            combo.setItems(FXCollections.observableArrayList(allCategories.stream()
                    .filter(category -> category.isActive() || category.getId().equals(selectedId))
                    .toList()));
            combo.setValue(allCategories.stream()
                    .filter(category -> category.getId().equals(selectedId))
                    .findFirst().orElse(null));
            combo.setDisable(disable);
        }
    }

    private static class CategoryCell extends ListCell<CollectionCategory> {
        @Override
        protected void updateItem(CollectionCategory category, boolean empty) {
            super.updateItem(category, empty);
            setText(empty || category == null ? null
                    : category.displayText() + (category.isActive() ? "" : " (inactive)"));
        }
    }
}
```

- [ ] **Step 2: Wire it into `ChurchController.showChurchDialog`**

- Add the fields:

```java
    private final CollectionCategoryService categoryService = new CollectionCategoryService();
    private final CategoryAssignmentService categoryAssignmentService = new CategoryAssignmentService();
```

- Replace `dialog.getDialogPane().setContent(churchDialogContent(church));` with:

```java
        GridPane detailsGrid = churchDialogContent(church);
        ChurchCategoryPane categoryPane;
        try {
            categoryPane = new ChurchCategoryPane(categoryService, categoryAssignmentService,
                    editing ? church.getId() : null, LocalDate.now().getYear());
        } catch (RuntimeException exception) {
            showFriendlyError("Unable to load categories right now. Please try again later.");
            return;
        }
        Tab detailsTab = new Tab("Details", detailsGrid);
        Tab categoriesTab = new Tab("Categories", categoryPane);
        detailsTab.setClosable(false);
        categoriesTab.setClosable(false);
        TabPane tabPane = new TabPane(detailsTab, categoriesTab);
        tabPane.setUserData(detailsGrid.getUserData());
        dialog.getDialogPane().setContent(tabPane);
```

   `tabPane.setUserData(...)` keeps the existing `getContent().getUserData()` lookup working unchanged.
- In the `ProcessingDialog.run` action, after the existing `churchService.update(...)` call in the `editing` branch, add:

```java
                    if (categoryPane.hasEditableChanges()) {
                        categoryAssignmentService.saveAssignments(church.getId(), fields.churchCodeField().getText(),
                                categoryPane.selectedYear(), categoryPane.selectedCategoryIds(),
                                currentUser.getUserId());
                    }
```

- In the `else` (create) branch, change the `churchService.create(...)` call to the 11-argument overload, inserting `categoryPane.selectedCategoryIds(),` before `currentUser.getUserId()`.
- Imports: `com.churchmanagement.service.CategoryAssignmentService`, `com.churchmanagement.service.CollectionCategoryService`, `javafx.scene.control.Tab`, `javafx.scene.control.TabPane`, `java.time.LocalDate`.

- [ ] **Step 3: Compile and run the tests**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual check (dev DB)**

1. **New Church:** the Categories tab shows the current year (disabled) with A/A/A. Change Offerings to E and save. In the DB, the 3 rows exist for the current year, with OFFERTORY = E.
2. **Edit branch 0013, year 2026:** shows Tithes A / Offerings E / Others A. If 0013 has 2026 receipts, the combos are disabled with "🔒 Locked: N receipt(s) exist for 2026."
3. **Same branch, year 2027:** shows "Not set. Will carry forward from 2026…", with the 2026 values pre-filled. Change Tithes to B and click Update. The DB has 2027 rows, and an activity-log entry `CATEGORY_ASSIGNMENTS_UPDATED` was written.
4. Create category F, deactivate it (allowed: unused), then open any branch. F is not listed in the combos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/controller/ChurchCategoryPane.java src/main/java/com/churchmanagement/controller/ChurchController.java
git commit   # "feat(branches): Categories tab in the branch dialog"
```

---

### Task 9: Category filter in the Receipt Collection Report

**Files:**
- Modify: `dto/report/ReportSearchCriteria.java`, `repository/ReportRepository.java` (`getReceiptCollectionReport` ~line 70; `getSummaryTotals` ~line 430; new `appendCategoryFilter`), `service/ReportService.java` (`filterSummary` ~line 316), `reports/export/ReportPdfExporter.java` (~line 1457), `controller/ReportsController.java`, `src/main/resources/com/churchmanagement/view/reports-view.fxml` (~line 100)
- Test: `src/test/java/com/churchmanagement/repository/ReportRepositoryTest.java` (new)

**Interfaces:**
- Consumes: `CollectionCategoryService.findAll()` and `CollectionCategory.displayText()` (Task 3); `RecordingDataSource` (Task 2).
- Produces: `ReportSearchCriteria.getCategoryId()/setCategoryId(Long)` and `getCategoryLabel()/setCategoryLabel(String)`.

- [ ] **Step 1: Write the failing tests**

```java
package com.churchmanagement.repository;

import com.churchmanagement.dto.report.ReportExclusion;
import com.churchmanagement.dto.report.ReportSearchCriteria;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRepositoryTest {
    private final RecordingDataSource dataSource = new RecordingDataSource();
    private final ReportRepository repository = new ReportRepository(dataSource);

    @Test
    void receiptCollectionReportFiltersByCategoryTogetherWithExceptList() {
        ReportSearchCriteria criteria = receiptCollectionCriteria();
        criteria.setCategoryId(5L);
        criteria.setExclusions(List.of(new ReportExclusion(7L, "0007 Hanwella", CollectionType.TITHES)));

        repository.getReceiptCollectionReport(criteria);

        String sql = dataSource.lastSql();
        assertTrue(sql.contains("AND ri.category_id = ?"));
        assertTrue(sql.contains("AND NOT (r.church_id = ? AND ri.collection_type = ?)"));
        assertTrue(dataSource.lastValues().contains(5L));
    }

    @Test
    void summaryTotalsUseTheCategoryFilter() {
        ReportSearchCriteria criteria = receiptCollectionCriteria();
        criteria.setCategoryId(5L);

        // RecordingDataSource returns no rows and queryOne() calls orElseThrow(); only the SQL matters here.
        assertThrows(NoSuchElementException.class, () -> repository.getSummaryTotals(criteria));

        assertTrue(dataSource.lastSql().contains("AND ri.category_id = ?"));
        assertTrue(dataSource.lastValues().contains(5L));
    }

    @Test
    void noCategorySelectedAddsNoFilter() {
        repository.getReceiptCollectionReport(receiptCollectionCriteria());

        assertFalse(dataSource.lastSql().contains("ri.category_id"));
    }

    private ReportSearchCriteria receiptCollectionCriteria() {
        ReportSearchCriteria criteria = new ReportSearchCriteria();
        criteria.setReportType(ReportType.RECEIPT_COLLECTION);
        return criteria;
    }
}
```

> **Executor note:** `ReportRepository.bind` uses `setObject`, so `lastValues()` holds `5L` as written. `queryOne` calls `orElseThrow()`, which is why the summary test expects `NoSuchElementException` from the empty fake result. Don't change production code to suit the fake.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=ReportRepositoryTest`
Expected: COMPILATION ERROR, because `setCategoryId` doesn't exist.

- [ ] **Step 3: Add the criteria fields**

In `ReportSearchCriteria`, after `exclusions`:

```java
    private Long categoryId;
    private String categoryLabel;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    /** "A - Category A" for export headers and the activity log; null when no category is selected. */
    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }
```

If the class has a copy method or constructor (search for `exclusions` in it), copy both fields there too.

- [ ] **Step 4: Add the repository filter**

In `ReportRepository`, next to `appendExclusionFilter`:

```java
    /** Category quick filter: keeps only receipt lines in the chosen category, so totals follow. */
    private void appendCategoryFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria) {
        if (criteria.getCategoryId() != null) {
            sql.append("AND ri.category_id = ? ");
            parameters.add(criteria.getCategoryId());
        }
    }
```

Call `appendCategoryFilter(sql, parameters, criteria);` immediately after `appendExclusionFilter(sql, parameters, criteria);` in **both** `getReceiptCollectionReport` and the `RECEIPT_COLLECTION` branch of `getSummaryTotals`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=ReportRepositoryTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Show the filter in the PDF header and the activity log**

`ReportPdfExporter`, after the `"Except:"` filter line (~1457–1459):

```java
        addFilter(filterItems, "Category:", criteria.getCategoryLabel());
```

(`addFilter` already skips null or blank values, so nothing appears when no category is selected.)

`ReportService.filterSummary`, before the `+ ", exclusions="` part:

```java
                + ", category=" + (criteria.getCategoryLabel() == null ? "" : criteria.getCategoryLabel())
```

- [ ] **Step 7: Add the combo to the report screen**

`reports-view.fxml`: inside `exceptBox` (line ~100), before the `exceptButton`:

```xml
                                <ComboBox fx:id="categoryComboBox" prefWidth="200" promptText="Category"/>
```

`ReportsController`:

```java
    @FXML private ComboBox<CollectionCategory> categoryComboBox;
    private final CollectionCategoryService categoryService = new CollectionCategoryService();
```

- In `configureFilters()`, add:

```java
        categoryComboBox.setCellFactory(list -> new CategoryFilterCell());
        categoryComboBox.setButtonCell(new CategoryFilterCell());
```

- In `loadLookupData()`, after the church lists:

```java
        CollectionCategory allCategories = new CollectionCategory();
        allCategories.setName(ALL);
        categoryComboBox.setItems(FXCollections.observableArrayList(allCategories));
        categoryComboBox.getItems().addAll(categoryService.findAll());
        categoryComboBox.setValue(allCategories);
```

- In the criteria builder, after `criteria.setExclusions(...)` (~line 679):

```java
        CollectionCategory category = categoryComboBox.getValue();
        boolean categoryFilter = EXCLUSION_REPORT_TYPES.contains(criteria.getReportType())
                && category != null && category.getId() != null;
        criteria.setCategoryId(categoryFilter ? category.getId() : null);
        criteria.setCategoryLabel(categoryFilter ? category.displayText() : null);
```

- Where the filters are reset (the method containing `exclusions.clear();` at ~line 215), add:

```java
        if (!categoryComboBox.getItems().isEmpty()) {
            categoryComboBox.setValue(categoryComboBox.getItems().getFirst());
        }
```

- Add the cell class at the bottom of the controller:

```java
    private static class CategoryFilterCell extends ListCell<CollectionCategory> {
        @Override
        protected void updateItem(CollectionCategory category, boolean empty) {
            super.updateItem(category, empty);
            setText(empty || category == null ? null
                    : category.getId() == null ? category.getName() : category.displayText());
        }
    }
```

- Imports: `com.churchmanagement.entity.CollectionCategory`, `com.churchmanagement.service.CollectionCategoryService`, and `javafx.scene.control.ListCell` (if missing).

The combo sits inside `exceptBox`, so it is already shown only for the Receipt Collection Report by `applyReportTypeVisibility`.

- [ ] **Step 8: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Manual check (dev DB)**

Open Receipt Collection Report → This Month:
1. Category **All**: totals unchanged from before this feature.
2. Category **E**: only the E branches (0013, 0037, …) appear, and only their Offerings amounts. Tithes and Others are 0. Totals match the rows.
3. **E** plus Except List "0013 (Offerings)": 0013 disappears and the totals drop by its amount.
4. Export PDF: the header filter line shows `Category: E - Category E`. Export Excel: the data is filtered the same way.
5. The Activity Log has a `REPORT_FILTER_CHANGED` entry with `category=E - Category E`.
6. Switching to another report type hides the combo.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/report/ReportSearchCriteria.java src/main/java/com/churchmanagement/repository/ReportRepository.java src/main/java/com/churchmanagement/service/ReportService.java src/main/java/com/churchmanagement/reports/export/ReportPdfExporter.java src/main/java/com/churchmanagement/controller/ReportsController.java src/main/resources/com/churchmanagement/view/reports-view.fxml src/test/java/com/churchmanagement/repository/ReportRepositoryTest.java
git commit   # "feat(reports): Category filter in the Receipt Collection Report"
```

---

### Task 10: End-to-end check on the dev database

**Files:** none (verification only)

- [ ] **Step 1: Full test suite**

Run: `mvn test`
Expected: BUILD SUCCESS, with 0 failures. Note the total test count in the summary.

- [ ] **Step 2: Receipt flow**

On the dev DB, as Admin:
1. Enter a receipt for 0013 with all three types filled in. Check in the DB that `receipts.accounting_year = 2026`, and that the `receipt_items.category_id` codes are TITHES=A, OFFERTORY=E, OTHER_DONATIONS=A.
2. Open 0013 → Categories → 2026: it is locked.
3. Cancel that receipt, then create its correction. The correction has `accounting_year = 2026` and the same categories.
4. Create a new branch with defaults, then enter a receipt for it. Its lines are category A.
5. Simulate carry-forward: in SQL, delete the new branch's current-year rows and insert the same rows for the previous year. Then enter a receipt. The current-year rows are recreated, and the Activity Log shows `CATEGORY_ASSIGNMENTS_CARRIED_FORWARD`.
6. Simulate "nothing to carry forward": delete all of a test branch's assignment rows, then try to enter a receipt. You should see exactly: "Categories are not assigned for this branch. Assign them in the branch's Categories tab." No receipt is saved.

- [ ] **Step 3: Restore the dev database**

Restore the backup taken in Task 1 if the test data shouldn't be kept.

- [ ] **Step 4: Report**

Summarise the results (test counts, the manual checks that passed, anything that didn't) for the user. User-manual updates (EN + SI) are a separate follow-up, as the spec §8 notes.
