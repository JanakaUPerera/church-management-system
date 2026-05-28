package com.churchmanagement.service;

import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.RegionRepository;
import com.churchmanagement.validation.ChurchValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class ChurchService {
    private final ChurchRepository churchRepository;
    private final RegionRepository regionRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public ChurchService() {
        this(new ChurchRepository(), new RegionRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public ChurchService(ChurchRepository churchRepository, RegionRepository regionRepository,
                         ActivityLogService activityLogService, Clock clock) {
        this.churchRepository = churchRepository;
        this.regionRepository = regionRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<Church> findAll() {
        try {
            return churchRepository.findAll();
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to load churches right now. Please try again later.", exception);
        }
    }

    public List<Church> search(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return findAll();
        }

        try {
            return churchRepository.search(searchText.strip());
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to search churches right now. Please try again later.", exception);
        }
    }

    public List<Region> findActiveRegions() {
        try {
            return regionRepository.findActiveRegions();
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to load active regions right now. Please try again later.", exception);
        }
    }

    public Church create(String churchCode, String churchName, Long regionId, Church.Status status, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status);
        validate(church);

        try {
            ensureActiveRegion(church.getRegionId());
            if (churchRepository.existsByChurchCode(church.getChurchCode())) {
                throw new ChurchException("A church with this code already exists.");
            }

            church.setCreatedAt(LocalDateTime.now(clock));
            Church savedChurch = churchRepository.save(church);
            activityLogService.logChurchAction(userId, ActivityLogService.CHURCH_CREATED, savedChurch.getChurchCode());
            return savedChurch;
        } catch (ChurchException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to save church right now. Please try again later.", exception);
        }
    }

    public Church update(long id, String churchCode, String churchName, Long regionId, Church.Status status, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status);
        church.setId(id);
        validate(church);

        try {
            ensureActiveRegion(church.getRegionId());
            if (churchRepository.existsByChurchCodeAndIdNot(church.getChurchCode(), id)) {
                throw new ChurchException("A church with this code already exists.");
            }

            church.setUpdatedAt(LocalDateTime.now(clock));
            Church updatedChurch = churchRepository.update(church);
            activityLogService.logChurchAction(userId, ActivityLogService.CHURCH_UPDATED, updatedChurch.getChurchCode());
            return updatedChurch;
        } catch (ChurchException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to update church right now. Please try again later.", exception);
        }
    }

    public void activate(long id, long userId) {
        updateStatus(id, Church.Status.ACTIVE, userId, ActivityLogService.CHURCH_ACTIVATED);
    }

    public void deactivate(long id, long userId) {
        updateStatus(id, Church.Status.INACTIVE, userId, ActivityLogService.CHURCH_DEACTIVATED);
    }

    private void updateStatus(long id, Church.Status status, long userId, String action) {
        try {
            Church church = churchRepository.findById(id)
                    .orElseThrow(() -> new ChurchException("Church could not be found."));
            churchRepository.updateStatus(id, status, LocalDateTime.now(clock));
            activityLogService.logChurchAction(userId, action, church.getChurchCode());
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to update church status right now. Please try again later.", exception);
        }
    }

    private Church normalize(String churchCode, String churchName, Long regionId, Church.Status status) {
        String normalizedCode = churchCode == null ? "" : churchCode.strip().toUpperCase(Locale.ROOT);
        String normalizedName = churchName == null ? "" : churchName.strip();
        return new Church(null, normalizedCode, normalizedName, regionId, null, null, status, null, null);
    }

    private void validate(Church church) {
        List<String> errors = ChurchValidator.validateForCreateOrUpdate(
                church.getChurchCode(),
                church.getChurchName(),
                church.getRegionId(),
                church.getStatus()
        );

        if (!errors.isEmpty()) {
            throw new ChurchException(String.join("\n", errors));
        }
    }

    private void ensureActiveRegion(long regionId) {
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new ChurchException("Selected region could not be found."));
        if (region.getStatus() != Region.Status.ACTIVE) {
            throw new ChurchException("Selected region is inactive. Choose an active region.");
        }
    }

    public static class ChurchException extends RuntimeException {
        public ChurchException(String message) {
            super(message);
        }

        public ChurchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
