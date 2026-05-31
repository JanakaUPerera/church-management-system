package com.churchmanagement.service;

import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.reports.ReceiptPdfGenerator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptFontServiceTest {
    @Test
    void selectsFontForReceiptLanguage() {
        ReceiptFontService service = new ReceiptFontService();

        assertEquals(ReceiptFontService.NOTO_SANS, service.fontFor(ReceiptLanguage.ENGLISH));
        assertEquals(ReceiptFontService.NOTO_SANS_SINHALA, service.fontFor(ReceiptLanguage.SINHALA));
        assertEquals(ReceiptFontService.NOTO_SANS_TAMIL, service.fontFor(ReceiptLanguage.TAMIL));
    }

    @Test
    void unicodeFontTestPdfEmbedsIdentityHFonts() throws Exception {
        ReceiptPdfGenerator generator = new ReceiptPdfGenerator(null, null, null, Clock.systemUTC(),
                new ReceiptFontService());

        Path pdf = Path.of(generator.generateUnicodeFontTestPdf());
        String pdfText = Files.readString(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(Files.size(pdf) > 0);
        assertTrue(pdfText.contains("/Identity-H"));
        assertTrue(pdfText.contains("/FontFile2"));
        assertTrue(pdfText.contains("NotoSans"));
        assertTrue(pdfText.contains("NotoSansSinhala"));
        assertTrue(pdfText.contains("NotoSansTamil"));
    }
}
