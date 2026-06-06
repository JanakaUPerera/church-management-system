package com.churchmanagement.service;

import com.churchmanagement.dto.AtCommandResult;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class AtCommandService {
    public static final List<String> TEST_COMMANDS = List.of("AT", "ATI", "AT+CSQ", "AT+CREG?", "AT+CMGF=1");
    private static final int COMMAND_TIMEOUT_MILLIS = 2_000;
    private static final int PROBE_TIMEOUT_MILLIS = 1_500;
    private static final int TEST_TIMEOUT_MILLIS = 10_000;

    private final SerialPortService serialPortService;

    public AtCommandService() {
        this(new SerialPortService());
    }

    public AtCommandService(SerialPortService serialPortService) {
        this.serialPortService = serialPortService;
    }

    public AtCommandResult testModem(String systemPortName, int baudRate) {
        if (systemPortName == null || systemPortName.isBlank()) {
            return failed("Select a COM port before testing.", "");
        }

        SerialPort port = serialPortService.openPort(systemPortName.strip(), baudRate, COMMAND_TIMEOUT_MILLIS);
        if (port == null) {
            return failed("Cannot open COM port.", "");
        }

        StringBuilder response = new StringBuilder();
        long testDeadline = System.currentTimeMillis() + TEST_TIMEOUT_MILLIS;
        try {
            for (String command : TEST_COMMANDS) {
                if (System.currentTimeMillis() >= testDeadline) {
                    throw new AtCommandTimeoutException();
                }
                response.append("> ").append(command).append(System.lineSeparator());
                String commandResponse = sendCommand(port, command, remainingTimeout(testDeadline));
                response.append(commandResponse).append(System.lineSeparator());
                if ("AT".equals(command) && !containsOk(commandResponse)) {
                    return failed("Modem not responding.", response.toString());
                }
            }
            return new AtCommandResult(true, "Modem detected.", response.toString(), TEST_COMMANDS);
        } catch (AtCommandTimeoutException exception) {
            return failed("AT command timeout.", response.toString());
        } catch (IOException exception) {
            return failed("Modem not responding.", response + exception.getMessage());
        } finally {
            port.closePort();
        }
    }

    public boolean isModemPort(String systemPortName, int baudRate) {
        return isModemPort(systemPortName, baudRate, PROBE_TIMEOUT_MILLIS);
    }

    public boolean isModemPort(String systemPortName, int baudRate, int timeoutMillis) {
        if (systemPortName == null || systemPortName.isBlank()) {
            return false;
        }

        int safeTimeoutMillis = Math.max(250, timeoutMillis);
        SerialPort port = serialPortService.openPort(systemPortName.strip(), baudRate, safeTimeoutMillis);
        if (port == null) {
            return false;
        }

        try {
            return containsOk(sendCommand(port, "AT", safeTimeoutMillis));
        } catch (IOException exception) {
            return false;
        } finally {
            port.closePort();
        }
    }

    private String sendCommand(SerialPort port, String command, int timeoutMillis) throws IOException {
        OutputStream outputStream = port.getOutputStream();
        InputStream inputStream = port.getInputStream();
        outputStream.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();

        long deadline = System.currentTimeMillis() + timeoutMillis;
        byte[] buffer = new byte[256];
        StringBuilder response = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            int available = inputStream.available();
            if (available > 0) {
                int read = inputStream.read(buffer, 0, Math.min(buffer.length, available));
                if (read > 0) {
                    response.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
                    if (containsOk(response.toString()) || containsError(response.toString())) {
                        return response.toString().strip();
                    }
                }
            } else {
                sleepBriefly();
            }
        }
        if (response.isEmpty()) {
            throw new AtCommandTimeoutException();
        }
        return response.toString().strip();
    }

    private int remainingTimeout(long deadline) throws AtCommandTimeoutException {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            throw new AtCommandTimeoutException();
        }
        return (int) Math.min(COMMAND_TIMEOUT_MILLIS, remaining);
    }

    private AtCommandResult failed(String message, String response) {
        return new AtCommandResult(false, message, response, TEST_COMMANDS);
    }

    private boolean containsOk(String response) {
        return response != null && response.toUpperCase(Locale.ROOT).contains("OK");
    }

    private boolean containsError(String response) {
        return response != null && response.toUpperCase(Locale.ROOT).contains("ERROR");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static class AtCommandTimeoutException extends IOException {
    }
}
