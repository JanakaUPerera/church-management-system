package com.churchmanagement.reports;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ReceiptPrintRepository;
import com.churchmanagement.repository.ReceiptRepository;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ReceiptPdfGenerator {
    private static final String TEMPLATE_PATH = "/reports/receipt_template.jrxml";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

    private final ReceiptRepository receiptRepository;
    private final ReceiptPrintRepository receiptPrintRepository;
    private final DataSource dataSource;
    private final Clock clock;

    public ReceiptPdfGenerator() {
        this(new ReceiptRepository(), new ReceiptPrintRepository(), DatabaseConfig.getDataSource(),
                Clock.systemDefaultZone());
    }

    public ReceiptPdfGenerator(ReceiptRepository receiptRepository, ReceiptPrintRepository receiptPrintRepository,
                               DataSource dataSource, Clock clock) {
        this.receiptRepository = receiptRepository;
        this.receiptPrintRepository = receiptPrintRepository;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public String generateReceiptPdf(long receiptId) {
        ReceiptResponseDto receipt = receiptRepository.findReceiptDetailsById(receiptId)
                .orElseThrow(() -> new ReceiptPdfException("Receipt not found."));

        try {
            Path outputFolder = outputFolder();
            Files.createDirectories(outputFolder);
            Path outputFile = outputFolder.resolve(receipt.getReceiptNo() + ".pdf").normalize();

            JasperPrint print = JasperFillManager.fillReport(compileTemplate(), parameters(receipt),
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

    private JasperReport compileTemplate() throws Exception {
        try (InputStream inputStream = ReceiptPdfGenerator.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (inputStream == null) {
                throw new ReceiptPdfException("Receipt PDF template was not found.");
            }
            return JasperCompileManager.compileReport(inputStream);
        }
    }

    private Map<String, Object> parameters(ReceiptResponseDto receipt) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("organizationName", AppConfig.APPLICATION_NAME);
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
        parameters.put("cancelledWatermark", receipt.getStatus() == ReceiptStatus.CANCELLED ? "CANCELLED" : "");
        return parameters;
    }

    private String itemsText(ReceiptResponseDto receipt) {
        StringBuilder builder = new StringBuilder();
        for (ReceiptItemDto item : receipt.getItems()) {
            builder.append(padRight(item.getCollectionType().getDisplayLabel(), 28))
                    .append(padLeft(formatAmount(item.getAmount()), 14))
                    .append("   ")
                    .append(nullToDash(item.getNote()))
                    .append('\n');
        }
        return builder.toString();
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
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMAT);
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
