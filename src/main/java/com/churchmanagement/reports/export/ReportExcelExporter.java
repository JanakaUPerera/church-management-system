package com.churchmanagement.reports.export;

import com.churchmanagement.dto.report.ReportSummaryTotals;
import com.churchmanagement.dto.report.ReportTableRow;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.util.SystemDateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisCrossBetween;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDLbls;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPieChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReportExcelExporter {
    private static final int MAX_CHART_POINTS = 25;
    private static final int MAX_WEEKLY_CHURCH_CHART_POINTS = 20;
    private static final String[] DISTINCT_CHART_COLORS = {
            "2563EB", "DC2626", "16A34A", "F59E0B", "7C3AED",
            "0891B2", "DB2777", "65A30D", "EA580C", "4F46E5",
            "0D9488", "BE123C", "9333EA", "0284C7", "CA8A04",
            "15803D", "B91C1C", "6D28D9", "0369A1", "A16207"
    };
    private static final Set<ReportType> CHART_REPORT_TYPES = Set.of(
            ReportType.WEEKLY_CHURCH_COLLECTION,
            ReportType.WEEKLY_REGION_SUMMARY,
            ReportType.SUBMISSION_STATUS,
            ReportType.CHURCH_ANNUAL_COLLECTION,
            ReportType.REGION_ANNUAL_COLLECTION,
            ReportType.CHURCH_MONTHLY_COLLECTION,
            ReportType.REGION_MONTHLY_COLLECTION
    );
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();

    public <T extends ReportTableRow> Path export(ReportType reportType, List<T> rows, ReportSummaryTotals totals) {
        try {
            Path folder = ReportExportLocationResolver.exportFolder();
            Files.createDirectories(folder);
            Path output = folder.resolve(reportType.name().toLowerCase() + "-" + System.currentTimeMillis() + ".xlsx");
            try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream stream = Files.newOutputStream(output)) {
                Sheet sheet = workbook.createSheet("Report");
                CellStyle headerStyle = headerStyle(workbook);
                CellStyle totalStyle = totalStyle(workbook);
                CellStyle amountStyle = amountStyle(workbook, false);
                CellStyle totalAmountStyle = amountStyle(workbook, true);
                List<String> headers = rows == null || rows.isEmpty()
                        ? List.of("No data")
                        : rows.getFirst().columns().keySet().stream().toList();
                Row header = sheet.createRow(0);
                for (int column = 0; column < headers.size(); column++) {
                    Cell cell = header.createCell(column);
                    cell.setCellValue(headers.get(column));
                    cell.setCellStyle(headerStyle);
                }
                int rowIndex = 1;
                List<LinkedHashMap<String, Object>> exportRows = rowsWithTotals(rows, totals);
                int dataRowCount = rows == null ? 0 : rows.size();
                for (int rowOffset = 0; rowOffset < exportRows.size(); rowOffset++) {
                    LinkedHashMap<String, Object> exportRow = exportRows.get(rowOffset);
                    boolean totalsRow = rowOffset >= dataRowCount;
                    Row row = sheet.createRow(rowIndex++);
                    int column = 0;
                    for (Object value : exportRow.values()) {
                        Cell cell = row.createCell(column++);
                        setCellValue(cell, value);
                        if (value instanceof BigDecimal) {
                            cell.setCellStyle(totalsRow ? totalAmountStyle : amountStyle);
                        } else if (totalsRow) {
                            cell.setCellStyle(totalStyle);
                        }
                    }
                }
                for (int column = 0; column < headers.size(); column++) {
                    sheet.autoSizeColumn(column);
                }
                createChartSheet(workbook, reportType, rows);
                workbook.write(stream);
            }
            return output;
        } catch (Exception exception) {
            throw new ReportPdfExporter.ReportExportException("Export failed.", exception);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle totalStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle amountStyle(Workbook workbook, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        if (bold) {
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
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

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal amount) {
            cell.setCellValue(amount.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTimeFormatter.formatDateTime(dateTime));
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(dateTimeFormatter.formatDate(date));
        } else if (value instanceof LocalTime time) {
            cell.setCellValue(dateTimeFormatter.formatTime(time));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private <T extends ReportTableRow> void createChartSheet(XSSFWorkbook workbook, ReportType reportType, List<T> rows) {
        if (supportsWeeklyCollectionCharts(reportType)) {
            createWeeklyCollectionChartSheet(workbook, reportType, rows);
            return;
        }
        if (reportType == ReportType.SUBMISSION_STATUS) {
            createSubmissionStatusChartSheet(workbook, rows);
            return;
        }
        if (supportsMonthlyCollectionCharts(reportType)) {
            createMonthlyCollectionChartSheet(workbook, reportType, rows);
            return;
        }
        if (supportsAnnualCollectionCharts(reportType)) {
            createAnnualCollectionChartSheet(workbook, reportType, rows);
            return;
        }

        List<ChartPoint> points = chartPoints(reportType, rows);
        if (points.isEmpty()) {
            return;
        }

        XSSFSheet chartSheet = workbook.createSheet("Charts");
        Row header = chartSheet.createRow(0);
        header.createCell(0).setCellValue("Category");
        header.createCell(1).setCellValue(chartValueTitle(reportType));
        for (int index = 0; index < points.size(); index++) {
            Row row = chartSheet.createRow(index + 1);
            row.createCell(0).setCellValue(points.get(index).label());
            row.createCell(1).setCellValue(points.get(index).value().doubleValue());
        }
        chartSheet.autoSizeColumn(0);
        chartSheet.autoSizeColumn(1);

        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 1, 17, 25);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(reportType.getDisplayName());
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(chartCategoryTitle(reportType));
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(chartValueTitle(reportType));
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(1, points.size(), 0, 0));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                chartSheet, new CellRangeAddress(1, points.size(), 1, 1));
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle(chartValueTitle(reportType), null);
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);
        chart.plot(data);
    }

    private <T extends ReportTableRow> void createSubmissionStatusChartSheet(XSSFWorkbook workbook, List<T> rows) {
        List<ChartPoint> points = statusChartPoints(rows);
        if (points.isEmpty()) {
            return;
        }

        XSSFSheet chartSheet = workbook.createSheet("Charts");
        writeChartPointBlock(chartSheet, 0, "Submission Status Breakdown",
                "Pie chart shows churches grouped by submission status.", "Status", "Church Count", points);
        chartSheet.autoSizeColumn(0);
        chartSheet.autoSizeColumn(1);

        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        createPieChart(drawing, chartSheet, "Submission Status Breakdown", 3, 2 + points.size(), 0, 1,
                3, 0, 18, 18);
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

    private <T extends ReportTableRow> void createWeeklyCollectionChartSheet(XSSFWorkbook workbook, ReportType reportType,
                                                                             List<T> rows) {
        List<ChartPoint> topPoints = topWeeklyCollectionPoints(reportType, rows);
        List<ChartPoint> collectionTypePoints = weeklyCollectionTypePoints(rows);
        if (topPoints.isEmpty() && collectionTypePoints.isEmpty()) {
            return;
        }

        XSSFSheet chartSheet = workbook.createSheet("Charts");
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        if (!topPoints.isEmpty()) {
            writeChartPointBlock(chartSheet, 0, topChartBlockTitle(reportType), topChartCategoryTitle(reportType),
                    "Grand Total", topPoints);
            createBarChart(drawing, chartSheet, topChartTitle(reportType), topChartCategoryTitle(reportType),
                    "Grand Total", 2, 1 + topPoints.size(), 0, 1, 3, 0, 18, 24);
        }
        if (!collectionTypePoints.isEmpty()) {
            int collectionStartRow = Math.max(26, topPoints.size() + 4);
            writeChartPointBlock(chartSheet, collectionStartRow, collectionTypeChartTitle(reportType),
                    collectionTypeChartNote(reportType),
                    "Collection Type", "Amount", collectionTypePoints);
            createPieChart(drawing, chartSheet, collectionTypeChartTitle(reportType), collectionStartRow + 3,
                    collectionStartRow + 2 + collectionTypePoints.size(), 0, 1, 3, collectionStartRow, 18,
                    collectionStartRow + 18);
        }
        chartSheet.autoSizeColumn(0);
        chartSheet.autoSizeColumn(1);
    }

    private <T extends ReportTableRow> void createAnnualCollectionChartSheet(XSSFWorkbook workbook,
                                                                             ReportType reportType, List<T> rows) {
        List<AnnualCollectionSeries> topSeries = topAnnualCollections(reportType, rows);
        List<Integer> pieYears = annualYears(rows);
        List<Integer> barYears = annualBarYears(rows);
        if (topSeries.isEmpty() && pieYears.isEmpty()) {
            return;
        }

        XSSFSheet chartSheet = workbook.createSheet("Charts");
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        if (!topSeries.isEmpty() && !barYears.isEmpty()) {
            writeAnnualCollectionBlock(chartSheet, reportType, 0, topSeries, barYears);
            createMultiSeriesBarChart(drawing, chartSheet, annualCollectionBarTitle(reportType),
                    annualCollectionCategoryTitle(reportType), "Grand Total", 2, 1 + topSeries.size(), barYears,
                    0, 1, 3, 0, 20, 26);
        }
        int comparisonStartRow = Math.max(28, topSeries.size() + 5);
        int pieStartRow = comparisonStartRow;
        if (barYears.size() > 1) {
            List<CollectionTypePeriodTotal> typeYears = annualCollectionTypePeriodTotals(rows, barYears);
            if (!typeYears.isEmpty()) {
                writeCollectionTypePeriodBlock(chartSheet, comparisonStartRow, annualCollectionTypeBarTitle(reportType),
                        "Year", typeYears);
                createNamedSeriesBarChart(drawing, chartSheet, annualCollectionTypeBarTitle(reportType), "Year",
                        "Grand Total", comparisonStartRow + 2, comparisonStartRow + 1 + typeYears.size(), 0, 1,
                        List.of("Offerings", "Tithes", "Other Donations"), 4, comparisonStartRow, 20,
                        comparisonStartRow + 22);
                pieStartRow = comparisonStartRow + 24;
            }
        }
        for (int index = 0; index < pieYears.size(); index++) {
            Integer year = pieYears.get(index);
            List<ChartPoint> collectionTypePoints = annualCollectionTypePoints(rows, year);
            if (collectionTypePoints.isEmpty()) {
                continue;
            }
            int startRow = pieStartRow + (index * 20);
            String title = pieYears.size() > 1 ? "Collection Type-wise (" + year + ")" : annualCollectionPieScopeTitle(reportType);
            String note = pieYears.size() > 1
                    ? "Pie chart uses all " + annualCollectionEntityName(reportType).toLowerCase() + "s in " + year
                    + ",\nnot only the Top 20."
                    : annualCollectionPieNote(reportType);
            writeChartPointBlock(chartSheet, startRow, title, note, "Collection Type", "Amount", collectionTypePoints);
            createPieChart(drawing, chartSheet, title, startRow + 3, startRow + 2 + collectionTypePoints.size(),
                    0, 1, 3, startRow, 18, startRow + 18);
        }
        for (int column = 0; column <= barYears.size(); column++) {
            chartSheet.autoSizeColumn(column);
        }
    }

    private <T extends ReportTableRow> void createMonthlyCollectionChartSheet(XSSFWorkbook workbook,
                                                                              ReportType reportType, List<T> rows) {
        List<PeriodCollectionSeries> topSeries = topMonthlyCollections(reportType, rows);
        List<MonthPeriod> piePeriods = monthlyPeriods(rows, true);
        List<MonthPeriod> barPeriods = monthlyPeriods(rows, false);
        if (topSeries.isEmpty() && piePeriods.isEmpty()) {
            return;
        }

        XSSFSheet chartSheet = workbook.createSheet("Charts");
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        if (!topSeries.isEmpty() && !barPeriods.isEmpty()) {
            writeMonthlyCollectionBlock(chartSheet, reportType, 0, topSeries, barPeriods);
            createMultiSeriesPeriodBarChart(drawing, chartSheet, monthlyCollectionBarTitle(reportType),
                    monthlyCollectionCategoryTitle(reportType), "Grand Total", 2, 1 + topSeries.size(), barPeriods,
                    0, 1, 3, 0, 20, 26);
        }
        int comparisonStartRow = Math.max(28, topSeries.size() + 5);
        int pieStartRow = comparisonStartRow;
        if (barPeriods.size() > 1) {
            List<CollectionTypePeriodTotal> typePeriods = monthlyCollectionTypePeriodTotals(rows, barPeriods);
            if (!typePeriods.isEmpty()) {
                writeCollectionTypePeriodBlock(chartSheet, comparisonStartRow, monthlyCollectionTypeBarTitle(reportType),
                        "Month", typePeriods);
                createNamedSeriesBarChart(drawing, chartSheet, monthlyCollectionTypeBarTitle(reportType), "Month",
                        "Grand Total", comparisonStartRow + 2, comparisonStartRow + 1 + typePeriods.size(), 0, 1,
                        List.of("Offerings", "Tithes", "Other Donations"), 4, comparisonStartRow, 20,
                        comparisonStartRow + 22);
                pieStartRow = comparisonStartRow + 24;
            }
        }
        for (int index = 0; index < piePeriods.size(); index++) {
            MonthPeriod period = piePeriods.get(index);
            List<ChartPoint> collectionTypePoints = monthlyCollectionTypePoints(rows, period);
            if (collectionTypePoints.isEmpty()) {
                continue;
            }
            int startRow = pieStartRow + (index * 20);
            String title = piePeriods.size() > 1
                    ? "Collection Type-wise (" + period.label() + ")"
                    : monthlyCollectionPieScopeTitle(reportType);
            String note = piePeriods.size() > 1
                    ? "Pie chart uses all " + monthlyCollectionEntityName(reportType).toLowerCase() + "s in "
                    + period.label() + ",\nnot only the Top 20."
                    : monthlyCollectionPieNote(reportType);
            writeChartPointBlock(chartSheet, startRow, title, note, "Collection Type", "Amount", collectionTypePoints);
            createPieChart(drawing, chartSheet, title, startRow + 3, startRow + 2 + collectionTypePoints.size(),
                    0, 1, 3, startRow, 18, startRow + 18);
        }
        for (int column = 0; column <= barPeriods.size(); column++) {
            chartSheet.autoSizeColumn(column);
        }
    }

    private void writeCollectionTypePeriodBlock(XSSFSheet chartSheet, int startRow, String title,
                                                String periodHeader, List<CollectionTypePeriodTotal> totals) {
        Row titleRow = chartSheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(title);
        Row header = chartSheet.createRow(startRow + 1);
        header.createCell(0).setCellValue(periodHeader);
        header.createCell(1).setCellValue("Offerings");
        header.createCell(2).setCellValue("Tithes");
        header.createCell(3).setCellValue("Other Donations");
        for (int index = 0; index < totals.size(); index++) {
            CollectionTypePeriodTotal total = totals.get(index);
            Row row = chartSheet.createRow(startRow + index + 2);
            row.createCell(0).setCellValue(total.label());
            row.createCell(1).setCellValue(total.offertory().doubleValue());
            row.createCell(2).setCellValue(total.tithes().doubleValue());
            row.createCell(3).setCellValue(total.otherDonations().doubleValue());
        }
    }

    private void writeChartPointBlock(XSSFSheet chartSheet, int startRow, String title, String categoryHeader,
                                      String valueHeader, List<ChartPoint> points) {
        writeChartPointBlock(chartSheet, startRow, title, null, categoryHeader, valueHeader, points);
    }

    private void writeChartPointBlock(XSSFSheet chartSheet, int startRow, String title, String note,
                                      String categoryHeader, String valueHeader, List<ChartPoint> points) {
        Row titleRow = chartSheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(title);
        int headerRowIndex = startRow + 1;
        if (note != null && !note.isBlank()) {
            Row noteRow = chartSheet.createRow(startRow + 1);
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue(note);
            CellStyle noteStyle = chartSheet.getWorkbook().createCellStyle();
            noteStyle.setWrapText(true);
            noteCell.setCellStyle(noteStyle);
            noteRow.setHeightInPoints(30);
            headerRowIndex++;
        }
        Row header = chartSheet.createRow(headerRowIndex);
        header.createCell(0).setCellValue(categoryHeader);
        header.createCell(1).setCellValue(valueHeader);
        for (int index = 0; index < points.size(); index++) {
            Row row = chartSheet.createRow(headerRowIndex + index + 1);
            row.createCell(0).setCellValue(points.get(index).label());
            row.createCell(1).setCellValue(points.get(index).value().doubleValue());
        }
    }

    private void writeAnnualCollectionBlock(XSSFSheet chartSheet, ReportType reportType, int startRow,
                                            List<AnnualCollectionSeries> topSeries, List<Integer> years) {
        Row titleRow = chartSheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(annualCollectionBlockTitle(reportType));
        Row header = chartSheet.createRow(startRow + 1);
        header.createCell(0).setCellValue(annualCollectionCategoryTitle(reportType));
        for (int index = 0; index < years.size(); index++) {
            header.createCell(index + 1).setCellValue(years.get(index));
        }
        for (int rowIndex = 0; rowIndex < topSeries.size(); rowIndex++) {
            AnnualCollectionSeries church = topSeries.get(rowIndex);
            Row row = chartSheet.createRow(startRow + rowIndex + 2);
            row.createCell(0).setCellValue(church.label());
            for (int yearIndex = 0; yearIndex < years.size(); yearIndex++) {
                row.createCell(yearIndex + 1)
                        .setCellValue(church.yearTotals().getOrDefault(years.get(yearIndex), BigDecimal.ZERO)
                                .doubleValue());
            }
        }
    }

    private void writeMonthlyCollectionBlock(XSSFSheet chartSheet, ReportType reportType, int startRow,
                                             List<PeriodCollectionSeries> topSeries, List<MonthPeriod> periods) {
        Row titleRow = chartSheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(monthlyCollectionBlockTitle(reportType));
        Row header = chartSheet.createRow(startRow + 1);
        header.createCell(0).setCellValue(monthlyCollectionCategoryTitle(reportType));
        for (int index = 0; index < periods.size(); index++) {
            header.createCell(index + 1).setCellValue(periods.get(index).label());
        }
        for (int rowIndex = 0; rowIndex < topSeries.size(); rowIndex++) {
            PeriodCollectionSeries series = topSeries.get(rowIndex);
            Row row = chartSheet.createRow(startRow + rowIndex + 2);
            row.createCell(0).setCellValue(series.label());
            for (int periodIndex = 0; periodIndex < periods.size(); periodIndex++) {
                row.createCell(periodIndex + 1)
                        .setCellValue(series.periodTotals().getOrDefault(periods.get(periodIndex), BigDecimal.ZERO)
                                .doubleValue());
            }
        }
    }

    private void createBarChart(XSSFDrawing drawing, XSSFSheet chartSheet, String title, String categoryTitle,
                                String valueTitle, int firstDataRow, int lastDataRow, int categoryColumn,
                                int valueColumn, int col1, int row1, int col2, int row2) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(categoryTitle);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(valueTitle);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        leftAxis.getOrAddMajorGridProperties();

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, valueColumn, valueColumn));
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle(valueTitle, null);
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);
        chart.plot(data);
        applyDistinctBarColors(chart, lastDataRow - firstDataRow + 1);
        showLegend(chart);
    }

    private void createMultiSeriesBarChart(XSSFDrawing drawing, XSSFSheet chartSheet, String title,
                                           String categoryTitle, String valueTitle, int firstDataRow, int lastDataRow,
                                           List<Integer> years, int categoryColumn, int firstValueColumn,
                                           int col1, int row1, int col2, int row2) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(categoryTitle);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(valueTitle);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        leftAxis.getOrAddMajorGridProperties();

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        for (int index = 0; index < years.size(); index++) {
            int column = firstValueColumn + index;
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, column, column));
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(years.get(index).toString(), null);
        }
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);
        chart.plot(data);
        applySeriesBarColors(chart, years.size());
        showLegend(chart);
    }

    private void createMultiSeriesPeriodBarChart(XSSFDrawing drawing, XSSFSheet chartSheet, String title,
                                                 String categoryTitle, String valueTitle, int firstDataRow,
                                                 int lastDataRow, List<MonthPeriod> periods, int categoryColumn,
                                                 int firstValueColumn, int col1, int row1, int col2, int row2) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(categoryTitle);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(valueTitle);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        leftAxis.getOrAddMajorGridProperties();

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        for (int index = 0; index < periods.size(); index++) {
            int column = firstValueColumn + index;
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, column, column));
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(periods.get(index).label(), null);
        }
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);
        chart.plot(data);
        applySeriesBarColors(chart, periods.size());
        showLegend(chart);
    }

    private void createNamedSeriesBarChart(XSSFDrawing drawing, XSSFSheet chartSheet, String title,
                                           String categoryTitle, String valueTitle, int firstDataRow, int lastDataRow,
                                           int categoryColumn, int firstValueColumn, List<String> seriesTitles,
                                           int col1, int row1, int col2, int row2) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(categoryTitle);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(valueTitle);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        leftAxis.getOrAddMajorGridProperties();

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        for (int index = 0; index < seriesTitles.size(); index++) {
            int column = firstValueColumn + index;
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, column, column));
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(seriesTitles.get(index), null);
        }
        ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);
        chart.plot(data);
        applySeriesBarColors(chart, seriesTitles.size());
        showLegend(chart);
    }

    private void createPieChart(XSSFDrawing drawing, XSSFSheet chartSheet, String title, int firstDataRow,
                                int lastDataRow, int categoryColumn, int valueColumn, int col1, int row1,
                                int col2, int row2) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, categoryColumn, categoryColumn));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                chartSheet, new CellRangeAddress(firstDataRow, lastDataRow, valueColumn, valueColumn));
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle(title, null);
        chart.plot(data);
        applyDistinctPieColors(chart, lastDataRow - firstDataRow + 1);
        showLegend(chart);
        showPieDataLabels(chart);
    }

    private void applyDistinctBarColors(XSSFChart chart, int pointCount) {
        if (chart.getCTChart().getPlotArea().sizeOfBarChartArray() == 0 || pointCount <= 0) {
            return;
        }
        CTBarChart barChart = chart.getCTChart().getPlotArea().getBarChartArray(0);
        if (barChart.sizeOfSerArray() == 0) {
            return;
        }
        CTBarSer series = barChart.getSerArray(0);
        while (series.sizeOfDPtArray() > 0) {
            series.removeDPt(0);
        }
        for (int index = 0; index < pointCount; index++) {
            applyPointColor(series.addNewDPt(), index);
        }
    }

    private void applyDistinctPieColors(XSSFChart chart, int pointCount) {
        if (chart.getCTChart().getPlotArea().sizeOfPieChartArray() == 0 || pointCount <= 0) {
            return;
        }
        CTPieChart pieChart = chart.getCTChart().getPlotArea().getPieChartArray(0);
        if (pieChart.sizeOfSerArray() == 0) {
            return;
        }
        CTPieSer series = pieChart.getSerArray(0);
        while (series.sizeOfDPtArray() > 0) {
            series.removeDPt(0);
        }
        for (int index = 0; index < pointCount; index++) {
            applyPointColor(series.addNewDPt(), index);
        }
    }

    private void applySeriesBarColors(XSSFChart chart, int seriesCount) {
        if (chart.getCTChart().getPlotArea().sizeOfBarChartArray() == 0 || seriesCount <= 0) {
            return;
        }
        CTBarChart barChart = chart.getCTChart().getPlotArea().getBarChartArray(0);
        for (int index = 0; index < Math.min(seriesCount, barChart.sizeOfSerArray()); index++) {
            CTBarSer series = barChart.getSerArray(index);
            CTShapeProperties shape = series.isSetSpPr() ? series.getSpPr() : series.addNewSpPr();
            if (shape.isSetGradFill()) {
                shape.unsetGradFill();
            }
            if (shape.isSetNoFill()) {
                shape.unsetNoFill();
            }
            shape.addNewSolidFill().addNewSrgbClr().setVal(hexBytes(DISTINCT_CHART_COLORS[
                    index % DISTINCT_CHART_COLORS.length]));
            shape.addNewLn().addNewNoFill();
        }
    }


    private void applyPointColor(CTDPt point, int index) {
        point.addNewIdx().setVal(index);
        CTShapeProperties shape = point.addNewSpPr();
        if (shape.isSetGradFill()) {
            shape.unsetGradFill();
        }
        if (shape.isSetNoFill()) {
            shape.unsetNoFill();
        }
        shape.addNewSolidFill().addNewSrgbClr().setVal(hexBytes(DISTINCT_CHART_COLORS[
                index % DISTINCT_CHART_COLORS.length]));
        shape.addNewLn().addNewNoFill();
    }

    private byte[] hexBytes(String color) {
        int red = Integer.parseInt(color.substring(0, 2), 16);
        int green = Integer.parseInt(color.substring(2, 4), 16);
        int blue = Integer.parseInt(color.substring(4, 6), 16);
        return new byte[]{(byte) red, (byte) green, (byte) blue};
    }

    private void showLegend(XSSFChart chart) {
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);
        legend.setOverlay(false);
    }

    private void showPieDataLabels(XSSFChart chart) {
        if (chart.getCTChart().getPlotArea().sizeOfPieChartArray() == 0) {
            return;
        }
        CTPieChart pieChart = chart.getCTChart().getPlotArea().getPieChartArray(0);
        CTDLbls labels = pieChart.isSetDLbls() ? pieChart.getDLbls() : pieChart.addNewDLbls();
        labels.addNewShowLegendKey().setVal(false);
        labels.addNewShowVal().setVal(true);
        labels.addNewShowCatName().setVal(true);
        labels.addNewShowSerName().setVal(false);
        labels.addNewShowPercent().setVal(true);
        labels.addNewShowBubbleSize().setVal(false);
        labels.addNewShowLeaderLines().setVal(true);
    }

    private <T extends ReportTableRow> List<ChartPoint> chartPoints(ReportType reportType, List<T> rows) {
        if (!CHART_REPORT_TYPES.contains(reportType) || rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (reportType == ReportType.SUBMISSION_STATUS) {
            return statusChartPoints(rows);
        }
        return amountChartPoints(reportType, rows);
    }

    private <T extends ReportTableRow> List<ChartPoint> topWeeklyCollectionPoints(ReportType reportType, List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        String labelColumn = reportType == ReportType.WEEKLY_REGION_SUMMARY ? "Region" : "Church";
        Map<String, BigDecimal> totalsByLabel = new LinkedHashMap<>();
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            String label = text(columns.get(labelColumn));
            if (label.isBlank()) {
                label = "Unlabelled";
            }
            totalsByLabel.merge(label, amount(columns.get("Grand Total")), BigDecimal::add);
        }
        return totalsByLabel.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .filter(point -> point.value().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(ChartPoint::value).reversed())
                .limit(MAX_WEEKLY_CHURCH_CHART_POINTS)
                .toList();
    }

    private <T extends ReportTableRow> List<AnnualCollectionSeries> topAnnualCollections(ReportType reportType,
                                                                                          List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, AnnualCollectionAccumulator> totalsByLabel = new LinkedHashMap<>();
        String column = annualCollectionCategoryTitle(reportType);
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            int year = intValue(columns.get("Year"));
            if (year <= 0) {
                continue;
            }
            String label = text(columns.get(column));
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
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
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
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> intValue(row.columns().get("Year")))
                .filter(year -> year > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private <T extends ReportTableRow> List<Integer> annualBarYears(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> intValue(row.columns().get("Year")))
                .filter(year -> year > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private <T extends ReportTableRow> List<MonthPeriod> monthlyPeriods(List<T> rows, boolean descending) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
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

    private String topChartBlockTitle(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY ? "Top 20 Regions" : "Top 20 Churches";
    }

    private String annualCollectionBlockTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION ? "Top 20 Regions" : "Top 20 Churches";
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
                ? "Pie chart uses all regions in this report,\nnot only the Top 20."
                : "Pie chart uses all churches in this report,\nnot only the Top 20.";
    }

    private String annualCollectionTypeBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_ANNUAL_COLLECTION
                ? "Collection Types by Year (All Regions)"
                : "Collection Types by Year (All Churches)";
    }

    private String monthlyCollectionBlockTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION ? "Top 20 Regions" : "Top 20 Churches";
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
                ? "Pie chart uses all regions in this report,\nnot only the Top 20."
                : "Pie chart uses all churches in this report,\nnot only the Top 20.";
    }

    private String monthlyCollectionTypeBarTitle(ReportType reportType) {
        return reportType == ReportType.REGION_MONTHLY_COLLECTION
                ? "Collection Types by Month (All Regions)"
                : "Collection Types by Month (All Churches)";
    }

    private String topChartTitle(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Top 20 Regions by Collection"
                : "Top 20 Churches by Collection";
    }

    private String topChartCategoryTitle(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY ? "Region" : "Church";
    }

    private String collectionTypeChartTitle(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Collection Type-wise (All Regions)"
                : "Collection Type-wise (All Churches)";
    }

    private String collectionTypeChartNote(ReportType reportType) {
        return reportType == ReportType.WEEKLY_REGION_SUMMARY
                ? "Pie chart uses all regions in this report,\nnot only the Top 20."
                : "Pie chart uses all churches in this report,\nnot only the Top 20.";
    }

    private <T extends ReportTableRow> List<ChartPoint> weeklyCollectionTypePoints(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
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

    private <T extends ReportTableRow> List<ChartPoint> annualCollectionTypePoints(List<T> rows, Integer year) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
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
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
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
                    totalForPeriodAndHeader(rows, row -> intValue(row.columns().get("Year")) == year,
                            "Other Donations")));
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
                    totalForPeriodAndHeader(rows, row -> period.equals(monthPeriod(row.columns())),
                            "Other Donations")));
        }
        return totals.stream().filter(this::hasCollectionTypeValue).toList();
    }

    private <T extends ReportTableRow> List<ChartPoint> statusChartPoints(List<T> rows) {
        Map<String, BigDecimal> countsByStatus = new LinkedHashMap<>();
        for (ReportTableRow row : rows) {
            String status = text(row.columns().get("Status"));
            if (status.isBlank()) {
                status = "Unknown";
            }
            countsByStatus.merge(status, BigDecimal.ONE, BigDecimal::add);
        }
        return countsByStatus.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private <T extends ReportTableRow> List<ChartPoint> amountChartPoints(ReportType reportType, List<T> rows) {
        List<ChartPoint> points = new ArrayList<>();
        Set<String> usedLabels = new LinkedHashSet<>();
        for (ReportTableRow row : rows) {
            LinkedHashMap<String, Object> columns = row.columns();
            BigDecimal amount = amount(columns.get("Grand Total"));
            String label = uniqueLabel(chartLabel(reportType, columns), usedLabels);
            points.add(new ChartPoint(label, amount));
            if (points.size() >= MAX_CHART_POINTS) {
                break;
            }
        }
        return points;
    }

    private String chartLabel(ReportType reportType, LinkedHashMap<String, Object> columns) {
        return switch (reportType) {
            case WEEKLY_CHURCH_COLLECTION, CHURCH_ANNUAL_COLLECTION -> text(columns.get("Church"));
            case WEEKLY_REGION_SUMMARY, REGION_ANNUAL_COLLECTION -> text(columns.get("Region"));
            case CHURCH_MONTHLY_COLLECTION -> text(columns.get("Month")) + " - " + text(columns.get("Church"));
            case REGION_MONTHLY_COLLECTION -> text(columns.get("Month")) + " - " + text(columns.get("Region"));
            default -> text(columns.values().stream().findFirst().orElse(""));
        };
    }

    private String chartCategoryTitle(ReportType reportType) {
        return reportType == ReportType.SUBMISSION_STATUS ? "Status" : "Report Rows";
    }

    private String chartValueTitle(ReportType reportType) {
        return reportType == ReportType.SUBMISSION_STATUS ? "Church Count" : "Grand Total";
    }

    private String uniqueLabel(String label, Set<String> usedLabels) {
        String normalized = label == null || label.isBlank() ? "Unlabelled" : label;
        String candidate = normalized;
        int suffix = 2;
        while (!usedLabels.add(candidate)) {
            candidate = normalized + " (" + suffix++ + ")";
        }
        return candidate;
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

    private record ChartPoint(String label, BigDecimal value) {
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
}
