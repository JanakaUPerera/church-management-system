package com.churchmanagement.reports.export;

import com.churchmanagement.dto.report.ReportSearchCriteria;
import com.churchmanagement.dto.report.ReportSummaryTotals;
import com.churchmanagement.dto.report.ReportTableRow;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.service.SystemConfigurationCache;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRSection;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import java.io.InputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReportPdfExporter {
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final Color TITLE_COLOR = new Color(31, 78, 121);
    private static final Color HEADER_BACKGROUND = new Color(31, 78, 121);
    private static final Color HEADER_FOREGROUND = Color.WHITE;
    private static final Color FILTER_BACKGROUND = new Color(238, 244, 250);
    private static final Color TEXT_COLOR = new Color(31, 41, 55);

    private final Clock clock;

    public ReportPdfExporter() {
        this(Clock.systemDefaultZone());
    }

    public ReportPdfExporter(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public <T extends ReportTableRow> Path export(ReportType reportType, ReportSearchCriteria criteria,
                                                  List<T> rows, ReportSummaryTotals totals) {
        try {
            Path folder = Path.of("./reports");
            Files.createDirectories(folder);
            Path output = folder.resolve(reportType.name().toLowerCase() + "-" + System.currentTimeMillis() + ".pdf");
            Map<String, Object> parameters = parameters(reportType, criteria, rows, totals);
            InputStream template = getClass().getResourceAsStream("/reports/" + templateName(reportType));
            if (template == null) {
                throw new IllegalStateException("Missing JasperReports template for " + reportType.getDisplayName());
            }
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    compileReport(template, rows),
                    parameters,
                    new JRBeanCollectionDataSource(exportRows(rows, totals)));
            JasperExportManager.exportReportToPdfFile(jasperPrint, output.toString());
            return output;
        } catch (Exception exception) {
            throw new ReportExportException("Export failed.", exception);
        }
    }

    private <T extends ReportTableRow> net.sf.jasperreports.engine.JasperReport compileReport(InputStream template,
                                                                                              List<T> rows)
            throws JRException {
        JasperDesign design = JRXmlLoader.load(template);
        List<String> headers = rows == null || rows.isEmpty()
                ? List.of()
                : rows.getFirst().columns().keySet().stream().toList();
        applyDesign(design, headers);
        return JasperCompileManager.compileReport(design);
    }

    private <T extends ReportTableRow> Map<String, Object> parameters(ReportType reportType, ReportSearchCriteria criteria,
                                                                       List<T> rows, ReportSummaryTotals totals)
            throws JRException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("organizationName", organizationName());
        parameters.put("reportTitle", reportType.getDisplayName());
        parameters.put("generatedAt", LocalDateTime.now(clock).toString());
        parameters.put("filters", filters(criteria));
        parameters.put("rowCount", rows == null ? 0 : rows.size());
        ReportSummaryTotals safeTotals = totals == null ? new ReportSummaryTotals() : totals;
        parameters.put("offertoryTotal", formatAmount(safeTotals.getOffertoryTotal()));
        parameters.put("tithesTotal", formatAmount(safeTotals.getTithesTotal()));
        parameters.put("otherDonationsTotal", formatAmount(safeTotals.getOtherDonationsTotal()));
        parameters.put("grandTotal", formatAmount(safeTotals.getGrandTotal()));
        List<String> headers = rows == null || rows.isEmpty()
                ? List.of()
                : rows.getFirst().columns().keySet().stream().toList();
        for (int index = 0; index < 12; index++) {
            parameters.put("header" + (index + 1), index < headers.size() ? headers.get(index) : "");
        }
        return parameters;
    }

    private <T extends ReportTableRow> List<LinkedHashMap<String, Object>> rowsWithTotals(List<T> rows,
                                                                                          ReportSummaryTotals totals) {
        List<LinkedHashMap<String, Object>> exportRows = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return exportRows;
        }
        rows.stream().map(ReportTableRow::columns).forEach(exportRows::add);
        if (totals != null) {
            totalsRow(rows.getFirst().columns(), totals).ifPresent(exportRows::add);
        }
        return exportRows;
    }

    private <T extends ReportTableRow> List<ReportExportRow> exportRows(List<T> rows, ReportSummaryTotals totals) {
        return rowsWithTotals(rows, totals).stream()
                .map(row -> new ReportExportRow(row.values().stream()
                        .map(this::formatValue)
                        .toList()))
                .toList();
    }

    private Optional<LinkedHashMap<String, Object>> totalsRow(LinkedHashMap<String, Object> columns,
                                                              ReportSummaryTotals totals) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        boolean hasTotalColumn = false;
        int firstTotalColumn = -1;
        int index = 0;
        for (String header : columns.keySet()) {
            Object total = totalForHeader(header, totals);
            if (total != null && firstTotalColumn < 0) {
                firstTotalColumn = index;
            }
            hasTotalColumn = hasTotalColumn || total != null;
            row.put(header, total == null ? "" : total);
            index++;
        }
        if (!hasTotalColumn) {
            return Optional.empty();
        }
        if (firstTotalColumn > 0) {
            String labelColumn = row.keySet().stream().skip(firstTotalColumn - 1L).findFirst().orElse(null);
            if (labelColumn != null) {
                row.put(labelColumn, "Totals");
            }
        } else if (!row.isEmpty()) {
            row.put(row.keySet().iterator().next(), "Totals");
        }
        return Optional.of(row);
    }

    private BigDecimal totalForHeader(String header, ReportSummaryTotals totals) {
        return switch (header) {
            case "Offertory" -> totals.getOffertoryTotal();
            case "Tithes" -> totals.getTithesTotal();
            case "Other Donations" -> totals.getOtherDonationsTotal();
            case "Grand Total" -> totals.getGrandTotal();
            default -> null;
        };
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return formatAmount(amount);
        }
        return value.toString();
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String filters(ReportSearchCriteria criteria) {
        return "<b>Date From:</b> " + blank(criteria.getDateFrom())
                + "   <b>Date To:</b> " + blank(criteria.getDateTo())
                + "   <b>Week Start:</b> " + blank(criteria.getWeekStartDate())
                + "   <b>Region ID:</b> " + blank(criteria.getRegionId())
                + "   <b>Church ID:</b> " + blank(criteria.getChurchId())
                + "   <b>Status:</b> " + blank(criteria.getStatus())
                + "   <b>Receipt No:</b> " + blank(criteria.getReceiptNo())
                + "   <b>User ID:</b> " + blank(criteria.getUserId());
    }

    private void applyDesign(JasperDesign design, List<String> headers) {
        styleBand(design.getTitle(), ElementRole.TITLE, headers);
        styleBand(design.getColumnHeader(), ElementRole.COLUMN_HEADER, headers);
        JRSection detailSection = design.getDetailSection();
        if (detailSection != null) {
            for (var band : detailSection.getBands()) {
                styleBand(band, ElementRole.DETAIL, headers);
            }
        }
        styleBand(design.getPageFooter(), ElementRole.FOOTER, headers);
    }

    private void styleBand(net.sf.jasperreports.engine.JRBand band, ElementRole role, List<String> headers) {
        if (band == null) {
            return;
        }
        for (JRElement element : band.getElements()) {
            if (element instanceof JRDesignTextField textField) {
                styleTextField(textField, role, headers);
            }
        }
    }

    private void styleTextField(JRDesignTextField textField, ElementRole role, List<String> headers) {
        textField.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        textField.setFontSize(role == ElementRole.TITLE ? 13f : 11f);
        textField.setForecolor(TEXT_COLOR);
        if (role == ElementRole.COLUMN_HEADER) {
            textField.setBold(Boolean.TRUE);
            textField.setMode(ModeEnum.OPAQUE);
            textField.setBackcolor(HEADER_BACKGROUND);
            textField.setForecolor(HEADER_FOREGROUND);
            textField.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        } else if (role == ElementRole.DETAIL) {
            int columnIndex = columnIndex(textField, "$F{column");
            textField.setHorizontalTextAlign(isAmountColumn(headers, columnIndex)
                    ? HorizontalTextAlignEnum.RIGHT
                    : HorizontalTextAlignEnum.LEFT);
        } else if (role == ElementRole.TITLE) {
            textField.setBold(Boolean.TRUE);
            textField.setForecolor(TITLE_COLOR);
            if (expressionText(textField).contains("$P{filters}")) {
                textField.setFontSize(11f);
                textField.setBold(Boolean.FALSE);
                textField.setMarkup("html");
                textField.setMode(ModeEnum.OPAQUE);
                textField.setBackcolor(FILTER_BACKGROUND);
                textField.setForecolor(TEXT_COLOR);
            }
        }
    }

    private boolean isAmountColumn(List<String> headers, int zeroBasedColumn) {
        return zeroBasedColumn >= 0
                && zeroBasedColumn < headers.size()
                && amountHeader(headers.get(zeroBasedColumn));
    }

    private boolean amountHeader(String header) {
        return "Offertory".equals(header)
                || "Tithes".equals(header)
                || "Other Donations".equals(header)
                || "Grand Total".equals(header)
                || "Total Collections".equals(header);
    }

    private int columnIndex(JRDesignTextField textField, String prefix) {
        String text = expressionText(textField);
        int start = text.indexOf(prefix);
        if (start < 0) {
            return -1;
        }
        int numberStart = start + prefix.length();
        int numberEnd = text.indexOf('}', numberStart);
        if (numberEnd < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(numberStart, numberEnd)) - 1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String expressionText(JRDesignTextField textField) {
        return textField.getExpression() == null ? "" : textField.getExpression().getText();
    }

    private enum ElementRole {
        TITLE,
        COLUMN_HEADER,
        DETAIL,
        FOOTER
    }

    private String organizationName() {
        String name = SystemConfigurationCache.getInstance().getString("organization.name");
        return name == null || name.isBlank() ? "Church Management System" : name.strip();
    }

    private String blank(Object value) {
        return value == null ? "" : value.toString();
    }

    private String templateName(ReportType reportType) {
        return switch (reportType) {
            case WEEKLY_CHURCH_COLLECTION -> "weekly_church_collection.jrxml";
            case WEEKLY_REGION_SUMMARY -> "weekly_region_summary.jrxml";
            case SUBMISSION_STATUS -> "submission_status.jrxml";
            case LATE_SUBMISSION -> "late_submission.jrxml";
            case CHURCH_ANNUAL_COLLECTION, REGION_ANNUAL_COLLECTION,
                    CHURCH_MONTHLY_COLLECTION, REGION_MONTHLY_COLLECTION -> "annual_collection.jrxml";
            case CANCELLED_RECEIPT -> "cancelled_receipt.jrxml";
            case SMS_DELIVERY -> "sms_delivery.jrxml";
            default -> "generic_report.jrxml";
        };
    }

    public static class ReportExportException extends RuntimeException {
        public ReportExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
