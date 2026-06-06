package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

public class SimDongleSmsService implements SmsService {
    public static final String PROVIDER = "SIM Dongle";
    static final int COMMAND_TIMEOUT_MILLIS = 5_000;
    static final int SMS_SEND_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_TEXT_MODE_LENGTH = 160;
    private static final byte CTRL_Z = 0x1A;

    private final SmsSettingsRepository smsSettingsRepository;
    private final SerialPortService serialPortService;
    private final Clock clock;

    public SimDongleSmsService() {
        this(new SmsSettingsRepository(), new SerialPortService(), Clock.systemDefaultZone());
    }

    public SimDongleSmsService(SmsSettingsRepository smsSettingsRepository, SerialPortService serialPortService,
                               Clock clock) {
        this.smsSettingsRepository = smsSettingsRepository;
        this.serialPortService = serialPortService;
        this.clock = clock;
    }

    @Override
    public SmsResult sendSms(String mobileNumber, String message) {
        String normalizedNumber = normalizeSriLankanMobileNumber(mobileNumber);
        if (normalizedNumber == null) {
            return failed("Mobile number is required.");
        }
        if (!isValidSriLankanMobileNumber(normalizedNumber)) {
            return failed("Invalid mobile number.");
        }
        if (message == null || message.isBlank()) {
            return failed("SMS message is required.");
        }
        if (message.length() > MAX_TEXT_MODE_LENGTH) {
            return failed("SMS message is too long. Maximum allowed length is 160 characters for text mode.");
        }

        SmsSettings settings = smsSettingsRepository.getSettings();
        if (!settings.isSmsEnabled()) {
            return failed("SMS is disabled.");
        }
        if (settings.getGatewayType() != SmsSettings.GatewayType.SIM_DONGLE) {
            return failed("SMS gateway is not configured for SIM dongle.");
        }
        if (settings.getComPort() == null || settings.getComPort().isBlank()) {
            return failed("SIM dongle COM port is not configured.");
        }

        int baudRate = settings.getBaudRate() == null ? 9600 : settings.getBaudRate();
        try (SerialPortService.SerialConnection connection = serialPortService.openConnection(
                settings.getComPort().strip(), baudRate, COMMAND_TIMEOUT_MILLIS)) {
            if (connection == null) {
                return failed("Unable to open COM port.");
            }

            String atResponse = sendCommand(connection, "AT", COMMAND_TIMEOUT_MILLIS);
            if (!containsOk(atResponse)) {
                return modemFailure("Modem did not respond to AT command.", atResponse);
            }
            String textModeResponse = sendCommand(connection, "AT+CMGF=1", COMMAND_TIMEOUT_MILLIS);
            if (!containsOk(textModeResponse)) {
                return modemFailure("Unable to set SMS text mode.", textModeResponse);
            }
            String characterSetResponse = sendCommand(connection, "AT+CSCS=\"GSM\"", COMMAND_TIMEOUT_MILLIS);
            if (!containsOk(characterSetResponse)) {
                return modemFailure("Unable to set SMS text mode.", characterSetResponse);
            }
            String recipientResponse = sendCommand(connection, "AT+CMGS=\"" + normalizedNumber + "\"",
                    COMMAND_TIMEOUT_MILLIS, ">");
            if (!recipientResponse.contains(">")) {
                return modemFailure("Modem did not accept recipient number.", recipientResponse);
            }

            String sendResponse = sendMessage(connection, message);
            if (sendResponse == null || sendResponse.isBlank()) {
                return failed("SMS sending timed out.");
            }
            if (containsError(sendResponse)) {
                return modemFailure(specificFailureMessage(sendResponse), sendResponse);
            }
            if (sendResponse.contains("+CMGS:") && containsOk(sendResponse)) {
                return new SmsResult(true, "SMS sent successfully.", PROVIDER, LocalDateTime.now(clock));
            }
            return modemFailure("SMS sending failed.", sendResponse);
        } catch (SmsTimeoutException exception) {
            return failed("SMS sending timed out.");
        } catch (IOException exception) {
            return failed("SMS sending failed.");
        }
    }

    String normalizeSriLankanMobileNumber(String mobileNumber) {
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

    boolean isValidSriLankanMobileNumber(String mobileNumber) {
        return mobileNumber != null && mobileNumber.matches("\\+947\\d{8}");
    }

    private String sendCommand(SerialPortService.SerialConnection connection, String command, int timeoutMillis)
            throws IOException {
        return sendCommand(connection, command, timeoutMillis, null);
    }

    private String sendCommand(SerialPortService.SerialConnection connection, String command, int timeoutMillis,
                               String expectedPrompt) throws IOException {
        OutputStream outputStream = connection.outputStream();
        outputStream.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
        return readResponse(connection.inputStream(), timeoutMillis, expectedPrompt);
    }

    private String sendMessage(SerialPortService.SerialConnection connection, String message) throws IOException {
        OutputStream outputStream = connection.outputStream();
        outputStream.write(message.getBytes(StandardCharsets.US_ASCII));
        outputStream.write(CTRL_Z);
        outputStream.flush();
        return readResponse(connection.inputStream(), SMS_SEND_TIMEOUT_MILLIS, null);
    }

    private String readResponse(InputStream inputStream, int timeoutMillis, String expectedPrompt) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        byte[] buffer = new byte[256];
        StringBuilder response = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            int available = inputStream.available();
            if (available > 0) {
                int read = inputStream.read(buffer, 0, Math.min(buffer.length, available));
                if (read > 0) {
                    response.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
                    String responseText = response.toString();
                    if ((expectedPrompt != null && responseText.contains(expectedPrompt))
                            || containsOk(responseText)
                            || containsError(responseText)) {
                        return responseText.strip();
                    }
                }
            } else {
                sleepBriefly();
            }
        }
        if (response.isEmpty()) {
            throw new SmsTimeoutException();
        }
        return response.toString().strip();
    }

    private SmsResult failed(String message) {
        return new SmsResult(false, message, PROVIDER, null);
    }

    private SmsResult modemFailure(String friendlyMessage, String rawResponse) {
        if (rawResponse != null && !rawResponse.isBlank()) {
            System.err.println("SIM dongle SMS failed. Response: " + rawResponse.strip());
        }
        return failed(friendlyMessage);
    }

    private String specificFailureMessage(String response) {
        String upper = response == null ? "" : response.toUpperCase(Locale.ROOT);
        if (upper.contains("+CMS ERROR") || upper.contains("+CME ERROR")) {
            return "SIM card may not be inserted.";
        }
        if (upper.contains("NO CARRIER") || upper.contains("NO DIALTONE")) {
            return "Network signal may be weak.";
        }
        return "SMS sending failed.";
    }

    private boolean containsOk(String response) {
        return response != null && response.toUpperCase(Locale.ROOT).contains("OK");
    }

    private boolean containsError(String response) {
        if (response == null) {
            return false;
        }
        String upper = response.toUpperCase(Locale.ROOT);
        return upper.contains("ERROR")
                || upper.contains("+CMS ERROR")
                || upper.contains("+CME ERROR")
                || upper.contains("NO CARRIER")
                || upper.contains("NO DIALTONE");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static class SmsTimeoutException extends IOException {
    }
}
