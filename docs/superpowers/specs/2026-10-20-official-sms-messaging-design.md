# Official SMS Messaging — Design

**Requirement:** Phase 02 / 05b (Milestone M1)
**Date:** 2026-10-20
**Status:** Approved in brainstorming, pending spec review
**Related:** [PHASE-02-DEVELOPMENT-PLAN.md](../../PHASE-02-DEVELOPMENT-PLAN.md) §2 (05b, 05c), [2026-09-03-sms-queue-design.md](2026-09-03-sms-queue-design.md)

---

## 1. Goal

Head office can send an official SMS notice to a chosen set of branches from a dedicated **Send Message** screen. Messages go through the existing SMS queue (`sms_logs`, `SmsQueueProcessor`), so delivery status, the SMS Logs screen and resend keep working. Member recipients are added in M3.

## 2. Decisions

| # | Decision |
|---|----------|
| D1 | Messages are **English (GSM 7-bit alphabet) only**. Characters outside the GSM 7-bit alphabet (e.g. Sinhala, Tamil, emoji) are rejected with the offending character named. Unicode (UCS-2) is out of scope. |
| D2 | Messages may be **multi-part, up to 3 parts / 459 GSM-7 characters**, footer included. |
| D3 | Multi-part uses **PDU mode, only when the message is longer than 160 characters**. Messages of 160 characters or fewer — including every receipt SMS — keep the current text-mode path unchanged. |
| D4 | A configurable footer, setting `sms.official.footer` (default blank), is appended to every official message on its own line and counts toward the length limit. Answers Q15. |
| D5 | The screen is its **own sidebar item, "Send Message"**, directly under SMS Logs. Settings → Modem gets an "Open Send Message" link. The existing "Send Test SMS" box stays for modem diagnostics. |
| D6 | Permissions follow the repo's naming: `sms.official.menu.view` (see the screen) and `sms.official.send` (send). The plan's `SEND_OFFICIAL_SMS` maps to `sms.official.send`. Both are granted to Admin. |
| D7 | Recipients are **active branches** only, using `churches.sms_mobile_number`. Branches with a missing or invalid number cannot be selected and are reported as skipped. |
| D8 | A send to N branches inserts **N `QUEUED` rows in one transaction**, sharing one `batch_uuid`. Nothing is sent from the sending machine; the primary machine's `SmsQueueProcessor` sends them. |
| D9 | If one part of a multi-part message fails, the whole row is `FAILED`. A retry/resend sends **all** parts again, so a recipient may receive an earlier part twice. Accepted, and documented in the user manual. |
| D10 | When 05c (module navigation) ships, "Send Message" becomes the default item of the **Messaging** module; its registry entry uses `sms.official.menu.view`. No 05b code depends on 05c. |

## 3. Data model — migration `V41__official_sms_messaging.sql`

### 3.1 `sms_logs` (alter)

| Column | Type | Notes |
|--------|------|-------|
| `sms_type` | VARCHAR(30) NOT NULL DEFAULT 'RECEIPT' | Existing rows become `RECEIPT`. Official rows are `OFFICIAL_MESSAGE`. Free text, like `status` (V21), so M3 can add `MEMBER_MESSAGE` without a migration. |
| `batch_uuid` | CHAR(36) NULL | Shared by all rows from one Send click. NULL for receipt SMS. |

Index on `sms_type` (SMS Logs filter).

Resend copies `sms_type` and `batch_uuid` from the original row.

### 3.2 Permissions

Same pattern as V26/V40: insert `sms.official.menu.view` ("View Send Message menu") and `sms.official.send` ("Send official SMS messages") under module `SMS`, granted to the `Admin` role, both idempotent (`WHERE NOT EXISTS`).

### 3.3 System setting

`system_settings` row `sms.official.footer`, value `''`, inserted if missing (same pattern as V31).

## 4. Components

### 4.1 `SmsType` (enum)

`RECEIPT`, `OFFICIAL_MESSAGE`. `SmsLogDto` gets `smsType` and `batchUuid`. Unknown stored values map to `RECEIPT` when read.

### 4.2 `SriLankanMobileNumber` (util, new)

`normalize(String)` and `isValid(String)` move here from `SimDongleSmsService.normalizeSriLankanMobileNumber` / `isValidSriLankanMobileNumber` unchanged (`+947XXXXXXXX`). `SimDongleSmsService` calls the util. Shared by the modem and the recipient preview so both apply the same rule.

### 4.3 `SmsLengthCalculator` (util, new, pure)

- `isGsm7(String)` / `firstUnsupportedCharacter(String)`: GSM 03.38 basic alphabet plus extension table.
- `septetLength(String)`: basic characters count 1; extension characters (`^ { } \ [ ] ~ | €`, form feed) count 2.
- `partCount(String)`: 0 for empty; 1 if ≤ 160 septets; otherwise `ceil(septets / 153)`.
- Constants: `SINGLE_PART_LIMIT = 160`, `MULTI_PART_SEGMENT = 153`, `MAX_PARTS = 3`, `MAX_SEPTETS = 459`.
- A multi-part split never puts the two halves of an extension character in different parts.

Used by the screen's live counter and by `OfficialSmsService` validation.

### 4.4 `SmsPduEncoder` (util, new, pure)

`List<SmsPdu> encode(String normalizedNumber, String message, int concatReference)` returns one PDU per part, each with its hex TPDU and its TPDU length (octets, excluding the SMSC prefix) for `AT+CMGS=<length>`.

Per part, SMS-SUBMIT:
- SMSC: `00` (use the SIM's SMSC).
- First octet: TP-MTI = SUBMIT, TP-VPF = relative, TP-SRR = 1 (status report requested, matching today's `AT+CSMP=49,…`), TP-UDHI = 1 when multi-part.
- TP-MR `00`, destination address as international (`91`) with semi-octet digits, TP-PID `00`, TP-DCS `00` (GSM 7-bit), TP-VP `A7` (24 h, matching `AT+CSMP=49,167,…`).
- Multi-part UDH: `05 00 03 <ref> <total> <seq>` (8-bit concatenation reference); the user data is padded with 1 fill bit so the septets after the 6-octet header start on a septet boundary.
- TP-UDL in septets (including the header's 7 septets when multi-part).

`concatReference` is chosen by the caller (`SimDongleSmsService`) as a value from 0–255 that differs between consecutive messages (an in-memory counter on the primary machine is enough).

### 4.5 `SimDongleSmsService.sendSms` (change)

- Length check changes from "> 160 rejects" to "> 459 septets or non-GSM-7 rejects".
- **≤ 160 septets:** the existing text-mode sequence, byte for byte unchanged (`AT`, `AT+CMGF=1`, `AT+CSCS="GSM"`, delivery-report setup, `AT+CMGS="<number>"`, text, Ctrl+Z).
- **> 160 septets:** `AT`, `AT+CMGF=0`, delivery-report setup (`AT+CNMI` only; `AT+CSMP` is text-mode only and the PDU carries the same options), then for each part `AT+CMGS=<tpdu length>`, wait for `>`, write the PDU hex, Ctrl+Z, parse `+CMGS: <ref>`. On success, `modemMessageReference` holds the part references comma-joined (e.g. `45,46,47`) and the raw responses are joined. If any part fails, the result is `FAILED` with that part's error, and later parts are not sent. Finally `AT+CMGF=1` restores text mode (best effort) so the next receipt SMS starts from the usual state.

`MockSmsService` accepts any message up to 459 septets.

### 4.6 `OfficialSmsService` (new)

Constructor-injected (`SmsLogRepository`, `ChurchRepository`, `ActivityLogService`, `SystemConfigurationCache`, `Clock`) with a no-arg default constructor, like `SmsResendService`. Runs on any machine.

- `List<OfficialSmsRecipient> previewRecipients(Collection<Long> churchIds)`: one row per branch with code, name, region, normalized mobile number and a status of `OK`, `MISSING` or `INVALID`.
- `String composeMessage(String body)`: the stripped body, plus `"\n" + footer` when the footer is not blank.
- `OfficialSmsResult sendOfficialMessage(String body, Collection<Long> churchIds)`:
  1. Requires a signed-in user with `sms.official.send`; otherwise throws `OfficialSmsException`.
  2. Rejects a blank body, no recipients, a non-GSM-7 character (names it), or a composed message over 459 septets.
  3. Previews the recipients; if none are `OK`, throws "None of the selected branches has a valid mobile number."
  4. Inserts one row per `OK` recipient in one transaction (`SmsLogRepository.enqueueOfficialBatch`): `status = QUEUED`, `delivery_status = UNKNOWN`, `sms_type = OFFICIAL_MESSAGE`, `receipt_id = NULL`, `church_id`, normalized mobile number, composed message, shared `batch_uuid`, `queued_by_user_id`, `created_at = now`.
  5. Writes one activity-log entry, `OFFICIAL_SMS_QUEUED`, with the batch UUID, queued and skipped counts, part count and the first 100 characters of the message.
  6. Returns batch UUID, queued count, skipped recipients and part count.

### 4.7 `SmsQueueProcessor` (small change)

No change to how rows are picked up or sent. `logActivity` gets a branch for `OFFICIAL_MESSAGE` rows that logs `OFFICIAL_SMS_SENT` or `OFFICIAL_SMS_FAILED` (church, mobile number, batch UUID, error) instead of the receipt SMS entries.

### 4.8 `SmsResendService` (verify only)

Resend already copies `receipt_id` (NULL here) and `church_id`, and takes the church's current mobile number. It must also copy `sms_type` and `batch_uuid` (via `enqueueResend`). A test confirms that resending an official row works with `receipt_id = NULL`.

## 5. UI

### 5.1 Send Message screen (`send-message-view.fxml`, `SendMessageController`)

Sidebar item "Send Message" under SMS Logs, icon `fas-paper-plane`, permission `sms.official.menu.view`, activity code `NAVIGATE_SEND_MESSAGE`.

**Recipients (left):**
- Region filter combo (All regions / each region) and a search field (branch code or name).
- Table of active branches: checkbox, code, name, region, mobile, status. Rows with `MISSING`/`INVALID` show the status in the warning style and their checkbox is disabled.
- **Select all shown** (selects every selectable row the current filter shows — this is "select all in region") and **Clear**.
- Summary line: "42 selected · 2 shown without a valid number".
- Selection is kept when the filter changes.

**Message (right):**
- Message `TextArea`, then a read-only footer preview (hidden when the footer is blank).
- Live counter: `312 / 459 characters · 3 SMS parts`, red when over the limit; an unsupported character shows "Unsupported character: 'අ'".
- Total line: "126 SMS will be sent" (recipients × parts).

**Send:**
- Enabled only with `sms.official.send`, at least one selected recipient, a non-blank body and a valid length/alphabet.
- Confirmation: "Send this message to 42 branches (126 SMS)?"
- Runs via `ProcessingDialog`, then shows: "42 messages queued. 2 skipped (no valid number). Track delivery in SMS Logs."
- On success the message and selection are cleared. Errors from `OfficialSmsException` are shown as-is.

### 5.2 SMS Logs screen

- New **Type** column (Receipt / Official) and a **Type** filter (All / Receipt / Official) on `SmsLogSearchCriteria` (`smsType`).
- Detail view shows Type and Batch. Receipt No. shows "-" for official rows.

### 5.3 Settings → Modem

- "Official message footer" text field, saved with the existing settings save, with the hint "Added to every official message and counted in its length."
- "Open Send Message" link, shown only with `sms.official.menu.view`.

## 6. Tests (JUnit)

- **`SmsLengthCalculatorTest`:** 0, 160, 161, 306, 307, 459, 460 septets; extension characters count 2; an extension character is not split across parts; first unsupported character found.
- **`SmsPduEncoderTest`:** septet packing against known vectors (`hello` → `E8329BFD06`); destination address encoding (odd digit count, `F` padding); single-part PDU against a reference PDU; 3-part split with UDH bytes, fill bit, sequence numbers and TPDU lengths.
- **`OfficialSmsServiceTest`:** no permission; blank body; no recipients; unsupported character; too long (footer included); footer appended and counted; missing/invalid numbers skipped; none valid → error; one row per valid recipient with type, batch, `queued_by`, NULL receipt; activity entry written.
- **`SimDongleSmsServiceTest`:** ≤ 160 sends the exact current text-mode command sequence (receipt regression guard); > 160 sends `AT+CMGF=0` and one `AT+CMGS=<len>` per part; part references comma-joined; a part-2 failure gives `FAILED` and part 3 is not sent; text mode restored afterwards.
- **`SmsResendServiceTest`:** resend of an official row (`receipt_id` NULL) keeps type and batch.
- **`SmsQueueProcessorTest`:** an official row logs `OFFICIAL_SMS_SENT` / `OFFICIAL_SMS_FAILED`.
- **Manual (real dongle, primary machine):** a 1-part and a 3-part message to two phones; the 3-part message arrives joined; a receipt SMS still sends after a multi-part send.

## 7. Documentation

User manual EN + SI: a "Send Message" section (selecting branches, the counter and parts, the footer setting, tracking in SMS Logs, and the duplicate-part-on-retry note from D9).

## 8. Out of scope

- Unicode (Sinhala/Tamil) messages.
- Member recipients (M3).
- Scheduled or recurring messages, message templates, saved recipient groups.
- Parsing delivery reports per part (delivery reports are not matched to rows today either).
