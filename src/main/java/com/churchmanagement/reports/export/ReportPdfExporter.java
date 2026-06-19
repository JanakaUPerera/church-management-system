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
import net.sf.jasperreports.engine.base.JRBasePrintImage;
import net.sf.jasperreports.engine.base.JRBasePrintPage;
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
import net.sf.jasperreports.engine.type.OnErrorTypeEnum;
import net.sf.jasperreports.engine.type.ScaleImageEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.renderers.SimpleDataRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ReportPdfExporter {
    private static final int PAGE_MARGIN = 20;
    private static final int MAX_EXPORT_COLUMNS = 12;
    private static final int MAX_WEEKLY_CHURCH_CHART_POINTS = 20;
    private static final int PDF_CHART_SCALE = 3;
    private static final int PDF_CHART_WIDTH = 1400;
    private static final int PDF_CHART_HEIGHT = 1040;
    private static final String BODY_FONT = "Noto Sans";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final Color TITLE_COLOR = new Color(31, 78, 121);
    private static final Color HEADER_BACKGROUND = new Color(31, 78, 121);
    private static final Color HEADER_FOREGROUND = Color.WHITE;
    private static final Color FILTER_BACKGROUND = new Color(238, 244, 250);
    private static final Color DETAIL_ALTERNATE_BACKGROUND = new Color(248, 250, 252);
    private static final Color TEXT_COLOR = new Color(31, 41, 55);
    private static final Color GRID_COLOR = new Color(229, 231, 235);
    private static final Color[] DISTINCT_CHART_COLORS = {
            new Color(37, 99, 235), new Color(220, 38, 38), new Color(22, 163, 74),
            new Color(245, 158, 11), new Color(124, 58, 237), new Color(8, 145, 178),
            new Color(219, 39, 119), new Color(101, 163, 13), new Color(234, 88, 12),
            new Color(79, 70, 229), new Color(13, 148, 136), new Color(190, 18, 60),
            new Color(147, 51, 234), new Color(2, 132, 199), new Color(202, 138, 4),
            new Color(21, 128, 61), new Color(185, 28, 28), new Color(109, 40, 217),
            new Color(3, 105, 161), new Color(161, 98, 7)
    };

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
                    compileReport(template, reportType, rows),
                    parameters,
                    new JRBeanCollectionDataSource(exportRows(reportType, rows, totals)));
            appendReportChartPages(jasperPrint, reportType, rows);
            JasperExportManager.exportReportToPdfFile(jasperPrint, output.toString());
            return output;
        } catch (Exception exception) {
            throw new ReportExportException("Export failed.", exception);
        }
    }

    private <T extends ReportTableRow> net.sf.jasperreports.engine.JasperReport compileReport(InputStream template,
                                                                                              ReportType reportType,
                                                                                              List<T> rows)
            throws JRException {
        JasperDesign design = JRXmlLoader.load(template);
        List<String> headers = exportHeaders(reportType, rows);
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
        List<String> headers = exportHeaders(reportType, rows);
        for (int index = 0; index < MAX_EXPORT_COLUMNS; index++) {
            parameters.put("header" + (index + 1), index < headers.size() ? headers.get(index) : "");
        }
        return parameters;
    }

    private <T extends ReportTableRow> List<String> exportHeaders(ReportType reportType, List<T> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : pdfColumns(reportType, rows.getFirst().columns()).keySet().stream().toList();
    }

    private <T extends ReportTableRow> List<LinkedHashMap<String, Object>> rowsWithTotals(ReportType reportType,
                                                                                          List<T> rows,
                                                                                          ReportSummaryTotals totals) {
        List<LinkedHashMap<String, Object>> exportRows = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return exportRows;
        }
        rows.stream().map(ReportTableRow::columns).map(columns -> pdfColumns(reportType, columns)).forEach(exportRows::add);
        if (totals != null) {
            totalsRow(pdfColumns(reportType, rows.getFirst().columns()), totals).ifPresent(exportRows::add);
        }
        return exportRows;
    }

    private LinkedHashMap<String, Object> pdfColumns(ReportType reportType, LinkedHashMap<String, Object> columns) {
        LinkedHashMap<String, Object> pdfColumns = new LinkedHashMap<>(columns);
        if (reportType == ReportType.SUBMISSION_STATUS) {
            pdfColumns.remove("Region");
        }
        return pdfColumns;
    }

    private <T extends ReportTableRow> List<ReportExportRow> exportRows(ReportType reportType, List<T> rows,
                                                                        ReportSummaryTotals totals) {
        List<LinkedHashMap<String, Object>> exportRows = rowsWithTotals(reportType, rows, totals);
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
            case "Offerings" -> totals.getOffertoryTotal();
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

    private boolean pdfChartsEnabled() {
        String value = SystemConfigurationCache.getInstance().getString("reports.pdf.charts.enabled");
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }

    private <T extends ReportTableRow> void appendReportChartPages(JasperPrint jasperPrint, ReportType reportType,
                                                                    List<T> rows) throws Exception {
        if (!pdfChartsEnabled() || rows == null || rows.isEmpty()) {
            return;
        }
        if (reportType == ReportType.SUBMISSION_STATUS) {
            List<ChartPoint> statusPoints = statusChartPoints(rows);
            if (!statusPoints.isEmpty()) {
                jasperPrint.addPage(chartPrintPage(jasperPrint, pieChartImage(reportType, statusPoints)));
            }
            return;
        }
        if (supportsMonthlyCollectionCharts(reportType)) {
            List<PeriodCollectionSeries> topSeries = topMonthlyCollections(reportType, rows);
            List<MonthPeriod> piePeriods = monthlyPeriods(rows, true);
            List<MonthPeriod> barPeriods = monthlyPeriods(rows, false);
            if (!topSeries.isEmpty() && !barPeriods.isEmpty()) {
                jasperPrint.addPage(chartPrintPage(jasperPrint, monthlyCollectionBarChartImage(reportType,
                        topSeries, barPeriods)));
            }
            if (barPeriods.size() > 1) {
                List<CollectionTypePeriodTotal> typePeriods = monthlyCollectionTypePeriodTotals(rows, barPeriods);
                if (!typePeriods.isEmpty()) {
                    jasperPrint.addPage(chartPrintPage(jasperPrint, monthlyCollectionTypeBarChartImage(reportType,
                            typePeriods)));
                }
            }
            for (MonthPeriod period : piePeriods) {
                List<ChartPoint> collectionTypePoints = monthlyCollectionTypePoints(rows, period);
                if (!collectionTypePoints.isEmpty()) {
                    jasperPrint.addPage(chartPrintPage(jasperPrint, monthlyCollectionTypePieChartImage(reportType,
                            period, collectionTypePoints, piePeriods.size())));
                }
            }
            return;
        }
        if (supportsAnnualCollectionCharts(reportType)) {
            List<AnnualCollectionSeries> topSeries = topAnnualCollections(reportType, rows);
            List<Integer> pieYears = annualYears(rows);
            List<Integer> barYears = annualBarYears(rows);
            if (!topSeries.isEmpty() && !barYears.isEmpty()) {
                jasperPrint.addPage(chartPrintPage(jasperPrint, annualCollectionBarChartImage(reportType, topSeries, barYears)));
            }
            if (barYears.size() > 1) {
                List<CollectionTypePeriodTotal> typeYears = annualCollectionTypePeriodTotals(rows, barYears);
                if (!typeYears.isEmpty()) {
                    jasperPrint.addPage(chartPrintPage(jasperPrint, annualCollectionTypeBarChartImage(reportType,
                            typeYears)));
                }
            }
            for (Integer year : pieYears) {
                List<ChartPoint> collectionTypePoints = annualCollectionTypePoints(rows, year);
                if (!collectionTypePoints.isEmpty()) {
                    jasperPrint.addPage(chartPrintPage(jasperPrint, annualCollectionTypePieChartImage(reportType,
                            year, collectionTypePoints, pieYears.size())));
                }
            }
            return;
        }
        if (!supportsWeeklyCollectionCharts(reportType)) {
            return;
        }
        List<ChartPoint> topPoints = topWeeklyCollectionPoints(reportType, rows);
        List<ChartPoint> collectionTypePoints = weeklyCollectionTypePoints(rows);
        if (!topPoints.isEmpty()) {
            jasperPrint.addPage(chartPrintPage(jasperPrint, barChartImage(reportType, topPoints)));
        }
        if (!collectionTypePoints.isEmpty()) {
            jasperPrint.addPage(chartPrintPage(jasperPrint, pieChartImage(reportType, collectionTypePoints)));
        }
    }

    private boolean supportsWeeklyCollectionCharts(ReportType reportType) {
        return reportType == ReportType.WEEKLY_CHURCH_COLLECTION || reportType == ReportType.WEEKLY_REGION_SUMMARY;
    }

    private boolean supportsAnnualCollectionCharts(ReportType reportType) {
        return reportType == ReportType.CHURCH_ANNUAL_COLLECTION || reportType == ReportType.REGION_ANNUAL_COLLECTION;
    }

    private boolean supportsMonthlyCollectionCharts(ReportType reportType) {
        return reportType == ReportType.CHURCH_MONTHLY_COLLECTION || reportType == ReportType.REGION_MONTHLY_COLLECTION;
    }

    private BufferedImage barChartImage(ReportType reportType, List<ChartPoint> topPoints) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawBarChart(graphics, topPoints, barChartTitle(reportType), barChartCategory(reportType),
                    28, 40, 1344, 910);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage pieChartImage(ReportType reportType, List<ChartPoint> collectionTypePoints) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawPieChart(graphics, collectionTypePoints, pieChartTitle(reportType), pieChartNote(reportType),
                    reportType != ReportType.SUBMISSION_STATUS, 58, 50, 1284, 820);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage annualCollectionBarChartImage(ReportType reportType, List<AnnualCollectionSeries> topSeries,
                                                        List<Integer> years) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawAnnualCollectionBarChart(graphics, reportType, topSeries, years, 28, 40, 1344, 910);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage annualCollectionTypePieChartImage(ReportType reportType, Integer year,
                                                            List<ChartPoint> collectionTypePoints,
                                                            int yearCount) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            String title = yearCount > 1
                    ? "Collection Type-wise (" + year + ")"
                    : annualCollectionPieScopeTitle(reportType);
            String note = yearCount > 1
                    ? "Pie chart uses all " + annualCollectionEntityName(reportType).toLowerCase() + "s in " + year
                    + ", not only the Top 20."
                    : annualCollectionPieNote(reportType);
            drawPieChart(graphics, collectionTypePoints, title, note, true, 58, 50, 1284, 820);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage monthlyCollectionBarChartImage(ReportType reportType, List<PeriodCollectionSeries> topSeries,
                                                         List<MonthPeriod> periods) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawMonthlyCollectionBarChart(graphics, reportType, topSeries, periods, 28, 40, 1344, 910);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage monthlyCollectionTypePieChartImage(ReportType reportType, MonthPeriod period,
                                                             List<ChartPoint> collectionTypePoints, int periodCount) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            String title = periodCount > 1
                    ? "Collection Type-wise (" + period.label() + ")"
                    : monthlyCollectionPieScopeTitle(reportType);
            String note = periodCount > 1
                    ? "Pie chart uses all " + monthlyCollectionEntityName(reportType).toLowerCase() + "s in "
                    + period.label() + ", not only the Top 20."
                    : monthlyCollectionPieNote(reportType);
            drawPieChart(graphics, collectionTypePoints, title, note, true, 58, 50, 1284, 820);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage annualCollectionTypeBarChartImage(ReportType reportType,
                                                            List<CollectionTypePeriodTotal> periodTotals) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawCollectionTypeComparisonBarChart(graphics, annualCollectionTypeBarTitle(reportType), "Year",
                    periodTotals, 28, 40, 1344, 910);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage monthlyCollectionTypeBarChartImage(ReportType reportType,
                                                             List<CollectionTypePeriodTotal> periodTotals) {
        BufferedImage image = new BufferedImage(PDF_CHART_WIDTH * PDF_CHART_SCALE,
                PDF_CHART_HEIGHT * PDF_CHART_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(PDF_CHART_SCALE, PDF_CHART_SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_CHART_WIDTH, PDF_CHART_HEIGHT);
            drawCollectionTypeComparisonBarChart(graphics, monthlyCollectionTypeBarTitle(reportType), "Month",
                    periodTotals, 28, 40, 1344, 910);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private JRBasePrintPage chartPrintPage(JasperPrint jasperPrint, BufferedImage image) throws Exception {
        JRBasePrintPage page = new JRBasePrintPage();
        JRBasePrintImage printImage = new JRBasePrintImage(jasperPrint.getDefaultStyleProvider());
        int margin = PAGE_MARGIN * 2;
        printImage.setX(margin);
        printImage.setY(margin);
        printImage.setWidth(jasperPrint.getPageWidth() - (margin * 2));
        printImage.setHeight(jasperPrint.getPageHeight() - (margin * 2));
        printImage.setScaleImage(ScaleImageEnum.RETAIN_SHAPE);
        printImage.setOnErrorType(OnErrorTypeEnum.BLANK);
        printImage.setRenderer(SimpleDataRenderer.getInstance(pngBytes(image)));
        page.addElement(printImage);
        return page;
    }

    private byte[] pngBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void drawBarChart(Graphics2D graphics, List<ChartPoint> points, String title, String categoryTitle,
                              int x, int y, int width, int height) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 34));
        graphics.drawString(title, x, y);
        if (points.isEmpty()) {
            drawNoData(graphics, x, y + 35, width, height - 35);
            return;
        }

        int plotX = x + 124;
        int plotY = y + 170;
        int plotWidth = width - 145;
        int plotHeight = 510;
        AxisBounds bounds = axisBounds(points);
        for (int line = 0; line <= 6; line++) {
            int lineY = plotY + (plotHeight * line / 6);
            graphics.setColor(GRID_COLOR);
            graphics.drawLine(plotX, lineY, plotX + plotWidth, lineY);
            double labelValue = bounds.max() - ((bounds.max() - bounds.min()) * line / 6.0);
            graphics.setColor(TEXT_COLOR);
            graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
            graphics.drawString(AMOUNT_FORMAT.format(labelValue), x + 38, lineY + 7);
        }
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.drawLine(plotX, plotY, plotX, plotY + plotHeight);
        graphics.drawLine(plotX, plotY + plotHeight, plotX + plotWidth, plotY + plotHeight);

        int gap = 8;
        int barWidth = Math.max(28, (plotWidth - (points.size() - 1) * gap) / points.size());
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
        for (int index = 0; index < points.size(); index++) {
            ChartPoint point = points.get(index);
            double ratio = (point.value().doubleValue() - bounds.min()) / (bounds.max() - bounds.min());
            int barHeight = Math.max(1, (int) Math.round(ratio * plotHeight));
            int barX = plotX + index * (barWidth + gap);
            int barY = plotY + plotHeight - barHeight;
            graphics.setColor(chartColor(index));
            graphics.fillRect(barX, barY, barWidth, barHeight);
            drawBarValue(graphics, point.value(), barX, barY, barWidth);
            drawXAxisIndex(graphics, index + 1, barX, plotY + plotHeight + 34, barWidth);
        }

        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 22));
        drawCenteredString(graphics, categoryTitle, plotX, plotY + plotHeight + 76, plotWidth);
        drawVerticalAxisTitle(graphics, "Grand Total", x + 20, plotY, plotHeight);
        drawNumberedLegend(graphics, points, plotX, plotY + plotHeight + 126, plotWidth, 5);
    }

    private void drawAnnualCollectionBarChart(Graphics2D graphics, ReportType reportType,
                                              List<AnnualCollectionSeries> seriesRows,
                                              List<Integer> years, int x, int y, int width, int height) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 34));
        graphics.drawString(annualCollectionBarTitle(reportType), x, y);
        if (seriesRows.isEmpty() || years.isEmpty()) {
            drawNoData(graphics, x, y + 35, width, height - 35);
            return;
        }

        int plotX = x + 124;
        int plotY = y + 170;
        int plotWidth = width - 145;
        int plotHeight = 510;
        AxisBounds bounds = annualAxisBounds(seriesRows, years);
        for (int line = 0; line <= 6; line++) {
            int lineY = plotY + (plotHeight * line / 6);
            graphics.setColor(GRID_COLOR);
            graphics.drawLine(plotX, lineY, plotX + plotWidth, lineY);
            double labelValue = bounds.max() - ((bounds.max() - bounds.min()) * line / 6.0);
            graphics.setColor(TEXT_COLOR);
            graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
            graphics.drawString(AMOUNT_FORMAT.format(labelValue), x + 38, lineY + 7);
        }
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.drawLine(plotX, plotY, plotX, plotY + plotHeight);
        graphics.drawLine(plotX, plotY + plotHeight, plotX + plotWidth, plotY + plotHeight);

        int groupWidth = Math.max(1, plotWidth / seriesRows.size());
        int groupGap = years.size() == 1 ? 8 : 10;
        int innerGap = years.size() == 1 ? 0 : 3;
        int barWidth = Math.max(6, (groupWidth - groupGap - ((years.size() - 1) * innerGap)) / years.size());
        int usedGroupWidth = (barWidth * years.size()) + (innerGap * Math.max(0, years.size() - 1));
        for (int entityIndex = 0; entityIndex < seriesRows.size(); entityIndex++) {
            AnnualCollectionSeries series = seriesRows.get(entityIndex);
            int groupX = plotX + entityIndex * groupWidth + Math.max(0, (groupWidth - usedGroupWidth) / 2);
            for (int yearIndex = 0; yearIndex < years.size(); yearIndex++) {
                BigDecimal value = series.yearTotals().getOrDefault(years.get(yearIndex), BigDecimal.ZERO);
                double ratio = (value.doubleValue() - bounds.min()) / (bounds.max() - bounds.min());
                int barHeight = Math.max(1, (int) Math.round(ratio * plotHeight));
                int barX = groupX + yearIndex * (barWidth + innerGap);
                int barY = plotY + plotHeight - barHeight;
                graphics.setColor(annualBarColor(yearIndex, entityIndex));
                graphics.fillRect(barX, barY, barWidth, barHeight);
                if (years.size() == 1 || barWidth >= 18) {
                    drawBarValue(graphics, value, barX, barY, barWidth);
                }
            }
            drawXAxisIndex(graphics, entityIndex + 1, groupX, plotY + plotHeight + 34, usedGroupWidth);
        }

        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 22));
        drawCenteredString(graphics, annualCollectionCategoryTitle(reportType), plotX, plotY + plotHeight + 76, plotWidth);
        drawVerticalAxisTitle(graphics, "Grand Total", x + 20, plotY, plotHeight);
        drawYearLegend(graphics, years, plotX + 760, y + 12);
        drawAnnualNumberedLegend(graphics, seriesRows, plotX, plotY + plotHeight + 126, plotWidth, 5);
    }

    private void drawMonthlyCollectionBarChart(Graphics2D graphics, ReportType reportType,
                                               List<PeriodCollectionSeries> seriesRows, List<MonthPeriod> periods,
                                               int x, int y, int width, int height) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 34));
        graphics.drawString(monthlyCollectionBarTitle(reportType), x, y);
        if (seriesRows.isEmpty() || periods.isEmpty()) {
            drawNoData(graphics, x, y + 35, width, height - 35);
            return;
        }

        int plotX = x + 124;
        int plotY = y + 170;
        int plotWidth = width - 145;
        int plotHeight = 510;
        AxisBounds bounds = monthlyAxisBounds(seriesRows, periods);
        for (int line = 0; line <= 6; line++) {
            int lineY = plotY + (plotHeight * line / 6);
            graphics.setColor(GRID_COLOR);
            graphics.drawLine(plotX, lineY, plotX + plotWidth, lineY);
            double labelValue = bounds.max() - ((bounds.max() - bounds.min()) * line / 6.0);
            graphics.setColor(TEXT_COLOR);
            graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
            graphics.drawString(AMOUNT_FORMAT.format(labelValue), x + 38, lineY + 7);
        }
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.drawLine(plotX, plotY, plotX, plotY + plotHeight);
        graphics.drawLine(plotX, plotY + plotHeight, plotX + plotWidth, plotY + plotHeight);

        int groupWidth = Math.max(1, plotWidth / seriesRows.size());
        int groupGap = periods.size() == 1 ? 8 : 10;
        int innerGap = periods.size() == 1 ? 0 : 3;
        int barWidth = Math.max(6, (groupWidth - groupGap - ((periods.size() - 1) * innerGap)) / periods.size());
        int usedGroupWidth = (barWidth * periods.size()) + (innerGap * Math.max(0, periods.size() - 1));
        for (int entityIndex = 0; entityIndex < seriesRows.size(); entityIndex++) {
            PeriodCollectionSeries series = seriesRows.get(entityIndex);
            int groupX = plotX + entityIndex * groupWidth + Math.max(0, (groupWidth - usedGroupWidth) / 2);
            for (int periodIndex = 0; periodIndex < periods.size(); periodIndex++) {
                BigDecimal value = series.periodTotals().getOrDefault(periods.get(periodIndex), BigDecimal.ZERO);
                double ratio = (value.doubleValue() - bounds.min()) / (bounds.max() - bounds.min());
                int barHeight = Math.max(1, (int) Math.round(ratio * plotHeight));
                int barX = groupX + periodIndex * (barWidth + innerGap);
                int barY = plotY + plotHeight - barHeight;
                graphics.setColor(annualBarColor(periodIndex, entityIndex));
                graphics.fillRect(barX, barY, barWidth, barHeight);
                if (periods.size() == 1 || barWidth >= 18) {
                    drawBarValue(graphics, value, barX, barY, barWidth);
                }
            }
            drawXAxisIndex(graphics, entityIndex + 1, groupX, plotY + plotHeight + 34, usedGroupWidth);
        }

        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 22));
        drawCenteredString(graphics, monthlyCollectionCategoryTitle(reportType), plotX, plotY + plotHeight + 76, plotWidth);
        drawVerticalAxisTitle(graphics, "Grand Total", x + 20, plotY, plotHeight);
        drawMonthLegend(graphics, periods, plotX + 630, y + 12);
        drawPeriodNumberedLegend(graphics, seriesRows, plotX, plotY + plotHeight + 126, plotWidth, 5);
    }

    private void drawCollectionTypeComparisonBarChart(Graphics2D graphics, String title, String categoryTitle,
                                                      List<CollectionTypePeriodTotal> periodTotals,
                                                      int x, int y, int width, int height) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 34));
        graphics.drawString(title, x, y);
        if (periodTotals.isEmpty()) {
            drawNoData(graphics, x, y + 35, width, height - 35);
            return;
        }

        int plotX = x + 124;
        int plotY = y + 170;
        int plotWidth = width - 145;
        int plotHeight = 510;
        AxisBounds bounds = collectionTypePeriodAxisBounds(periodTotals);
        for (int line = 0; line <= 6; line++) {
            int lineY = plotY + (plotHeight * line / 6);
            graphics.setColor(GRID_COLOR);
            graphics.drawLine(plotX, lineY, plotX + plotWidth, lineY);
            double labelValue = bounds.max() - ((bounds.max() - bounds.min()) * line / 6.0);
            graphics.setColor(TEXT_COLOR);
            graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
            graphics.drawString(AMOUNT_FORMAT.format(labelValue), x + 38, lineY + 7);
        }
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.drawLine(plotX, plotY, plotX, plotY + plotHeight);
        graphics.drawLine(plotX, plotY + plotHeight, plotX + plotWidth, plotY + plotHeight);

        int seriesCount = 3;
        int groupWidth = Math.max(1, plotWidth / periodTotals.size());
        int groupGap = 14;
        int innerGap = 4;
        int barWidth = Math.max(14, (groupWidth - groupGap - ((seriesCount - 1) * innerGap)) / seriesCount);
        int usedGroupWidth = (barWidth * seriesCount) + (innerGap * (seriesCount - 1));
        for (int periodIndex = 0; periodIndex < periodTotals.size(); periodIndex++) {
            CollectionTypePeriodTotal total = periodTotals.get(periodIndex);
            int groupX = plotX + periodIndex * groupWidth + Math.max(0, (groupWidth - usedGroupWidth) / 2);
            List<BigDecimal> values = List.of(total.offertory(), total.tithes(), total.otherDonations());
            for (int seriesIndex = 0; seriesIndex < values.size(); seriesIndex++) {
                BigDecimal value = values.get(seriesIndex);
                double ratio = (value.doubleValue() - bounds.min()) / (bounds.max() - bounds.min());
                int barHeight = Math.max(1, (int) Math.round(ratio * plotHeight));
                int barX = groupX + seriesIndex * (barWidth + innerGap);
                int barY = plotY + plotHeight - barHeight;
                graphics.setColor(chartColor(seriesIndex));
                graphics.fillRect(barX, barY, barWidth, barHeight);
                if (barWidth >= 18) {
                    drawBarValue(graphics, value, barX, barY, barWidth);
                }
            }
            drawPeriodCategoryLabel(graphics, total.label(), groupX, plotY + plotHeight + 32, usedGroupWidth);
        }

        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 22));
        drawCenteredString(graphics, categoryTitle, plotX, plotY + plotHeight + 88, plotWidth);
        drawVerticalAxisTitle(graphics, "Grand Total", x + 20, plotY, plotHeight);
        drawSeriesLegend(graphics, List.of("Offerings", "Tithes", "Other Donations"), plotX + 660, y + 12);
    }

    private void drawBarValue(Graphics2D graphics, BigDecimal value, int barX, int barY, int barWidth) {
        String label = AMOUNT_FORMAT.format(value == null ? BigDecimal.ZERO : value);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 20));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(TEXT_COLOR);
        java.awt.geom.AffineTransform original = graphics.getTransform();
        int labelX = barX + (barWidth / 2) - (metrics.getHeight() / 4);
        int labelY = Math.max(58 + metrics.stringWidth(label), barY - 6);
        graphics.translate(labelX, labelY);
        graphics.rotate(-Math.PI / 2);
        graphics.drawString(label, 0, 0);
        graphics.setTransform(original);
    }

    private void drawXAxisIndex(Graphics2D graphics, int index, int barX, int y, int barWidth) {
        String label = Integer.toString(index);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 19));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(TEXT_COLOR);
        graphics.drawString(label, barX + Math.max(0, (barWidth - metrics.stringWidth(label)) / 2), y);
    }

    private void drawPieChart(Graphics2D graphics, List<ChartPoint> points, String title, String note,
                              boolean amountValues,
                              int x, int y, int width, int height) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 34));
        graphics.drawString(title, x, y);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 20));
        graphics.drawString(note, x, y + 38);
        if (points.isEmpty()) {
            drawNoData(graphics, x, y + 64, width, height - 64);
            return;
        }

        int diameter = 430;
        int pieX = x + 130;
        int pieY = y + 150;
        BigDecimal total = points.stream().map(ChartPoint::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        int startAngle = 90;
        int remainingAngle = 360;
        for (int index = 0; index < points.size(); index++) {
            ChartPoint point = points.get(index);
            int arc = index == points.size() - 1
                    ? remainingAngle
                    : point.value().multiply(BigDecimal.valueOf(360)).divide(total, 0,
                    java.math.RoundingMode.HALF_UP).intValue();
            arc = Math.max(0, Math.min(arc, remainingAngle));
            graphics.setColor(chartColor(index));
            graphics.fillArc(pieX, pieY, diameter, diameter, startAngle, -arc);
            graphics.setColor(Color.WHITE);
            graphics.drawArc(pieX, pieY, diameter, diameter, startAngle, -arc);
            startAngle -= arc;
            remainingAngle -= arc;
        }

        int labelX = pieX + diameter + 110;
        int labelY = pieY + 80;
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 28));
        for (int index = 0; index < points.size(); index++) {
            ChartPoint point = points.get(index);
            graphics.setColor(chartColor(index));
            graphics.fillRect(labelX, labelY + index * 64 - 24, 26, 26);
            graphics.setColor(TEXT_COLOR);
            String label = point.label() + " - " + formatChartValue(point.value(), amountValues)
                    + " (" + percentage(point.value(), total) + "%)";
            graphics.drawString(label, labelX + 42, labelY + index * 64);
        }
    }

    private String formatChartValue(BigDecimal value, boolean amountValue) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return amountValue ? AMOUNT_FORMAT.format(safeValue) : safeValue.toBigInteger().toString();
    }

    private void drawNumberedLegend(Graphics2D graphics, List<ChartPoint> points, int x, int y, int width, int columns) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemWidth = Math.max(220, width / columns);
        for (int index = 0; index < points.size(); index++) {
            int itemX = x + (index % columns) * itemWidth;
            int itemY = y + (index / columns) * 34;
            graphics.setColor(chartColor(index));
            graphics.fillRect(itemX, itemY - 16, 16, 16);
            graphics.setColor(TEXT_COLOR);
            String label = (index + 1) + ". " + points.get(index).label();
            graphics.drawString(ellipsize(label, metrics, itemWidth - 38), itemX + 26, itemY);
        }
    }

    private void drawAnnualNumberedLegend(Graphics2D graphics, List<AnnualCollectionSeries> churches, int x, int y,
                                          int width, int columns) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemWidth = Math.max(220, width / columns);
        for (int index = 0; index < churches.size(); index++) {
            int itemX = x + (index % columns) * itemWidth;
            int itemY = y + (index / columns) * 34;
            graphics.setColor(annualBarColor(0, index));
            graphics.fillRect(itemX, itemY - 16, 16, 16);
            graphics.setColor(TEXT_COLOR);
            String label = (index + 1) + ". " + churches.get(index).label();
            graphics.drawString(ellipsize(label, metrics, itemWidth - 38), itemX + 26, itemY);
        }
    }

    private void drawYearLegend(Graphics2D graphics, List<Integer> years, int x, int y) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemX = x;
        for (int index = 0; index < years.size(); index++) {
            String label = years.get(index).toString();
            graphics.setColor(annualBarColor(index, 0));
            graphics.fillRect(itemX, y, 18, 18);
            graphics.setColor(TEXT_COLOR);
            graphics.drawString(label, itemX + 28, y + 17);
            itemX += 48 + metrics.stringWidth(label);
        }
    }

    private void drawMonthLegend(Graphics2D graphics, List<MonthPeriod> periods, int x, int y) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemX = x;
        int itemY = y;
        for (int index = 0; index < periods.size(); index++) {
            String label = periods.get(index).label();
            int itemWidth = 48 + metrics.stringWidth(label);
            if (itemX + itemWidth > x + 520) {
                itemX = x;
                itemY += 26;
            }
            graphics.setColor(annualBarColor(index, 0));
            graphics.fillRect(itemX, itemY, 18, 18);
            graphics.setColor(TEXT_COLOR);
            graphics.drawString(label, itemX + 28, itemY + 17);
            itemX += itemWidth;
        }
    }

    private void drawSeriesLegend(Graphics2D graphics, List<String> labels, int x, int y) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemX = x;
        for (int index = 0; index < labels.size(); index++) {
            String label = labels.get(index);
            graphics.setColor(chartColor(index));
            graphics.fillRect(itemX, y, 18, 18);
            graphics.setColor(TEXT_COLOR);
            graphics.drawString(label, itemX + 28, y + 17);
            itemX += 48 + metrics.stringWidth(label);
        }
    }

    private void drawPeriodCategoryLabel(Graphics2D graphics, String label, int x, int y, int width) {
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 17));
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(label) <= width + 18) {
            graphics.drawString(label, x + Math.max(0, (width - metrics.stringWidth(label)) / 2), y);
            return;
        }
        java.awt.geom.AffineTransform original = graphics.getTransform();
        int labelX = x + Math.max(12, width / 4);
        graphics.translate(labelX, y + 6);
        graphics.rotate(-Math.PI / 4);
        graphics.drawString(label, 0, 0);
        graphics.setTransform(original);
    }

    private void drawPeriodNumberedLegend(Graphics2D graphics, List<PeriodCollectionSeries> seriesRows, int x, int y,
                                          int width, int columns) {
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        int itemWidth = Math.max(220, width / columns);
        for (int index = 0; index < seriesRows.size(); index++) {
            int itemX = x + (index % columns) * itemWidth;
            int itemY = y + (index / columns) * 34;
            graphics.setColor(annualBarColor(0, index));
            graphics.fillRect(itemX, itemY - 16, 16, 16);
            graphics.setColor(TEXT_COLOR);
            String label = (index + 1) + ". " + seriesRows.get(index).label();
            graphics.drawString(ellipsize(label, metrics, itemWidth - 38), itemX + 26, itemY);
        }
    }

    private void drawVerticalAxisTitle(Graphics2D graphics, String title, int x, int plotY, int plotHeight) {
        java.awt.geom.AffineTransform original = graphics.getTransform();
        graphics.rotate(-Math.PI / 2, x, plotY + (plotHeight / 2));
        graphics.setColor(Color.BLACK);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.BOLD, 19));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(title, x - (metrics.stringWidth(title) / 2), plotY + (plotHeight / 2));
        graphics.setTransform(original);
    }

    private void drawCenteredString(Graphics2D graphics, String text, int x, int y, int width) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, x + Math.max(0, (width - metrics.stringWidth(text)) / 2), y);
    }

    private void drawNoData(Graphics2D graphics, int x, int y, int width, int height) {
        graphics.setColor(GRID_COLOR);
        graphics.drawRect(x, y, width, height);
        graphics.setColor(TEXT_COLOR);
        graphics.setFont(new java.awt.Font(BODY_FONT, java.awt.Font.PLAIN, 13));
        graphics.drawString("No chart data available.", x + 18, y + 34);
    }

    private Color chartColor(int index) {
        return DISTINCT_CHART_COLORS[index % DISTINCT_CHART_COLORS.length];
    }

    private Color annualBarColor(int yearIndex, int churchIndex) {
        Color base = chartColor(yearIndex);
        double factor = 0.03 + (churchIndex % 6) * 0.055;
        int red = (int) Math.round(base.getRed() + (255 - base.getRed()) * factor);
        int green = (int) Math.round(base.getGreen() + (255 - base.getGreen()) * factor);
        int blue = (int) Math.round(base.getBlue() + (255 - base.getBlue()) * factor);
        return new Color(Math.min(255, red), Math.min(255, green), Math.min(255, blue));
    }

    private String ellipsize(String text, FontMetrics metrics, int maxWidth) {
        String value = text == null || text.isBlank() ? "Unlabelled" : text;
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        while (value.length() > 1 && metrics.stringWidth(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private String percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return "0";
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private AxisBounds axisBounds(List<ChartPoint> points) {
        double min = points.stream().map(ChartPoint::value).mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double max = points.stream().map(ChartPoint::value).mapToDouble(BigDecimal::doubleValue).max().orElse(1);
        if (Double.compare(min, max) == 0) {
            min = Math.max(0, min * 0.9);
            max = max * 1.1 + 1;
        } else {
            double padding = (max - min) * 0.10;
            min = Math.max(0, min - padding);
            max = max + padding;
        }
        double step = Math.max(1, Math.pow(10, Math.floor(Math.log10(Math.max(1, max - min))) - 1));
        return new AxisBounds(Math.floor(min / step) * step, Math.ceil(max / step) * step);
    }

    private AxisBounds annualAxisBounds(List<AnnualCollectionSeries> churches, List<Integer> years) {
        List<ChartPoint> points = new ArrayList<>();
        for (AnnualCollectionSeries church : churches) {
            for (Integer year : years) {
                points.add(new ChartPoint(church.label(), church.yearTotals().getOrDefault(year, BigDecimal.ZERO)));
            }
        }
        return axisBounds(points);
    }

    private AxisBounds monthlyAxisBounds(List<PeriodCollectionSeries> seriesRows, List<MonthPeriod> periods) {
        List<ChartPoint> points = new ArrayList<>();
        for (PeriodCollectionSeries series : seriesRows) {
            for (MonthPeriod period : periods) {
                points.add(new ChartPoint(series.label(), series.periodTotals().getOrDefault(period, BigDecimal.ZERO)));
            }
        }
        return axisBounds(points);
    }

    private AxisBounds collectionTypePeriodAxisBounds(List<CollectionTypePeriodTotal> periodTotals) {
        List<ChartPoint> points = new ArrayList<>();
        for (CollectionTypePeriodTotal total : periodTotals) {
            points.add(new ChartPoint(total.label(), total.offertory()));
            points.add(new ChartPoint(total.label(), total.tithes()));
            points.add(new ChartPoint(total.label(), total.otherDonations()));
        }
        return axisBounds(points);
    }

    private <T extends ReportTableRow> List<ChartPoint> topWeeklyCollectionPoints(ReportType reportType, List<T> rows) {
        Map<String, BigDecimal> totalsByLabel = new LinkedHashMap<>();
        String column = reportType == ReportType.WEEKLY_REGION_SUMMARY ? "Region" : "Church";
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            String label = text(columns.get(column));
            totalsByLabel.merge(label.isBlank() ? "Unlabelled" : label, amount(columns.get("Grand Total")),
                    BigDecimal::add);
        }
        return totalsByLabel.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .filter(point -> point.value().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(ChartPoint::value).reversed())
                .limit(MAX_WEEKLY_CHURCH_CHART_POINTS)
                .toList();
    }

    private <T extends ReportTableRow> List<AnnualCollectionSeries> topAnnualCollections(ReportType reportType, List<T> rows) {
        Map<String, AnnualCollectionAccumulator> totalsByLabel = new LinkedHashMap<>();
        String column = annualCollectionCategoryTitle(reportType);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            String label = text(columns.get(column));
            int year = intValue(columns.get("Year"));
            if (year <= 0) {
                continue;
            }
            AnnualCollectionAccumulator accumulator = totalsByLabel.computeIfAbsent(
                    label.isBlank() ? "Unlabelled" : label, AnnualCollectionAccumulator::new);
            BigDecimal value = amount(columns.get("Grand Total"));
            accumulator.yearTotals().merge(year, value, BigDecimal::add);
            accumulator.add(value);
        }
        return totalsByLabel.values().stream()
                .map(accumulator -> new AnnualCollectionSeries(accumulator.label(), accumulator.yearTotals(),
                        accumulator.total()))
                .filter(series -> series.total().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(AnnualCollectionSeries::total).reversed())
                .limit(MAX_WEEKLY_CHURCH_CHART_POINTS)
                .toList();
    }

    private <T extends ReportTableRow> List<PeriodCollectionSeries> topMonthlyCollections(ReportType reportType,
                                                                                           List<T> rows) {
        Map<String, PeriodCollectionAccumulator> totalsByLabel = new LinkedHashMap<>();
        String column = monthlyCollectionCategoryTitle(reportType);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            MonthPeriod period = monthPeriod(columns);
            if (period == null) {
                continue;
            }
            String label = text(columns.get(column));
            PeriodCollectionAccumulator accumulator = totalsByLabel.computeIfAbsent(
                    label.isBlank() ? "Unlabelled" : label, PeriodCollectionAccumulator::new);
            BigDecimal value = amount(columns.get("Grand Total"));
            accumulator.periodTotals().merge(period, value, BigDecimal::add);
            accumulator.add(value);
        }
        return totalsByLabel.values().stream()
                .map(accumulator -> new PeriodCollectionSeries(accumulator.label(), accumulator.periodTotals(),
                        accumulator.total()))
                .filter(series -> series.total().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(PeriodCollectionSeries::total).reversed())
                .limit(MAX_WEEKLY_CHURCH_CHART_POINTS)
                .toList();
    }

    private <T extends ReportTableRow> List<Integer> annualYears(List<T> rows) {
        return rows.stream()
                .map(row -> intValue(row.columns().get("Year")))
                .filter(year -> year > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private <T extends ReportTableRow> List<Integer> annualBarYears(List<T> rows) {
        return rows.stream()
                .map(row -> intValue(row.columns().get("Year")))
                .filter(year -> year > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private <T extends ReportTableRow> List<MonthPeriod> monthlyPeriods(List<T> rows, boolean descending) {
        Comparator<MonthPeriod> comparator = Comparator
                .comparingInt(MonthPeriod::year)
                .thenComparingInt(MonthPeriod::month);
        if (descending) {
            comparator = comparator.reversed();
        }
        return rows.stream()
                .map(ReportTableRow::columns)
                .map(this::monthPeriod)
                .filter(period -> period != null)
                .distinct()
                .sorted(comparator)
                .toList();
    }

    private <T extends ReportTableRow> List<ChartPoint> annualCollectionTypePoints(List<T> rows, Integer year) {
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("Offerings", BigDecimal.ZERO);
        totals.put("Tithes", BigDecimal.ZERO);
        totals.put("Other Donations", BigDecimal.ZERO);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            if (intValue(columns.get("Year")) != year) {
                continue;
            }
            for (String header : totals.keySet()) {
                if (columns.containsKey(header)) {
                    totals.merge(header, amount(columns.get(header)), BigDecimal::add);
                }
            }
        }
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private <T extends ReportTableRow> List<ChartPoint> monthlyCollectionTypePoints(List<T> rows, MonthPeriod period) {
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("Offerings", BigDecimal.ZERO);
        totals.put("Tithes", BigDecimal.ZERO);
        totals.put("Other Donations", BigDecimal.ZERO);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            MonthPeriod rowPeriod = monthPeriod(columns);
            if (!period.equals(rowPeriod)) {
                continue;
            }
            for (String header : totals.keySet()) {
                if (columns.containsKey(header)) {
                    totals.merge(header, amount(columns.get(header)), BigDecimal::add);
                }
            }
        }
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private <T extends ReportTableRow> List<CollectionTypePeriodTotal> annualCollectionTypePeriodTotals(List<T> rows,
                                                                                                          List<Integer> years) {
        List<CollectionTypePeriodTotal> totals = new ArrayList<>();
        for (Integer year : years) {
            totals.add(new CollectionTypePeriodTotal(year.toString(),
                    totalForPeriodAndHeader(rows, row -> intValue(row.columns().get("Year")) == year, "Offerings"),
                    totalForPeriodAndHeader(rows, row -> intValue(row.columns().get("Year")) == year, "Tithes"),
                    totalForPeriodAndHeader(rows, row -> intValue(row.columns().get("Year")) == year, "Other Donations")));
        }
        return totals.stream().filter(this::hasCollectionTypeValue).toList();
    }

    private <T extends ReportTableRow> List<CollectionTypePeriodTotal> monthlyCollectionTypePeriodTotals(List<T> rows,
                                                                                                           List<MonthPeriod> periods) {
        List<CollectionTypePeriodTotal> totals = new ArrayList<>();
        for (MonthPeriod period : periods) {
            totals.add(new CollectionTypePeriodTotal(period.label(),
                    totalForPeriodAndHeader(rows, row -> period.equals(monthPeriod(row.columns())), "Offerings"),
                    totalForPeriodAndHeader(rows, row -> period.equals(monthPeriod(row.columns())), "Tithes"),
                    totalForPeriodAndHeader(rows, row -> period.equals(monthPeriod(row.columns())), "Other Donations")));
        }
        return totals.stream().filter(this::hasCollectionTypeValue).toList();
    }

    private String barChartTitle(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Top 20 Regions by Collection"
                : "Top 20 Churches by Collection";
    }

    private String annualCollectionBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION
                ? "Top 20 Regions by Collection"
                : "Top 20 Churches by Collection";
    }

    private String annualCollectionCategoryTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION ? "Region" : "Church";
    }

    private String annualCollectionEntityName(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION ? "Region" : "Church";
    }

    private String annualCollectionPieScopeTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION
                ? "Collection Type-wise (All Regions)"
                : "Collection Type-wise (All Churches)";
    }

    private String annualCollectionPieNote(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION
                ? "Pie chart uses all regions in this report, not only the Top 20."
                : "Pie chart uses all churches in this report, not only the Top 20.";
    }

    private String annualCollectionTypeBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION
                ? "Collection Types by Year (All Regions)"
                : "Collection Types by Year (All Churches)";
    }

    private String monthlyCollectionBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION
                ? "Top 20 Regions by Collection"
                : "Top 20 Churches by Collection";
    }

    private String monthlyCollectionCategoryTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION ? "Region" : "Church";
    }

    private String monthlyCollectionEntityName(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION ? "Region" : "Church";
    }

    private String monthlyCollectionPieScopeTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION
                ? "Collection Type-wise (All Regions)"
                : "Collection Type-wise (All Churches)";
    }

    private String monthlyCollectionPieNote(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION
                ? "Pie chart uses all regions in this report, not only the Top 20."
                : "Pie chart uses all churches in this report, not only the Top 20.";
    }

    private String monthlyCollectionTypeBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION
                ? "Collection Types by Month (All Regions)"
                : "Collection Types by Month (All Churches)";
    }

    private String barChartCategory(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY ? "Region" : "Church";
    }

    private String pieChartNote(ReportType reportType) {
        if (reportType == ReportType.SUBMISSION_STATUS) {
            return "Pie chart shows churches grouped by submission status.";
        }
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Pie chart uses all regions in this report, not only the Top 20."
                : "Pie chart uses all churches in this report, not only the Top 20.";
    }

    private String pieChartTitle(ReportType reportType) {
        if (reportType == ReportType.SUBMISSION_STATUS) {
            return "Submission Status Breakdown";
        }
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Collection Type-wise (All Regions)"
                : "Collection Type-wise (All Churches)";
    }

    private <T extends ReportTableRow> List<ChartPoint> statusChartPoints(List<T> rows) {
        Map<String, BigDecimal> countsByStatus = new LinkedHashMap<>();
        for (ReportTableRow row : rows) {
            String status = text(row.columns().get("Status"));
            countsByStatus.merge(status.isBlank() ? "Unknown" : status, BigDecimal.ONE, BigDecimal::add);
        }
        return countsByStatus.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private <T extends ReportTableRow> List<ChartPoint> weeklyCollectionTypePoints(List<T> rows) {
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("Offerings", BigDecimal.ZERO);
        totals.put("Tithes", BigDecimal.ZERO);
        totals.put("Other Donations", BigDecimal.ZERO);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            for (String header : List.of("Offerings", "Tithes", "Other Donations")) {
                if (columns.containsKey(header)) {
                    totals.merge(header, amount(columns.get(header)), BigDecimal::add);
                }
            }
        }
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private BigDecimal amount(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString().replace(",", ""));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private MonthPeriod monthPeriod(LinkedHashMap<String, Object> columns) {
        int year = intValue(columns.get("Year"));
        int month = monthNumber(columns.get("Month"));
        if (year <= 0 || month <= 0) {
            return null;
        }
        return new MonthPeriod(year, month, monthLabel(year, month));
    }

    private int monthNumber(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().strip();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Month.valueOf(text.toUpperCase(Locale.ENGLISH)).getValue();
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private String monthLabel(int year, int month) {
        return Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private <T extends ReportTableRow> BigDecimal totalForPeriodAndHeader(List<T> rows,
                                                                          java.util.function.Predicate<T> predicate,
                                                                          String header) {
        BigDecimal total = BigDecimal.ZERO;
        for (T row : rows) {
            if (predicate.test(row)) {
                total = total.add(amount(row.columns().get(header)));
            }
        }
        return total;
    }

    private boolean hasCollectionTypeValue(CollectionTypePeriodTotal total) {
        return total.offertory().compareTo(BigDecimal.ZERO) > 0
                || total.tithes().compareTo(BigDecimal.ZERO) > 0
                || total.otherDonations().compareTo(BigDecimal.ZERO) > 0;
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
        layoutBandColumns(design.getColumnHeader(), "$P{header", headers, activeColumns, design.getColumnWidth());
        JRSection detailSection = design.getDetailSection();
        if (detailSection != null) {
            for (var band : detailSection.getBands()) {
                layoutBandColumns(band, "$F{column", headers, activeColumns, design.getColumnWidth());
            }
        }
    }

    private void layoutBandColumns(net.sf.jasperreports.engine.JRBand band, String expressionPrefix,
                                   List<String> headers, int activeColumns, int printableWidth) {
        if (band == null) {
            return;
        }
        int[] widths = columnWidths(headers, activeColumns, printableWidth);
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
                int x = 0;
                for (int index = 0; index < columnIndex; index++) {
                    x += widths[index];
                }
                designElement.setX(x);
                designElement.setWidth(widths[columnIndex]);
            }
        }
    }

    private int[] columnWidths(List<String> headers, int activeColumns, int printableWidth) {
        double[] weights = new double[activeColumns];
        double totalWeight = 0;
        for (int index = 0; index < activeColumns; index++) {
            weights[index] = columnWeight(headers.size() > index ? headers.get(index) : "");
            totalWeight += weights[index];
        }
        int[] widths = new int[activeColumns];
        int assignedWidth = 0;
        for (int index = 0; index < activeColumns; index++) {
            widths[index] = (int) Math.floor(printableWidth * weights[index] / totalWeight);
            assignedWidth += widths[index];
        }
        for (int index = 0; assignedWidth < printableWidth && index < widths.length; index++, assignedWidth++) {
            widths[index]++;
        }
        return widths;
    }

    private double columnWeight(String header) {
        if (header == null) {
            return 1.0;
        }
        return switch (header) {
            case "Church" -> 1.55;
            case "Church Code" -> 1.05;
            case "Week Start" -> 1.35;
            case "Receipt No" -> 1.45;
            case "Submitted At" -> 1.65;
            case "Status" -> 1.25;
            case "Late Submission" -> 0.95;
            case "Offerings", "Tithes", "Other Donations", "Grand Total" -> 1.20;
            default -> 1.0;
        };
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
        textField.getLineBox().setLeftPadding(4);
        textField.getLineBox().setRightPadding(4);
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
            textField.setHorizontalTextAlign(isRightAlignedColumn(headers, columnIndex)
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

    private boolean isRightAlignedColumn(List<String> headers, int zeroBasedColumn) {
        return zeroBasedColumn >= 0
                && zeroBasedColumn < headers.size()
                && rightAlignedHeader(headers.get(zeroBasedColumn));
    }

    private boolean rightAlignedHeader(String header) {
        if (header == null) {
            return false;
        }
        String normalized = header.toLowerCase();
        return normalized.contains("amount")
                || normalized.contains("total")
                || normalized.contains("collection")
                || normalized.contains("Offerings")
                || normalized.contains("tithes")
                || normalized.contains("donation")
                || normalized.equals("submitted")
                || normalized.equals("missing")
                || normalized.equals("late")
                || normalized.endsWith(" count")
                || normalized.endsWith(" churches")
                || normalized.endsWith(" weeks");
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

    private record ChartPoint(String label, BigDecimal value) {
    }

    private record AxisBounds(double min, double max) {
    }

    private record AnnualCollectionSeries(String label, Map<Integer, BigDecimal> yearTotals, BigDecimal total) {
    }

    private record PeriodCollectionSeries(String label, Map<MonthPeriod, BigDecimal> periodTotals, BigDecimal total) {
    }

    private record MonthPeriod(int year, int month, String label) {
    }

    private record CollectionTypePeriodTotal(String label, BigDecimal offertory, BigDecimal tithes,
                                             BigDecimal otherDonations) {
    }

    private static class AnnualCollectionAccumulator {
        private final String label;
        private final Map<Integer, BigDecimal> yearTotals = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        private AnnualCollectionAccumulator(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private Map<Integer, BigDecimal> yearTotals() {
            return yearTotals;
        }

        private BigDecimal total() {
            return total;
        }

        private void add(BigDecimal value) {
            total = total.add(value == null ? BigDecimal.ZERO : value);
        }
    }

    private static class PeriodCollectionAccumulator {
        private final String label;
        private final Map<MonthPeriod, BigDecimal> periodTotals = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        private PeriodCollectionAccumulator(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private Map<MonthPeriod, BigDecimal> periodTotals() {
            return periodTotals;
        }

        private BigDecimal total() {
            return total;
        }

        private void add(BigDecimal value) {
            total = total.add(value == null ? BigDecimal.ZERO : value);
        }
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
