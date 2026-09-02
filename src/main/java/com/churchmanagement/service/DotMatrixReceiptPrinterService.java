package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Sends the print-only receipt (see {@code ReceiptPdfGenerator#renderPrintJasperPrint}) straight
 * to the OS default printer — no PDF file is written. Built for the Epson LQ-310 dot-matrix
 * printer feeding the pre-printed 4.75in x 5.5in receipt pad: the page matches that card exactly,
 * and the printable area is inset by the pad's physical margins, so any field the design places
 * outside that area is clipped by the printer rather than repositioned.
 */
public class DotMatrixReceiptPrinterService implements ReceiptPrinterService {
    static final double POINTS_PER_CM = 72.0 / 2.54;
    static final double PAGE_WIDTH_POINTS = 342; // 4.75in
    static final double PAGE_HEIGHT_POINTS = 396; // 5.5in
    static final double LEFT_MARGIN_CM = 1.2;
    static final double RIGHT_MARGIN_CM = 1.2;
    static final double TOP_MARGIN_CM = 0.4;
    static final double BOTTOM_MARGIN_CM = 0.6;

    private static final String PRINTER_LABEL = "Dot Matrix Printer";

    private final Supplier<PrintService> defaultPrintServiceSupplier;
    private final Clock clock;

    public DotMatrixReceiptPrinterService() {
        this(PrintServiceLookup::lookupDefaultPrintService, Clock.systemDefaultZone());
    }

    DotMatrixReceiptPrinterService(Supplier<PrintService> defaultPrintServiceSupplier, Clock clock) {
        this.defaultPrintServiceSupplier = defaultPrintServiceSupplier;
        this.clock = clock;
    }

    @Override
    public PrintResult print(JasperPrint jasperPrint) {
        if (jasperPrint == null) {
            return failure("A receipt to print is required.");
        }

        PrintService printService = defaultPrintServiceSupplier.get();
        if (printService == null) {
            return failure("No default printer is configured on this computer.");
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(printService);
            job.setPrintable(printableFor(jasperPrint), job.validatePage(buildPageFormat()));
            job.print();
            return success(printService.getName());
        } catch (PrinterException exception) {
            return failure(friendlyMessage(exception));
        }
    }

    /**
     * The physical receipt page: 4.75in x 5.5in, with an imageable area inset by the dot-matrix
     * printer's required margins (1.2cm left/right, 0.4cm top, 0.6cm bottom). Content the report
     * places outside that imageable area is clipped by the printing API, not shifted to fit it.
     */
    static PageFormat buildPageFormat() {
        double leftMargin = LEFT_MARGIN_CM * POINTS_PER_CM;
        double rightMargin = RIGHT_MARGIN_CM * POINTS_PER_CM;
        double topMargin = TOP_MARGIN_CM * POINTS_PER_CM;
        double bottomMargin = BOTTOM_MARGIN_CM * POINTS_PER_CM;

        Paper paper = new Paper();
        paper.setSize(PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS);
        paper.setImageableArea(leftMargin, topMargin,
                PAGE_WIDTH_POINTS - leftMargin - rightMargin,
                PAGE_HEIGHT_POINTS - topMargin - bottomMargin);

        PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    private Printable printableFor(JasperPrint jasperPrint) {
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            exportToGraphics(jasperPrint, (Graphics2D) graphics);
            return Printable.PAGE_EXISTS;
        };
    }

    private void exportToGraphics(JasperPrint jasperPrint, Graphics2D graphics2D) throws PrinterException {
        try {
            JRGraphics2DExporter exporter = new JRGraphics2DExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            SimpleGraphics2DExporterOutput output = new SimpleGraphics2DExporterOutput();
            output.setGraphics2D(graphics2D);
            exporter.setExporterOutput(output);
            SimpleGraphics2DReportConfiguration configuration = new SimpleGraphics2DReportConfiguration();
            configuration.setPageIndex(0);
            exporter.setConfiguration(configuration);
            exporter.exportReport();
        } catch (JRException exception) {
            throw new PrinterException(exception.getMessage());
        }
    }

    private PrintResult success(String printerName) {
        return new PrintResult(true, "Printed successfully.", printerName, LocalDateTime.now(clock));
    }

    private PrintResult failure(String message) {
        return new PrintResult(false, message, PRINTER_LABEL, LocalDateTime.now(clock));
    }

    private String friendlyMessage(PrinterException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Printer is not available."
                : exception.getMessage();
    }
}
