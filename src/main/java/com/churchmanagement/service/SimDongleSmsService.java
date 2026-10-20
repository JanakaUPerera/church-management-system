package com.churchmanagement.service;

import com.churchmanagement.dto.SmsParsedResponse;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.util.SmsLengthCalculator;
import com.churchmanagement.util.SmsModemResponseParser;
import com.churchmanagement.util.SmsPduEncoder;
import com.churchmanagement.util.SriLankanMobileNumber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SimDongleSmsService implements SmsService {
    public static final String PROVIDER = "SIM Dongle";
    static final int COMMAND_TIMEOUT_MILLIS = 5_000;
    static final int SMS_SEND_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_TEXT_MODE_LENGTH = 160;
    private static final byte CTRL_Z = 0x1A;

    private final SmsSettingsRepository smsSettingsRepository;
    private final SerialPortService serialPortService;
    private final Clock clock;
    private final SmsModemResponseParser responseParser = new SmsModemResponseParser();
    private final AtomicInteger concatReference = new AtomicInteger();

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
        boolean pduMode = requiresPduMode(message);
        if (!pduMode && message.length() > MAX_TEXT_MODE_LENGTH) {
            return failed("SMS message is too long. Maximum allowed length is 160 characters for text mode.");
        }
        if (pduMode && SmsLengthCalculator.septetLength(message) > SmsLengthCalculator.MAX_SEPTETS) {
            return failed("SMS message is too long. Maximum allowed length is "
                    + SmsLengthCalculator.MAX_SEPTETS + " characters.");
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
            return pduMode
                    ? sendPduMode(connection, normalizedNumber, message)
                    : sendTextMode(connection, normalizedNumber, message);
        } catch (SmsTimeoutException exception) {
            return failed("SMS sending timed out.");
        } catch (IOException exception) {
            return failed("SMS sending failed.");
        }
    }

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

    public boolean enableDeliveryReports() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        if (settings.getComPort() == null || settings.getComPort().isBlank()) {
            return false;
        }
        int baudRate = settings.getBaudRate() == null ? 9600 : settings.getBaudRate();
        try (SerialPortService.SerialConnection connection = serialPortService.openConnection(
                settings.getComPort().strip(), baudRate, COMMAND_TIMEOUT_MILLIS)) {
            return connection != null && enableDeliveryReports(connection);
        } catch (IOException exception) {
            return false;
        }
    }

    String normalizeSriLankanMobileNumber(String mobileNumber) {
        return SriLankanMobileNumber.normalize(mobileNumber);
    }

    boolean isValidSriLankanMobileNumber(String mobileNumber) {
        return SriLankanMobileNumber.isValid(mobileNumber);
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
        return new SmsResult(false, message, PROVIDER, null, SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED,
                null, null, null, message);
    }

    private SmsResult modemFailure(String friendlyMessage, String rawResponse) {
        if (rawResponse != null && !rawResponse.isBlank()) {
            System.err.println("SIM dongle SMS failed. Response: " + rawResponse.strip());
        }
        SmsParsedResponse parsedResponse = responseParser.parseSendResponse(rawResponse);
        return new SmsResult(false, friendlyMessage, PROVIDER, null, SmsSendStatus.FAILED,
                SmsDeliveryStatus.FAILED, null, parsedResponse.getModemRawResponse(),
                parsedResponse.getErrorCode(), friendlyMessage);
    }

    private boolean containsOk(String response) {
        return response != null && response.toUpperCase(java.util.Locale.ROOT).contains("OK");
    }

    private boolean containsError(String response) {
        if (response == null) {
            return false;
        }
        String upper = response.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("ERROR")
                || upper.contains("+CMS ERROR")
                || upper.contains("+CME ERROR")
                || upper.contains("NO CARRIER")
                || upper.contains("NO DIALTONE");
    }

    private boolean enableDeliveryReports(SerialPortService.SerialConnection connection) throws IOException {
        String csmpResponse = sendCommand(connection, "AT+CSMP=49,167,0,0", COMMAND_TIMEOUT_MILLIS);
        if (!containsOk(csmpResponse)) {
            return false;
        }
        String cnmiResponse = sendCommand(connection, "AT+CNMI=2,1,0,1,0", COMMAND_TIMEOUT_MILLIS);
        return containsOk(cnmiResponse);
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
