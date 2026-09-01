package com.churchmanagement.service;

import com.churchmanagement.dto.SystemSettingDto;
import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.entity.SystemSetting;
import com.churchmanagement.repository.SystemSettingRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SystemSettingService {
    public static final String CATEGORY_GENERAL = "GENERAL";
    public static final String CATEGORY_RECEIPT = "RECEIPT";
    public static final String CATEGORY_SMS = "SMS";
    public static final String CATEGORY_BACKUP = "BACKUP";
    public static final String CATEGORY_SYSTEM = "SYSTEM";

    private final SystemSettingRepository systemSettingRepository;
    private final SystemConfigurationCache systemConfigurationCache;
    private final ActivityLogService activityLogService;

    public SystemSettingService() {
        this(new SystemSettingRepository(), SystemConfigurationCache.getInstance(), new ActivityLogService());
    }

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                                SystemConfigurationCache systemConfigurationCache,
                                ActivityLogService activityLogService) {
        this.systemSettingRepository = systemSettingRepository;
        this.systemConfigurationCache = systemConfigurationCache;
        this.activityLogService = activityLogService;
    }

    public List<SystemSettingDto> loadSettings() {
        requireSettingsManage();
        return systemSettingRepository.findAll().stream().map(this::toDto).toList();
    }

    public List<SystemSettingDto> loadByCategory(String category) {
        requireSettingsManage();
        return systemSettingRepository.findByCategory(category).stream().map(this::toDto).toList();
    }

    public List<SystemSettingDto> updateSettings(List<UpdateSystemSettingRequest> requests) {
        AuthenticatedUser currentUser = requireSettingsManage();
        if (requests == null || requests.isEmpty()) {
            return loadSettings();
        }

        try {
            Map<String, SystemSetting> existingSettings = systemSettingRepository.findAll().stream()
                    .collect(Collectors.toMap(SystemSetting::getSettingKey, setting -> setting));
            validateRequests(requests, existingSettings);
            for (UpdateSystemSettingRequest request : requests) {
                SystemSetting existing = existingSettings.get(request.getSettingKey());
                SystemSetting updated = systemSettingRepository.updateSetting(request.getSettingKey(),
                        normalizeValue(request.getSettingValue()));
                activityLogService.logSettingUpdated(currentUser.getUserId(), updated.getSettingKey(),
                        existing.getSettingValue(), updated.getSettingValue());
            }
            systemConfigurationCache.reload();
            activityLogService.logSettingsCacheReloaded(currentUser.getUserId());
            return loadSettings();
        } catch (RuntimeException exception) {
            activityLogService.logSettingsUpdateFailed(currentUser.getUserId(), "Settings update failed: "
                    + exception.getMessage());
            throw exception;
        }
    }

    private void validateRequests(List<UpdateSystemSettingRequest> requests, Map<String, SystemSetting> existingSettings) {
        for (UpdateSystemSettingRequest request : requests) {
            if (request == null || request.getSettingKey() == null || request.getSettingKey().isBlank()) {
                throw new SystemSettingException("Setting key is required.");
            }
            SystemSetting existing = existingSettings.get(request.getSettingKey());
            if (existing == null) {
                throw new SystemSettingException("Unknown setting: " + request.getSettingKey());
            }
            if (!existing.isEditable()) {
                throw new SystemSettingException("Setting cannot be edited: " + request.getSettingKey());
            }
            validateValue(request.getSettingKey(), request.getSettingValue());
        }
    }

    private void validateValue(String key, String value) {
        String normalized = normalizeValue(value);
        switch (key) {
            case "organization.name" -> requireText(normalized, "Organization name is required.");
            case "receipt.sequence.padding" -> requireIntegerBetween(normalized, 4, 10,
                    "Receipt sequence padding must be between 4 and 10.");
            case "sms.retry.max.attempts" -> requireIntegerBetween(normalized, 0, 10,
                    "SMS retry max attempts must be between 0 and 10.");
            case "backup.retention.days" -> requireIntegerBetween(normalized, 1, 365,
                    "Backup retention days must be between 1 and 365.");
            case "reports.pdf.charts.enabled" -> requireBoolean(normalized,
                    "PDF report charts setting must be true or false.");
            case "receipt.allow.back.week" -> requireBoolean(normalized,
                    "Allow back week setting must be true or false.");
            case "receipt.late.reason.required" -> requireBoolean(normalized,
                    "Late submission reason required setting must be true or false.");
            case "system.theme" -> requireOneOf(normalized, "System theme must be ORCHID, LIGHT or DARK.", "ORCHID", "LIGHT", "DARK");
            case "receipt.default.language" -> requireOneOf(normalized,
                    "Receipt default language must be ENGLISH, SINHALA, or TAMIL.",
                    "ENGLISH", "SINHALA", "TAMIL");
            case "receipt.week.identifier.day" -> requireOneOf(normalized,
                    "Week identifier day must be MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, or SUNDAY.",
                    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
            default -> {
            }
        }
    }

    private AuthenticatedUser requireSettingsManage() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new SystemSettingException("You do not have permission to manage settings."));
        try {
            new PermissionGuard(currentUser).require("settings.manage");
            return currentUser;
        } catch (SecurityException exception) {
            throw new SystemSettingException("You do not have permission to manage settings.", exception);
        }
    }

    private SystemSettingDto toDto(SystemSetting setting) {
        SystemSettingDto dto = new SystemSettingDto();
        dto.setId(setting.getId());
        dto.setSettingKey(setting.getSettingKey());
        dto.setSettingValue(setting.getSettingValue());
        dto.setSettingType(setting.getSettingType());
        dto.setCategory(setting.getCategory());
        dto.setDescription(setting.getDescription());
        dto.setEditable(setting.isEditable());
        return dto;
    }

    private String normalizeValue(String value) {
        return value == null ? null : value.strip();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SystemSettingException(message);
        }
    }

    private void requireIntegerBetween(String value, int min, int max, String message) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new SystemSettingException(message);
            }
        } catch (NumberFormatException exception) {
            throw new SystemSettingException(message, exception);
        }
    }

    private void requireOneOf(String value, String message, String... allowedValues) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(normalized)) {
                return;
            }
        }
        throw new SystemSettingException(message);
    }

    private void requireBoolean(String value, String message) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new SystemSettingException(message);
        }
    }

    public static class SystemSettingException extends RuntimeException {
        public SystemSettingException(String message) {
            super(message);
        }

        public SystemSettingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
