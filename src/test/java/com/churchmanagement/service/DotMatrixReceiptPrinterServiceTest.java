package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JREmptyDataSource;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void printableCompensatesForThePrintPipelinesImplicitImageableAreaOffset() throws Exception {
        // Printable.print(graphics, pageFormat, pageIndex) hands us a Graphics2D whose (0,0) is
        // already translated to the imageable area's top-left in physical page space, and whose
        // clip is sized to the imageable area, not the full page - both applied by the real
        // print pipeline before our Printable ever runs, which is exactly what's emulated below
        // instead of a real PrinterJob (no physical printer needed to catch this). A report
        // element positioned at absolute page x=280 (inside the imageable area, which runs to
        // x=308) must still render at physical x=280, not at imageableX(34) + 280 = 314 -
        // pushed off the right edge of a 308pt-wide imageable area and clipped away entirely,
        // exactly what the right-column fields (Number/Offerings/Others/Total) did on the actual
        // Epson LQ-310 printout.
        PageFormat pageFormat = DotMatrixReceiptPrinterService.buildPageFormat();
        JasperPrint jasperPrint = fillProbeReport(280, 100, 20, 30);

        BufferedImage physicalPage = new BufferedImage(342, 396, BufferedImage.TYPE_INT_RGB);
        Graphics2D pageGraphics = physicalPage.createGraphics();
        pageGraphics.setColor(Color.WHITE);
        pageGraphics.fillRect(0, 0, 342, 396);

        Graphics2D printPipelineGraphics = (Graphics2D) pageGraphics.create();
        printPipelineGraphics.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        printPipelineGraphics.clip(new Rectangle2D.Double(0, 0,
                pageFormat.getImageableWidth(), pageFormat.getImageableHeight()));

        new DotMatrixReceiptPrinterService().printableFor(jasperPrint)
                .print(printPipelineGraphics, pageFormat, 0);

        assertTrue(hasDarkPixel(physicalPage, 278, 95, 24, 40),
                "expected the probe content at page x=280 to render at physical x=280, inside the imageable area");
    }

    private JasperPrint fillProbeReport(int x, int y, int width, int height) throws Exception {
        String jrxml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
                              name="probe" pageWidth="342" pageHeight="396" columnWidth="342"
                              leftMargin="0" rightMargin="0" topMargin="0" bottomMargin="0">
                    <detail>
                        <band height="396">
                            <staticText>
                                <reportElement x="%d" y="%d" width="%d" height="%d" forecolor="#000000"/>
                                <textElement/>
                                <text><![CDATA[XX]]></text>
                            </staticText>
                        </band>
                    </detail>
                </jasperReport>
                """.formatted(x, y, width, height);

        try (InputStream inputStream = new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8))) {
            JasperReport report = JasperCompileManager.compileReport(inputStream);
            return JasperFillManager.fillReport(report, new HashMap<>(), new JREmptyDataSource(1));
        }
    }

    private boolean hasDarkPixel(BufferedImage image, int x, int y, int width, int height) {
        for (int px = x; px < x + width; px++) {
            for (int py = y; py < y + height; py++) {
                if (new Color(image.getRGB(px, py)).getRed() < 128) {
                    return true;
                }
            }
        }
        return false;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }
}
