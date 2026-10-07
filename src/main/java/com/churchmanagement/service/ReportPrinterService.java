package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import net.sf.jasperreports.engine.JasperPrint;

public interface ReportPrinterService {
    PrintResult print(JasperPrint jasperPrint);
}
