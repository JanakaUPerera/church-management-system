# Multi-Machine SMS Sending — Server-Owned Queue

Date: 2026-09-03
Status: Approved (design), pending implementation plan

## Problem

The system runs on multiple machines sharing one MySQL database over JDBC,
but only **one** machine — the server machine that also hosts the database —
has the physical SIM modem attached. Today, `SimDongleSmsService` opens a
local serial COM port directly on whichever machine calls it
(`ReceiptSmsNotificationService`, `SmsResendService`), which only works when
that machine is the one with the modem. As soon as a receipt is submitted
from any other machine, the SMS send fails, because that machine has no COM
port to open.

Requirement: any machine can trigger an SMS (receipt submission, manual
"Send SMS," "Resend SMS"), but only the server machine may ever touch the
modem, sending messages one at a time in the order they were requested.

## Decision

`sms_logs` rows become the queue itself — no new table. Any machine
enqueues a row with `status = QUEUED` and returns immediately, without
touching the modem. A new `SmsQueueProcessor`, running only on the
designated server machine, polls the table and sends queued rows to the
modem one at a time, oldest first, writing the final result back onto the
same row.

This reuses two things already in the codebase rather than inventing new
mechanisms:

- **`PrimaryMachine.isPrimary()`** — already designates one machine (via
  the `db.run-migrations` local property) as the one that runs singleton
  duties (migrations, scheduled backups). The server machine — the one
  hosting the database and now the modem — is that same primary machine.
  No new "which machine sends SMS" configuration is needed.
- **`SmsSendStatus.QUEUED` / `SENDING`** — already declared in the enum,
  and `sms_logs.status` is already a free-text `VARCHAR(30)` (migrated off
  a rigid `ENUM` back in `V21`), so no status-column migration is needed
  either. These values simply go from unused to load-bearing.

### Approaches considered

- **DB-backed queue polled by a primary-machine-only processor (chosen).**
  Zero new infrastructure — the shared MySQL database is already the only
  cross-machine coordination point in this app. Durable: if the server
  app isn't running, or the modem is misconfigured, queued rows simply
  wait; nothing is lost. Matches the existing `PrimaryMachine` /
  `AutoBackupScheduler` precedent exactly.
- **Direct network call from client machines to a listener on the server
  machine (rejected).** Lower latency, but introduces a new network
  service (port, firewall, auth) where none exists today, and duplicates
  retry/durability semantics the DB-backed queue gets for free from the
  database the app already depends on.
- **External message broker — Redis/RabbitMQ (rejected).** Standard
  pattern for this problem in general, but disproportionate infrastructure
  for a manual-JDBC, no-Spring desktop app at this scale, and a new
  deployable/dependency for every install.

## Non-goals

- No new table. `sms_logs` serves as both the queue and the historical
  log, as it already does for send/delivery status today.
- No change to `SimDongleSmsService`, `MockSmsService`, `SmsServiceFactory`,
  or the `sms.retry.*` settings — the processor calls
  `SmsServiceFactory.createRoutingSmsService()` exactly as
  `ReceiptSmsNotificationService` and `SmsResendService` do today; retry
  behavior against the modem is unchanged, just relocated to run on the
  server machine instead of the submitting machine.
- No configurability added for poll interval or stale-`SENDING` threshold
  (see Design B) — both are constants, matching how
  `AutoBackupScheduler`'s daily-backup cadence is also a constant, not a
  system setting. Can be promoted to a setting later if needed.
- No change to how a machine decides MOCK vs SIM_DONGLE — that's still the
  shared `sms_settings` row, read by whichever machine's processor runs
  (which is always the primary machine now).

## Design

### A. Enqueue path — any machine

`ReceiptSmsNotificationService.sendReceiptSubmissionSms(receiptId)` and
`SmsResendService.resendSms(request)` stop calling `smsService.sendSms(...)`
synchronously. Instead, each:

1. Runs the same cheap pre-checks it runs today that don't need the modem
   — SMS enabled, church has a mobile number, message text builds — so a
   message that's doomed regardless of gateway (missing number, SMS
   disabled) never enters the queue. These checks and their
   `activityLogService.logSmsSkipped(...)` logging stay exactly as they
   are, still running on the submitting machine.
2. Inserts one `sms_logs` row via a new `SmsLogRepository.enqueue(...)`
   method: `status = QUEUED`, `delivery_status = UNKNOWN`,
   `created_at = now` (this becomes the FIFO order key — the enqueue
   time), `queued_by_user_id = ` the current `AuthContext` user, and no
   `sent_at` / `modem_*` / `sent`-outcome fields yet. Returns immediately.

`ReceiptSmsNotificationService.sendReceiptSubmissionSms` keeps its
`void` signature (callers already treat it as fire-and-forget). Its only
thrown exception path (`SmsNotificationException` for a missing receipt/
church) is unchanged — that's a data problem, not a modem problem, and
should still surface at submission time.

`SmsResendService.resendSms(request)` changes return type from
`SmsResult` to `void`: it no longer has a real send outcome to hand back
at call time (the actual send hasn't happened yet). Its only caller,
`SmsLogController.resendSms`, already discards the returned value today
(its `ProcessingDialog.run` success callback ignores `result` and shows a
fixed message), so this is a signature cleanup, not a behavior loss — see
Design D for the wording change to that fixed message.

Both `ReceiptSmsNotificationService` and `SmsResendService` drop their
`SmsService`/`SmsServiceFactory` dependency entirely — they no longer talk
to the modem at all, only to `SmsLogRepository`. `activityLogService.
logSmsSentAcceptedByModem` / `logSmsSendFailed` / `logSmsDeliveryStatusUnknown`
calls move out of these two services and into the processor (Design B),
since those describe a modem outcome that now only exists later, on the
server machine.

### B. `SmsQueueProcessor` — server machine only

New class, same shape as `AutoBackupScheduler`
(`src/main/java/com/churchmanagement/service/AutoBackupScheduler.java`):
a singleton (initialization-on-demand holder), backed by a single daemon
`ScheduledExecutorService` thread, no-oping entirely unless
`PrimaryMachine.isPrimary()` is true.

```java
public class SmsQueueProcessor {
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration STALE_SENDING_THRESHOLD = Duration.ofMinutes(2);

    public static SmsQueueProcessor getInstance() { ... }   // Holder pattern
    public synchronized void start() { ... }                // no-op if !PrimaryMachine.isPrimary()
    public synchronized void cancel() { ... }                // mirrors AutoBackupScheduler.cancel()
}
```

Each tick (`scheduleWithFixedDelay`, so the next tick never overlaps a
still-running one):

1. **Reclaim stale rows.** `UPDATE sms_logs SET status = 'QUEUED' WHERE
   status = 'SENDING' AND last_attempt_at < ?` (now minus 2 minutes — well
   past `SimDongleSmsService.SMS_SEND_TIMEOUT_MILLIS`'s 30s). Covers the
   server app being killed mid-send: on restart, that row is picked up
   again instead of being stuck forever.
2. **Drain the queue.** Loop: fetch the single oldest `QUEUED` row
   (`ORDER BY created_at ASC LIMIT 1`), mark it `SENDING` with
   `last_attempt_at = now` via a conditional
   `UPDATE ... WHERE id = ? AND status = 'QUEUED'` (guards against
   double-processing if this method is ever called concurrently — not
   expected today, since only one primary machine ever runs one processor
   thread, but cheap to make correct regardless), send it through
   `smsServiceFactory.createRoutingSmsService().sendSms(mobileNumber, message)`
   (unchanged — still loops its own configured retries), write the final
   outcome onto that row via a new `SmsLogRepository.updateSendResult(id, ...)`
   method (status, delivery status, modem reference/raw response, error
   code/message, attempt count, `sent_at`), then log activity attributed
   to `queued_by_user_id`. Repeat until no `QUEUED` rows remain, then the
   tick ends and the executor sleeps until the next poll.

Because the drain loop processes everything available before sleeping,
a backlog built up while the server was offline clears in one burst on
restart rather than trickling out one row per 5-second tick.

### C. Lifecycle wiring

- `SmsQueueProcessor.getInstance().start()` — called from the same place
  `AutoBackupScheduler.getInstance().reloadSchedule()` is triggered today
  (`DashboardController`, after login/dashboard load).
- `SmsQueueProcessor.getInstance().cancel()` — added alongside the
  existing `AutoBackupScheduler.getInstance().cancel()` in
  `MainApplication.stop()`.

On a non-primary (client) machine, `start()` is a no-op (matching
`AutoBackupScheduler.reloadSchedule()`'s existing pattern for secondary
machines), so no processor thread runs there at all.

### D. UI-visible wording changes

Both existing send actions already treat SMS as fire-and-forget (a
generic toast; real status lives on the SMS Logs screen), so the queueing
change is mostly a wording fix, not a UX redesign:

- `ReceiptHistoryController.sendSms`: `"SMS notification processed for
  receipt " + receiptNo + "."` → `"SMS queued for receipt " + receiptNo + "."`
- `SmsLogController.resendSms`: `"SMS resent for log " + id + "."` →
  `"SMS resend queued for log " + id + "."`
- `ReceiptService`'s `warningMessage = "Receipt saved, but SMS
  notification failed."` path is unchanged in shape — it still only
  triggers when enqueue itself throws (missing church/mobile number),
  which remains a synchronous, immediate failure.

The SMS Logs screen (`SmsLogController`, `sms-log-view.fxml`) needs no
structural change — `QUEUED`/`SENDING` are existing `SmsSendStatus`
values, so the status badge and filter dropdown already round-trip them
once rows are written; only cosmetic exclusions worth applying
(implementation-time decision, not a design gate): `SmsLogService.
applyCanResend` should probably not offer "Resend" on a row that's still
`QUEUED`/`SENDING` (nothing to resend yet), alongside the resend-window
and already-resent checks it already applies.

### E. Schema change

One migration, next available Flyway version number (`V39` as of this
branch — confirm at implementation time):

```sql
ALTER TABLE sms_logs
    ADD COLUMN queued_by_user_id BIGINT NULL AFTER resend_reason,
    ADD CONSTRAINT fk_sms_logs_queued_by
        FOREIGN KEY (queued_by_user_id) REFERENCES users (id);
```

Nullable because historical rows (and any future system-initiated send
with no acting user) have none.

### F. Testing

- `ReceiptSmsNotificationServiceTest`, `SmsResendServiceTest`: rewritten —
  these currently assert on a synchronous `SmsResult`/modem outcome; they
  now assert an `sms_logs` row was inserted with `status = QUEUED` and the
  correct `queued_by_user_id`, and that the pre-checks (SMS disabled, no
  mobile number) still skip enqueue exactly as they skip sending today.
- `SmsQueueProcessorTest` (new): non-primary machine → `start()` does
  nothing, no row is touched; primary machine → drains multiple `QUEUED`
  rows oldest-first in one tick; a `SENDING` row older than the stale
  threshold is reclaimed to `QUEUED` and reprocessed; a fresh `SENDING`
  row (within the threshold) is left alone.
- `SmsLogRepositoryTest`: new cases for `enqueue(...)` and
  `updateSendResult(...)`.
- `SimDongleSmsServiceTest`, `SmsServiceFactoryTest`, `MockSmsServiceTest`:
  unchanged — nothing about the modem-facing layer changes.
- `SmsLogServiceTest`: new case confirming `QUEUED`/`SENDING` rows are not
  offered for resend.
