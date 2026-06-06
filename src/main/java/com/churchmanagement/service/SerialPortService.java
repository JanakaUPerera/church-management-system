package com.churchmanagement.service;

import com.churchmanagement.dto.ComPortDto;
import com.fazecast.jSerialComm.SerialPort;

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
}
