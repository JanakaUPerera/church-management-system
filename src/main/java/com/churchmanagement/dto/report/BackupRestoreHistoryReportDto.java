package com.churchmanagement.dto.report;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class BackupRestoreHistoryReportDto extends AbstractReportRow {
    private Long id;
    private String actionType;
    private String fileName;
    private String status;
    private String userFullName;
    private LocalDateTime actionAt;
    private String errorMessage;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Type", text(actionType), "File", text(fileName), "Status", text(status), "User",
                text(userFullName), "Date/Time", dateTime(actionAt), "Error", text(errorMessage));
    }

    @Override
    public Long detailId() { return id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }
    public LocalDateTime getActionAt() { return actionAt; }
    public void setActionAt(LocalDateTime actionAt) { this.actionAt = actionAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
