package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.repository.SmsSettingsRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimDongleSmsServiceTest {
    @Test
    void rejectEmptyMobileNumber() {
        SmsResult result = service(settings(true, "COM3", 9600), new FakeSerialPortService()).sendSms(" ", "Hello");

        assertFalse(result.isSuccess());
        assertEquals("Mobile number is required.", result.getMessage());
    }

    @Test
    void normalizeLocalMobileNumber() {
        SimDongleSmsService service = service(settings(true, "COM3", 9600), new FakeSerialPortService());

        assertEquals("+94771234567", service.normalizeSriLankanMobileNumber("0771234567"));
    }

    @Test
    void rejectInvalidMobileNumber() {
        SmsResult result = service(settings(true, "COM3", 9600), new FakeSerialPortService()).sendSms("0111234567",
                "Hello");

        assertFalse(result.isSuccess());
        assertEquals("Invalid mobile number.", result.getMessage());
    }

    @Test
    void rejectEmptyMessage() {
        SmsResult result = service(settings(true, "COM3", 9600), new FakeSerialPortService()).sendSms("0771234567",
                " ");

        assertFalse(result.isSuccess());
        assertEquals("SMS message is required.", result.getMessage());
    }

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

    @Test
    void failedWhenComPortNotConfigured() {
        SmsResult result = service(settings(true, " ", 9600), new FakeSerialPortService()).sendSms("0771234567",
                "Hello");

        assertFalse(result.isSuccess());
        assertEquals("SIM dongle COM port is not configured.", result.getMessage());
    }

    @Test
    void sendsTextModeSmsWithCmgs() {
        FakeSerialPortService serialPortService = new FakeSerialPortService(
                "\r\nOK\r\n",
                "\r\nOK\r\n",
                "\r\nOK\r\n",
                "\r\nOK\r\n",
                "\r\nOK\r\n",
                "\r\n> ",
                "\r\n+CMGS: 23\r\nOK\r\n");

        SmsResult result = service(settings(true, "COM3", 9600), serialPortService).sendSms("0771234567", "Hello");

        assertTrue(result.isSuccess());
        assertEquals(SimDongleSmsService.PROVIDER, result.getProvider());
        assertEquals(LocalDateTime.of(2026, 5, 18, 9, 0), result.getSentAt());
        assertEquals(SmsDeliveryStatus.UNKNOWN, result.getDeliveryStatus());
        assertEquals("23", result.getModemMessageReference());
        assertEquals("AT\rAT+CMGF=1\rAT+CSCS=\"GSM\"\rAT+CSMP=49,167,0,0\rAT+CNMI=2,1,0,1,0\rAT+CMGS=\"+94771234567\"\rHello\u001A",
                serialPortService.connection.outputText());
        assertTrue(serialPortService.connection.closed);
    }

    private SimDongleSmsService service(SmsSettings settings, SerialPortService serialPortService) {
        return new SimDongleSmsService(new FakeSmsSettingsRepository(settings), serialPortService,
                Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC")));
    }

    private SmsSettings settings(boolean enabled, String comPort, Integer baudRate) {
        SmsSettings settings = new SmsSettings();
        settings.setSmsEnabled(enabled);
        settings.setGatewayType(SmsSettings.GatewayType.SIM_DONGLE);
        settings.setComPort(comPort);
        settings.setBaudRate(baudRate);
        return settings;
    }

    private static class FakeSmsSettingsRepository extends SmsSettingsRepository {
        private final SmsSettings settings;

        private FakeSmsSettingsRepository(SmsSettings settings) {
            super((DataSource) null);
            this.settings = settings;
        }

        @Override
        public SmsSettings getSettings() {
            return settings;
        }
    }

    private static class FakeSerialPortService extends SerialPortService {
        private FakeSerialConnection connection;
        private final String[] responses;

        private FakeSerialPortService(String... responses) {
            this.responses = responses;
        }

        @Override
        public SerialConnection openConnection(String systemPortName, int baudRate, int timeoutMillis) {
            connection = new FakeSerialConnection(responses);
            return connection;
        }
    }

    private static class FakeSerialConnection implements SerialPortService.SerialConnection {
        private final StagedInputStream input = new StagedInputStream();
        private final OutputStream output;
        private boolean closed;

        private FakeSerialConnection(String... responses) {
            output = new StagedOutputStream(input, responses);
        }

        @Override
        public InputStream inputStream() {
            return input;
        }

        @Override
        public OutputStream outputStream() {
            return output;
        }

        @Override
        public void close() {
            closed = true;
        }

        private String outputText() {
            return ((StagedOutputStream) output).outputText();
        }
    }

    private static class StagedInputStream extends InputStream {
        private final Queue<Byte> bytes = new ArrayDeque<>();

        @Override
        public int read() {
            return bytes.isEmpty() ? -1 : bytes.remove() & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (bytes.isEmpty()) {
                return -1;
            }
            int read = 0;
            while (read < length && !bytes.isEmpty()) {
                buffer[offset + read] = bytes.remove();
                read++;
            }
            return read;
        }

        @Override
        public int available() {
            return bytes.size();
        }

        private void addResponse(String response) {
            for (byte value : response.getBytes(StandardCharsets.US_ASCII)) {
                bytes.add(value);
            }
        }
    }

    private static class StagedOutputStream extends OutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final StagedInputStream input;
        private final String[] responses;
        private int responseIndex;

        private StagedOutputStream(StagedInputStream input, String[] responses) {
            this.input = input;
            this.responses = responses;
        }

        @Override
        public void write(int value) {
            output.write(value);
            if ((value == '\r' || value == 0x1A) && responseIndex < responses.length) {
                input.addResponse(responses[responseIndex++]);
            }
        }

        private String outputText() {
            return output.toString(StandardCharsets.US_ASCII);
        }
    }
}
