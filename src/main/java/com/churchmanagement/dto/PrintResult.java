package com.churchmanagement.dto;

import java.time.LocalDateTime;

public class PrintResult {
    private boolean success;
    private String message;
    private String printerName;
    private LocalDateTime printedAt;

    public PrintResult() {
    }

    public PrintResult(boolean success, String message, String printerName, LocalDateTime printedAt) {
        this.success = success;
        this.message = message;
        this.printerName = printerName;
        this.printedAt = printedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }
}
