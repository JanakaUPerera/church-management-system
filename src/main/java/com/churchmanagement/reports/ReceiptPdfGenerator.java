package com.churchmanagement.reports;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ReceiptPrintRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.service.ReceiptFontService;
import com.churchmanagement.service.ReceiptLabelTranslationService;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.SystemDateTimeFormatter;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;

import javax.sql.DataSource;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ReceiptPdfGenerator {
    /**
     * Flips the in-app "Preview" action off across the UI without touching the generation logic.
     */
    public static final boolean PREVIEW_FEATURE_ENABLED = true;

    private static final String TEMPLATE_PATH = "/reports/receipt_template.jrxml";
    private static final String UNICODE_TEST_TEMPLATE_PATH = "/reports/unicode_font_test.jrxml";
    private static final String BACKGROUND_TOP_IMAGE_PATH = "/reports/receipt_background_top.png";
    private static final String BACKGROUND_FOOTER_BAR_IMAGE_PATH = "/reports/receipt_background_footer_bar.png";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final int PAGE_WIDTH = 382;
    private static final int PAGE_HEIGHT = 436;
    private static final float PREVIEW_ZOOM = 3f;

    private final ReceiptRepository receiptRepository;
    private final ReceiptPrintRepository receiptPrintRepository;
    private final ReceiptFontService receiptFontService;
    private final ReceiptLabelTranslationService translationService;
    private final DataSource dataSource;
    private final Clock clock;
    private final SystemConfigurationCache configurationCache;
    private final SystemDateTimeFormatter dateTimeFormatter;

    public ReceiptPdfGenerator() {
        this(new ReceiptRepository(), new ReceiptPrintRepository(), DatabaseConfig.getDataSource(),
                Clock.systemDefaultZone(), new ReceiptFontService());
    }

    public ReceiptPdfGenerator(ReceiptRepository receiptRepository, ReceiptPrintRepository receiptPrintRepository,
                               DataSource dataSource, Clock clock) {
        this(receiptRepository, receiptPrintRepository, dataSource, clock, new ReceiptFontService());
    }

    public ReceiptPdfGenerator(ReceiptRepository receiptRepository, ReceiptPrintRepository receiptPrintRepository,
                               DataSource dataSource, Clock clock, ReceiptFontService receiptFontService) {
        this(receiptRepository, receiptPrintRepository, dataSource, clock, receiptFontService,
                new ReceiptLabelTranslationService());
    }

    public ReceiptPdfGenerator(ReceiptRepository receiptRepository, ReceiptPrintRepository receiptPrintRepository,
                               DataSource dataSource, Clock clock, ReceiptFontService receiptFontService,
                               ReceiptLabelTranslationService translationService) {
        this(receiptRepository, receiptPrintRepository, dataSource, clock, receiptFontService, translationService,
                SystemConfigurationCache.getInstance(), new SystemDateTimeFormatter());
    }

    public ReceiptPdfGenerator(ReceiptRepository receiptRepository, ReceiptPrintRepository receiptPrintRepository,
                               DataSource dataSource, Clock clock, ReceiptFontService receiptFontService,
                               ReceiptLabelTranslationService translationService,
                               SystemConfigurationCache configurationCache,
                               SystemDateTimeFormatter dateTimeFormatter) {
        this.receiptRepository = receiptRepository;
        this.receiptPrintRepository = receiptPrintRepository;
        this.dataSource = dataSource;
        this.clock = clock;
        this.receiptFontService = receiptFontService;
        this.translationService = translationService;
        this.configurationCache = configurationCache;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public String generateReceiptPdf(long receiptId) {
        ReceiptResponseDto receipt = receiptRepository.findReceiptDetailsById(receiptId)
                .orElseThrow(() -> new ReceiptPdfException("Receipt not found."));

        try {
            Path outputFolder = outputFolder();
            Files.createDirectories(outputFolder);
            Path outputFile = outputFolder.resolve(receipt.getReceiptNo() + ".pdf").normalize();

            JasperPrint print = fill(receipt);
            JasperExportManager.exportReportToPdfFile(print, outputFile.toString());

            updatePdfFilePath(receiptId, outputFile.toString());
            return outputFile.toString();
        } catch (ReceiptPdfException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReceiptPdfException("PDF generation failed.", exception);
        }
    }

    /**
     * Renders the receipt to an in-memory image for the "Preview" action — no PDF file is written
     * and no database state is changed. Gated at the call sites by {@link #PREVIEW_FEATURE_ENABLED}.
     */
    public BufferedImage renderReceiptPreview(long receiptId) {
        ReceiptResponseDto receipt = receiptRepository.findReceiptDetailsById(receiptId)
                .orElseThrow(() -> new ReceiptPdfException("Receipt not found."));
        return renderReceiptPreview(receipt);
    }

    public BufferedImage renderReceiptPreview(ReceiptResponseDto receipt) {
        try {
            JasperPrint print = fill(receipt);

            int width = Math.round(PAGE_WIDTH * PREVIEW_ZOOM);
            int height = Math.round(PAGE_HEIGHT * PREVIEW_ZOOM);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.scale(PREVIEW_ZOOM, PREVIEW_ZOOM);

                JRGraphics2DExporter exporter = new JRGraphics2DExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                SimpleGraphics2DExporterOutput output = new SimpleGraphics2DExporterOutput();
                output.setGraphics2D(graphics);
                exporter.setExporterOutput(output);
                SimpleGraphics2DReportConfiguration configuration = new SimpleGraphics2DReportConfiguration();
                configuration.setPageIndex(0);
                exporter.setConfiguration(configuration);
                exporter.exportReport();
            } finally {
                graphics.dispose();
            }
            return image;
        } catch (ReceiptPdfException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReceiptPdfException("Receipt preview rendering failed.", exception);
        }
    }

    public String generateUnicodeFontTestPdf() {
        try {
            Path outputFolder = outputFolder();
            Files.createDirectories(outputFolder);
            Path outputFile = outputFolder.resolve("unicode-font-test.pdf").normalize();

            JasperPrint print = JasperFillManager.fillReport(
                    compileTemplate(UNICODE_TEST_TEMPLATE_PATH, ReceiptFontService.NOTO_SANS),
                    new HashMap<>(),
                    new JREmptyDataSource(1));
            JasperExportManager.exportReportToPdfFile(print, outputFile.toString());
            return outputFile.toString();
        } catch (Exception exception) {
            throw new ReceiptPdfException("Unicode font test PDF generation failed.", exception);
        }
    }

    private JasperPrint fill(ReceiptResponseDto receipt) throws Exception {
        try (InputStream topImage = openBundledResource(BACKGROUND_TOP_IMAGE_PATH);
             InputStream footerBarImage = openBundledResource(BACKGROUND_FOOTER_BAR_IMAGE_PATH)) {
            Map<String, Object> parameters = parameters(receipt);
            parameters.put("backgroundTopImage", topImage);
            parameters.put("backgroundFooterBarImage", footerBarImage);
            return JasperFillManager.fillReport(compileTemplate(TEMPLATE_PATH, ReceiptFontService.NOTO_SANS),
                    parameters, new JREmptyDataSource(1));
        }
    }

    private InputStream openBundledResource(String path) {
        InputStream stream = ReceiptPdfGenerator.class.getResourceAsStream(path);
        if (stream == null) {
            throw new ReceiptPdfException("Receipt template resource was not found: " + path);
        }
        return stream;
    }

    private JasperReport compileTemplate(String templatePath, String defaultFontName) throws Exception {
        try (InputStream inputStream = ReceiptPdfGenerator.class.getResourceAsStream(templatePath)) {
            if (inputStream == null) {
                throw new ReceiptPdfException("Receipt PDF template was not found.");
            }
            JasperDesign design = JRXmlLoader.load(inputStream);
            applyDefaultFont(design, defaultFontName);
            return JasperCompileManager.compileReport(design);
        }
    }

    private void applyDefaultFont(JasperDesign design, String fontName) throws Exception {
        JRDesignStyle style = new JRDesignStyle();
        style.setName("ReceiptDefaultFont");
        style.setDefault(true);
        style.setFontName(fontName);
        style.setPdfEncoding(ReceiptFontService.PDF_ENCODING);
        style.setPdfEmbedded(Boolean.TRUE);
        design.addStyle(style);
        design.setDefaultStyle(style);
    }

    Map<String, Object> parameters(ReceiptResponseDto receipt) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("receivedFrom", truncate(receipt.getSubmittedByName(), 30));
        parameters.put("branchChurch", truncate(receipt.getChurchName(), 14));
        parameters.put("number", nullToDash(receipt.getChurchCode()));
        parameters.put("titheAmount", amountFor(receipt, CollectionType.TITHES));
        parameters.put("churchServiceDate", receipt.getChurchServiceDate() == null
                ? "-" : dateTimeFormatter.formatDate(receipt.getChurchServiceDate()));
        parameters.put("churchServiceWeek", "(Week: " + dateTimeFormatter.formatDate(receipt.getWeekStartDate())
                + " - " + dateTimeFormatter.formatDate(receipt.getWeekEndDate()) + ")");

        // Other Donations and Total have no field of their own on the physical pad, so rather
        // than add a new section below it, they ride along on the two rows that already exist.
        // Total (roomy row, full-width label fits in every language) is tacked onto the end of
        // the Date Received row as "<label>: <amount>". Other Donations (only when the receipt
        // actually has one) rides the Offerings row instead - that column is only ~124pt wide,
        // not enough to also spell out the (long, in Sinhala/Tamil) translated label without the
        // amount itself getting clipped off the end, so it's shown as a plain "+ <amount>"
        // addition instead: unambiguous in context (the Offerings row), and the figure - the
        // part that actually matters - always stays fully visible.
        ReceiptLanguage language = receipt.getReceiptLanguage();
        String otherDonationsAmount = amountFor(receipt, CollectionType.OTHER_DONATIONS);
        String offeringsAmount = amountFor(receipt, CollectionType.OFFERTORY);
        parameters.put("offeringsAmount", "-".equals(otherDonationsAmount)
                ? offeringsAmount
                : offeringsAmount + " + " + otherDonationsAmount);

        String dateReceived = receipt.getReceiptDateTime() == null
                ? "-" : dateTimeFormatter.formatDate(receipt.getReceiptDateTime().toLocalDate());
        parameters.put("dateReceived", dateReceived + "   " + translationService.label("total_amount", language)
                + ": " + formatAmount(receipt.getTotalAmount()));

        parameters.put("receiptLanguageIsSinhala", language == ReceiptLanguage.SINHALA);
        parameters.put("receiptLanguageIsTamil", language == ReceiptLanguage.TAMIL);
        parameters.put("receiptLanguageIsEnglish", language != ReceiptLanguage.SINHALA && language != ReceiptLanguage.TAMIL);

        parameters.put("receiptNo", nullToDash(receipt.getReceiptNo()));
        parameters.put("printedAt", formatDateTime(LocalDateTime.now(clock)));
        parameters.put("issuedBy", truncate(receipt.getIssuedByFullName(), 16));

        parameters.put("cancelledWatermark", receipt.getStatus() == ReceiptStatus.CANCELLED ? "CANCELLED" : "");
        return parameters;
    }

    private String amountFor(ReceiptResponseDto receipt, CollectionType collectionType) {
        for (ReceiptItemDto item : receipt.getItems()) {
            if (item.getCollectionType() == collectionType) {
                return formatAmount(item.getAmount());
            }
        }
        return "-";
    }

    private void updatePdfFilePath(long receiptId, String path) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            receiptPrintRepository.updatePdfFilePath(receiptId, path, connection);
        } catch (DatabaseException exception) {
            throw exception;
        }
    }

    private Path outputFolder() {
        String configured = DatabaseConfig.getProperty("receipt.pdf.output.folder");
        if (configured == null || configured.isBlank()) {
            configured = "./receipts";
        }
        return Path.of(configured).normalize();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTimeFormatter.formatDateTime(dateTime);
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String truncate(String value, int maxLength) {
        String text = nullToDash(value);
        return text.length() > maxLength ? text.substring(0, maxLength - 1) + "…" : text;
    }

    public static class ReceiptPdfException extends RuntimeException {
        public ReceiptPdfException(String message) {
            super(message);
        }

        public ReceiptPdfException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
