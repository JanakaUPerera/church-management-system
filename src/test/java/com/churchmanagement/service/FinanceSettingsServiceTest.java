package com.churchmanagement.service;

import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.dto.SystemSettingDto;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceSettingsServiceTest {
    @Test
    void savesBothSettingsWhileNothingIsPosted() {
        RecordingSettings settingsService = new RecordingSettings();
        FinanceSettingsService service = new FinanceSettingsService(settingsService,
                new FinanceFakes.FakeJournalRepository(), new FinanceFakes.FakeFinanceSettings(),
                new FinanceFakes.RecordingActivityLog());

        service.update(LocalDate.of(2026, 11, 1), true);

        assertEquals(List.of("finance.go_live_date=2026-11-01", "finance.approval.required=true"), settingsService.saved);
    }

    @Test
    void goLiveDateIsLockedOncePosted() {
        FinanceFakes.FakeJournalRepository journal = new FinanceFakes.FakeJournalRepository();
        journal.insertPosted(new JournalEntry(null, null, LocalDate.of(2027, 1, 1), 2027, "x", JournalSourceType.JOURNAL,
                null, JournalStatus.DRAFT, null, 1L, LocalDateTime.now(), null, null, List.of()), 1L, LocalDateTime.now());
        RecordingSettings settingsService = new RecordingSettings();
        FinanceSettingsService service = new FinanceSettingsService(settingsService, journal,
                new FinanceFakes.FakeFinanceSettings(), new FinanceFakes.RecordingActivityLog());

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.update(LocalDate.of(2027, 2, 1), false));

        assertEquals("The go-live date cannot change after entries have been posted.", exception.getMessage());
        service.update(LocalDate.of(2027, 1, 1), true);
        assertEquals(List.of("finance.go_live_date=2027-01-01", "finance.approval.required=true"), settingsService.saved);
    }

    private static class RecordingSettings extends SystemSettingService {
        private final List<String> saved = new ArrayList<>();

        private RecordingSettings() {
            super(null, null, null);
        }

        @Override
        public List<SystemSettingDto> updateSettings(List<UpdateSystemSettingRequest> requests) {
            requests.forEach(request -> saved.add(request.getSettingKey() + "=" + request.getSettingValue()));
            return List.of();
        }
    }
}
