package com.churchmanagement.reports;

import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.service.ReceiptFontService;
import com.churchmanagement.service.ReceiptLabelTranslationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptPdfGeneratorTest {
    private final ReceiptPdfGenerator generator = new ReceiptPdfGenerator(null, null, null, Clock.systemUTC(),
            new ReceiptFontService(), new ReceiptLabelTranslationService());

    @Test
    void generatePdfParametersPlaceReceiptValues() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        assertEquals("Treasurer", parameters.get("receivedFrom"));
        assertEquals("Main Church", parameters.get("branchChurch"));
        assertEquals("CH001", parameters.get("number"));
        assertEquals("500.00", parameters.get("titheAmount"));
        assertEquals("250.00", parameters.get("offeringsAmount"));
        assertEquals("-", parameters.get("otherDonationsAmount"));
        assertEquals("750.00", parameters.get("totalAmount"));
        assertEquals("REC26000001", parameters.get("receiptNo"));
        assertEquals("Admin", parameters.get("issuedBy"));
        assertEquals("", parameters.get("cancelledWatermark"));
    }

    @Test
    void generatePdfParametersUseSingleLanguageLabelMatchingReceiptLanguage() {
        Map<String, Object> english = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));
        assertEquals("Other Donations", english.get("otherDonationsLabel"));
        assertEquals("Total Amount", english.get("totalLabel"));
        assertEquals(Boolean.TRUE, english.get("receiptLanguageIsEnglish"));
        assertEquals(Boolean.FALSE, english.get("receiptLanguageIsSinhala"));
        assertEquals(Boolean.FALSE, english.get("receiptLanguageIsTamil"));

        Map<String, Object> sinhala = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.SINHALA));
        assertEquals("වෙනත් පරිත්‍යාග", sinhala.get("otherDonationsLabel"));
        assertEquals("මුළු මුදල", sinhala.get("totalLabel"));
        assertEquals(Boolean.TRUE, sinhala.get("receiptLanguageIsSinhala"));
        assertEquals(Boolean.FALSE, sinhala.get("receiptLanguageIsEnglish"));

        Map<String, Object> tamil = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.TAMIL));
        assertEquals("மற்ற நன்கொடைகள்", tamil.get("otherDonationsLabel"));
        assertEquals("மொத்த தொகை", tamil.get("totalLabel"));
        assertEquals(Boolean.TRUE, tamil.get("receiptLanguageIsTamil"));
        assertEquals(Boolean.FALSE, tamil.get("receiptLanguageIsEnglish"));
    }

    @Test
    void generatePdfParametersShowCancelledWatermarkOnlyWhenCancelled() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.CANCELLED, ReceiptLanguage.ENGLISH));

        assertEquals("CANCELLED", parameters.get("cancelledWatermark"));
    }

    private ReceiptResponseDto receipt(ReceiptStatus status, ReceiptLanguage language) {
        ReceiptResponseDto receipt = new ReceiptResponseDto();
        receipt.setReceiptLanguage(language);
        receipt.setReceiptNo("REC26000001");
        receipt.setChurchCode("CH001");
        receipt.setChurchName("Main Church");
        receipt.setRegionCode("REG001");
        receipt.setRegionName("Colombo");
        receipt.setWeekStartDate(LocalDate.of(2026, 1, 5));
        receipt.setWeekEndDate(LocalDate.of(2026, 1, 11));
        receipt.setChurchServiceDate(LocalDate.of(2026, 1, 11));
        receipt.setReceiptDateTime(LocalDateTime.of(2026, 1, 12, 9, 30));
        receipt.setSubmittedByName("Treasurer");
        receipt.setIssuedByFullName("Admin");
        receipt.setStatus(status);
        receipt.setTotalAmount(new BigDecimal("750.00"));
        receipt.setItems(List.of(
                new ReceiptItemDto(CollectionType.TITHES, new BigDecimal("500.00"), null),
                new ReceiptItemDto(CollectionType.OFFERTORY, new BigDecimal("250.00"), null)
        ));
        return receipt;
    }
}
