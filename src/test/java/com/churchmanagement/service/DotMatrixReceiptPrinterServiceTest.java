package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class DotMatrixReceiptPrinterServiceTest {
    private static final double DELTA = 0.01;

    @Test
    void printWithoutAReportFailsBeforeLookingUpAPrinter() {
        DotMatrixReceiptPrinterService service = new DotMatrixReceiptPrinterService(
                () -> fail("must not look up a printer when there is nothing to print"),
                fixedClock());

        PrintResult result = service.print(null);

        assertFalse(result.isSuccess());
        assertEquals("A receipt to print is required.", result.getMessage());
    }

    @Test
    void printWithoutADefaultPrinterFailsWithAFriendlyMessage() {
        DotMatrixReceiptPrinterService service = new DotMatrixReceiptPrinterService(() -> null, fixedClock());

        PrintResult result = service.print(new JasperPrint());

        assertFalse(result.isSuccess());
        assertEquals("No default printer is configured on this computer.", result.getMessage());
    }

    @Test
    void pageFormatMatchesTheReceiptPadWithinItsPhysicalMargins() {
        PageFormat pageFormat = DotMatrixReceiptPrinterService.buildPageFormat();

        assertEquals(342.0, pageFormat.getWidth(), DELTA, "4.75in receipt width");
        assertEquals(396.0, pageFormat.getHeight(), DELTA, "5.5in receipt height");

        Rectangle2D imageableArea = new Rectangle2D.Double(
                pageFormat.getImageableX(), pageFormat.getImageableY(),
                pageFormat.getImageableWidth(), pageFormat.getImageableHeight());
        double leftRightMarginPoints = 1.2 * 72.0 / 2.54;
        double topMarginPoints = 0.4 * 72.0 / 2.54;
        double bottomMarginPoints = 0.6 * 72.0 / 2.54;
        assertEquals(leftRightMarginPoints, imageableArea.getX(), DELTA, "1.2cm left margin");
        assertEquals(topMarginPoints, imageableArea.getY(), DELTA, "0.4cm top margin");
        assertEquals(342.0 - 2 * leftRightMarginPoints, imageableArea.getWidth(), DELTA);
        assertEquals(396.0 - topMarginPoints - bottomMarginPoints, imageableArea.getHeight(), DELTA);
    }

    @Test
    void describePageFormatReportsPaperSizeAndImageableAreaInPoints() {
        String description = DotMatrixReceiptPrinterService.describePageFormat(
                DotMatrixReceiptPrinterService.buildPageFormat());

        // Whatever the printer driver actually validated our requested page format down to -
        // the ground truth for diagnosing clipped content, since PrinterJob.validatePage(...)
        // can silently shrink a custom Paper to whatever the driver/media size supports.
        assertEquals("page=342.0x396.0pt, imageable=[x=34.0,y=11.3,w=274.0,h=367.7]pt", description);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }
}
