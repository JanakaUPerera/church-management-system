# Official SMS Messaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Head office can send an official SMS (English, up to 3 parts) to selected branches from a new Send Message screen, through the existing `sms_logs` queue.

**Architecture:** Pure utilities (`SmsLengthCalculator`, `SmsPduEncoder`, `SriLankanMobileNumber`) hold the GSM-7 and PDU rules. `SimDongleSmsService` keeps its text-mode path for every message it handles today and switches to PDU mode only for GSM-7 messages over 160 characters or with non-ASCII GSM characters. `OfficialSmsService` validates and inserts one `QUEUED` `sms_logs` row per branch in one transaction; the unchanged `SmsQueueProcessor` sends them on the primary machine.

**Tech Stack:** Java 21, JavaFX 21 (FXML), manual JDBC (MySQL), Flyway, JUnit 5 (hand-written fakes, no Mockito), Maven.

**Spec:** [docs/superpowers/specs/2026-10-20-official-sms-messaging-design.md](../specs/2026-10-20-official-sms-messaging-design.md)

## Global Constraints

- Branch: `feat/official-sms-messaging` (created from `develop`). Do not commit `docs/PHASE-02-DEVELOPMENT-PLAN.md` — it holds the user's own uncommitted edits.
- No new Maven dependencies. Tests use hand-written fakes that subclass the real class with a `null` `DataSource` (existing pattern), never Mockito.
- Max message: **459 GSM-7 septets / 3 parts, footer included**. Single part ≤ 160 septets; multi-part segments are 153 septets.
- Messages ≤ 160 characters that are pure ASCII — every receipt SMS — must keep the exact current text-mode AT sequence.
- Permission codes: `sms.official.menu.view`, `sms.official.send` (module `SMS`). Setting key: `sms.official.footer`. SMS type values: `RECEIPT`, `OFFICIAL_MESSAGE`.
- Activity codes: `NAVIGATE_SEND_MESSAGE`, `OFFICIAL_SMS_QUEUED`, `OFFICIAL_SMS_SENT`, `OFFICIAL_SMS_FAILED`.
- Migration is `V41__official_sms_messaging.sql` (latest existing is V40).
- Commits: one per task, conventional-commits style with a detailed body, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Run single tests with `mvn -q test -Dtest=<ClassName>`; full suite with `mvn -q test`.

### Refinement over the spec (agreed during planning)

Spec §4.5 says "≤ 160 septets → text mode". The existing text-mode path writes the message as US-ASCII, so a GSM character outside ASCII (e.g. `£`, `é`, `€`) would arrive as `?`. The routing rule is therefore:

| Message | Path |
|---------|------|
| GSM-7, pure ASCII, ≤ 160 characters | text mode (unchanged) |
| GSM-7, and (> 160 characters **or** contains non-ASCII GSM characters), ≤ 459 septets | PDU mode |
| GSM-7, > 459 septets | rejected: "SMS message is too long. Maximum allowed length is 459 characters." |
| not GSM-7, ≤ 160 characters | text mode (unchanged — today's behaviour) |
| not GSM-7, > 160 characters | rejected with today's text-mode message |

---

## File Structure

**Create**
- `src/main/java/com/churchmanagement/util/SmsLengthCalculator.java` — GSM-7 alphabet, septet length, part split.
- `src/main/java/com/churchmanagement/util/SriLankanMobileNumber.java` — number normalize/validate (moved from `SimDongleSmsService`).
- `src/main/java/com/churchmanagement/util/SmsPduEncoder.java` — SMS-SUBMIT PDUs with concatenation header.
- `src/main/java/com/churchmanagement/enums/SmsType.java`
- `src/main/java/com/churchmanagement/dto/OfficialSmsRecipient.java`
- `src/main/java/com/churchmanagement/dto/OfficialSmsResult.java`
- `src/main/java/com/churchmanagement/service/OfficialSmsService.java`
- `src/main/java/com/churchmanagement/controller/SendMessageController.java`
- `src/main/resources/com/churchmanagement/view/send-message-view.fxml`
- `src/main/resources/db/migration/V41__official_sms_messaging.sql`
- Tests: `util/SmsLengthCalculatorTest`, `util/SriLankanMobileNumberTest`, `util/SmsPduEncoderTest`, `service/OfficialSmsServiceTest`

**Modify**
- `service/SimDongleSmsService.java` — routing + PDU send.
- `dto/SmsLogDto.java`, `dto/SmsLogSearchCriteria.java`, `repository/SmsLogRepository.java` — type, batch, filter, batch insert, resend copy.
- `service/SmsResendService.java` — pass type/batch.
- `service/SmsQueueProcessor.java` — official activity logging.
- `service/ActivityLogService.java` — new codes and log methods.
- `service/SystemSettingService.java` — footer validation.
- `controller/SettingsController.java`, `view/settings-view.fxml` — footer field, Open Send Message.
- `controller/DashboardController.java`, `view/dashboard-view.fxml` — sidebar item.
- `controller/SmsLogController.java`, `view/sms-log-view.fxml` — Type column, filter, details.
- `docs/user-manual.md`, `docs/user-manual-si.md`.
- Tests: `SimDongleSmsServiceTest`, `SmsLogRepositoryTest`, `SmsResendServiceTest`, `SmsQueueProcessorTest`, `SystemSettingServiceTest`.

---

### Task 1: SmsLengthCalculator

**Files:**
- Create: `src/main/java/com/churchmanagement/util/SmsLengthCalculator.java`
- Test: `src/test/java/com/churchmanagement/util/SmsLengthCalculatorTest.java`

**Interfaces:**
- Produces (all `public static`): `int SINGLE_PART_LIMIT = 160`, `int MULTI_PART_SEGMENT = 153`, `int MAX_PARTS = 3`, `int MAX_SEPTETS = 459`; `String firstUnsupportedCharacter(String text)` (null when all supported); `boolean isGsm7(String text)`; `int[] toSeptets(String text)` (throws `IllegalArgumentException` on unsupported); `int septetLength(String text)`; `int partCount(String text)`; `List<int[]> splitIntoParts(int[] septets)`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsLengthCalculatorTest {
    @Test
    void emptyTextHasNoParts() {
        assertEquals(0, SmsLengthCalculator.septetLength(""));
        assertEquals(0, SmsLengthCalculator.partCount(""));
    }

    @Test
    void partBoundaries() {
        assertEquals(1, SmsLengthCalculator.partCount("a".repeat(160)));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(161)));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(306)));
        assertEquals(3, SmsLengthCalculator.partCount("a".repeat(307)));
        assertEquals(3, SmsLengthCalculator.partCount("a".repeat(459)));
        assertEquals(4, SmsLengthCalculator.partCount("a".repeat(460)));
    }

    @Test
    void extensionCharactersCountTwo() {
        assertEquals(4, SmsLengthCalculator.septetLength("{}"));
        assertEquals(3, SmsLengthCalculator.septetLength("a€"));
        assertEquals(2, SmsLengthCalculator.partCount("a".repeat(159) + "€"));
    }

    @Test
    void extensionCharacterIsNotSplitAcrossParts() {
        // 152 + 2 (euro) + 10 = 164 septets: the 153-septet cut would fall between ESC and the euro code.
        int[] septets = SmsLengthCalculator.toSeptets("a".repeat(152) + "€" + "b".repeat(10));

        List<int[]> parts = SmsLengthCalculator.splitIntoParts(septets);

        assertEquals(2, parts.size());
        assertEquals(152, parts.get(0).length);
        assertEquals(12, parts.get(1).length);
        assertEquals(0x1B, parts.get(1)[0]);
        assertEquals(0x65, parts.get(1)[1]);
    }

    @Test
    void basicAlphabetCodes() {
        assertArrayEquals(new int[]{0x00, 0x02, 0x01, 0x41, 0x61, 0x0A}, SmsLengthCalculator.toSeptets("@$£Aa\n"));
    }

    @Test
    void findsFirstUnsupportedCharacter() {
        assertNull(SmsLengthCalculator.firstUnsupportedCharacter("Hello £é {test}"));
        assertEquals("අ", SmsLengthCalculator.firstUnsupportedCharacter("Hello අන"));
        assertEquals("`", SmsLengthCalculator.firstUnsupportedCharacter("a`b"));
        assertEquals("😀", SmsLengthCalculator.firstUnsupportedCharacter("Hi 😀"));
        assertTrue(SmsLengthCalculator.isGsm7("Meeting at 10:00 - Head Office"));
        assertFalse(SmsLengthCalculator.isGsm7("අ"));
    }

    @Test
    void toSeptetsRejectsUnsupportedCharacter() {
        assertThrows(IllegalArgumentException.class, () -> SmsLengthCalculator.toSeptets("අ"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SmsLengthCalculatorTest`
Expected: compilation FAILURE — `cannot find symbol: class SmsLengthCalculator`.

- [ ] **Step 3: Write the implementation**

```java
package com.churchmanagement.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GSM 03.38 7-bit default alphabet rules: which characters an SMS can carry, how many septets a
 * text takes (extension-table characters take two), and how it splits into concatenated parts.
 */
public final class SmsLengthCalculator {
    public static final int SINGLE_PART_LIMIT = 160;
    public static final int MULTI_PART_SEGMENT = 153;
    public static final int MAX_PARTS = 3;
    public static final int MAX_SEPTETS = MULTI_PART_SEGMENT * MAX_PARTS;
    static final int ESCAPE = 0x1B;

    // Index = septet code. Position 0x1B is the escape code and never matches a character.
    private static final String BASIC_ALPHABET =
            "@£$¥èéùìòÇ\nØø\rÅå"
                    + "Δ_ΦΓΛΩΠΨΣΘΞ￿ÆæßÉ"
                    + " !\"#¤%&'()*+,-./"
                    + "0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNO"
                    + "PQRSTUVWXYZÄÖÑÜ§"
                    + "¿abcdefghijklmno"
                    + "pqrstuvwxyzäöñüà";
    private static final String EXTENSION_CHARACTERS = "\f^{}\\[~]|€";
    private static final int[] EXTENSION_CODES = {0x0A, 0x14, 0x28, 0x29, 0x2F, 0x3C, 0x3D, 0x3E, 0x40, 0x65};

    private SmsLengthCalculator() {
    }

    public static String firstUnsupportedCharacter(String text) {
        if (text == null) {
            return null;
        }
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (codePoint > 0xFFFF || (basicCode((char) codePoint) < 0 && extensionCode((char) codePoint) < 0)) {
                return new String(Character.toChars(codePoint));
            }
            index += Character.charCount(codePoint);
        }
        return null;
    }

    public static boolean isGsm7(String text) {
        return firstUnsupportedCharacter(text) == null;
    }

    public static int[] toSeptets(String text) {
        String unsupported = firstUnsupportedCharacter(text);
        if (unsupported != null) {
            throw new IllegalArgumentException("Unsupported SMS character: " + unsupported);
        }
        String safeText = text == null ? "" : text;
        int[] septets = new int[safeText.length() * 2];
        int length = 0;
        for (char character : safeText.toCharArray()) {
            int basic = basicCode(character);
            if (basic >= 0) {
                septets[length++] = basic;
            } else {
                septets[length++] = ESCAPE;
                septets[length++] = extensionCode(character);
            }
        }
        return Arrays.copyOf(septets, length);
    }

    public static int septetLength(String text) {
        return toSeptets(text).length;
    }

    public static int partCount(String text) {
        return splitIntoParts(toSeptets(text)).size();
    }

    public static List<int[]> splitIntoParts(int[] septets) {
        List<int[]> parts = new ArrayList<>();
        if (septets.length == 0) {
            return parts;
        }
        if (septets.length <= SINGLE_PART_LIMIT) {
            parts.add(septets);
            return parts;
        }
        int start = 0;
        while (start < septets.length) {
            int end = Math.min(start + MULTI_PART_SEGMENT, septets.length);
            if (end < septets.length && septets[end - 1] == ESCAPE) {
                end--; // keep an extension character's escape and code in the same part
            }
            parts.add(Arrays.copyOfRange(septets, start, end));
            start = end;
        }
        return parts;
    }

    private static int basicCode(char character) {
        return character == '￿' ? -1 : BASIC_ALPHABET.indexOf(character);
    }

    private static int extensionCode(char character) {
        int index = EXTENSION_CHARACTERS.indexOf(character);
        return index < 0 ? -1 : EXTENSION_CODES[index];
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SmsLengthCalculatorTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/util/SmsLengthCalculator.java src/test/java/com/churchmanagement/util/SmsLengthCalculatorTest.java
git commit -m "feat(sms): GSM 7-bit length and part calculator" -m "Adds SmsLengthCalculator: the GSM 03.38 default alphabet and extension table, septet length (extension characters count two), and the split into concatenated parts (160 single, 153 per part) that never separates an escape from its code. Shared by the Send Message counter, official message validation and the PDU encoder." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: SriLankanMobileNumber

**Files:**
- Create: `src/main/java/com/churchmanagement/util/SriLankanMobileNumber.java`
- Modify: `src/main/java/com/churchmanagement/service/SimDongleSmsService.java` (methods `normalizeSriLankanMobileNumber`, `isValidSriLankanMobileNumber`, ~lines 133–152)
- Test: `src/test/java/com/churchmanagement/util/SriLankanMobileNumberTest.java`

**Interfaces:**
- Produces: `public static String normalize(String mobileNumber)` (null when blank), `public static boolean isValid(String normalizedNumber)` (`+947XXXXXXXX`).

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SriLankanMobileNumberTest {
    @Test
    void normalizesLocalAndInternationalForms() {
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("0771234567"));
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("94771234567"));
        assertEquals("+94771234567", SriLankanMobileNumber.normalize("+94 77-123-4567"));
        assertNull(SriLankanMobileNumber.normalize("  "));
        assertNull(SriLankanMobileNumber.normalize(null));
    }

    @Test
    void validatesMobileNumbersOnly() {
        assertTrue(SriLankanMobileNumber.isValid("+94771234567"));
        assertFalse(SriLankanMobileNumber.isValid("+94111234567"));
        assertFalse(SriLankanMobileNumber.isValid("0771234567"));
        assertFalse(SriLankanMobileNumber.isValid(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SriLankanMobileNumberTest`
Expected: compilation FAILURE — `cannot find symbol: class SriLankanMobileNumber`.

- [ ] **Step 3: Write the implementation**

```java
package com.churchmanagement.util;

/** Sri Lankan mobile number rules shared by the modem and the official message recipient preview. */
public final class SriLankanMobileNumber {
    private SriLankanMobileNumber() {
    }

    public static String normalize(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return null;
        }
        String number = mobileNumber.strip().replace(" ", "").replace("-", "");
        if (number.startsWith("+94") && number.length() == 12) {
            return number;
        }
        if (number.startsWith("94") && number.length() == 11) {
            return "+" + number;
        }
        if (number.startsWith("0") && number.length() == 10) {
            return "+94" + number.substring(1);
        }
        return number;
    }

    public static boolean isValid(String normalizedNumber) {
        return normalizedNumber != null && normalizedNumber.matches("\\+947\\d{8}");
    }
}
```

In `SimDongleSmsService`, replace the bodies of the two existing package-private methods so they delegate (keep the methods — `SimDongleSmsServiceTest.normalizeLocalMobileNumber` calls one):

```java
    String normalizeSriLankanMobileNumber(String mobileNumber) {
        return SriLankanMobileNumber.normalize(mobileNumber);
    }

    boolean isValidSriLankanMobileNumber(String mobileNumber) {
        return SriLankanMobileNumber.isValid(mobileNumber);
    }
```

Add `import com.churchmanagement.util.SriLankanMobileNumber;`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=SriLankanMobileNumberTest,SimDongleSmsServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/util/SriLankanMobileNumber.java src/test/java/com/churchmanagement/util/SriLankanMobileNumberTest.java src/main/java/com/churchmanagement/service/SimDongleSmsService.java
git commit -m "refactor(sms): move Sri Lankan mobile number rules to a shared util" -m "SriLankanMobileNumber holds the normalize and +947XXXXXXXX validation rules that were private to SimDongleSmsService, unchanged, so the Send Message recipient preview flags exactly the numbers the modem would reject. SimDongleSmsService delegates to it." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: SmsPduEncoder

**Files:**
- Create: `src/main/java/com/churchmanagement/util/SmsPduEncoder.java`
- Test: `src/test/java/com/churchmanagement/util/SmsPduEncoderTest.java`

**Interfaces:**
- Consumes: `SmsLengthCalculator.toSeptets`, `splitIntoParts`, `MAX_PARTS` (Task 1).
- Produces: `public record SmsPdu(String hex, int tpduLength)` (nested in `SmsPduEncoder`); `public static List<SmsPdu> encode(String normalizedNumber, String message, int concatReference)` — throws `IllegalArgumentException` for a bad number, unsupported character or more than 3 parts. `hex` includes the leading `00` SMSC octet; `tpduLength` excludes it (value for `AT+CMGS=`).

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsPduEncoderTest {
    private static final String ADDRESS = "0B914977214365F7"; // +94771234567

    @Test
    void packsSeptetsLikeTheReferenceVector() {
        byte[] packed = SmsPduEncoder.packSeptets(SmsLengthCalculator.toSeptets("hello"), 0);

        assertEquals("E8329BFD06", HexFormat.of().withUpperCase().formatHex(packed));
    }

    @Test
    void packsWithFillBit() {
        byte[] packed = SmsPduEncoder.packSeptets(new int[]{0x68, 0x65}, 1);

        assertEquals("D065", HexFormat.of().withUpperCase().formatHex(packed));
    }

    @Test
    void encodesSinglePartMessage() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "hello", 0);

        assertEquals(1, pdus.size());
        assertEquals("0031" + "00" + ADDRESS + "0000A7" + "05" + "E8329BFD06", pdus.get(0).hex());
        assertEquals(19, pdus.get(0).tpduLength());
    }

    @Test
    void encodesThreePartMessageWithConcatenationHeader() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "a".repeat(459), 42);

        assertEquals(3, pdus.size());
        for (int part = 1; part <= 3; part++) {
            SmsPduEncoder.SmsPdu pdu = pdus.get(part - 1);
            String expectedPrefix = "0071" + "00" + ADDRESS + "0000A7" + "A0" + "050003" + "2A" + "03" + "0" + part;
            assertTrue(pdu.hex().startsWith(expectedPrefix), "part " + part + ": " + pdu.hex());
            assertEquals(154, pdu.tpduLength());
            assertEquals((pdu.tpduLength() + 1) * 2, pdu.hex().length());
        }
    }

    @Test
    void lastPartLengthMatchesItsSeptets() {
        List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode("+94771234567", "a".repeat(161), 0);

        assertEquals(2, pdus.size());
        assertEquals(154, pdus.get(0).tpduLength());
        assertEquals(28, pdus.get(1).tpduLength()); // 14 header octets + 6 UDH + 8 packed (1 fill bit + 8 septets)
    }

    @Test
    void encodesEvenDigitAddressWithoutPadding() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        SmsPduEncoder.writeAddress(out, "1234");

        assertEquals("04912143", HexFormat.of().withUpperCase().formatHex(out.toByteArray()));
    }

    @Test
    void rejectsMoreThanThreeParts() {
        assertThrows(IllegalArgumentException.class,
                () -> SmsPduEncoder.encode("+94771234567", "a".repeat(460), 0));
    }

    @Test
    void rejectsUnsupportedCharacterAndBadNumber() {
        assertThrows(IllegalArgumentException.class, () -> SmsPduEncoder.encode("+94771234567", "අ", 0));
        assertThrows(IllegalArgumentException.class, () -> SmsPduEncoder.encode("0771234567", "hello", 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SmsPduEncoderTest`
Expected: compilation FAILURE — `cannot find symbol: class SmsPduEncoder`.

- [ ] **Step 3: Write the implementation**

```java
package com.churchmanagement.util;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds SMS-SUBMIT PDUs (GSM 7-bit) for modem PDU mode. Multi-part messages carry an 8-bit
 * concatenation header so the phone joins the parts into one message.
 */
public final class SmsPduEncoder {
    private static final int FIRST_OCTET = 0x31; // SMS-SUBMIT, relative validity, status report requested
    private static final int USER_DATA_HEADER_INDICATOR = 0x40;
    private static final int INTERNATIONAL_NUMBER = 0x91;
    private static final int VALIDITY_24_HOURS = 0xA7; // same as AT+CSMP=49,167,...
    private static final int HEADER_SEPTETS = 7; // 6-octet header + 1 fill bit

    public record SmsPdu(String hex, int tpduLength) {
    }

    private SmsPduEncoder() {
    }

    public static List<SmsPdu> encode(String normalizedNumber, String message, int concatReference) {
        if (normalizedNumber == null || !normalizedNumber.matches("\\+\\d{1,15}")) {
            throw new IllegalArgumentException("Invalid mobile number.");
        }
        List<int[]> parts = SmsLengthCalculator.splitIntoParts(SmsLengthCalculator.toSeptets(message));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("SMS message is required.");
        }
        if (parts.size() > SmsLengthCalculator.MAX_PARTS) {
            throw new IllegalArgumentException("SMS message is longer than " + SmsLengthCalculator.MAX_PARTS + " parts.");
        }
        String digits = normalizedNumber.substring(1);
        boolean multiPart = parts.size() > 1;
        List<SmsPdu> pdus = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            pdus.add(encodePart(digits, parts.get(index), multiPart, concatReference & 0xFF, parts.size(), index + 1));
        }
        return pdus;
    }

    private static SmsPdu encodePart(String digits, int[] septets, boolean multiPart, int reference, int total,
                                     int sequence) {
        ByteArrayOutputStream tpdu = new ByteArrayOutputStream();
        tpdu.write(multiPart ? FIRST_OCTET | USER_DATA_HEADER_INDICATOR : FIRST_OCTET);
        tpdu.write(0x00); // message reference, assigned by the modem
        writeAddress(tpdu, digits);
        tpdu.write(0x00); // protocol identifier
        tpdu.write(0x00); // data coding scheme: GSM 7-bit
        tpdu.write(VALIDITY_24_HOURS);
        if (multiPart) {
            tpdu.write(HEADER_SEPTETS + septets.length);
            tpdu.writeBytes(new byte[]{0x05, 0x00, 0x03, (byte) reference, (byte) total, (byte) sequence});
            tpdu.writeBytes(packSeptets(septets, 1));
        } else {
            tpdu.write(septets.length);
            tpdu.writeBytes(packSeptets(septets, 0));
        }
        byte[] bytes = tpdu.toByteArray();
        return new SmsPdu("00" + HexFormat.of().withUpperCase().formatHex(bytes), bytes.length);
    }

    static void writeAddress(ByteArrayOutputStream out, String digits) {
        out.write(digits.length());
        out.write(INTERNATIONAL_NUMBER);
        String padded = digits.length() % 2 == 0 ? digits : digits + "F";
        for (int index = 0; index < padded.length(); index += 2) {
            int low = Character.digit(padded.charAt(index), 16);
            int high = Character.digit(padded.charAt(index + 1), 16);
            out.write((high << 4) | low);
        }
    }

    static byte[] packSeptets(int[] septets, int fillBits) {
        int totalBits = fillBits + septets.length * 7;
        byte[] packed = new byte[(totalBits + 7) / 8];
        int bitPosition = fillBits;
        for (int septet : septets) {
            for (int bit = 0; bit < 7; bit++) {
                if (((septet >> bit) & 1) != 0) {
                    packed[bitPosition / 8] |= (byte) (1 << (bitPosition % 8));
                }
                bitPosition++;
            }
        }
        return packed;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SmsPduEncoderTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/util/SmsPduEncoder.java src/test/java/com/churchmanagement/util/SmsPduEncoderTest.java
git commit -m "feat(sms): SMS-SUBMIT PDU encoder with concatenation header" -m "SmsPduEncoder builds GSM 7-bit SMS-SUBMIT PDUs for modem PDU mode: international destination address, 24-hour validity and status-report request (matching the current AT+CSMP=49,167 text-mode options), and for multi-part messages an 8-bit concatenation UDH with one fill bit. Returns each part's hex and its TPDU length for AT+CMGS. Checked against the standard 'hello' -> E8329BFD06 vector." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: PDU sending in SimDongleSmsService

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/SimDongleSmsService.java`
- Test: `src/test/java/com/churchmanagement/service/SimDongleSmsServiceTest.java`

**Interfaces:**
- Consumes: `SmsLengthCalculator.isGsm7`, `septetLength`, `MAX_SEPTETS` (Task 1); `SmsPduEncoder.encode`, `SmsPdu` (Task 3).
- Produces: package-private `static boolean requiresPduMode(String message)`. `sendSms` contract unchanged; for multi-part success `getModemMessageReference()` is comma-joined part references.

- [ ] **Step 1: Write the failing tests**

In `SimDongleSmsServiceTest`, **delete** the test `rejectMessageOver160Characters` and add:

```java
    @Test
    void rejectGsmMessageOver459Characters() {
        SmsResult result = service(settings(true, "COM3", 9600), new FakeSerialPortService()).sendSms("0771234567",
                "x".repeat(460));

        assertFalse(result.isSuccess());
        assertEquals("SMS message is too long. Maximum allowed length is 459 characters.", result.getMessage());
    }

    @Test
    void rejectNonGsmMessageOver160Characters() {
        SmsResult result = service(settings(true, "COM3", 9600), new FakeSerialPortService()).sendSms("0771234567",
                "අ".repeat(161));

        assertFalse(result.isSuccess());
        assertEquals("SMS message is too long. Maximum allowed length is 160 characters for text mode.",
                result.getMessage());
    }

    @Test
    void routesOnlyLongOrNonAsciiGsmMessagesToPduMode() {
        assertFalse(SimDongleSmsService.requiresPduMode("Hello"));
        assertFalse(SimDongleSmsService.requiresPduMode("a".repeat(160)));
        assertTrue(SimDongleSmsService.requiresPduMode("a".repeat(161)));
        assertTrue(SimDongleSmsService.requiresPduMode("Price £5"));
        assertFalse(SimDongleSmsService.requiresPduMode("අ"));
        assertFalse(SimDongleSmsService.requiresPduMode("a`b"));
    }

    @Test
    void sends160CharacterAsciiMessageInTextModeUnchanged() {
        FakeSerialPortService serialPortService = new FakeSerialPortService(
                "\r\nOK\r\n", "\r\nOK\r\n", "\r\nOK\r\n", "\r\nOK\r\n", "\r\nOK\r\n", "\r\n> ",
                "\r\n+CMGS: 23\r\nOK\r\n");
        String message = "a".repeat(160);

        SmsResult result = service(settings(true, "COM3", 9600), serialPortService).sendSms("0771234567", message);

        assertTrue(result.isSuccess());
        assertEquals("AT\rAT+CMGF=1\rAT+CSCS=\"GSM\"\rAT+CSMP=49,167,0,0\rAT+CNMI=2,1,0,1,0\rAT+CMGS=\"+94771234567\"\r"
                + message + "\u001A", serialPortService.connection.outputText());
    }

    @Test
    void sendsLongMessageAsPduParts() {
        FakeSerialPortService serialPortService = new FakeSerialPortService(
                "\r\nOK\r\n",                 // AT
                "\r\nOK\r\n",                 // AT+CMGF=0
                "\r\nOK\r\n",                 // AT+CNMI
                "\r\n> ",                     // AT+CMGS=154
                "\r\n+CMGS: 45\r\nOK\r\n",    // part 1
                "\r\n> ",                     // AT+CMGS=28
                "\r\n+CMGS: 46\r\nOK\r\n",    // part 2
                "\r\nOK\r\n");                // AT+CMGF=1

        SmsResult result = service(settings(true, "COM3", 9600), serialPortService).sendSms("0771234567",
                "a".repeat(161));

        assertTrue(result.isSuccess());
        assertEquals("45,46", result.getModemMessageReference());
        assertEquals(SmsDeliveryStatus.UNKNOWN, result.getDeliveryStatus());
        String output = serialPortService.connection.outputText();
        assertTrue(output.startsWith("AT\rAT+CMGF=0\rAT+CNMI=2,1,0,1,0\rAT+CMGS=154\r00"), output);
        assertTrue(output.contains("\u001AAT+CMGS=28\r00"), output);
        assertTrue(output.contains("050003000201"), output);
        assertTrue(output.contains("050003000202"), output);
        assertTrue(output.endsWith("\u001AAT+CMGF=1\r"), output);
        assertTrue(serialPortService.connection.closed);
    }

    @Test
    void failedPartStopsLaterPartsAndRestoresTextMode() {
        FakeSerialPortService serialPortService = new FakeSerialPortService(
                "\r\nOK\r\n", "\r\nOK\r\n", "\r\nOK\r\n",
                "\r\n> ", "\r\n+CMGS: 45\r\nOK\r\n",
                "\r\n> ", "\r\n+CMS ERROR: 500\r\n",
                "\r\nOK\r\n");

        SmsResult result = service(settings(true, "COM3", 9600), serialPortService).sendSms("0771234567",
                "a".repeat(307));

        assertFalse(result.isSuccess());
        String output = serialPortService.connection.outputText();
        assertEquals(2, output.split("AT\\+CMGS=", -1).length - 1, output);
        assertTrue(output.endsWith("\u001AAT+CMGF=1\r"), output);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=SimDongleSmsServiceTest`
Expected: compilation FAILURE — `cannot find symbol: method requiresPduMode`.

- [ ] **Step 3: Write the implementation**

In `SimDongleSmsService`:

1. Add imports: `com.churchmanagement.util.SmsLengthCalculator`, `com.churchmanagement.util.SmsPduEncoder`, `java.util.ArrayList`, `java.util.List`, `java.util.concurrent.atomic.AtomicInteger`.
2. Add a field: `private final AtomicInteger concatReference = new AtomicInteger();`
3. Replace the length check in `sendSms`:

```java
        boolean pduMode = requiresPduMode(message);
        if (!pduMode && message.length() > MAX_TEXT_MODE_LENGTH) {
            return failed("SMS message is too long. Maximum allowed length is 160 characters for text mode.");
        }
        if (pduMode && SmsLengthCalculator.septetLength(message) > SmsLengthCalculator.MAX_SEPTETS) {
            return failed("SMS message is too long. Maximum allowed length is "
                    + SmsLengthCalculator.MAX_SEPTETS + " characters.");
        }
```

4. Inside the `try (SerialPortService.SerialConnection connection = ...)` block, keep the `connection == null` check and the `AT` check, then replace everything after the `AT` check up to the end of the `try` block with:

```java
            return pduMode
                    ? sendPduMode(connection, normalizedNumber, message)
                    : sendTextMode(connection, normalizedNumber, message);
```

5. Move the removed text-mode lines, **unchanged**, into a new method:

```java
    private SmsResult sendTextMode(SerialPortService.SerialConnection connection, String normalizedNumber,
                                   String message) throws IOException {
        String textModeResponse = sendCommand(connection, "AT+CMGF=1", COMMAND_TIMEOUT_MILLIS);
        if (!containsOk(textModeResponse)) {
            return modemFailure("Unable to set SMS text mode.", textModeResponse);
        }
        String characterSetResponse = sendCommand(connection, "AT+CSCS=\"GSM\"", COMMAND_TIMEOUT_MILLIS);
        if (!containsOk(characterSetResponse)) {
            return modemFailure("Unable to set SMS text mode.", characterSetResponse);
        }
        boolean deliveryReportsEnabled = enableDeliveryReports(connection);
        String recipientResponse = sendCommand(connection, "AT+CMGS=\"" + normalizedNumber + "\"",
                COMMAND_TIMEOUT_MILLIS, ">");
        if (!recipientResponse.contains(">")) {
            return modemFailure("Modem did not accept recipient number.", recipientResponse);
        }

        String sendResponse = sendMessage(connection, message);
        if (sendResponse == null || sendResponse.isBlank()) {
            return failed("SMS sending timed out.");
        }
        SmsParsedResponse parsedResponse = responseParser.parseSendResponse(sendResponse);
        if (parsedResponse.isSuccess()) {
            SmsDeliveryStatus deliveryStatus = deliveryReportsEnabled
                    ? SmsDeliveryStatus.UNKNOWN
                    : SmsDeliveryStatus.NOT_SUPPORTED;
            String messageText = "SMS accepted by modem. Final delivery report is not confirmed.";
            return new SmsResult(true, messageText, PROVIDER, LocalDateTime.now(clock),
                    SmsSendStatus.SENT, deliveryStatus, parsedResponse.getModemMessageReference(),
                    parsedResponse.getModemRawResponse(), null, null);
        }
        return new SmsResult(false, parsedResponse.getErrorMessage(), PROVIDER, null,
                SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED, null,
                parsedResponse.getModemRawResponse(), parsedResponse.getErrorCode(),
                parsedResponse.getErrorMessage());
    }
```

6. Add the PDU path and routing:

```java
    /**
     * Text mode keeps every message it already sends correctly (pure ASCII up to 160 characters,
     * which covers every receipt SMS). PDU mode takes GSM 7-bit messages that are longer than one
     * SMS or use GSM characters outside ASCII, which text mode would send as '?'.
     */
    static boolean requiresPduMode(String message) {
        return SmsLengthCalculator.isGsm7(message)
                && (message.length() > MAX_TEXT_MODE_LENGTH || !isAscii(message));
    }

    private static boolean isAscii(String message) {
        return message.chars().allMatch(character -> character < 0x80);
    }

    private SmsResult sendPduMode(SerialPortService.SerialConnection connection, String normalizedNumber,
                                  String message) throws IOException {
        String pduModeResponse = sendCommand(connection, "AT+CMGF=0", COMMAND_TIMEOUT_MILLIS);
        if (!containsOk(pduModeResponse)) {
            return modemFailure("Unable to set SMS PDU mode.", pduModeResponse);
        }
        try {
            // AT+CSMP is text-mode only; the PDU itself carries validity and the status-report request.
            boolean deliveryReportsEnabled = containsOk(
                    sendCommand(connection, "AT+CNMI=2,1,0,1,0", COMMAND_TIMEOUT_MILLIS));
            List<SmsPduEncoder.SmsPdu> pdus = SmsPduEncoder.encode(normalizedNumber, message,
                    concatReference.getAndIncrement());
            List<String> references = new ArrayList<>();
            List<String> rawResponses = new ArrayList<>();
            for (int index = 0; index < pdus.size(); index++) {
                SmsPduEncoder.SmsPdu pdu = pdus.get(index);
                String promptResponse = sendCommand(connection, "AT+CMGS=" + pdu.tpduLength(),
                        COMMAND_TIMEOUT_MILLIS, ">");
                if (!promptResponse.contains(">")) {
                    return modemFailure("Modem did not accept SMS part " + (index + 1) + " of " + pdus.size() + ".",
                            promptResponse);
                }
                String sendResponse = sendMessage(connection, pdu.hex());
                if (sendResponse == null || sendResponse.isBlank()) {
                    return failed("SMS sending timed out.");
                }
                SmsParsedResponse parsedResponse = responseParser.parseSendResponse(sendResponse);
                if (!parsedResponse.isSuccess()) {
                    return new SmsResult(false, parsedResponse.getErrorMessage(), PROVIDER, null,
                            SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED, null,
                            parsedResponse.getModemRawResponse(), parsedResponse.getErrorCode(),
                            parsedResponse.getErrorMessage());
                }
                references.add(parsedResponse.getModemMessageReference());
                rawResponses.add(parsedResponse.getModemRawResponse());
            }
            SmsDeliveryStatus deliveryStatus = deliveryReportsEnabled
                    ? SmsDeliveryStatus.UNKNOWN
                    : SmsDeliveryStatus.NOT_SUPPORTED;
            return new SmsResult(true, "SMS accepted by modem. Final delivery report is not confirmed.", PROVIDER,
                    LocalDateTime.now(clock), SmsSendStatus.SENT, deliveryStatus, String.join(",", references),
                    String.join("\n", rawResponses), null, null);
        } finally {
            restoreTextMode(connection);
        }
    }

    private void restoreTextMode(SerialPortService.SerialConnection connection) {
        try {
            sendCommand(connection, "AT+CMGF=1", COMMAND_TIMEOUT_MILLIS);
        } catch (IOException exception) {
            System.err.println("Unable to restore SMS text mode: " + exception.getMessage());
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=SimDongleSmsServiceTest,SmsServiceFactoryTest,SmsQueueProcessorTest`
Expected: PASS. `sendsTextModeSmsWithCmgs` must still pass unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/SimDongleSmsService.java src/test/java/com/churchmanagement/service/SimDongleSmsServiceTest.java
git commit -m "feat(sms): send long GSM messages as multi-part PDUs" -m "SimDongleSmsService switches the modem to PDU mode (AT+CMGF=0) only for GSM 7-bit messages over 160 characters, or with GSM characters outside ASCII that text mode would send as '?', and sends each part with AT+CMGS=<tpdu length>. Part references are stored comma-joined; a failed part stops the remaining parts and fails the message. Text mode is restored afterwards. Every pure-ASCII message of 160 characters or fewer, including all receipt SMS, keeps the exact text-mode command sequence. The new limit is 459 characters (3 parts)." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Migration, SmsType and repository support

**Files:**
- Create: `src/main/resources/db/migration/V41__official_sms_messaging.sql`
- Create: `src/main/java/com/churchmanagement/enums/SmsType.java`
- Create: `src/main/java/com/churchmanagement/dto/OfficialSmsRecipient.java`
- Modify: `src/main/java/com/churchmanagement/dto/SmsLogDto.java`
- Modify: `src/main/java/com/churchmanagement/dto/SmsLogSearchCriteria.java`
- Modify: `src/main/java/com/churchmanagement/repository/SmsLogRepository.java` (`enqueueResend`, `searchSmsLogs`, `baseSelect`, `mapLogs`, new `enqueueOfficialBatch`)
- Modify: `src/test/java/com/churchmanagement/service/SmsResendServiceTest.java` (fake signature only)
- Test: `src/test/java/com/churchmanagement/repository/SmsLogRepositoryTest.java`

**Interfaces:**
- Produces:
  - `enum SmsType { RECEIPT("Receipt"), OFFICIAL_MESSAGE("Official") }` with `String label()` and `static SmsType fromDatabase(String value)` (unknown/null → `RECEIPT`).
  - `record OfficialSmsRecipient(long churchId, String churchCode, String churchName, String regionName, String mobileNumber, OfficialSmsRecipient.Status status)` with nested `enum Status { OK, MISSING, INVALID }` and `boolean isSendable()`.
  - `SmsLogDto`: `SmsType getSmsType()/setSmsType(SmsType)` (default `RECEIPT`), `String getBatchUuid()/setBatchUuid(String)`.
  - `SmsLogSearchCriteria`: `SmsType getSmsType()/setSmsType(SmsType)`.
  - `SmsLogRepository.enqueueOfficialBatch(String batchUuid, String message, List<OfficialSmsRecipient> recipients, long queuedByUserId, LocalDateTime queuedAt)`.
  - `SmsLogRepository.enqueueResend(Long receiptId, Long churchId, String mobileNumber, String message, long originalSmsLogId, long resentByUserId, String resendReason, int priorAttemptCount, LocalDateTime queuedAt, SmsType smsType, String batchUuid)` — two new trailing parameters.

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V41__official_sms_messaging.sql`:

```sql
ALTER TABLE sms_logs
    ADD COLUMN sms_type VARCHAR(30) NOT NULL DEFAULT 'RECEIPT' AFTER sms_log_uuid,
    ADD COLUMN batch_uuid CHAR(36) NULL AFTER sms_type,
    ADD INDEX idx_sms_logs_sms_type (sms_type),
    ADD INDEX idx_sms_logs_batch_uuid (batch_uuid);

INSERT INTO permissions (name, module, description)
SELECT permission_code, module_name, permission_description
FROM (
    SELECT 'sms.official.menu.view' permission_code, 'SMS' module_name, 'View Send Message menu' permission_description
    UNION ALL SELECT 'sms.official.send', 'SMS', 'Send official SMS messages to branches'
) new_permissions
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = new_permissions.permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('sms.official.menu.view', 'sms.official.send')
WHERE r.name = 'Admin'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO system_settings
    (setting_key, setting_value, setting_type, category, description, editable, created_at)
SELECT 'sms.official.footer', '', 'STRING', 'SMS',
       'Footer added on its own line to every official SMS message (counts toward the length)',
       TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings WHERE setting_key = 'sms.official.footer'
);
```

- [ ] **Step 2: Write the failing repository tests**

Add to `SmsLogRepositoryTest` (it already has a private inner `RecordingDataSource` with `sql` and `valueAt(int)`; the shared one in `testsupport` is referenced by its full name to avoid the clash). Add imports `com.churchmanagement.dto.OfficialSmsRecipient`, `com.churchmanagement.enums.SmsType`, `java.util.List`:

```java
    @Test
    void searchBySmsType() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setSmsType(SmsType.OFFICIAL_MESSAGE);

        repository.searchSmsLogs(criteria);

        assertTrue(dataSource.sql.contains("AND sl.sms_type = ?"));
        assertEquals(SmsType.OFFICIAL_MESSAGE.name(), dataSource.valueAt(1));
    }

    @Test
    void enqueueResendCopiesTypeAndBatch() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.enqueueResend(null, 10L, "+94771234567", "Notice", 100L, 7L, "Phone was off", 1,
                LocalDateTime.of(2026, 10, 20, 9, 0), SmsType.OFFICIAL_MESSAGE, "batch-1");

        assertTrue(dataSource.sql.contains("sms_type"));
        assertEquals(SmsType.OFFICIAL_MESSAGE.name(), dataSource.valueAt(14));
        assertEquals("batch-1", dataSource.valueAt(15));
    }

    @Test
    void enqueueOfficialBatchInsertsOneQueuedRowPerRecipientInOneTransaction() {
        com.churchmanagement.testsupport.RecordingDataSource dataSource =
                new com.churchmanagement.testsupport.RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);
        List<OfficialSmsRecipient> recipients = List.of(
                new OfficialSmsRecipient(1L, "0001", "Katunayake", "R001", "+94771234567",
                        OfficialSmsRecipient.Status.OK),
                new OfficialSmsRecipient(2L, "0002", "Seeduwa", "R001", "+94771234568",
                        OfficialSmsRecipient.Status.OK));

        repository.enqueueOfficialBatch("batch-1", "Notice", recipients, 7L, LocalDateTime.of(2026, 10, 20, 9, 0));

        assertEquals(1, dataSource.sqls().size());
        assertTrue(dataSource.lastSql().contains("sms_type"));
        assertTrue(dataSource.committed());
        List<Object> values = dataSource.lastValues();
        assertEquals(SmsType.OFFICIAL_MESSAGE.name(), values.get(1));
        assertEquals("batch-1", values.get(2));
        assertEquals(2L, values.get(3));
        assertEquals("+94771234568", values.get(4));
        assertEquals("Notice", values.get(5));
        assertEquals(SmsSendStatus.QUEUED.name(), values.get(6));
        assertEquals(7L, values.get(9));
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=SmsLogRepositoryTest`
Expected: compilation FAILURE — `cannot find symbol: class SmsType`.

- [ ] **Step 4: Write the implementation**

`src/main/java/com/churchmanagement/enums/SmsType.java`:

```java
package com.churchmanagement.enums;

public enum SmsType {
    RECEIPT("Receipt"),
    OFFICIAL_MESSAGE("Official");

    private final String label;

    SmsType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SmsType fromDatabase(String value) {
        if (value == null) {
            return RECEIPT;
        }
        for (SmsType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return RECEIPT;
    }
}
```

`src/main/java/com/churchmanagement/dto/OfficialSmsRecipient.java`:

```java
package com.churchmanagement.dto;

public record OfficialSmsRecipient(long churchId, String churchCode, String churchName, String regionName,
                                   String mobileNumber, Status status) {
    public enum Status {
        OK,
        MISSING,
        INVALID
    }

    public boolean isSendable() {
        return status == Status.OK;
    }
}
```

`SmsLogDto` — add fields after `queuedByUserId`, with getters/setters in the same style as the rest of the class:

```java
    private SmsType smsType = SmsType.RECEIPT;
    private String batchUuid;

    public SmsType getSmsType() {
        return smsType;
    }

    public void setSmsType(SmsType smsType) {
        this.smsType = smsType == null ? SmsType.RECEIPT : smsType;
    }

    public String getBatchUuid() {
        return batchUuid;
    }

    public void setBatchUuid(String batchUuid) {
        this.batchUuid = batchUuid;
    }
```

`SmsLogSearchCriteria` — add `private SmsType smsType;` with `getSmsType()` / `setSmsType(SmsType)`.

`SmsLogRepository`:

1. `baseSelect()`: change the first select line to
   `SELECT sl.id, sl.sms_log_uuid, sl.sms_type, sl.batch_uuid, sl.receipt_id, sl.church_id, r.receipt_no,`
2. `mapLogs`: after `log.setSmsLogUuid(...)` add
   ```java
                log.setSmsType(SmsType.fromDatabase(resultSet.getString("sms_type")));
                log.setBatchUuid(resultSet.getString("batch_uuid"));
   ```
3. `searchSmsLogs(SmsLogSearchCriteria)`: after the delivery-status block add
   ```java
        if (safeCriteria.getSmsType() != null) {
            sql.append("AND sl.sms_type = ? ");
            parameters.add(safeCriteria.getSmsType().name());
        }
   ```
4. Replace `enqueueResend` with:

```java
    public void enqueueResend(Long receiptId, Long churchId, String mobileNumber, String message,
                              long originalSmsLogId, long resentByUserId, String resendReason,
                              int priorAttemptCount, LocalDateTime queuedAt, SmsType smsType, String batchUuid) {
        String sql = """
                INSERT INTO sms_logs (
                    sms_log_uuid, receipt_id, church_id, mobile_number, message, status, delivery_status,
                    attempt_count, queued_by_user_id, created_at, resend_of_sms_log_id, resent_by_user_id,
                    resend_reason, sms_type, batch_uuid
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            setNullableLong(statement, 2, receiptId);
            setNullableLong(statement, 3, churchId);
            statement.setString(4, mobileNumber);
            statement.setString(5, message);
            statement.setString(6, SmsSendStatus.QUEUED.name());
            statement.setString(7, SmsDeliveryStatus.UNKNOWN.name());
            statement.setInt(8, priorAttemptCount);
            statement.setLong(9, resentByUserId);
            statement.setTimestamp(10, Timestamp.valueOf(queuedAt));
            statement.setLong(11, originalSmsLogId);
            statement.setLong(12, resentByUserId);
            setNullableString(statement, 13, truncate(resendReason, 255));
            statement.setString(14, (smsType == null ? SmsType.RECEIPT : smsType).name());
            setNullableString(statement, 15, batchUuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to queue SMS resend.", exception);
        }
    }
```

5. Add after `enqueueResend`:

```java
    /** Queues one official message row per recipient, all or nothing. */
    public void enqueueOfficialBatch(String batchUuid, String message, List<OfficialSmsRecipient> recipients,
                                     long queuedByUserId, LocalDateTime queuedAt) {
        String sql = """
                INSERT INTO sms_logs (
                    sms_log_uuid, sms_type, batch_uuid, church_id, mobile_number, message, status, delivery_status,
                    attempt_count, queued_by_user_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (OfficialSmsRecipient recipient : recipients) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, SmsType.OFFICIAL_MESSAGE.name());
                    statement.setString(3, batchUuid);
                    statement.setLong(4, recipient.churchId());
                    statement.setString(5, recipient.mobileNumber());
                    statement.setString(6, message);
                    statement.setString(7, SmsSendStatus.QUEUED.name());
                    statement.setString(8, SmsDeliveryStatus.UNKNOWN.name());
                    statement.setInt(9, 0);
                    statement.setLong(10, queuedByUserId);
                    statement.setTimestamp(11, Timestamp.valueOf(queuedAt));
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to queue official SMS messages.", exception);
        }
    }
```

Add imports `com.churchmanagement.dto.OfficialSmsRecipient` and `com.churchmanagement.enums.SmsType`.

6. `SmsResendService.resendSms` — update the call so it compiles (behaviour is tested in Task 6):

```java
        smsLogRepository.enqueueResend(original.getReceiptId(), original.getChurchId(), resendMobileNumber,
                original.getMessage(), original.getId(), currentUser.getUserId(), resendReason, priorAttemptCount,
                LocalDateTime.now(clock), original.getSmsType(), original.getBatchUuid());
```

7. `SmsResendServiceTest.FakeSmsLogRepository.enqueueResend` — add the two parameters `SmsType smsType, String batchUuid` to the override signature (import `com.churchmanagement.enums.SmsType`) and store them in new fields `private SmsType smsType; private String batchUuid;`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=SmsLogRepositoryTest,SmsResendServiceTest,SmsLogServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V41__official_sms_messaging.sql src/main/java/com/churchmanagement/enums/SmsType.java src/main/java/com/churchmanagement/dto/OfficialSmsRecipient.java src/main/java/com/churchmanagement/dto/SmsLogDto.java src/main/java/com/churchmanagement/dto/SmsLogSearchCriteria.java src/main/java/com/churchmanagement/repository/SmsLogRepository.java src/main/java/com/churchmanagement/service/SmsResendService.java src/test/java/com/churchmanagement/repository/SmsLogRepositoryTest.java src/test/java/com/churchmanagement/service/SmsResendServiceTest.java
git commit -m "feat(sms): SMS type, batch and official queue support in sms_logs" -m "V41 adds sms_logs.sms_type (default RECEIPT, so existing rows are receipts) and batch_uuid, the sms.official.menu.view / sms.official.send permissions for Admin, and the blank sms.official.footer setting." -m "SmsLogRepository reads both columns, filters by type, copies them on resend, and gains enqueueOfficialBatch, which inserts one QUEUED row per recipient in a single transaction." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Resend keeps the SMS type and batch

**Files:**
- Test: `src/test/java/com/churchmanagement/service/SmsResendServiceTest.java`

**Interfaces:**
- Consumes: `SmsLogDto.setSmsType/setBatchUuid`, the fake's `smsType` / `batchUuid` fields (Task 5).

- [ ] **Step 1: Write the test**

```java
    @Test
    void resendOfOfficialMessageKeepsTypeAndBatchWithoutReceipt() {
        smsLogRepository.original.setReceiptId(null);
        smsLogRepository.original.setSmsType(SmsType.OFFICIAL_MESSAGE);
        smsLogRepository.original.setBatchUuid("batch-1");

        smsResendService.resendSms(validRequest());

        assertEquals(1, smsLogRepository.enqueueCount);
        assertNull(smsLogRepository.receiptId);
        assertEquals(10L, smsLogRepository.churchId);
        assertEquals(SmsType.OFFICIAL_MESSAGE, smsLogRepository.smsType);
        assertEquals("batch-1", smsLogRepository.batchUuid);
    }
```

Add `import static org.junit.jupiter.api.Assertions.assertNull;`.

- [ ] **Step 2: Run test**

Run: `mvn -q test -Dtest=SmsResendServiceTest`
Expected: PASS (the production change was made in Task 5; this test locks it in). If it fails, fix `SmsResendService.resendSms` to pass `original.getSmsType()` and `original.getBatchUuid()`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/churchmanagement/service/SmsResendServiceTest.java
git commit -m "test(sms): resending an official message keeps its type and batch" -m "Locks in that a resend of an OFFICIAL_MESSAGE row works without a receipt and stays in the original batch, so SMS Logs still groups and filters it as an official message." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: OfficialSmsService

**Files:**
- Create: `src/main/java/com/churchmanagement/dto/OfficialSmsResult.java`
- Create: `src/main/java/com/churchmanagement/service/OfficialSmsService.java`
- Modify: `src/main/java/com/churchmanagement/service/ActivityLogService.java`
- Test: `src/test/java/com/churchmanagement/service/OfficialSmsServiceTest.java`

**Interfaces:**
- Consumes: `SmsLengthCalculator` (Task 1), `SriLankanMobileNumber` (Task 2), `OfficialSmsRecipient`, `SmsLogRepository.enqueueOfficialBatch` (Task 5), `ChurchRepository.findOperationalChurches()` (existing — active branches in active regions, ordered by code).
- Produces:
  - `record OfficialSmsResult(String batchUuid, int queuedCount, List<OfficialSmsRecipient> skipped, int partCount)`.
  - `OfficialSmsService`: `static final String FOOTER_SETTING_KEY = "sms.official.footer"`, `static final String SEND_PERMISSION = "sms.official.send"`; `List<OfficialSmsRecipient> loadRecipients()`; `static OfficialSmsRecipient toRecipient(Church church)`; `String footer()`; `String composeMessage(String body)`; `OfficialSmsResult sendOfficialMessage(String body, Collection<Long> churchIds)`; nested `static class OfficialSmsException extends RuntimeException`.
  - `ActivityLogService`: constants `NAVIGATE_SEND_MESSAGE`, `OFFICIAL_SMS_QUEUED`, `OFFICIAL_SMS_SENT`, `OFFICIAL_SMS_FAILED`; methods `logOfficialSmsQueued(Long userId, String batchUuid, int queuedCount, int skippedCount, int partCount, String message)`, `logOfficialSmsSent(Long userId, String batchUuid, Long churchId, String mobileNumber, String modemReference)`, `logOfficialSmsFailed(Long userId, String batchUuid, Long churchId, String mobileNumber, String reason)`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.entity.Church;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficialSmsServiceTest {
    private FakeSmsLogRepository smsLogRepository;
    private FakeActivityLogService activityLogService;
    private Map<String, String> settings;
    private OfficialSmsService service;

    @BeforeEach
    void setUp() {
        smsLogRepository = new FakeSmsLogRepository();
        activityLogService = new FakeActivityLogService();
        settings = new HashMap<>(Map.of(OfficialSmsService.FOOTER_SETTING_KEY, "- Head Office"));
        service = new OfficialSmsService(smsLogRepository, new FakeChurchRepository(), activityLogService,
                new FakeSystemConfigurationCache(settings),
                Clock.fixed(Instant.parse("2026-10-20T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of(OfficialSmsService.SEND_PERMISSION)));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void queuesOneRowPerValidRecipientWithFooter() {
        OfficialSmsResult result = service.sendOfficialMessage("Meeting on Sunday", List.of(1L, 2L, 3L));

        assertEquals(1, result.queuedCount());
        assertEquals(2, result.skipped().size());
        assertEquals(1, result.partCount());
        assertEquals(List.of(1L), smsLogRepository.recipients.stream().map(OfficialSmsRecipient::churchId).toList());
        assertEquals("+94771234567", smsLogRepository.recipients.getFirst().mobileNumber());
        assertEquals("Meeting on Sunday\n- Head Office", smsLogRepository.message);
        assertEquals(7L, smsLogRepository.queuedByUserId);
        assertEquals(result.batchUuid(), smsLogRepository.batchUuid);
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), smsLogRepository.queuedAt);
    }

    @Test
    void writesOneActivityEntryPerSend() {
        OfficialSmsResult result = service.sendOfficialMessage("Meeting on Sunday", List.of(1L, 2L, 3L));

        assertEquals(ActivityLogService.OFFICIAL_SMS_QUEUED, activityLogService.action);
        assertEquals(result.batchUuid(), activityLogService.batchUuid);
        assertEquals(1, activityLogService.queuedCount);
        assertEquals(2, activityLogService.skippedCount);
    }

    @Test
    void composeMessageWithoutFooterWhenBlank() {
        settings.put(OfficialSmsService.FOOTER_SETTING_KEY, "  ");

        assertEquals("Hello", service.composeMessage("  Hello  "));
    }

    @Test
    void countsPartsOfLongMessage() {
        OfficialSmsResult result = service.sendOfficialMessage("a".repeat(200), List.of(1L));

        assertEquals(2, result.partCount());
    }

    @Test
    void acceptsExactly459CharactersIncludingFooter() {
        // 445 + newline + 13-character footer = 459
        OfficialSmsResult result = service.sendOfficialMessage("a".repeat(445), List.of(1L));

        assertEquals(3, result.partCount());
    }

    @Test
    void rejectsMessageOver459CharactersIncludingFooter() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("a".repeat(446), List.of(1L)));

        assertEquals("Message is too long: 460 of 459 characters, footer included.", exception.getMessage());
        assertNull(smsLogRepository.message);
    }

    @Test
    void rejectsUnsupportedCharacter() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello අ", List.of(1L)));

        assertEquals("Unsupported character: 'අ'. Only English (GSM) characters can be sent.",
                exception.getMessage());
    }

    @Test
    void rejectsWithoutPermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L, "User",
                List.of("sms.menu.view")));

        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of(1L)));

        assertEquals("You do not have permission to send official messages.", exception.getMessage());
    }

    @Test
    void rejectsBlankMessage() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("   ", List.of(1L)));

        assertEquals("Message is required.", exception.getMessage());
    }

    @Test
    void rejectsNoRecipients() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of()));

        assertEquals("Select at least one branch.", exception.getMessage());
    }

    @Test
    void rejectsWhenNoSelectedBranchHasValidNumber() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of(2L, 3L)));

        assertEquals("None of the selected branches has a valid mobile number.", exception.getMessage());
        assertNull(smsLogRepository.message);
    }

    @Test
    void flagsMissingAndInvalidNumbers() {
        List<OfficialSmsRecipient> recipients = service.loadRecipients();

        assertEquals(OfficialSmsRecipient.Status.OK, recipients.get(0).status());
        assertEquals(OfficialSmsRecipient.Status.MISSING, recipients.get(1).status());
        assertEquals(OfficialSmsRecipient.Status.INVALID, recipients.get(2).status());
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private String batchUuid;
        private String message;
        private List<OfficialSmsRecipient> recipients;
        private long queuedByUserId;
        private LocalDateTime queuedAt;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public void enqueueOfficialBatch(String batchUuid, String message, List<OfficialSmsRecipient> recipients,
                                         long queuedByUserId, LocalDateTime queuedAt) {
            this.batchUuid = batchUuid;
            this.message = message;
            this.recipients = recipients;
            this.queuedByUserId = queuedByUserId;
            this.queuedAt = queuedAt;
        }
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public List<Church> findOperationalChurches() {
            return List.of(church(1L, "0001", "0771234567"), church(2L, "0002", null),
                    church(3L, "0003", "0111234567"));
        }

        private static Church church(long id, String code, String mobile) {
            Church church = new Church();
            church.setId(id);
            church.setChurchCode(code);
            church.setChurchName("Church " + code);
            church.setRegionName("Region 1");
            church.setSmsMobileNumber(mobile);
            return church;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;
        private String batchUuid;
        private int queuedCount;
        private int skippedCount;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logOfficialSmsQueued(Long userId, String batchUuid, int queuedCount, int skippedCount,
                                         int partCount, String message) {
            this.action = OFFICIAL_SMS_QUEUED;
            this.batchUuid = batchUuid;
            this.queuedCount = queuedCount;
            this.skippedCount = skippedCount;
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final Map<String, String> values;

        private FakeSystemConfigurationCache(Map<String, String> values) {
            super(null);
            this.values = values;
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OfficialSmsServiceTest`
Expected: compilation FAILURE — `cannot find symbol: class OfficialSmsService`.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/churchmanagement/dto/OfficialSmsResult.java`:

```java
package com.churchmanagement.dto;

import java.util.List;

public record OfficialSmsResult(String batchUuid, int queuedCount, List<OfficialSmsRecipient> skipped,
                                int partCount) {
}
```

`ActivityLogService` — add constants next to `NAVIGATE_SMS_LOGS` / the SMS constants:

```java
    public static final String NAVIGATE_SEND_MESSAGE = "NAVIGATE_SEND_MESSAGE";
    public static final String OFFICIAL_SMS_QUEUED = "OFFICIAL_SMS_QUEUED";
    public static final String OFFICIAL_SMS_SENT = "OFFICIAL_SMS_SENT";
    public static final String OFFICIAL_SMS_FAILED = "OFFICIAL_SMS_FAILED";
```

and methods after `logSmsResendBlockedPermission`:

```java
    public void logOfficialSmsQueued(Long userId, String batchUuid, int queuedCount, int skippedCount,
                                     int partCount, String message) {
        String preview = message == null ? "" : message.substring(0, Math.min(100, message.length()));
        log(userId, OFFICIAL_SMS_QUEUED, "SMS", batchUuid,
                "batch_uuid: " + nullToBlank(batchUuid)
                        + ", queued: " + queuedCount
                        + ", skipped: " + skippedCount
                        + ", parts: " + partCount
                        + ", message: " + preview);
    }

    public void logOfficialSmsSent(Long userId, String batchUuid, Long churchId, String mobileNumber,
                                   String modemReference) {
        log(userId, OFFICIAL_SMS_SENT, "SMS", batchUuid,
                "batch_uuid: " + nullToBlank(batchUuid)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", mobile_number: " + maskMobileNumber(mobileNumber)
                        + ", modem_reference: " + nullToBlank(modemReference));
    }

    public void logOfficialSmsFailed(Long userId, String batchUuid, Long churchId, String mobileNumber,
                                     String reason) {
        log(userId, OFFICIAL_SMS_FAILED, "SMS", batchUuid,
                "batch_uuid: " + nullToBlank(batchUuid)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", mobile_number: " + maskMobileNumber(mobileNumber)
                        + ", reason: " + nullToBlank(reason));
    }
```

`src/main/java/com/churchmanagement/service/OfficialSmsService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.entity.Church;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.SmsLengthCalculator;
import com.churchmanagement.util.SriLankanMobileNumber;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Queues an official message to selected branches. Runs on any machine; the primary machine's
 * {@link SmsQueueProcessor} sends the queued rows.
 */
public class OfficialSmsService {
    public static final String FOOTER_SETTING_KEY = "sms.official.footer";
    public static final String SEND_PERMISSION = "sms.official.send";

    private final SmsLogRepository smsLogRepository;
    private final ChurchRepository churchRepository;
    private final ActivityLogService activityLogService;
    private final SystemConfigurationCache configurationCache;
    private final Clock clock;

    public OfficialSmsService() {
        this(new SmsLogRepository(), new ChurchRepository(), new ActivityLogService(),
                SystemConfigurationCache.getInstance(), Clock.systemDefaultZone());
    }

    public OfficialSmsService(SmsLogRepository smsLogRepository, ChurchRepository churchRepository,
                              ActivityLogService activityLogService, SystemConfigurationCache configurationCache,
                              Clock clock) {
        this.smsLogRepository = smsLogRepository;
        this.churchRepository = churchRepository;
        this.activityLogService = activityLogService;
        this.configurationCache = configurationCache;
        this.clock = clock;
    }

    /** Every active branch, with its number checked the same way the modem checks it. */
    public List<OfficialSmsRecipient> loadRecipients() {
        return churchRepository.findOperationalChurches().stream()
                .map(OfficialSmsService::toRecipient)
                .toList();
    }

    public static OfficialSmsRecipient toRecipient(Church church) {
        String normalized = SriLankanMobileNumber.normalize(church.getSmsMobileNumber());
        OfficialSmsRecipient.Status status = normalized == null
                ? OfficialSmsRecipient.Status.MISSING
                : SriLankanMobileNumber.isValid(normalized)
                ? OfficialSmsRecipient.Status.OK
                : OfficialSmsRecipient.Status.INVALID;
        return new OfficialSmsRecipient(church.getId(), church.getChurchCode(), church.getChurchName(),
                church.getRegionName(), normalized == null ? "" : normalized, status);
    }

    public String footer() {
        String footer = configurationCache.getString(FOOTER_SETTING_KEY);
        return footer == null ? "" : footer.strip();
    }

    public String composeMessage(String body) {
        String stripped = body == null ? "" : body.strip();
        String footer = footer();
        return footer.isEmpty() ? stripped : stripped + "\n" + footer;
    }

    public OfficialSmsResult sendOfficialMessage(String body, Collection<Long> churchIds) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new OfficialSmsException("Please sign in to send messages."));
        if (!new PermissionGuard(currentUser).can(SEND_PERMISSION)) {
            throw new OfficialSmsException("You do not have permission to send official messages.");
        }
        if (body == null || body.isBlank()) {
            throw new OfficialSmsException("Message is required.");
        }
        if (churchIds == null || churchIds.isEmpty()) {
            throw new OfficialSmsException("Select at least one branch.");
        }
        String message = composeMessage(body);
        String unsupported = SmsLengthCalculator.firstUnsupportedCharacter(message);
        if (unsupported != null) {
            throw new OfficialSmsException("Unsupported character: '" + unsupported
                    + "'. Only English (GSM) characters can be sent.");
        }
        int septets = SmsLengthCalculator.septetLength(message);
        if (septets > SmsLengthCalculator.MAX_SEPTETS) {
            throw new OfficialSmsException("Message is too long: " + septets + " of "
                    + SmsLengthCalculator.MAX_SEPTETS + " characters, footer included.");
        }

        Set<Long> selectedIds = new HashSet<>(churchIds);
        List<OfficialSmsRecipient> selected = loadRecipients().stream()
                .filter(recipient -> selectedIds.contains(recipient.churchId()))
                .toList();
        List<OfficialSmsRecipient> sendable = selected.stream().filter(OfficialSmsRecipient::isSendable).toList();
        List<OfficialSmsRecipient> skipped = selected.stream().filter(recipient -> !recipient.isSendable()).toList();
        if (sendable.isEmpty()) {
            throw new OfficialSmsException("None of the selected branches has a valid mobile number.");
        }

        String batchUuid = UUID.randomUUID().toString();
        int partCount = SmsLengthCalculator.partCount(message);
        smsLogRepository.enqueueOfficialBatch(batchUuid, message, sendable, currentUser.getUserId(),
                LocalDateTime.now(clock));
        activityLogService.logOfficialSmsQueued(currentUser.getUserId(), batchUuid, sendable.size(), skipped.size(),
                partCount, message);
        return new OfficialSmsResult(batchUuid, sendable.size(), skipped, partCount);
    }

    public static class OfficialSmsException extends RuntimeException {
        public OfficialSmsException(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OfficialSmsServiceTest`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/OfficialSmsResult.java src/main/java/com/churchmanagement/service/OfficialSmsService.java src/main/java/com/churchmanagement/service/ActivityLogService.java src/test/java/com/churchmanagement/service/OfficialSmsServiceTest.java
git commit -m "feat(sms): OfficialSmsService queues an official message to branches" -m "Checks sms.official.send, a non-blank message, at least one branch, the GSM 7-bit alphabet (naming the first unsupported character) and the 459-character limit with the sms.official.footer appended on its own line. Branches with a missing or invalid number are skipped and reported back; the rest are queued in one transaction with a shared batch UUID and one OFFICIAL_SMS_QUEUED activity entry." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Official activity logging in SmsQueueProcessor

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/SmsQueueProcessor.java` (`logActivity`, ~line 162)
- Test: `src/test/java/com/churchmanagement/service/SmsQueueProcessorTest.java`

**Interfaces:**
- Consumes: `ActivityLogService.logOfficialSmsSent/logOfficialSmsFailed` (Task 7), `SmsLogDto.getSmsType/getBatchUuid` (Task 5).

- [ ] **Step 1: Write the failing tests**

Add to `SmsQueueProcessorTest` (import `com.churchmanagement.enums.SmsType`):

```java
    @Test
    void officialRowLogsOfficialSent() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(officialRow(1L));
        FakeActivityLogService activityLogService = new FakeActivityLogService();
        SmsQueueProcessor processor = new SmsQueueProcessor(repository, new FakeSmsServiceFactory(true),
                activityLogService, new FakeSystemConfigurationCache(Map.of("sms.retry.max.attempts", "3")),
                () -> true, fixedClock());

        processor.processTick();

        assertEquals(ActivityLogService.OFFICIAL_SMS_SENT, activityLogService.action);
    }

    @Test
    void officialRowLogsOfficialFailed() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(officialRow(1L));
        FakeActivityLogService activityLogService = new FakeActivityLogService();
        SmsQueueProcessor processor = new SmsQueueProcessor(repository, new FakeSmsServiceFactory(false),
                activityLogService, new FakeSystemConfigurationCache(Map.of("sms.retry.max.attempts", "3")),
                () -> true, fixedClock());

        processor.processTick();

        assertEquals(ActivityLogService.OFFICIAL_SMS_FAILED, activityLogService.action);
    }

    private SmsLogDto officialRow(long id) {
        SmsLogDto row = queuedRow(id, LocalDateTime.of(2026, 6, 6, 10, 0));
        row.setReceiptId(null);
        row.setSmsType(SmsType.OFFICIAL_MESSAGE);
        row.setBatchUuid("batch-1");
        row.setQueuedByUserId(7L);
        return row;
    }
```

And in its `FakeActivityLogService` add:

```java
        @Override
        public void logOfficialSmsSent(Long userId, String batchUuid, Long churchId, String mobileNumber,
                                       String modemReference) {
            action = OFFICIAL_SMS_SENT;
        }

        @Override
        public void logOfficialSmsFailed(Long userId, String batchUuid, Long churchId, String mobileNumber,
                                         String reason) {
            action = OFFICIAL_SMS_FAILED;
        }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=SmsQueueProcessorTest`
Expected: FAIL — `officialRowLogsOfficialSent` expected `OFFICIAL_SMS_SENT` but was `SMS_SENT_ACCEPTED_BY_MODEM`.

- [ ] **Step 3: Write the implementation**

In `SmsQueueProcessor.logActivity`, after the `if (isResend) { ... return; }` block, add (import `com.churchmanagement.enums.SmsType`):

```java
        if (row.getSmsType() == SmsType.OFFICIAL_MESSAGE) {
            if (success) {
                activityLogService.logOfficialSmsSent(userId, row.getBatchUuid(), row.getChurchId(),
                        row.getMobileNumber(), modemMessageReference);
            } else {
                activityLogService.logOfficialSmsFailed(userId, row.getBatchUuid(), row.getChurchId(),
                        row.getMobileNumber(), errorMessage);
            }
            return;
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=SmsQueueProcessorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/SmsQueueProcessor.java src/test/java/com/churchmanagement/service/SmsQueueProcessorTest.java
git commit -m "feat(sms): log official message sends as OFFICIAL_SMS_SENT / FAILED" -m "The queue processor already sends official rows like any other queued row. Only its activity logging changes: OFFICIAL_MESSAGE rows log the batch, branch and masked number instead of the receipt SMS entries. Resends keep their own resend entries." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Footer setting (validation and Settings screen)

**Files:**
- Modify: `src/main/java/com/churchmanagement/service/SystemSettingService.java` (`validateValue`, ~line 93)
- Modify: `src/main/java/com/churchmanagement/controller/SettingsController.java` (fields ~line 122, `saveSmsSettings` ~line 186, `applySettings` ~line 466)
- Modify: `src/main/resources/com/churchmanagement/view/settings-view.fxml` (SMS form grid ~line 88–108, action bar ~line 109)
- Test: `src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java`

**Interfaces:**
- Consumes: `SmsLengthCalculator.isGsm7` (Task 1), `OfficialSmsService.FOOTER_SETTING_KEY` (Task 7).
- Produces: the footer field only. The Settings "Open Send Message" button is added in Task 10 Step 4, together with the `DashboardController.openSendMessage()` it calls.

- [ ] **Step 1: Write the failing tests**

In `SystemSettingServiceTest`, add to the fake default list (after `sms.retry.max.attempts`):

```java
            add(setting("sms.official.footer", "", "STRING", "SMS"));
```

change `assertEquals(19, settings.size());` in `loadsSettings` to `assertEquals(20, settings.size());`, and add:

```java
    @Test
    void rejectsOfficialFooterOver100Characters() {
        assertInvalid("sms.official.footer", "a".repeat(101),
                "Official message footer cannot exceed 100 characters.");
    }

    @Test
    void rejectsOfficialFooterWithNonGsmCharacters() {
        assertInvalid("sms.official.footer", "අ",
                "Official message footer can only contain English (GSM) characters.");
    }

    @Test
    void acceptsBlankOfficialFooter() {
        AuthContext.setCurrentUser(settingsAdmin());
        TestSystemConfigurationCache cache = new TestSystemConfigurationCache();
        SystemSettingService service = serviceWithDefaults(cache);

        service.updateSettings(List.of(new UpdateSystemSettingRequest("sms.official.footer", "")));

        assertEquals("", cache.repository.getValue("sms.official.footer"));
        AuthContext.clear();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=SystemSettingServiceTest`
Expected: FAIL — the two `rejects…Footer…` tests expect an exception that is not thrown.

- [ ] **Step 3: Write the implementation**

In `SystemSettingService.validateValue`, add a case before `default`:

```java
            case "sms.official.footer" -> requireOfficialFooter(normalized);
```

and the helper next to `requireText`:

```java
    private void requireOfficialFooter(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.length() > 100) {
            throw new SystemSettingException("Official message footer cannot exceed 100 characters.");
        }
        if (!SmsLengthCalculator.isGsm7(value)) {
            throw new SystemSettingException("Official message footer can only contain English (GSM) characters.");
        }
    }
```

Import `com.churchmanagement.util.SmsLengthCalculator`.

`settings-view.fxml` — in the SMS `GridPane` (after the Baud rate row, row 5) add:

```xml
                    <Label text="Official message footer" GridPane.columnIndex="0" GridPane.rowIndex="6"/>
                    <TextField fx:id="officialSmsFooterField" prefWidth="360" promptText="e.g. - Head Office"
                               GridPane.columnIndex="1" GridPane.rowIndex="6"/>
                    <Label styleClass="muted-label" text="Added to every official message and counted in its length."
                           wrapText="true" GridPane.columnIndex="1" GridPane.rowIndex="7"/>
```

`SettingsController`:
- field: `@FXML private TextField officialSmsFooterField;`
- `saveSmsSettings`: add `request(OfficialSmsService.FOOTER_SETTING_KEY, officialSmsFooterField.getText())` to the `List.of(...)` passed to `systemSettingService.updateSettings`.
- `applySettings` (where `smsRetryMaxAttemptsField.setText(...)` is): add
  `officialSmsFooterField.setText(defaultValue(values.get(OfficialSmsService.FOOTER_SETTING_KEY), ""));`
- import `com.churchmanagement.service.OfficialSmsService`.

- [ ] **Step 4: Run tests and compile**

Run: `mvn -q test -Dtest=SystemSettingServiceTest` then `mvn -q compile`
Expected: PASS; compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/SystemSettingService.java src/main/java/com/churchmanagement/controller/SettingsController.java src/main/resources/com/churchmanagement/view/settings-view.fxml src/test/java/com/churchmanagement/service/SystemSettingServiceTest.java
git commit -m "feat(settings): official message footer setting" -m "Settings > SMS gains an 'Official message footer' field saved with the other SMS settings. The value may be blank, must be 100 characters or fewer and may only use the GSM 7-bit alphabet, so it can never make an official message unsendable on its own." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Send Message screen and sidebar item

**Files:**
- Create: `src/main/resources/com/churchmanagement/view/send-message-view.fxml`
- Create: `src/main/java/com/churchmanagement/controller/SendMessageController.java`
- Modify: `src/main/resources/com/churchmanagement/view/dashboard-view.fxml` (after `smsLogsButton`, line 69)
- Modify: `src/main/java/com/churchmanagement/controller/DashboardController.java` (fields ~186, handlers ~533, `createMenuDefinitions` ~591, `applyMenuIcons` ~621, static helpers ~730)
- Modify: `src/main/java/com/churchmanagement/controller/SettingsController.java`, `src/main/resources/com/churchmanagement/view/settings-view.fxml` (Open Send Message button)

**Interfaces:**
- Consumes: `OfficialSmsService.loadRecipients/footer/composeMessage/sendOfficialMessage`, `OfficialSmsException`, `SEND_PERMISSION` (Task 7); `OfficialSmsRecipient` (Task 5); `SmsLengthCalculator` (Task 1); `ActivityLogService.NAVIGATE_SEND_MESSAGE` (Task 7); existing `ProcessingDialog.run(String, String, Callable<T>, Consumer<T>, Consumer<Throwable>)`, `DialogStyler.apply(Alert)`, `ButtonIconUtil.applyIcon(Button, String)`.
- Produces: `public static void DashboardController.openSendMessage()`.

There is no automated UI test in this codebase; verification is `mvn -q compile` plus the manual checks in Step 6.

- [ ] **Step 1: Create the view**

`send-message-view.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<HBox spacing="18" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.SendMessageController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>

    <VBox spacing="10" HBox.hgrow="ALWAYS">
        <Label styleClass="receipt-detail-card-title" text="Recipients"/>
        <HBox spacing="10">
            <ComboBox fx:id="regionComboBox" prefWidth="200"/>
            <TextField fx:id="searchField" HBox.hgrow="ALWAYS" promptText="Search by branch code or name"/>
        </HBox>
        <HBox spacing="10">
            <Button fx:id="selectAllButton" onAction="#handleSelectAll" text="Select all shown"/>
            <Button fx:id="clearButton" onAction="#handleClear" text="Clear"/>
            <Region HBox.hgrow="ALWAYS"/>
            <Label fx:id="selectionSummaryLabel" text="0 selected"/>
        </HBox>
        <TableView fx:id="recipientTable" editable="true" VBox.vgrow="ALWAYS">
            <columns>
                <TableColumn fx:id="selectColumn" prefWidth="44" maxWidth="50" resizable="false" sortable="false"/>
                <TableColumn fx:id="codeColumn" prefWidth="80" text="Code"/>
                <TableColumn fx:id="nameColumn" prefWidth="200" text="Branch"/>
                <TableColumn fx:id="regionColumn" prefWidth="140" text="Region"/>
                <TableColumn fx:id="mobileColumn" prefWidth="130" text="Mobile"/>
                <TableColumn fx:id="statusColumn" prefWidth="130" text="Status"/>
            </columns>
            <columnResizePolicy>
                <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
            </columnResizePolicy>
        </TableView>
    </VBox>

    <VBox spacing="10" prefWidth="420" minWidth="360">
        <Label styleClass="receipt-detail-card-title" text="Message"/>
        <TextArea fx:id="messageTextArea" prefRowCount="10" wrapText="true" VBox.vgrow="ALWAYS"
                  promptText="Type the official message in English"/>
        <Label fx:id="footerPreviewLabel" styleClass="muted-label" wrapText="true"/>
        <Label fx:id="counterLabel" text="0 / 459 characters · 0 SMS parts"/>
        <Label fx:id="totalLabel" text="0 SMS will be sent"/>
        <HBox alignment="CENTER_RIGHT">
            <Button fx:id="sendButton" onAction="#handleSend" styleClass="primary-button" text="Send"/>
        </HBox>
    </VBox>
</HBox>
```

- [ ] **Step 2: Create the controller**

```java
package com.churchmanagement.controller;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.OfficialSmsService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.SmsLengthCalculator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SendMessageController {
    private static final String ALL_REGIONS = "All regions";
    private static final String ERROR_STYLE = "error-label";
    private static final String WARNING_STYLE = "warning-label";

    @FXML private ComboBox<String> regionComboBox;
    @FXML private TextField searchField;
    @FXML private Button selectAllButton;
    @FXML private Button clearButton;
    @FXML private Label selectionSummaryLabel;
    @FXML private TableView<RecipientRow> recipientTable;
    @FXML private TableColumn<RecipientRow, Boolean> selectColumn;
    @FXML private TableColumn<RecipientRow, String> codeColumn;
    @FXML private TableColumn<RecipientRow, String> nameColumn;
    @FXML private TableColumn<RecipientRow, String> regionColumn;
    @FXML private TableColumn<RecipientRow, String> mobileColumn;
    @FXML private TableColumn<RecipientRow, String> statusColumn;
    @FXML private TextArea messageTextArea;
    @FXML private Label footerPreviewLabel;
    @FXML private Label counterLabel;
    @FXML private Label totalLabel;
    @FXML private Button sendButton;

    private final OfficialSmsService officialSmsService = new OfficialSmsService();
    private final ObservableList<RecipientRow> allRows = FXCollections.observableArrayList();
    private final FilteredList<RecipientRow> shownRows = new FilteredList<>(allRows, row -> true);
    private boolean canSend;
    private boolean messageValid;
    private int partCount;

    @FXML
    private void initialize() {
        canSend = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can(OfficialSmsService.SEND_PERMISSION))
                .orElse(false);
        ButtonIconUtil.applyIcon(sendButton, "fas-paper-plane");
        configureTable();
        regionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        messageTextArea.textProperty().addListener((observable, oldValue, newValue) -> refreshMessageState());
        showFooterPreview();
        loadRecipients();
        refreshMessageState();
    }

    @FXML
    private void handleSelectAll() {
        shownRows.stream().filter(row -> row.recipient().isSendable()).forEach(row -> row.selected().set(true));
    }

    @FXML
    private void handleClear() {
        allRows.forEach(row -> row.selected().set(false));
    }

    @FXML
    private void handleSend() {
        List<Long> churchIds = selectedChurchIds();
        int smsCount = churchIds.size() * partCount;
        Alert confirmation = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        confirmation.setTitle("Send Message");
        confirmation.setHeaderText("Send Message");
        confirmation.setContentText("Send this message to " + churchIds.size() + " branch"
                + (churchIds.size() == 1 ? "" : "es") + " (" + smsCount + " SMS)?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String body = messageTextArea.getText();
        ProcessingDialog.run("Send Message", "Queuing messages...",
                () -> officialSmsService.sendOfficialMessage(body, churchIds),
                this::showSent,
                throwable -> showError(throwable instanceof OfficialSmsService.OfficialSmsException
                        ? throwable.getMessage()
                        : "Unable to queue the messages. Please try again."));
    }

    private void configureTable() {
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selected());
        selectColumn.setCellFactory(column -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                RecipientRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setDisable(row == null || !row.recipient().isSendable());
            }
        });
        selectColumn.setEditable(true);
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().churchCode()));
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().churchName()));
        regionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().regionName()));
        mobileColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().mobileNumber()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(statusText(cellData.getValue().recipient())));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove(WARNING_STYLE);
                RecipientRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row != null && !row.recipient().isSendable()) {
                    getStyleClass().add(WARNING_STYLE);
                }
            }
        });
        recipientTable.setItems(shownRows);
    }

    private void loadRecipients() {
        ProcessingDialog.run("Send Message", "Loading branches...",
                officialSmsService::loadRecipients,
                recipients -> {
                    allRows.setAll(recipients.stream().map(RecipientRow::new).toList());
                    allRows.forEach(row -> row.selected().addListener((observable, oldValue, newValue) ->
                            refreshMessageState()));
                    List<String> regions = recipients.stream().map(OfficialSmsRecipient::regionName)
                            .filter(Objects::nonNull).distinct().sorted().toList();
                    regionComboBox.getItems().setAll(ALL_REGIONS);
                    regionComboBox.getItems().addAll(regions);
                    regionComboBox.setValue(ALL_REGIONS);
                    refreshMessageState();
                },
                throwable -> showError("Unable to load branches."));
    }

    private void applyFilter() {
        String region = regionComboBox.getValue();
        String term = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        shownRows.setPredicate(row -> {
            OfficialSmsRecipient recipient = row.recipient();
            boolean regionMatches = region == null || ALL_REGIONS.equals(region)
                    || region.equals(recipient.regionName());
            boolean termMatches = term.isEmpty()
                    || recipient.churchCode().toLowerCase(Locale.ROOT).contains(term)
                    || recipient.churchName().toLowerCase(Locale.ROOT).contains(term);
            return regionMatches && termMatches;
        });
        refreshMessageState();
    }

    private void refreshMessageState() {
        String message = officialSmsService.composeMessage(messageTextArea.getText());
        String unsupported = SmsLengthCalculator.firstUnsupportedCharacter(message);
        counterLabel.getStyleClass().remove(ERROR_STYLE);
        if (unsupported != null) {
            messageValid = false;
            partCount = 0;
            counterLabel.setText("Unsupported character: '" + unsupported + "'");
            counterLabel.getStyleClass().add(ERROR_STYLE);
        } else {
            int septets = SmsLengthCalculator.septetLength(message);
            partCount = SmsLengthCalculator.partCount(message);
            messageValid = septets <= SmsLengthCalculator.MAX_SEPTETS;
            counterLabel.setText(septets + " / " + SmsLengthCalculator.MAX_SEPTETS + " characters · "
                    + partCount + " SMS part" + (partCount == 1 ? "" : "s"));
            if (!messageValid) {
                counterLabel.getStyleClass().add(ERROR_STYLE);
            }
        }

        int selected = selectedChurchIds().size();
        long shownWithoutNumber = shownRows.stream().filter(row -> !row.recipient().isSendable()).count();
        selectionSummaryLabel.setText(selected + " selected · " + shownWithoutNumber
                + " shown without a valid number");
        totalLabel.setText((messageValid ? selected * partCount : 0) + " SMS will be sent");
        boolean blank = messageTextArea.getText() == null || messageTextArea.getText().isBlank();
        sendButton.setDisable(!canSend || selected == 0 || blank || !messageValid);
    }

    private void showFooterPreview() {
        String footer = officialSmsService.footer();
        footerPreviewLabel.setText(footer.isEmpty() ? "" : "Footer added: " + footer);
        footerPreviewLabel.setVisible(!footer.isEmpty());
        footerPreviewLabel.setManaged(!footer.isEmpty());
    }

    private List<Long> selectedChurchIds() {
        return allRows.stream().filter(row -> row.selected().get()).map(row -> row.recipient().churchId()).toList();
    }

    private void showSent(OfficialSmsResult result) {
        StringBuilder text = new StringBuilder(result.queuedCount() + " message"
                + (result.queuedCount() == 1 ? "" : "s") + " queued.");
        if (!result.skipped().isEmpty()) {
            text.append(' ').append(result.skipped().size()).append(" skipped (no valid number).");
        }
        text.append(" Track delivery in SMS Logs.");
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Send Message");
        alert.setHeaderText("Messages queued");
        alert.setContentText(text.toString());
        alert.showAndWait();
        messageTextArea.clear();
        handleClear();
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Send Message");
        alert.setHeaderText("Send Message");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static String statusText(OfficialSmsRecipient recipient) {
        return switch (recipient.status()) {
            case OK -> "OK";
            case MISSING -> "No mobile number";
            case INVALID -> "Invalid number";
        };
    }

    private record RecipientRow(OfficialSmsRecipient recipient, BooleanProperty selected) {
        private RecipientRow(OfficialSmsRecipient recipient) {
            this(recipient, new SimpleBooleanProperty(false));
        }
    }
}
```

- [ ] **Step 3: Add the sidebar item**

`dashboard-view.fxml` — directly after the `smsLogsButton` line:

```xml
            <Button fx:id="sendMessageButton" maxWidth="Infinity" onAction="#showSendMessage" styleClass="menu-button" text="Send Message"/>
```

`DashboardController`:

```java
    @FXML
    private Button sendMessageButton;
```

(next to `smsLogsButton`),

```java
    @FXML
    private void showSendMessage() {
        loadMenu(findMenu(sendMessageButton));
    }
```

(after `showSmsLogs`), in `createMenuDefinitions()` directly after the "SMS Logs" entry:

```java
                new MenuDefinition("Send Message", "/com/churchmanagement/view/send-message-view.fxml",
                        sendMessageButton, ActivityLogService.NAVIGATE_SEND_MESSAGE, "sms.official.menu.view"),
```

in `applyMenuIcons()` after the SMS Logs icon:

```java
        setMenuIcon(sendMessageButton, "fas-paper-plane");
```

and after `openReceiptCorrection`:

```java
    public static void openSendMessage() {
        if (activeController != null) {
            activeController.loadMenu(activeController.findMenu(activeController.sendMessageButton));
        }
    }
```

- [ ] **Step 4: Add "Open Send Message" to Settings → SMS**

`settings-view.fxml` — in the `settings-action-bar` `HBox` after `saveSmsSettingsButton`:

```xml
                    <Button fx:id="openSendMessageButton" onAction="#openSendMessage" text="Open Send Message"/>
```

`SettingsController`:

```java
    @FXML private Button openSendMessageButton;
```

in `initialize()` (where other buttons are configured):

```java
        boolean canOpenSendMessage = currentUser != null
                && new PermissionGuard(currentUser).can("sms.official.menu.view");
        openSendMessageButton.setVisible(canOpenSendMessage);
        openSendMessageButton.setManaged(canOpenSendMessage);
```

(place it right after the existing `new PermissionGuard(currentUser).require("settings.menu.view");` line in `initialize()`; `PermissionGuard` is already imported) and the handler:

```java
    @FXML
    private void openSendMessage() {
        DashboardController.openSendMessage();
    }
```

- [ ] **Step 5: Compile and run the full test suite**

Run: `mvn -q compile` then `mvn -q test`
Expected: compile succeeds; all tests PASS.

- [ ] **Step 6: Manual check (MOCK gateway is enough)**

Launch the app as Admin and confirm:
1. "Send Message" appears under SMS Logs; a user without `sms.official.menu.view` does not see it.
2. Branches load; branches without a number show "No mobile number" in the warning style and cannot be ticked.
3. Choosing a region then **Select all shown** ticks only that region's sendable branches; changing the region keeps earlier ticks.
4. Typing updates the counter; 161 characters shows "2 SMS parts"; typing `අ` shows "Unsupported character: 'අ'" in red and disables Send.
5. Send → confirmation → "N messages queued … Track delivery in SMS Logs."; the rows appear in SMS Logs and turn SENT within ~5 s on the primary machine.
6. Settings → SMS → Open Send Message opens the screen.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/send-message-view.fxml src/main/java/com/churchmanagement/controller/SendMessageController.java src/main/resources/com/churchmanagement/view/dashboard-view.fxml src/main/java/com/churchmanagement/controller/DashboardController.java src/main/java/com/churchmanagement/controller/SettingsController.java src/main/resources/com/churchmanagement/view/settings-view.fxml
git commit -m "feat(sms): Send Message screen for official messages to branches" -m "New sidebar item under SMS Logs (sms.official.menu.view). Recipients: region filter, search, a checkbox table of active branches with numbers checked like the modem checks them, 'Select all shown' and 'Clear'. Message: live character and SMS-part counter with the footer included, the first unsupported character named, and the total SMS count. Send (sms.official.send) confirms, queues through OfficialSmsService and reports queued and skipped branches. Settings > SMS gets an 'Open Send Message' shortcut." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: SMS Logs — Type column, filter and details

**Files:**
- Modify: `src/main/resources/com/churchmanagement/view/sms-log-view.fxml`
- Modify: `src/main/java/com/churchmanagement/controller/SmsLogController.java` (fields ~67–81, `handleRefresh`, `configureFilters`, `configureTable`, `buildCriteria`, `sendDetailsGrid`)

**Interfaces:**
- Consumes: `SmsType` (Task 5), `SmsLogSearchCriteria.setSmsType` (Task 5), `SmsLogDto.getSmsType/getBatchUuid` (Task 5).

- [ ] **Step 1: Update the view**

In the filter `GridPane`, row 1, after the Delivery Status combo:

```xml
        <Label text="Type" GridPane.columnIndex="4" GridPane.rowIndex="1"/>
        <ComboBox fx:id="smsTypeComboBox" maxWidth="Infinity" GridPane.columnIndex="5" GridPane.rowIndex="1"/>
```

In the table, after `dateTimeColumn`:

```xml
            <TableColumn fx:id="smsTypeColumn" prefWidth="90" text="Type"/>
```

- [ ] **Step 2: Update the controller**

```java
    @FXML private ComboBox<String> smsTypeComboBox;
    @FXML private TableColumn<SmsLogDto, String> smsTypeColumn;
```

`configureFilters()` — add:

```java
        smsTypeComboBox.setItems(FXCollections.observableArrayList(STATUS_ALL, SmsType.RECEIPT.label(),
                SmsType.OFFICIAL_MESSAGE.label()));
        smsTypeComboBox.setValue(STATUS_ALL);
```

`handleRefresh()` — add `smsTypeComboBox.setValue(STATUS_ALL);`.

`configureTable()` — add:

```java
        smsTypeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getSmsType().label()));
```

`buildCriteria(int)` — before `criteria.setSearchText(...)`:

```java
        if (SmsType.RECEIPT.label().equals(smsTypeComboBox.getValue())) {
            criteria.setSmsType(SmsType.RECEIPT);
        } else if (SmsType.OFFICIAL_MESSAGE.label().equals(smsTypeComboBox.getValue())) {
            criteria.setSmsType(SmsType.OFFICIAL_MESSAGE);
        }
```

`sendDetailsGrid(SmsLogDto)` — after the "Resend Reason" row (index 14):

```java
        addDetailRow(grid, 15, "SMS Type", log.getSmsType().label());
        addDetailRow(grid, 16, "Batch", nullToDash(log.getBatchUuid()));
```

Import `com.churchmanagement.enums.SmsType`.

- [ ] **Step 3: Compile and check**

Run: `mvn -q compile` then `mvn -q test -Dtest=SmsLogRepositoryTest,SmsLogServiceTest`
Expected: PASS. Manually: SMS Logs shows a Type column; filtering "Official" lists only the rows queued in Task 10's manual check; the details dialog shows SMS Type and Batch; Receipt No shows "-" for them.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/sms-log-view.fxml src/main/java/com/churchmanagement/controller/SmsLogController.java
git commit -m "feat(sms): show and filter SMS type in SMS Logs" -m "SMS Logs gets a Type column (Receipt / Official), a Type filter, and SMS Type and Batch rows in the details dialog, so official messages can be tracked per send." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: User manuals (EN + SI)

**Files:**
- Modify: `docs/user-manual.md` (new §14.7 after §14.6 "Required Permissions", ~line 992; one line in §15.4 SMS Settings)
- Modify: `docs/user-manual-si.md` (matching §14.7 and §15.4 line)

- [ ] **Step 1: English section**

Append after §14.6 in `docs/user-manual.md`:

```markdown
### 14.7 Sending Official Messages

Use **Send Message** (sidebar, under SMS Logs) to send an official notice to branches.

1. **Choose branches.** Pick a region (or *All regions*) and optionally search by code or name. Tick branches one by one, or click **Select all shown** to tick every branch the list shows. Ticks are kept when you change the region, so you can combine regions. **Clear** removes all ticks.
2. Branches marked *No mobile number* or *Invalid number* cannot be ticked. Fix the number on the branch first (Churches → Edit → SMS Mobile Number).
3. **Type the message** in English. The counter shows the length and how many SMS parts it uses (1 part up to 160 characters; longer messages use up to 3 parts / 459 characters). The footer from Settings is added automatically and is counted. Sinhala, Tamil and emoji cannot be sent — the counter names the first character that is not allowed.
4. The line below shows the **total SMS** that will be sent (branches × parts). Each part is charged as one SMS.
5. Click **Send** and confirm. The messages are queued and sent by the server machine one after another. Track them in **SMS Logs** — set **Type** to *Official*.

**Note:** if a long message fails part-way and is resent, the branch may receive the first part(s) twice.

**Permissions:** *View Send Message menu* to open the screen; *Send official SMS messages* to send.
```

In §15.4 SMS Settings add a bullet:

```markdown
- **Official message footer** — text added on its own line to every official message (e.g. `- Head Office`). Up to 100 English characters; leave blank for none. It counts toward the message length.
```

Add `14.7` to any per-chapter list if the manual has one (the top Table of Contents lists chapters only — no change there).

- [ ] **Step 2: Sinhala section**

Append after §14.6 in `docs/user-manual-si.md`:

```markdown
### 14.7 නිල පණිවිඩ යැවීම

ශාඛාවලට නිල දැනුම්දීමක් යැවීමට **Send Message** (පැති මෙනුවේ, SMS Logs යටතේ) භාවිත කරන්න.

1. **ශාඛා තෝරන්න.** ප්‍රදේශයක් (හෝ *All regions*) තෝරා, අවශ්‍ය නම් කේතය හෝ නමින් සොයන්න. ශාඛා එකින් එක ලකුණු කරන්න, නැතහොත් ලැයිස්තුවේ පෙනෙන සියලු ශාඛා ලකුණු කිරීමට **Select all shown** ක්ලික් කරන්න. ප්‍රදේශය වෙනස් කළත් ලකුණු ඉවත් නොවේ. **Clear** සියලු ලකුණු ඉවත් කරයි.
2. *No mobile number* හෝ *Invalid number* ලෙස පෙන්වන ශාඛා ලකුණු කළ නොහැක. පළමුව ශාඛාවේ අංකය නිවැරදි කරන්න (Churches → Edit → SMS Mobile Number).
3. **පණිවිඩය ඉංග්‍රීසියෙන් ටයිප් කරන්න.** ගණකය දිග සහ SMS කොටස් ගණන පෙන්වයි (අකුරු 160 දක්වා කොටස් 1; දිගු පණිවිඩ කොටස් 3 / අකුරු 459 දක්වා). Settings හි පාදකය (footer) ස්වයංක්‍රීයව එකතු වන අතර එයද ගණනය වේ. සිංහල, දෙමළ සහ emoji යැවිය නොහැක — ඉඩ නොදෙන පළමු අකුර ගණකය පෙන්වයි.
4. පහළ පේළියේ යවන **මුළු SMS ගණන** (ශාඛා × කොටස්) පෙන්වයි. සෑම කොටසක්ම එක් SMS එකක් ලෙස අය කෙරේ.
5. **Send** ක්ලික් කර තහවුරු කරන්න. පණිවිඩ පෝලිමට එක් වී සේවාදායක පරිගණකය මගින් එකින් එක යවනු ලැබේ. **SMS Logs** හි **Type** *Official* ලෙස සකසා ඒවා පරීක්ෂා කරන්න.

**සටහන:** දිගු පණිවිඩයක් අතරමගදී අසාර්ථක වී නැවත යැවුවහොත්, ශාඛාවට පළමු කොටස(කොටස්) දෙවරක් ලැබිය හැක.

**අවසර:** තිරය විවෘත කිරීමට *View Send Message menu*; යැවීමට *Send official SMS messages*.
```

In §15.4 add:

```markdown
- **Official message footer** — සෑම නිල පණිවිඩයකටම වෙනම පේළියක එකතු වන පාඨය (උදා. `- Head Office`). ඉංග්‍රීසි අකුරු 100 දක්වා; අවශ්‍ය නැත්නම් හිස්ව තබන්න. එය පණිවිඩයේ දිගට ගණනය වේ.
```

- [ ] **Step 3: Commit**

```bash
git add docs/user-manual.md docs/user-manual-si.md
git commit -m "docs(manual): sending official messages (EN + SI)" -m "Adds section 14.7 Sending Official Messages and the Official message footer setting to both user manuals, including the 3-part / 459-character limit, English-only rule, per-part SMS cost, tracking in SMS Logs, and the duplicate-part-on-resend note." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Final verification

- [ ] **Step 1: Full test suite**

Run: `mvn -q test`
Expected: all tests PASS (record the count in the hand-off).

- [ ] **Step 2: Migration on a dev database**

Start the app on the primary (migrations-running) machine against a dev copy of the database. Confirm Flyway applies V41, `SHOW COLUMNS FROM sms_logs` lists `sms_type` and `batch_uuid`, existing rows are `RECEIPT`, and Admin has both new permissions.

- [ ] **Step 3: Real dongle check (primary machine, SIM_DONGLE gateway)**

1. Send a 1-part official message (~100 characters) to two branches whose numbers are test phones → both arrive.
2. Send a 3-part message (~400 characters) → each phone shows **one joined message**; SMS Logs shows three comma-joined modem refs.
3. Submit a receipt → its SMS still arrives (text mode restored after PDU sends).

If step 2 arrives as separate or garbled parts, stop and report — the fallback (split into independent text-mode SMS with a "(1/3)" prefix) needs a decision from the user.

- [ ] **Step 4: Hand-off**

Report test results and the manual checks done/not done. Integration of the branch is decided with the user (superpowers:finishing-a-development-branch).
