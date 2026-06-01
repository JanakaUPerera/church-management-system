package com.churchmanagement.service;

import com.churchmanagement.entity.Region;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.RegionRepository;
import com.churchmanagement.validation.RegionValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class RegionService {
    private final RegionRepository regionRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public RegionService() {
        this(new RegionRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public RegionService(RegionRepository regionRepository, ActivityLogService activityLogService, Clock clock) {
        this.regionRepository = regionRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<Region> findAll() {
        try {
            return regionRepository.findAll();
        } catch (DatabaseException exception) {
            throw new RegionException("Unable to load regions right now. Please try again later.", exception);
        }
    }

    public List<Region> search(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return findAll();
        }

        try {
            return regionRepository.search(searchText.strip());
        } catch (DatabaseException exception) {
            throw new RegionException("Unable to search regions right now. Please try again later.", exception);
        }
    }

    public Region create(String regionCode, String regionName, Region.Status status, long userId) {
        Region region = normalize(regionCode, regionName, status);
        validate(region);

        try {
            if (regionRepository.existsByRegionCode(region.getRegionCode())) {
                throw new RegionException("A region with this code already exists.");
            }

            region.setCreatedAt(LocalDateTime.now(clock));
            Region savedRegion = regionRepository.save(region);
            activityLogService.logRegionAction(userId, ActivityLogService.REGION_CREATED, savedRegion.getRegionCode());
            return savedRegion;
        } catch (RegionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RegionException("Unable to save region right now. Please try again later.", exception);
        }
    }

    public Region update(long id, String regionCode, String regionName, Region.Status status, long userId) {
        Region region = normalize(regionCode, regionName, status);
        region.setId(id);
        validate(region);

        try {
            Region existing = regionRepository.findById(id)
                    .orElseThrow(() -> new RegionException("Region could not be found."));
            Region previous = new Region(existing.getId(), existing.getRegionCode(), existing.getRegionName(),
                    existing.getStatus(), existing.getCreatedAt(), existing.getUpdatedAt());
            if (regionRepository.existsByRegionCodeAndIdNot(region.getRegionCode(), id)) {
                throw new RegionException("A region with this code already exists.");
            }

            region.setUpdatedAt(LocalDateTime.now(clock));
            Region updatedRegion = regionRepository.update(region);
            activityLogService.logRegionUpdated(userId, previous, updatedRegion);
            return updatedRegion;
        } catch (RegionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RegionException("Unable to update region right now. Please try again later.", exception);
        }
    }

    public void activate(long id, long userId) {
        updateStatus(id, Region.Status.ACTIVE, userId, ActivityLogService.REGION_ACTIVATED);
    }

    public void deactivate(long id, long userId) {
        updateStatus(id, Region.Status.INACTIVE, userId, ActivityLogService.REGION_DEACTIVATED);
    }

    private void updateStatus(long id, Region.Status status, long userId, String action) {
        try {
            Region region = regionRepository.findById(id)
                    .orElseThrow(() -> new RegionException("Region could not be found."));
            regionRepository.updateStatus(id, status, LocalDateTime.now(clock));
            activityLogService.logRegionAction(userId, action, region.getRegionCode());
        } catch (DatabaseException exception) {
            throw new RegionException("Unable to update region status right now. Please try again later.", exception);
        }
    }

    private Region normalize(String regionCode, String regionName, Region.Status status) {
        String normalizedCode = regionCode == null ? "" : regionCode.strip().toUpperCase(Locale.ROOT);
        String normalizedName = regionName == null ? "" : regionName.strip();
        return new Region(null, normalizedCode, normalizedName, status, null, null);
    }

    private void validate(Region region) {
        List<String> errors = RegionValidator.validateForCreateOrUpdate(
                region.getRegionCode(),
                region.getRegionName(),
                region.getStatus()
        );

        if (!errors.isEmpty()) {
            throw new RegionException(String.join("\n", errors));
        }
    }

    public static class RegionException extends RuntimeException {
        public RegionException(String message) {
            super(message);
        }

        public RegionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
