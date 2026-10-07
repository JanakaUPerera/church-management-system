package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Prints a filled report through the operating system's print dialog, so the
 * user picks the printer, copies and page range. Pages are sent as vector
 * graphics at the report's own page size and orientation.
 */
public class JasperReportPrinterService implements ReportPrinterService {
    private static final String SELECTED_PRINTER = "Printer selected in print dialog";

    private final Clock clock;

    public JasperReportPrinterService() {
        this(Clock.systemDefaultZone());
    }

    public JasperReportPrinterService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    public PrintResult print(JasperPrint jasperPrint) {
        if (jasperPrint == null || jasperPrint.getPages().isEmpty()) {
            return new PrintResult(false, "There is nothing to print.", null, LocalDateTime.now(clock));
        }
        try {
            boolean printed = JasperPrintManager.getInstance(DefaultJasperReportsContext.getInstance())
                    .print(jasperPrint, true);
            if (!printed) {
                return PrintResult.cancelled(LocalDateTime.now(clock));
            }
            return new PrintResult(true, "Report sent to the printer.", SELECTED_PRINTER, LocalDateTime.now(clock));
        } catch (JRException | RuntimeException exception) {
            String detail = exception.getMessage() == null ? "" : " " + exception.getMessage();
            return new PrintResult(false, "Print failed." + detail, null, LocalDateTime.now(clock));
        }
    }
}
