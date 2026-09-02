package com.churchmanagement.reports;

import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.service.ReceiptFontService;
import com.churchmanagement.service.ReceiptLabelTranslationService;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JRPrintImage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("2026-Jan-12", parameters.get("dateReceived"));
        assertEquals("REC26000001", parameters.get("receiptNo"));
        assertEquals("Admin", parameters.get("issuedBy"));
        assertEquals("", parameters.get("cancelledWatermark"));
    }

    @Test
    void generatePdfParametersShowOtherDonationsAsItsOwnRowUnderOfferingsWhenPresent() {
        ReceiptResponseDto withOtherDonations = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH);
        withOtherDonations.setItems(List.of(
                new ReceiptItemDto(CollectionType.TITHES, new BigDecimal("500.00"), null),
                new ReceiptItemDto(CollectionType.OFFERTORY, new BigDecimal("250.00"), null),
                new ReceiptItemDto(CollectionType.OTHER_DONATIONS, new BigDecimal("100.00"), null)
        ));

        // Offerings stays a plain figure - Other Donations gets its own "Others" row under it,
        // rather than riding along the Offerings row.
        Map<String, Object> english = generator.parameters(withOtherDonations);
        assertEquals("250.00", english.get("offeringsAmount"));
        assertEquals(Boolean.TRUE, english.get("hasOtherDonations"));
        assertEquals("Others", english.get("othersLabel"));
        assertEquals("100.00", english.get("othersValue"));

        withOtherDonations.setReceiptLanguage(ReceiptLanguage.SINHALA);
        assertEquals("වෙනත්", generator.parameters(withOtherDonations).get("othersLabel"));

        withOtherDonations.setReceiptLanguage(ReceiptLanguage.TAMIL);
        assertEquals("மற்றவை", generator.parameters(withOtherDonations).get("othersLabel"));
    }

    @Test
    void generatePdfParametersHideOtherDonationsRowWhenNotPresent() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        assertEquals("250.00", parameters.get("offeringsAmount"));
        assertEquals(Boolean.FALSE, parameters.get("hasOtherDonations"));
    }

    @Test
    void generatePdfParametersExposeTotalAsItsOwnLabelAndValuePerLanguage() {
        Map<String, Object> english = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));
        assertEquals("Total", english.get("totalLabel"));
        assertEquals("750.00", english.get("totalValue"));

        ReceiptResponseDto sinhalaReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.SINHALA);
        assertEquals("එකතුව", generator.parameters(sinhalaReceipt).get("totalLabel"));

        ReceiptResponseDto tamilReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.TAMIL);
        assertEquals("மொத்தம்", generator.parameters(tamilReceipt).get("totalLabel"));
    }

    @Test
    void generatePdfParametersPlaceWeekRangeOnItsOwnLineUnderChurchServiceDateTranslatedPerLanguage() {
        Map<String, Object> english = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));
        assertEquals("2026-Jan-11", english.get("churchServiceDate"));
        assertEquals("Week: 2026-Jan-05 - 2026-Jan-11", english.get("collectionWeek"));

        ReceiptResponseDto sinhalaReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.SINHALA);
        assertEquals("සතිය: 2026-Jan-05 - 2026-Jan-11", generator.parameters(sinhalaReceipt).get("collectionWeek"));

        ReceiptResponseDto tamilReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.TAMIL);
        assertEquals("வாரம்: 2026-Jan-05 - 2026-Jan-11", generator.parameters(tamilReceipt).get("collectionWeek"));
    }

    @Test
    void generatePdfParametersShowCancelledWatermarkOnlyWhenCancelled() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.CANCELLED, ReceiptLanguage.ENGLISH));

        assertEquals("CANCELLED", parameters.get("cancelledWatermark"));
    }

    @Test
    void generateTemporaryPdfWritesAThrowawayFileWithoutTrackingIt() throws Exception {
        String path = generator.generateTemporaryPdf(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        Path file = Path.of(path);
        assertTrue(Files.exists(file), "temporary preview PDF should exist");
        assertTrue(Files.size(file) > 0, "temporary preview PDF should not be empty");
        assertTrue(path.endsWith(".pdf"));
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                file.toAbsolutePath().getParent().toRealPath(),
                "must be written to the system temp folder, not the configured receipts folder");
    }

    @Test
    void renderPrintJasperPrintUsesTheFullReceiptPageWithNoBackgroundArtwork() {
        JasperPrint print = generator.renderPrintJasperPrint(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        assertEquals(342, print.getPageWidth());
        assertEquals(396, print.getPageHeight());
        assertTrue(print.getPages().get(0).getElements().stream().noneMatch(JRPrintImage.class::isInstance),
                "print-only report must not carry the receipt pad's background artwork");
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
