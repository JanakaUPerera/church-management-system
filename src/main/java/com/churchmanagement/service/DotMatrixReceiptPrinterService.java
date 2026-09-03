package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Sends the print-only receipt (see {@code ReceiptPdfGenerator#renderPrintJasperPrint}) straight
 * to the OS default printer — no PDF file is written. Built for the Epson LQ-310 dot-matrix
 * printer feeding the pre-printed 4.75in x 5.5in receipt pad, in either of two physical paper
 * setups (see {@link #CONTINUOUS_PAPER_KEY}):
 * <ul>
 *     <li>Single cut-sheet receipts: the page matches the pad exactly, with a margin-based
 *     imageable area inset from its edges (see {@link #buildSingleReceiptPageFormat()}).</li>
 *     <li>Continuous tractor-feed paper: no margin/clip model applies the same way, so alignment
 *     is a plain, empirically-tuned offset instead (see {@link #buildContinuousPageFormat()} and
 *     the translate in {@link #printableFor(JasperPrint)}).</li>
 * </ul>
 */
public class DotMatrixReceiptPrinterService implements ReceiptPrinterService {
    static final double POINTS_PER_CM = 72.0 / 2.54;
    static final double PAGE_WIDTH_POINTS = 342; // 4.75in
    static final double PAGE_HEIGHT_POINTS = 396; // 5.5in
    static final double LEFT_MARGIN_CM = 1.1;
    static final double RIGHT_MARGIN_CM = 1.1;
    static final double TOP_MARGIN_CM = 0.4;
    static final double BOTTOM_MARGIN_CM = 0.6;

    /**
     * Local per-machine setting (same rationale as the report/receipt export folder settings in
     * SettingsController - a paper-feed choice only makes sense for the printer physically
     * attached to this machine) - whether the dot-matrix printer is fed continuous tractor-feed
     * paper rather than single cut-sheet receipts. Read fresh on every print, so a change in
     * Settings takes effect on the next print without restarting the app.
     */
    static final String CONTINUOUS_PAPER_KEY = "receipt.print.continuous_paper";
    static final boolean CONTINUOUS_PAPER_DEFAULT = true;

    private static final String PRINTER_LABEL = "Dot Matrix Printer";

    private final Supplier<PrintService> defaultPrintServiceSupplier;
    private final Clock clock;
    private final BooleanSupplier continuousPaperSupplier;

    public DotMatrixReceiptPrinterService() {
        this(PrintServiceLookup::lookupDefaultPrintService, Clock.systemDefaultZone(),
                DotMatrixReceiptPrinterService::readContinuousPaperSetting);
    }

    DotMatrixReceiptPrinterService(Supplier<PrintService> defaultPrintServiceSupplier, Clock clock) {
        this(defaultPrintServiceSupplier, clock, () -> CONTINUOUS_PAPER_DEFAULT);
    }

    DotMatrixReceiptPrinterService(Supplier<PrintService> defaultPrintServiceSupplier, Clock clock,
                                   BooleanSupplier continuousPaperSupplier) {
        this.defaultPrintServiceSupplier = defaultPrintServiceSupplier;
        this.clock = clock;
        this.continuousPaperSupplier = continuousPaperSupplier;
    }

    private static boolean readContinuousPaperSetting() {
        String configured = DatabaseConfig.getProperty(CONTINUOUS_PAPER_KEY);
        return configured == null || configured.isBlank()
                ? CONTINUOUS_PAPER_DEFAULT
                : Boolean.parseBoolean(configured);
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
            // The driver can silently shrink our requested Paper to whatever it/the selected
            // media actually supports - capture what it validated down to, not just what we
            // asked for, so a clipped printout can be diagnosed from the result alone.
            PageFormat validatedPageFormat = job.validatePage(buildPageFormat());
            job.setPrintable(printableFor(jasperPrint), validatedPageFormat);
            job.print();
            return success(printService.getName(), validatedPageFormat);
        } catch (PrinterException exception) {
            return failure(friendlyMessage(exception));
        }
    }

    /**
     * The physical receipt page: 4.75in x 5.5in, in whichever of the two paper setups
     * {@link #CONTINUOUS_PAPER_KEY} currently selects.
     */
    PageFormat buildPageFormat() {
        return continuousPaperSupplier.getAsBoolean() ? buildContinuousPageFormat() : buildSingleReceiptPageFormat();
    }

    /**
     * Single cut-sheet receipts: imageable area inset from the pad's edges by the dot-matrix
     * printer's required margins (1.1cm left/right, 0.4cm top, 0.6cm bottom). Content the report
     * places outside that imageable area is clipped by the printing API, not shifted to fit it.
     */
    static PageFormat buildSingleReceiptPageFormat() {
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

    /**
     * Continuous tractor-feed paper: the whole page declared imageable (no margin/clip model -
     * the concept of an inset "sheet" doesn't apply the same way to a continuous feed), with
     * alignment handled instead by the plain offset in {@link #printableFor(JasperPrint)}.
     */
    static PageFormat buildContinuousPageFormat() {
        Paper paper = new Paper();
        paper.setSize(PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS);
        paper.setImageableArea(0, 0, PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS);

        PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    Printable printableFor(JasperPrint jasperPrint) {
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D graphics2D = (Graphics2D) graphics;
            // The print pipeline hands us a Graphics2D whose (0,0) is already translated to the
            // imageable area's top-left in physical page space (and clipped to its width/height),
            // but every field in the report is positioned in absolute page coordinates (e.g.
            // x=138 means physical page x=138, not "138 past the margin"). Left uncompensated,
            // everything renders imageableX/imageableY too far right/down. For single-sheet
            // receipts that cancels the pipeline's implicit shift exactly (see
            // buildSingleReceiptPageFormat()'s imageableX/Y); continuous paper has no such margin
            // model (imageableX/Y are 0) and instead needs the fixed offset below, tuned against
            // the actual Epson LQ-310 feeding continuous paper.
            if (continuousPaperSupplier.getAsBoolean()) {
                graphics2D.translate(-28, -3);
            } else {
                graphics2D.translate(-pageFormat.getImageableX(), -pageFormat.getImageableY());
            }
            exportToGraphics(jasperPrint, graphics2D);
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

    private PrintResult success(String printerName, PageFormat validatedPageFormat) {
        return new PrintResult(true, "Printed successfully. " + describePageFormat(validatedPageFormat),
                printerName, LocalDateTime.now(clock));
    }

    /**
     * Formats a page format's paper size and imageable area in points, for diagnosing clipped
     * printouts: {@link java.awt.print.PrinterJob#validatePage} can silently shrink our
     * requested {@link #buildPageFormat()} down to whatever the driver/selected media actually
     * supports, so this reports what was actually used, not what was requested.
     */
    static String describePageFormat(PageFormat pageFormat) {
        return String.format(
                "page=%.1fx%.1fpt, imageable=[x=%.1f,y=%.1f,w=%.1f,h=%.1f]pt",
                pageFormat.getWidth(), pageFormat.getHeight(),
                pageFormat.getImageableX(), pageFormat.getImageableY(),
                pageFormat.getImageableWidth(), pageFormat.getImageableHeight());
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
