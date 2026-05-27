package com.churchmanagement.service;

import com.churchmanagement.entity.Region;
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

class RegionServiceTest {
    private final FakeRegionRepository regionRepository = new FakeRegionRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final RegionService regionService = new RegionService(
            regionRepository,
            activityLogService,
            Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneId.of("UTC"))
    );

    @Test
    void createValidRegion() {
        Region region = regionService.create(" reg001 ", " Colombo ", Region.Status.ACTIVE, 1L);

        assertEquals("REG001", region.getRegionCode());
        assertEquals("Colombo", region.getRegionName());
        assertEquals(Region.Status.ACTIVE, region.getStatus());
        assertEquals(1, activityLogService.loggedActions.size());
        assertEquals(ActivityLogService.REGION_CREATED, activityLogService.loggedActions.getFirst());
    }

    @Test
    void rejectEmptyCode() {
        RegionService.RegionException exception = assertThrows(
                RegionService.RegionException.class,
                () -> regionService.create(" ", "Colombo", Region.Status.ACTIVE, 1L)
        );

        assertTrue(exception.getMessage().contains("Region code is required."));
    }

    @Test
    void rejectEmptyName() {
        RegionService.RegionException exception = assertThrows(
                RegionService.RegionException.class,
                () -> regionService.create("REG001", " ", Region.Status.ACTIVE, 1L)
        );

        assertTrue(exception.getMessage().contains("Region name is required."));
    }

    @Test
    void rejectDuplicateCode() {
        regionService.create("REG001", "Colombo", Region.Status.ACTIVE, 1L);

        RegionService.RegionException exception = assertThrows(
                RegionService.RegionException.class,
                () -> regionService.create("reg001", "Kandy", Region.Status.ACTIVE, 1L)
        );

        assertEquals("A region with this code already exists.", exception.getMessage());
    }

    @Test
    void updateRegion() {
        Region region = regionService.create("REG001", "Colombo", Region.Status.ACTIVE, 1L);

        Region updated = regionService.update(region.getId(), " reg002 ", " Kandy ", Region.Status.INACTIVE, 1L);

        assertEquals("REG002", updated.getRegionCode());
        assertEquals("Kandy", updated.getRegionName());
        assertEquals(Region.Status.INACTIVE, updated.getStatus());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.REGION_UPDATED));
    }

    @Test
    void deactivateRegion() {
        Region region = regionService.create("REG001", "Colombo", Region.Status.ACTIVE, 1L);

        regionService.deactivate(region.getId(), 1L);

        assertEquals(Region.Status.INACTIVE, regionRepository.findById(region.getId()).orElseThrow().getStatus());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.REGION_DEACTIVATED));
    }

    private static class FakeRegionRepository extends RegionRepository {
        private final List<Region> regions = new ArrayList<>();
        private long nextId = 1L;

        private FakeRegionRepository() {
            super((DataSource) null);
        }

        @Override
        public List<Region> findAll() {
            return List.copyOf(regions);
        }

        @Override
        public Optional<Region> findById(long id) {
            return regions.stream()
                    .filter(region -> region.getId() == id)
                    .findFirst();
        }

        @Override
        public boolean existsByRegionCode(String regionCode) {
            return regions.stream().anyMatch(region -> region.getRegionCode().equals(regionCode));
        }

        @Override
        public boolean existsByRegionCodeAndIdNot(String regionCode, long id) {
            return regions.stream()
                    .anyMatch(region -> region.getRegionCode().equals(regionCode) && region.getId() != id);
        }

        @Override
        public Region save(Region region) {
            region.setId(nextId++);
            regions.add(region);
            return region;
        }

        @Override
        public Region update(Region region) {
            Region existing = findById(region.getId()).orElseThrow();
            existing.setRegionCode(region.getRegionCode());
            existing.setRegionName(region.getRegionName());
            existing.setStatus(region.getStatus());
            existing.setUpdatedAt(region.getUpdatedAt());
            return existing;
        }

        @Override
        public void updateStatus(long id, Region.Status status, LocalDateTime updatedAt) {
            Region existing = findById(id).orElseThrow();
            existing.setStatus(status);
            existing.setUpdatedAt(updatedAt);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> loggedActions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logRegionAction(long userId, String action, String regionCode) {
            loggedActions.add(action);
        }
    }
}
