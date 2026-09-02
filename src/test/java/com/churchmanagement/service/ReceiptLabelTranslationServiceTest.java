package com.churchmanagement.service;

import com.churchmanagement.enums.ReceiptLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptLabelTranslationServiceTest {
    private final ReceiptLabelTranslationService service = new ReceiptLabelTranslationService();

    @Test
    void englishLabelReturnsEnglish() {
        assertEquals("Collection Receipt", service.label("receipt_title", ReceiptLanguage.ENGLISH));
    }

    @Test
    void sinhalaLabelReturnsSinhala() {
        assertEquals("රිසිට් අංකය", service.label("receipt_no", ReceiptLanguage.SINHALA));
    }

    @Test
    void tamilLabelReturnsTamil() {
        assertEquals("மொத்த தொகை", service.label("total_amount", ReceiptLanguage.TAMIL));
    }

    @Test
    void othersLabelIsTranslatedPerLanguage() {
        assertEquals("Others", service.label("others", ReceiptLanguage.ENGLISH));
        assertEquals("වෙනත්", service.label("others", ReceiptLanguage.SINHALA));
        assertEquals("மற்றவை", service.label("others", ReceiptLanguage.TAMIL));
    }

    @Test
    void totalLabelIsTranslatedPerLanguage() {
        assertEquals("Total", service.label("total", ReceiptLanguage.ENGLISH));
        assertEquals("එකතුව", service.label("total", ReceiptLanguage.SINHALA));
        assertEquals("மொத்தம்", service.label("total", ReceiptLanguage.TAMIL));
    }

    @Test
    void missingKeyFallsBackToEnglishKeyValue() {
        assertEquals("unknown_key", service.label("unknown_key", ReceiptLanguage.SINHALA));
    }

    @Test
    void nullLanguageFallsBackToEnglish() {
        assertEquals("Church Name", service.label("church_name", null));
    }
}
