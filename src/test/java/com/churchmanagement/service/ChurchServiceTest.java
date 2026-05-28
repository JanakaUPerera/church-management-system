package com.churchmanagement.service;

import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.AuthorizedPersonPosition;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.RegionRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChurchServiceTest {
    private final FakeChurchRepository churchRepository = new FakeChurchRepository();
    private final FakeRegionRepository regionRepository = new FakeRegionRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final ChurchService churchService = new ChurchService(
            churchRepository,
            regionRepository,
            activityLogService,
            Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneId.of("UTC"))
    );

    @Test
    void createValidChurch() {
        Church church = churchService.create(" ch001 ", " Main Church ", 1L, Church.Status.ACTIVE, 1L);

        assertEquals("CH001", church.getChurchCode());
        assertEquals("Main Church", church.getChurchName());
        assertEquals(1L, church.getRegionId());
        assertEquals(Church.Status.ACTIVE, church.getStatus());
        assertEquals(ActivityLogService.CHURCH_CREATED, activityLogService.loggedActions.getFirst());
    }

    @Test
    void createChurchWithoutAuthorizedPersonDetails() {
        Church church = churchService.create("CH010", "Main Church", 1L, Church.Status.ACTIVE,
                null, null, null, null, 1L);

        assertEquals(null, church.getAuthorizedPersonName());
        assertEquals(null, church.getAuthorizedPersonPosition());
        assertEquals(null, church.getSmsMobileNumber());
    }

    @Test
    void createChurchWithValidAuthorizedPersonDetails() {
        Church church = churchService.create("CH011", "Main Church", 1L, Church.Status.ACTIVE,
                "  Nimal  ", AuthorizedPersonPosition.TREASURER, null, "0771234567", 1L);

        assertEquals("Nimal", church.getAuthorizedPersonName());
        assertEquals(AuthorizedPersonPosition.TREASURER, church.getAuthorizedPersonPosition());
        assertEquals("+94771234567", church.getSmsMobileNumber());
    }

    @Test
    void rejectOtherPositionWithoutOtherPositionText() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("CH012", "Main Church", 1L, Church.Status.ACTIVE,
                        "Nimal", AuthorizedPersonPosition.OTHER, " ", null, 1L)
        );

        assertTrue(exception.getMessage().contains("Other position is required"));
    }

    @Test
    void saveOtherPositionAsNullWhenPositionIsNotOther() {
        Church church = churchService.create("CH013", "Main Church", 1L, Church.Status.ACTIVE,
                "Nimal", AuthorizedPersonPosition.PASTOR, "Ignored", null, 1L);

        assertEquals(null, church.getAuthorizedPersonPositionOther());
    }

    @Test
    void acceptSmsNumberStartingWithZeroAndNormalize() {
        Church church = churchService.create("CH014", "Main Church", 1L, Church.Status.ACTIVE,
                null, null, null, "0771234567", 1L);

        assertEquals("+94771234567", church.getSmsMobileNumber());
    }

    @Test
    void acceptSmsNumberStartingWithCountryCodeAndNormalize() {
        Church church = churchService.create("CH015", "Main Church", 1L, Church.Status.ACTIVE,
                null, null, null, "94771234567", 1L);

        assertEquals("+94771234567", church.getSmsMobileNumber());
    }

    @Test
    void acceptSmsNumberStartingWithPlusCountryCode() {
        Church church = churchService.create("CH016", "Main Church", 1L, Church.Status.ACTIVE,
                null, null, null, "+94771234567", 1L);

        assertEquals("+94771234567", church.getSmsMobileNumber());
    }

    @Test
    void rejectInvalidSmsNumber() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("CH017", "Main Church", 1L, Church.Status.ACTIVE,
                        null, null, null, "071234", 1L)
        );

        assertTrue(exception.getMessage().contains("SMS mobile number"));
    }

    @Test
    void updateChurchContactSmsDetails() {
        Church church = churchService.create("CH018", "Main Church", 1L, Church.Status.ACTIVE, 1L);

        Church updated = churchService.update(church.getId(), "CH018", "Main Church", 1L, Church.Status.ACTIVE,
                "Kamal", AuthorizedPersonPosition.OTHER, "Coordinator", "94771234567", 1L);

        assertEquals("Kamal", updated.getAuthorizedPersonName());
        assertEquals(AuthorizedPersonPosition.OTHER, updated.getAuthorizedPersonPosition());
        assertEquals("Coordinator", updated.getAuthorizedPersonPositionOther());
        assertEquals("+94771234567", updated.getSmsMobileNumber());
    }

    @Test
    void rejectEmptyCode() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create(" ", "Main Church", 1L, Church.Status.ACTIVE, 1L)
        );

        assertTrue(exception.getMessage().contains("Church code is required."));
    }

    @Test
    void rejectEmptyName() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("CH001", " ", 1L, Church.Status.ACTIVE, 1L)
        );

        assertTrue(exception.getMessage().contains("Church name is required."));
    }

    @Test
    void rejectMissingRegion() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("CH001", "Main Church", null, Church.Status.ACTIVE, 1L)
        );

        assertTrue(exception.getMessage().contains("Region is required."));
    }

    @Test
    void rejectInactiveRegion() {
        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("CH001", "Main Church", 2L, Church.Status.ACTIVE, 1L)
        );

        assertEquals("Selected region is inactive. Choose an active region.", exception.getMessage());
    }

    @Test
    void rejectDuplicateCode() {
        churchService.create("CH001", "Main Church", 1L, Church.Status.ACTIVE, 1L);

        ChurchService.ChurchException exception = assertThrows(
                ChurchService.ChurchException.class,
                () -> churchService.create("ch001", "Branch Church", 1L, Church.Status.ACTIVE, 1L)
        );

        assertEquals("A church with this code already exists.", exception.getMessage());
    }

    @Test
    void updateChurch() {
        Church church = churchService.create("CH001", "Main Church", 1L, Church.Status.ACTIVE, 1L);

        Church updated = churchService.update(church.getId(), " ch002 ", " Branch Church ", 1L,
                Church.Status.INACTIVE, 1L);

        assertEquals("CH002", updated.getChurchCode());
        assertEquals("Branch Church", updated.getChurchName());
        assertEquals(Church.Status.INACTIVE, updated.getStatus());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.CHURCH_UPDATED));
    }

    @Test
    void deactivateChurch() {
        Church church = churchService.create("CH001", "Main Church", 1L, Church.Status.ACTIVE, 1L);

        churchService.deactivate(church.getId(), 1L);

        assertEquals(Church.Status.INACTIVE, churchRepository.findById(church.getId()).orElseThrow().getStatus());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.CHURCH_DEACTIVATED));
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private final List<Church> churches = new ArrayList<>();
        private long nextId = 1L;

        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Church> findById(long id) {
            return churches.stream().filter(church -> church.getId() == id).findFirst();
        }

        @Override
        public boolean existsByChurchCode(String churchCode) {
            return churches.stream().anyMatch(church -> church.getChurchCode().equals(churchCode));
        }

        @Override
        public boolean existsByChurchCodeAndIdNot(String churchCode, long id) {
            return churches.stream()
                    .anyMatch(church -> church.getChurchCode().equals(churchCode) && church.getId() != id);
        }

        @Override
        public Church save(Church church) {
            church.setId(nextId++);
            churches.add(church);
            return church;
        }

        @Override
        public Church update(Church church) {
            Church existing = findById(church.getId()).orElseThrow();
            existing.setChurchCode(church.getChurchCode());
            existing.setChurchName(church.getChurchName());
            existing.setRegionId(church.getRegionId());
            existing.setStatus(church.getStatus());
            existing.setAuthorizedPersonName(church.getAuthorizedPersonName());
            existing.setAuthorizedPersonPosition(church.getAuthorizedPersonPosition());
            existing.setAuthorizedPersonPositionOther(church.getAuthorizedPersonPositionOther());
            existing.setSmsMobileNumber(church.getSmsMobileNumber());
            existing.setUpdatedAt(church.getUpdatedAt());
            return existing;
        }

        @Override
        public void updateStatus(long id, Church.Status status, LocalDateTime updatedAt) {
            Church existing = findById(id).orElseThrow();
            existing.setStatus(status);
            existing.setUpdatedAt(updatedAt);
        }
    }

    private static class FakeRegionRepository extends RegionRepository {
        private final List<Region> regions = List.of(
                new Region(1L, "REG001", "Colombo", Region.Status.ACTIVE, LocalDateTime.now(), null),
                new Region(2L, "REG002", "Kandy", Region.Status.INACTIVE, LocalDateTime.now(), null)
        );

        private FakeRegionRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Region> findById(long id) {
            return regions.stream().filter(region -> region.getId() == id).findFirst();
        }

        @Override
        public List<Region> findActiveRegions() {
            return regions.stream().filter(region -> region.getStatus() == Region.Status.ACTIVE).toList();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> loggedActions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logChurchAction(long userId, String action, String churchCode) {
            loggedActions.add(action);
        }
    }
}
