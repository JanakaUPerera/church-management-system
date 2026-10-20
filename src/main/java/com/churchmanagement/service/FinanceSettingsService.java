package com.churchmanagement.service;

import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.JournalRepository;
import com.churchmanagement.security.AuthContext;

import java.time.LocalDate;
import java.util.List;

/** Saves the finance settings; the go-live date is locked once any entry is posted. */
public class FinanceSettingsService {
    private final SystemSettingService systemSettingService;
    private final JournalRepository journalRepository;
    private final FinanceSettings financeSettings;
    private final ActivityLogService activityLogService;

    public FinanceSettingsService() {
        this(new SystemSettingService(), new JournalRepository(), new FinanceSettings(), new ActivityLogService());
    }

    public FinanceSettingsService(SystemSettingService systemSettingService, JournalRepository journalRepository,
                                  FinanceSettings financeSettings, ActivityLogService activityLogService) {
        this.systemSettingService = systemSettingService;
        this.journalRepository = journalRepository;
        this.financeSettings = financeSettings;
        this.activityLogService = activityLogService;
    }

    public boolean goLiveLocked() {
        try {
            return journalRepository.hasPostedEntries();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to check posted entries right now.", exception);
        }
    }

    public void update(LocalDate goLiveDate, boolean approvalRequired) {
        if (goLiveDate == null) {
            throw new FinanceException("Choose the go-live date.");
        }
        if (!goLiveDate.equals(financeSettings.goLiveDate()) && goLiveLocked()) {
            throw new FinanceException("The go-live date cannot change after entries have been posted.");
        }
        systemSettingService.updateSettings(List.of(
                new UpdateSystemSettingRequest(FinanceSettings.GO_LIVE_DATE_KEY, goLiveDate.toString()),
                new UpdateSystemSettingRequest(FinanceSettings.APPROVAL_REQUIRED_KEY, String.valueOf(approvalRequired))));
        activityLogService.logFinance(AuthContext.getCurrentUser().map(user -> user.getUserId()).orElse(null),
                ActivityLogService.FINANCE_SETTINGS_UPDATED, null,
                "go_live_date: " + goLiveDate + ", approval_required: " + approvalRequired);
    }
}
