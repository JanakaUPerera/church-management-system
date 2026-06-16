package com.churchmanagement.reports.export;

import com.churchmanagement.dto.report.ReportSearchCriteria;
import com.churchmanagement.dto.report.ReportSummaryTotals;
import com.churchmanagement.dto.report.ReportTableRow;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.SystemDateTimeFormatter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRSection;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignConditionalStyle;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignStyle;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReportPdfExporter {
    private static final int PAGE_MARGIN = 10;
    private static final int MAX_EXPORT_COLUMNS = 12;
    private static final String BODY_FONT = "Noto Sans";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final Color TITLE_COLOR = new Color(31, 78, 121);
    private static final Color HEADER_BACKGROUND = new Color(31, 78, 121);
    private static final Color HEADER_FOREGROUND = Color.WHITE;
    private static final Color FILTER_BACKGROUND = new Color(238, 244, 250);
    private static final Color DETAIL_ALTERNATE_BACKGROUND = new Color(248, 250, 252);
    private static final Color TEXT_COLOR = new Color(31, 41, 55);

    private final Clock clock;
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();

    public ReportPdfExporter() {
        this(Clock.systemDefaultZone());
    }

    public ReportPdfExporter(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public <T extends ReportTableRow> Path export(ReportType reportType, ReportSearchCriteria criteria,
                                                  List<T> rows, ReportSummaryTotals totals) {
        try {
            Path folder = ReportExportLocationResolver.exportFolder();
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
        parameters.put("generatedAt", dateTimeFormatter.formatDateTime(LocalDateTime.now(clock)));
        parameters.put("generatedBy", generatedBy());
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
        for (int index = 0; index < MAX_EXPORT_COLUMNS; index++) {
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
        List<LinkedHashMap<String, Object>> exportRows = rowsWithTotals(rows, totals);
        int dataRowCount = rows == null ? 0 : rows.size();
        List<ReportExportRow> reportExportRows = new ArrayList<>();
        for (int index = 0; index < exportRows.size(); index++) {
            LinkedHashMap<String, Object> row = exportRows.get(index);
            boolean totalsRow = index >= dataRowCount;
            reportExportRows.add(new ReportExportRow(row.values().stream()
                    .map(this::formatValue)
                    .toList(), totalsRow, !totalsRow && index % 2 == 1));
        }
        return reportExportRows;
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
            case "Grand Total", "Total Collections" -> totals.getGrandTotal();
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
        if (value instanceof LocalDateTime dateTime) {
            return dateTimeFormatter.formatDateTime(dateTime);
        }
        if (value instanceof LocalDate date) {
            return dateTimeFormatter.formatDate(date);
        }
        if (value instanceof LocalTime time) {
            return dateTimeFormatter.formatTime(time);
        }
        return value.toString();
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String filters(ReportSearchCriteria criteria) {
        List<String> filterItems = new ArrayList<>();
        addFilter(filterItems, "Date From:", criteria.getDateFrom());
        addFilter(filterItems, "Date To:", criteria.getDateTo());
        addFilter(filterItems, "Week Start:", criteria.getWeekStartDate());
        addFilter(filterItems, "Region ID:", criteria.getRegionId());
        addFilter(filterItems, "Church ID:", criteria.getChurchId());
        addFilter(filterItems, "Status:", criteria.getStatus());
        addFilter(filterItems, "Receipt No:", criteria.getReceiptNo());
        addFilter(filterItems, "User ID:", criteria.getUserId());
        return String.join("   ", filterItems);
    }

    private void addFilter(List<String> filterItems, String label, Object value) {
        String displayValue = formatValue(value).strip();
        if (displayValue.isEmpty()) {
            return;
        }
        filterItems.add(boldLabel(label) + " " + displayValue);
    }

    private void applyDesign(JasperDesign design, List<String> headers) {
        expandPrintableArea(design);
        addParameterIfAbsent(design, "generatedBy", String.class);
        JRDesignStyle detailStyle = addReportDetailStyle(design);
        stretchTitleAndFooter(design);
        layoutTableColumns(design, headers);
        styleBand(design.getTitle(), ElementRole.TITLE, headers, detailStyle);
        styleBand(design.getColumnHeader(), ElementRole.COLUMN_HEADER, headers, detailStyle);
        JRSection detailSection = design.getDetailSection();
        if (detailSection != null) {
            for (var band : detailSection.getBands()) {
                styleBand(band, ElementRole.DETAIL, headers, detailStyle);
            }
        }
        styleBand(design.getPageFooter(), ElementRole.FOOTER, headers, detailStyle);
    }

    private JRDesignStyle addReportDetailStyle(JasperDesign design) {
        JRDesignField totalsRowField = new JRDesignField();
        totalsRowField.setName("totalsRow");
        totalsRowField.setValueClass(Boolean.class);
        JRDesignField oddRowField = new JRDesignField();
        oddRowField.setName("oddRow");
        oddRowField.setValueClass(Boolean.class);

        JRDesignStyle detailStyle = new JRDesignStyle();
        detailStyle.setName("ReportDetailCellStyle");
        detailStyle.setFontName(BODY_FONT);
        detailStyle.setFontSize(11f);

        JRDesignConditionalStyle alternateRowStyle = new JRDesignConditionalStyle();
        alternateRowStyle.setConditionExpression(new JRDesignExpression(
                "Boolean.TRUE.equals($F{oddRow}) && !Boolean.TRUE.equals($F{totalsRow})"));
        alternateRowStyle.setBackcolor(DETAIL_ALTERNATE_BACKGROUND);
        detailStyle.addConditionalStyle(alternateRowStyle);

        JRDesignConditionalStyle totalsStyle = new JRDesignConditionalStyle();
        totalsStyle.setConditionExpression(new JRDesignExpression("Boolean.TRUE.equals($F{totalsRow})"));
        totalsStyle.setBold(Boolean.TRUE);
        detailStyle.addConditionalStyle(totalsStyle);

        try {
            design.addField(totalsRowField);
            design.addField(oddRowField);
            design.addStyle(detailStyle);
        } catch (JRException exception) {
            throw new ReportExportException("Unable to prepare report style.", exception);
        }
        return detailStyle;
    }

    private void addParameterIfAbsent(JasperDesign design, String name, Class<?> valueClass) {
        if (design.getParametersMap().containsKey(name)) {
            return;
        }
        JRDesignParameter parameter = new JRDesignParameter();
        parameter.setName(name);
        parameter.setValueClass(valueClass);
        try {
            design.addParameter(parameter);
        } catch (JRException exception) {
            throw new ReportExportException("Unable to prepare report parameter.", exception);
        }
    }

    private void expandPrintableArea(JasperDesign design) {
        int printableWidth = design.getPageWidth() - (PAGE_MARGIN * 2);
        design.setLeftMargin(PAGE_MARGIN);
        design.setRightMargin(PAGE_MARGIN);
        design.setColumnWidth(printableWidth);
    }

    private void stretchTitleAndFooter(JasperDesign design) {
        stretchBandTextFields(design.getTitle(), design.getColumnWidth());
        stretchBandTextFields(design.getPageFooter(), design.getColumnWidth());
    }

    private void stretchBandTextFields(net.sf.jasperreports.engine.JRBand band, int width) {
        if (band == null) {
            return;
        }
        for (JRElement element : band.getElements()) {
            if (element instanceof JRDesignElement designElement) {
                designElement.setX(0);
                designElement.setWidth(width);
            }
        }
    }

    private void layoutTableColumns(JasperDesign design, List<String> headers) {
        int activeColumns = Math.min(Math.max(headers.size(), 1), MAX_EXPORT_COLUMNS);
        layoutBandColumns(design.getColumnHeader(), "$P{header", activeColumns, design.getColumnWidth());
        JRSection detailSection = design.getDetailSection();
        if (detailSection != null) {
            for (var band : detailSection.getBands()) {
                layoutBandColumns(band, "$F{column", activeColumns, design.getColumnWidth());
            }
        }
    }

    private void layoutBandColumns(net.sf.jasperreports.engine.JRBand band, String expressionPrefix,
                                   int activeColumns, int printableWidth) {
        if (band == null) {
            return;
        }
        int baseWidth = printableWidth / activeColumns;
        int remainder = printableWidth % activeColumns;
        for (JRElement element : band.getElements()) {
            if (element instanceof JRDesignTextField textField) {
                int columnIndex = columnIndex(textField, expressionPrefix);
                if (columnIndex < 0) {
                    continue;
                }
                JRDesignElement designElement = textField;
                if (columnIndex >= activeColumns) {
                    designElement.setX(printableWidth - 1);
                    designElement.setWidth(1);
                    continue;
                }
                int x = columnIndex * baseWidth + Math.min(columnIndex, remainder);
                int width = baseWidth + (columnIndex < remainder ? 1 : 0);
                designElement.setX(x);
                designElement.setWidth(width);
            }
        }
    }

    private void styleBand(net.sf.jasperreports.engine.JRBand band, ElementRole role, List<String> headers,
                           JRDesignStyle detailStyle) {
        if (band == null) {
            return;
        }
        for (JRElement element : band.getElements()) {
            if (element instanceof JRDesignTextField textField) {
                styleTextField(textField, role, headers, detailStyle);
            }
        }
    }

    private void styleTextField(JRDesignTextField textField, ElementRole role, List<String> headers,
                                JRDesignStyle detailStyle) {
        textField.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        textField.setFontSize(role == ElementRole.TITLE ? 13f : 11f);
        textField.setForecolor(TEXT_COLOR);
        if (role == ElementRole.COLUMN_HEADER) {
            textField.setFontName(BODY_FONT);
            textField.setBold(Boolean.TRUE);
            styleHeaderExpression(textField);
            textField.setMode(ModeEnum.OPAQUE);
            textField.setBackcolor(HEADER_BACKGROUND);
            textField.setForecolor(HEADER_FOREGROUND);
            textField.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        } else if (role == ElementRole.DETAIL) {
            textField.setStyle(detailStyle);
            textField.setMode(ModeEnum.OPAQUE);
            textField.setBackcolor(Color.WHITE);
            int columnIndex = columnIndex(textField, "$F{column");
            textField.setHorizontalTextAlign(isAmountColumn(headers, columnIndex)
                    ? HorizontalTextAlignEnum.RIGHT
                    : HorizontalTextAlignEnum.LEFT);
        } else if (role == ElementRole.TITLE) {
            textField.setFontName(BODY_FONT);
            textField.setBold(Boolean.TRUE);
            textField.setForecolor(TITLE_COLOR);
            styleTitleExpression(textField);
            if (expressionText(textField).contains("$P{filters}")) {
                textField.setFontSize(11f);
                textField.setBold(Boolean.FALSE);
                textField.setMarkup("styled");
                textField.setMode(ModeEnum.OPAQUE);
                textField.setBackcolor(FILTER_BACKGROUND);
                textField.setForecolor(TEXT_COLOR);
            }
            if (expressionText(textField).contains("$P{generatedAt}")) {
                textField.setFontSize(8.5f);
                textField.setBold(Boolean.FALSE);
                textField.setMarkup("styled");
                textField.setExpression(new JRDesignExpression("\"<style isBold=\\\"true\\\">Generated:</style> \""
                        + " + $P{generatedAt} + \"    <style isBold=\\\"true\\\">Generated By:</style> \""
                        + " + $P{generatedBy} + \"    <style isBold=\\\"true\\\">Rows:</style> \" + $P{rowCount}"));
            }
        }
    }

    private void styleTitleExpression(JRDesignTextField textField) {
        String expression = expressionText(textField);
        if (expression.contains("$P{organizationName}")) {
            textField.setMarkup("styled");
            textField.setExpression(new JRDesignExpression("\"<style isBold=\\\"true\\\">\" + $P{organizationName} + \"</style>\""));
        } else if (expression.contains("$P{reportTitle}")) {
            textField.setMarkup("styled");
            textField.setExpression(new JRDesignExpression("\"<style isBold=\\\"true\\\">\" + $P{reportTitle} + \"</style>\""));
        }
    }

    private void styleHeaderExpression(JRDesignTextField textField) {
        int headerIndex = columnIndex(textField, "$P{header");
        if (headerIndex < 0 || headerIndex >= MAX_EXPORT_COLUMNS) {
            return;
        }
        textField.setMarkup("styled");
        String parameterName = "header" + (headerIndex + 1);
        textField.setExpression(new JRDesignExpression("\"<style isBold=\\\"true\\\">\" + $P{" + parameterName + "} + \"</style>\""));
    }

    private boolean isAmountColumn(List<String> headers, int zeroBasedColumn) {
        return zeroBasedColumn >= 0
                && zeroBasedColumn < headers.size()
                && amountHeader(headers.get(zeroBasedColumn));
    }

    private boolean amountHeader(String header) {
        if (header == null) {
            return false;
        }
        String normalized = header.toLowerCase();
        return normalized.contains("amount")
                || normalized.contains("total")
                || normalized.contains("collection")
                || normalized.contains("offertory")
                || normalized.contains("tithes")
                || normalized.contains("donation");
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

    private String generatedBy() {
        return AuthContext.getCurrentUser()
                .map(this::displayName)
                .orElse("System");
    }

    private String displayName(AuthenticatedUser user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().strip();
        }
        return user.getUsername() == null || user.getUsername().isBlank() ? "System" : user.getUsername().strip();
    }

    private String boldLabel(String text) {
        return "<style isBold=\"true\">" + text + "</style>";
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
