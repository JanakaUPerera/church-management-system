package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.JasperPrint;

/**
 * Sends an already-filled receipt report straight to a physical printer — distinct from the
 * generic, PDF-file-based {@link PrinterService} used by reports, since receipt printing never
 * writes a PDF file (see {@link com.churchmanagement.reports.ReceiptPdfGenerator#renderPrintJasperPrint}).
 */
public interface ReceiptPrinterService {
    PrintResult print(JasperPrint jasperPrint);
}
