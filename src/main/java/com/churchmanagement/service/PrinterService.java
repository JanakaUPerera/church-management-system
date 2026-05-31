package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;

public interface PrinterService {
    PrintResult printPdf(String pdfFilePath);
}
