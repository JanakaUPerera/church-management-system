package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

public class MockPrinterService implements PrinterService {
    public static final String PRINTER_NAME = "Mock Printer";

    private final Clock clock;

    public MockPrinterService() {
        this(Clock.systemDefaultZone());
    }

    public MockPrinterService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PrintResult printPdf(String pdfFilePath) {
        if (pdfFilePath == null || pdfFilePath.isBlank()) {
            return failure("PDF file path is required.");
        }

        if (!Files.exists(Path.of(pdfFilePath))) {
            return failure("PDF file does not exist.");
        }

        return new PrintResult(true, "Print simulated successfully.", PRINTER_NAME, LocalDateTime.now(clock));
    }

    private PrintResult failure(String message) {
        return new PrintResult(false, message, PRINTER_NAME, LocalDateTime.now(clock));
    }
}
