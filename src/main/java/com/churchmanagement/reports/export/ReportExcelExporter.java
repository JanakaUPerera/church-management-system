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
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReportExcelExporter {
    private static final int MAX_CHART_POINTS = 25;
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
            case "Offertory" -> totals.getOffertoryTotal();
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

    private <T extends ReportTableRow> List<ChartPoint> chartPoints(ReportType reportType, List<T> rows) {
        if (!CHART_REPORT_TYPES.contains(reportType) || rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (reportType == ReportType.SUBMISSION_STATUS) {
            return statusChartPoints(rows);
        }
        return amountChartPoints(reportType, rows);
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

    private record ChartPoint(String label, BigDecimal value) {
    }
}
