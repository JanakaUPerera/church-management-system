package com.churchmanagement.service;

import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.AuthorizedPersonPosition;
import com.churchmanagement.enums.ReceiptLanguage;
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

    public List<Church> findOperationalChurches() {
        try {
            return churchRepository.findOperationalChurches();
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to load churches right now. Please try again later.", exception);
        }
    }

    public Church findById(long id) {
        try {
            return churchRepository.findById(id)
                    .orElseThrow(() -> new ChurchException("Church could not be found."));
        } catch (ChurchException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new ChurchException("Unable to load church right now. Please try again later.", exception);
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
        return create(churchCode, churchName, regionId, status, null, null, null, null, userId);
    }

    public Church create(String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber, long userId) {
        return create(churchCode, churchName, regionId, status, authorizedPersonName, authorizedPersonPosition,
                authorizedPersonPositionOther, smsMobileNumber, ReceiptLanguage.ENGLISH, userId);
    }

    public Church update(long id, String churchCode, String churchName, Long regionId, Church.Status status, long userId) {
        return update(id, churchCode, churchName, regionId, status, null, null, null, null, userId);
    }

    public Church update(long id, String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status, authorizedPersonName,
                authorizedPersonPosition, authorizedPersonPositionOther, smsMobileNumber, ReceiptLanguage.ENGLISH);
        return update(id, church, userId);
    }

    public Church create(String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber,
                         ReceiptLanguage receiptLanguage, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status, authorizedPersonName,
                authorizedPersonPosition, authorizedPersonPositionOther, smsMobileNumber, receiptLanguage);
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

    public Church update(long id, String churchCode, String churchName, Long regionId, Church.Status status,
                         String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                         String authorizedPersonPositionOther, String smsMobileNumber,
                         ReceiptLanguage receiptLanguage, long userId) {
        Church church = normalize(churchCode, churchName, regionId, status, authorizedPersonName,
                authorizedPersonPosition, authorizedPersonPositionOther, smsMobileNumber, receiptLanguage);
        return update(id, church, userId);
    }

    private Church update(long id, Church church, long userId) {
        church.setId(id);
        validate(church);

        try {
            Church existing = churchRepository.findById(id)
                    .orElseThrow(() -> new ChurchException("Church could not be found."));
            Church previous = copyChurch(existing);
            ensureActiveRegion(church.getRegionId());
            if (churchRepository.existsByChurchCodeAndIdNot(church.getChurchCode(), id)) {
                throw new ChurchException("A church with this code already exists.");
            }

            church.setUpdatedAt(LocalDateTime.now(clock));
            Church updatedChurch = churchRepository.update(church);
            activityLogService.logChurchUpdated(userId, previous, updatedChurch);
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

    private Church normalize(String churchCode, String churchName, Long regionId, Church.Status status,
                             String authorizedPersonName, AuthorizedPersonPosition authorizedPersonPosition,
                             String authorizedPersonPositionOther, String smsMobileNumber,
                             ReceiptLanguage receiptLanguage) {
        String normalizedCode = churchCode == null ? "" : churchCode.strip().toUpperCase(Locale.ROOT);
        String normalizedName = churchName == null ? "" : churchName.strip();
        Church church = new Church(null, normalizedCode, normalizedName, regionId, null, null, status, null, null);
        church.setAuthorizedPersonName(blankToNull(authorizedPersonName));
        church.setAuthorizedPersonPosition(authorizedPersonPosition);
        church.setAuthorizedPersonPositionOther(authorizedPersonPosition == AuthorizedPersonPosition.OTHER
                ? blankToNull(authorizedPersonPositionOther)
                : null);
        church.setSmsMobileNumber(normalizeSmsMobileNumber(smsMobileNumber));
        church.setReceiptLanguage(receiptLanguage);
        return church;
    }

    private Church copyChurch(Church church) {
        Church copy = new Church(church.getId(), church.getChurchCode(), church.getChurchName(), church.getRegionId(),
                church.getRegionCode(), church.getRegionName(), church.getStatus(), church.getCreatedAt(),
                church.getUpdatedAt());
        copy.setRegionStatus(church.getRegionStatus());
        copy.setAuthorizedPersonName(church.getAuthorizedPersonName());
        copy.setAuthorizedPersonPosition(church.getAuthorizedPersonPosition());
        copy.setAuthorizedPersonPositionOther(church.getAuthorizedPersonPositionOther());
        copy.setSmsMobileNumber(church.getSmsMobileNumber());
        copy.setReceiptLanguage(church.getReceiptLanguage());
        return copy;
    }

    private void validate(Church church) {
        List<String> errors = ChurchValidator.validateForCreateOrUpdate(
                church.getChurchCode(),
                church.getChurchName(),
                church.getRegionId(),
                church.getStatus(),
                church.getAuthorizedPersonPosition(),
                church.getAuthorizedPersonPositionOther(),
                church.getSmsMobileNumber(),
                church.getReceiptLanguage()
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

    private String normalizeSmsMobileNumber(String smsMobileNumber) {
        String value = blankToNull(smsMobileNumber);
        if (value == null) {
            return null;
        }

        if (value.startsWith("+")) {
            return value;
        }

        if (value.startsWith("0")) {
            return "+94" + value.substring(1);
        }

        return "+" + value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
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
