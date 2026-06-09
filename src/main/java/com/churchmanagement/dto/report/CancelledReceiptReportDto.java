package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class CancelledReceiptReportDto extends AbstractReportRow {
    private Long receiptId;
    private String receiptNo;
    private String regionName;
    private String churchName;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Receipt No", text(receiptNo), "Region", text(regionName), "Church", text(churchName),
                "Grand Total", zero(grandTotal), "Cancelled By", text(cancelledBy),
                "Cancelled At", dateTime(cancelledAt), "Reason", text(cancelReason));
    }

    @Override
    public Long detailId() { return receiptId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = zero(grandTotal); }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
