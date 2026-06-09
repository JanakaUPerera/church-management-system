package com.churchmanagement.dto.report;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class UserActivityReportDto extends AbstractReportRow {
    private Long id;
    private String username;
    private String fullName;
    private String action;
    private String module;
    private String entityName;
    private Long entityId;
    private String details;
    private LocalDateTime activityAt;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Date/Time", dateTime(activityAt), "Username", text(username), "Full Name", text(fullName),
                "Action", text(action), "Module", text(module), "Entity", text(entityName),
                "Entity ID", entityId == null ? "" : entityId, "Details", text(details));
    }

    @Override
    public Long detailId() { return id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getActivityAt() { return activityAt; }
    public void setActivityAt(LocalDateTime activityAt) { this.activityAt = activityAt; }
}
