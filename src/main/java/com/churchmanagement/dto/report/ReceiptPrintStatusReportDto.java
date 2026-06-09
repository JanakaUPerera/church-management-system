package com.churchmanagement.dto.report;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class ReceiptPrintStatusReportDto extends AbstractReportRow {
    private Long receiptId;
    private String receiptNo;
    private String churchName;
    private boolean originalPrinted;
    private String printedBy;
    private LocalDateTime printedAt;
    private int printAttempts;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Receipt No", text(receiptNo), "Church", text(churchName), "Original Printed",
                originalPrinted ? "YES" : "NO", "Printed By", text(printedBy), "Printed At", dateTime(printedAt),
                "Print Attempts", printAttempts);
    }

    @Override
    public Long detailId() { return receiptId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public boolean isOriginalPrinted() { return originalPrinted; }
    public void setOriginalPrinted(boolean originalPrinted) { this.originalPrinted = originalPrinted; }
    public String getPrintedBy() { return printedBy; }
    public void setPrintedBy(String printedBy) { this.printedBy = printedBy; }
    public LocalDateTime getPrintedAt() { return printedAt; }
    public void setPrintedAt(LocalDateTime printedAt) { this.printedAt = printedAt; }
    public int getPrintAttempts() { return printAttempts; }
    public void setPrintAttempts(int printAttempts) { this.printAttempts = printAttempts; }
}
