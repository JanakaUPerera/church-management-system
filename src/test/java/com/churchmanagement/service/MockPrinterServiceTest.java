package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPrinterServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void printExistingPdfReturnsSuccess() throws Exception {
        Path pdf = tempDir.resolve("receipt.pdf");
        Files.writeString(pdf, "fake pdf content");

        PrintResult result = service().printPdf(pdf.toString());

        assertTrue(result.isSuccess());
        assertEquals("Mock Printer", result.getPrinterName());
        assertEquals("Print simulated successfully.", result.getMessage());
        assertEquals("2026-05-18T09:00", result.getPrintedAt().toString());
    }

    @Test
    void printMissingPdfReturnsFailure() {
        PrintResult result = service().printPdf(tempDir.resolve("missing.pdf").toString());

        assertFalse(result.isSuccess());
        assertEquals("PDF file does not exist.", result.getMessage());
        assertEquals("Mock Printer", result.getPrinterName());
    }

    @Test
    void printEmptyPathReturnsFailure() {
        PrintResult result = service().printPdf(" ");

        assertFalse(result.isSuccess());
        assertEquals("PDF file path is required.", result.getMessage());
        assertEquals("Mock Printer", result.getPrinterName());
    }

    private MockPrinterService service() {
        return new MockPrinterService(Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC")));
    }
}
