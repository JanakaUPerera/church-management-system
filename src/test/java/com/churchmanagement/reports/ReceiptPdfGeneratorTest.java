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
        assertEquals("2026-Jan-12   Total Amount: 750.00", parameters.get("dateReceived"));
        assertEquals("REC26000001", parameters.get("receiptNo"));
        assertEquals("Admin", parameters.get("issuedBy"));
        assertEquals("", parameters.get("cancelledWatermark"));
    }

    @Test
    void generatePdfParametersFoldOtherDonationsIntoOfferingsRowAsCompactAddition() {
        ReceiptResponseDto withOtherDonations = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH);
        withOtherDonations.setItems(List.of(
                new ReceiptItemDto(CollectionType.TITHES, new BigDecimal("500.00"), null),
                new ReceiptItemDto(CollectionType.OFFERTORY, new BigDecimal("250.00"), null),
                new ReceiptItemDto(CollectionType.OTHER_DONATIONS, new BigDecimal("100.00"), null)
        ));

        // Purely numeric ("250.00 + 100.00") rather than spelling out the Other Donations label:
        // the Offerings column is only ~124pt wide, too narrow in every language - especially the
        // longer Sinhala/Tamil translations - to fit a label without pushing the amount off the
        // edge, so it doesn't vary by language.
        assertEquals("250.00 + 100.00", generator.parameters(withOtherDonations).get("offeringsAmount"));

        withOtherDonations.setReceiptLanguage(ReceiptLanguage.SINHALA);
        assertEquals("250.00 + 100.00", generator.parameters(withOtherDonations).get("offeringsAmount"));

        withOtherDonations.setReceiptLanguage(ReceiptLanguage.TAMIL);
        assertEquals("250.00 + 100.00", generator.parameters(withOtherDonations).get("offeringsAmount"));
    }

    @Test
    void generatePdfParametersOmitOtherDonationsFromOfferingsRowWhenNotPresent() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        assertEquals("250.00", parameters.get("offeringsAmount"));
    }

    @Test
    void generatePdfParametersPlaceWeekRangeOnItsOwnLineUnderChurchServiceDate() {
        Map<String, Object> parameters = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));

        assertEquals("2026-Jan-11", parameters.get("churchServiceDate"));
        assertEquals("Week: 2026-Jan-05 - 2026-Jan-11", parameters.get("collectionWeek"));
    }

    @Test
    void generatePdfParametersFoldTotalIntoDateReceivedRowInReceiptLanguage() {
        Map<String, Object> english = generator.parameters(receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.ENGLISH));
        assertEquals("2026-Jan-12   Total Amount: 750.00", english.get("dateReceived"));

        ReceiptResponseDto sinhalaReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.SINHALA);
        Map<String, Object> sinhala = generator.parameters(sinhalaReceipt);
        assertEquals("2026-Jan-12   මුළු මුදල: 750.00", sinhala.get("dateReceived"));

        ReceiptResponseDto tamilReceipt = receipt(ReceiptStatus.ACTIVE, ReceiptLanguage.TAMIL);
        Map<String, Object> tamil = generator.parameters(tamilReceipt);
        assertEquals("2026-Jan-12   மொத்த தொகை: 750.00", tamil.get("dateReceived"));
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
