package com.churchmanagement.service;

import com.churchmanagement.dto.ComPortDto;
import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class SerialPortService {
    public List<ComPortDto> listAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(port -> new ComPortDto(
                        port.getDescriptivePortName(),
                        port.getPortDescription(),
                        port.getSystemPortName()))
                .toList();
    }

    SerialPort openPort(String systemPortName, int baudRate, int timeoutMillis) {
        SerialPort port = SerialPort.getCommPort(systemPortName);
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutMillis, timeoutMillis);
        return port.openPort() ? port : null;
    }

    public SerialConnection openConnection(String systemPortName, int baudRate, int timeoutMillis) {
        SerialPort port = openPort(systemPortName, baudRate, timeoutMillis);
        return port == null ? null : new JSerialCommConnection(port);
    }

    public interface SerialConnection extends AutoCloseable {
        InputStream inputStream();

        OutputStream outputStream();

        @Override
        void close();
    }

    private record JSerialCommConnection(SerialPort port) implements SerialConnection {
        @Override
        public InputStream inputStream() {
            return port.getInputStream();
        }

        @Override
        public OutputStream outputStream() {
            return port.getOutputStream();
        }

        @Override
        public void close() {
            port.closePort();
        }
    }
}
