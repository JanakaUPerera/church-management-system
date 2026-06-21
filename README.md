# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

**Run the application:**
```bash
mvn javafx:run
```

**Build a distributable JAR (with runtime deps copied to `target/lib/`):**
```bash
mvn clean package
```

**Run all tests:**
```bash
mvn test
```

**Run a single test class:**
```bash
mvn test -Dtest=ReceiptServiceTest
```

**Run a single test method:**
```bash
mvn test -Dtest=ReceiptServiceTest#shouldGenerateReceiptNumber
```

**Compile only (no tests):**
```bash
mvn clean compile
```

## Architecture

This is a **JavaFX 21 desktop application** — not a web app, not Spring Boot. There is no IoC container; all wiring is manual.

### Startup sequence

`AppLauncher.main()` → checks for `--auto-backup` CLI flag (used by the scheduled backup subprocess) → otherwise calls `MainApplication.launchUi()` → `MainApplication.init()` runs Flyway migrations and loads `SystemConfigurationCache` → `start()` loads `login-view.fxml`.

### Layer stack (top → bottom)

| Layer | Package | Role |
|---|---|---|
| View | `src/main/resources/…/view/*.fxml` | FXML layout files |
| Controller | `controller/` | JavaFX controllers; wire services in `initialize()` |
| Service | `service/` | Business logic; instantiated directly inside controllers |
| Repository | `repository/` | Raw JDBC; each class takes a `DataSource` constructor arg |
| Entity | `entity/` | Plain POJOs, no ORM annotations |

Services are **not** singletons unless explicitly designed as one (e.g. `SystemConfigurationCache`, `AutoBackupScheduler` use the initialization-on-demand holder pattern). Controllers create service instances directly.

### Data access

All persistence is **manual JDBC** — no JPA, no Hibernate. Repositories accept a `DataSource` in their constructor (defaulting to `DatabaseConfig.getDataSource()`) so tests can inject a test datasource. All SQL uses `PreparedStatement`. `DatabaseException` (unchecked) wraps all `SQLException`s. Schema is managed by **Flyway**; migration files live in `src/main/resources/db/migration/` using the `V{n}__{description}.sql` naming pattern.

Database connection config is in `src/main/resources/application.properties` — MySQL via HikariCP, database `church_management_system`, default credentials `root / asd@1234`.

### Security model

`AuthContext` holds a static `AuthenticatedUser` (set on login, cleared on logout). Permission checks go through `PermissionGuard.can(permissionCode)`:
- If the user's `forcePasswordChange` flag is set, all permissions are denied.
- Users with the `Admin` role bypass all permission checks.
- Otherwise, the user must have the specific permission code in their permission set.

Controllers obtain the current user from `AuthContext.getCurrentUser()` and instantiate a `PermissionGuard` to gate UI actions.

### Key singletons

- `SystemConfigurationCache.getInstance()` — in-memory map of `system_settings` rows; call `.reload()` after any settings change.
- `AutoBackupScheduler.getInstance()` — background timer that forks the JVM with `--auto-backup` args; cancelled in `MainApplication.stop()`.
- `DatabaseConfig.getDataSource()` — lazily initialised HikariCP pool; `closeDataSource()` called on shutdown.

### Receipt numbering

`ReceiptNumberGeneratorService` generates receipt numbers inside a DB transaction with `SELECT … FOR UPDATE` on the `receipt_sequences` table to prevent duplicate numbers under concurrent access.

### SMS integration

`SmsServiceFactory` returns either `SimDongleSmsService` (real AT-command communication over a serial COM port via jSerialComm) or `MockSmsService`, controlled by a system setting. SMS settings (COM port, baud rate, etc.) are stored in the `sms_settings` table.

### Reporting

`ReportService` queries the database and delegates to `ReportPdfExporter` (JasperReports) or `ReportExcelExporter` (Apache POI) based on the requested output format. Generated receipt PDFs are written to the folder configured in `receipt.pdf.output.folder` (default `./receipts`).

### Adding a new feature (typical pattern)

1. Add a Flyway migration `V{n+1}__description.sql` for any schema changes.
2. Add/update entity POJO in `entity/`.
3. Add repository methods with raw SQL in `repository/`.
4. Add service in `service/` injecting the repository.
5. Create/update `.fxml` in `src/main/resources/…/view/`.
6. Create/update controller in `controller/`; wire service in `initialize()`; check permissions via `PermissionGuard`.
7. Register the permission code in the DB migration if needed.
