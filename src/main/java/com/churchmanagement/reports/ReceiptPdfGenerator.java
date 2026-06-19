package com.churchmanagement.reports;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
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
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import javax.sql.DataSource;
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
    private static final String TEMPLATE_PATH = "/reports/receipt_template.jrxml";
    private static final String UNICODE_TEST_TEMPLATE_PATH = "/reports/unicode_font_test.jrxml";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

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

            String fontName = receiptFontService.fontFor(receipt.getReceiptLanguage());
            JasperPrint print = JasperFillManager.fillReport(compileTemplate(TEMPLATE_PATH, fontName), parameters(receipt),
                    new JREmptyDataSource(1));
            JasperExportManager.exportReportToPdfFile(print, outputFile.toString());

            updatePdfFilePath(receiptId, outputFile.toString());
            return outputFile.toString();
        } catch (ReceiptPdfException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReceiptPdfException("PDF generation failed.", exception);
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
        putLabels(parameters, receipt);
        parameters.put("organizationName", setting("organization.name", AppConfig.APPLICATION_NAME));
        parameters.put("organizationAddress", setting("organization.address", ""));
        parameters.put("organizationPhone", setting("organization.phone", ""));
        parameters.put("receiptNo", receipt.getReceiptNo());
        parameters.put("receiptDateTime", formatDateTime(receipt.getReceiptDateTime()));
        parameters.put("churchCode", nullToDash(receipt.getChurchCode()));
        parameters.put("churchName", nullToDash(receipt.getChurchName()));
        parameters.put("regionCode", nullToDash(receipt.getRegionCode()));
        parameters.put("regionName", nullToDash(receipt.getRegionName()));
        parameters.put("week", receipt.getWeekStartDate() + " to " + receipt.getWeekEndDate());
        parameters.put("bearerName", nullToDash(receipt.getSubmittedByName()));
        parameters.put("submittedBy", nullToDash(receipt.getSubmittedByName()));
        parameters.put("issuedBy", nullToDash(receipt.getIssuedByFullName()));
        parameters.put("lateSubmission", receipt.isLateSubmission() ? "Yes" : "No");
        parameters.put("lateSubmissionReason", nullToDash(receipt.getLateSubmissionReason()));
        parameters.put("itemsText", itemsText(receipt));
        parameters.put("totalAmount", formatAmount(receipt.getTotalAmount()));
        parameters.put("status", receipt.getStatus() == null ? "-" : receipt.getStatus().name());
        parameters.put("generatedAt", formatDateTime(LocalDateTime.now(clock)));
        parameters.put("cancelReason", nullToDash(receipt.getCancelReason()));
        parameters.put("cancelledWatermark", receipt.getStatus() == ReceiptStatus.CANCELLED
                ? label("cancelled", receipt)
                : "");
        return parameters;
    }

    private void putLabels(Map<String, Object> parameters, ReceiptResponseDto receipt) {
        parameters.put("PARAM_RECEIPT_TITLE", label("receipt_title", receipt));
        parameters.put("PARAM_RECEIPT_NO_LABEL", label("receipt_no", receipt));
        parameters.put("PARAM_RECEIPT_DATE_TIME_LABEL", label("receipt_date_time", receipt));
        parameters.put("PARAM_CHURCH_CODE_LABEL", label("church_code", receipt));
        parameters.put("PARAM_CHURCH_NAME_LABEL", label("church_name", receipt));
        parameters.put("PARAM_REGION_LABEL", label("region", receipt));
        parameters.put("PARAM_WEEK_LABEL", label("week", receipt));
        parameters.put("PARAM_BEARER_NAME_LABEL", label("bearer_name", receipt));
        parameters.put("PARAM_SUBMITTED_BY_LABEL", label("submitted_by", receipt));
        parameters.put("PARAM_ISSUED_BY_LABEL", label("issued_by", receipt));
        parameters.put("PARAM_COLLECTION_TYPE_LABEL", label("collection_type", receipt));
        parameters.put("PARAM_AMOUNT_LABEL", label("amount", receipt));
        parameters.put("PARAM_NOTE_LABEL", label("note", receipt));
        parameters.put("PARAM_TOTAL_AMOUNT_LABEL", label("total_amount", receipt));
        parameters.put("PARAM_LATE_SUBMISSION_LABEL", label("late_submission", receipt));
        parameters.put("PARAM_LATE_REASON_LABEL", label("late_reason", receipt));
        parameters.put("PARAM_STATUS_LABEL", label("status", receipt));
        parameters.put("PARAM_CANCEL_REASON_LABEL", label("cancel_reason", receipt));
        parameters.put("PARAM_ORIGINAL_RECEIPT_LABEL", label("original_receipt", receipt));
        parameters.put("PARAM_GENERATED_AT_LABEL", label("generated_at", receipt));
    }

    private String label(String key, ReceiptResponseDto receipt) {
        return translationService.label(key, receipt.getReceiptLanguage());
    }

    private String itemsText(ReceiptResponseDto receipt) {
        StringBuilder builder = new StringBuilder();
        for (ReceiptItemDto item : receipt.getItems()) {
            builder.append(padRight(collectionTypeLabel(item, receipt), 28))
                    .append(padLeft(formatAmount(item.getAmount()), 14))
                    .append("   ")
                    .append(nullToDash(item.getNote()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String collectionTypeLabel(ReceiptItemDto item, ReceiptResponseDto receipt) {
        if (item.getCollectionType() == null) {
            return "-";
        }

        return switch (item.getCollectionType()) {
            case OFFERTORY -> label("Offerings", receipt);
            case TITHES -> label("tithes", receipt);
            case OTHER_DONATIONS -> label("other_donations", receipt);
        };
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

    private String setting(String key, String fallback) {
        String value = configurationCache.getString(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String padRight(String value, int length) {
        String text = nullToDash(value);
        return text.length() >= length ? text : text + " ".repeat(length - text.length());
    }

    private String padLeft(String value, int length) {
        String text = nullToDash(value);
        return text.length() >= length ? text : " ".repeat(length - text.length()) + text;
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
