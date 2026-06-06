package com.churchmanagement.dto;

public class ComPortDto {
    private String portName;
    private String description;
    private String systemPortName;

    public ComPortDto() {
    }

    public ComPortDto(String portName, String description, String systemPortName) {
        this.portName = portName;
        this.description = description;
        this.systemPortName = systemPortName;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPortName() {
        return systemPortName;
    }

    public void setSystemPortName(String systemPortName) {
        this.systemPortName = systemPortName;
    }

    public String displayName() {
        String name = isBlank(systemPortName) ? portName : systemPortName;
        String detail = isBlank(description) ? portName : description;
        if (isBlank(detail) || detail.equals(name)) {
            return name == null ? "" : name;
        }
        return name + " - " + detail;
    }

    @Override
    public String toString() {
        return displayName();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
