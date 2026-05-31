package com.churchmanagement.reports;

import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.service.ReceiptFontService;
import com.churchmanagement.service.ReceiptLabelTranslationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptPdfGeneratorTest {
    private final ReceiptPdfGenerator generator = new ReceiptPdfGenerator(null, null, null, Clock.systemUTC(),
            new ReceiptFontService(), new ReceiptLabelTranslationService());

    @Test
    void generatePdfParametersForEnglishChurch() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptLanguage.ENGLISH));

        assertEquals("Collection Receipt", parameters.get("PARAM_RECEIPT_TITLE"));
        assertEquals("Receipt No", parameters.get("PARAM_RECEIPT_NO_LABEL"));
        assertEquals("Church Name", parameters.get("PARAM_CHURCH_NAME_LABEL"));
        assertEquals("Total Amount", parameters.get("PARAM_TOTAL_AMOUNT_LABEL"));
    }

    @Test
    void generatePdfParametersForSinhalaChurch() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptLanguage.SINHALA));

        assertEquals("එකතු කිරීමේ රිසිට්පත", parameters.get("PARAM_RECEIPT_TITLE"));
        assertEquals("රිසිට් අංකය", parameters.get("PARAM_RECEIPT_NO_LABEL"));
        assertEquals("දේවස්ථානයේ නම", parameters.get("PARAM_CHURCH_NAME_LABEL"));
        assertEquals("මුළු මුදල", parameters.get("PARAM_TOTAL_AMOUNT_LABEL"));
    }

    @Test
    void generatePdfParametersForTamilChurch() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptLanguage.TAMIL));

        assertEquals("காணிக்கை ரசீது", parameters.get("PARAM_RECEIPT_TITLE"));
        assertEquals("ரசீது எண்", parameters.get("PARAM_RECEIPT_NO_LABEL"));
        assertEquals("தேவாலய பெயர்", parameters.get("PARAM_CHURCH_NAME_LABEL"));
        assertEquals("மொத்த தொகை", parameters.get("PARAM_TOTAL_AMOUNT_LABEL"));
    }

    private ReceiptResponseDto receipt(ReceiptLanguage language) {
        ReceiptResponseDto receipt = new ReceiptResponseDto();
        receipt.setReceiptLanguage(language);
        receipt.setReceiptNo("REC26000001");
        receipt.setChurchCode("CH001");
        receipt.setChurchName("Main Church");
        receipt.setRegionCode("REG001");
        receipt.setRegionName("Colombo");
        receipt.setSubmittedByName("Treasurer");
        receipt.setIssuedByFullName("Admin");
        return receipt;
    }
}
