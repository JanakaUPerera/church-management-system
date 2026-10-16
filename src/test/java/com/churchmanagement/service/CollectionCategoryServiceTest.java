package com.churchmanagement.service;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.repository.CollectionCategoryRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionCategoryServiceTest {
    private final FakeCategoryRepository repository = new FakeCategoryRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final CollectionCategoryService service = new CollectionCategoryService(repository, activityLogService,
            Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("UTC")));

    @Test
    void createNormalisesCodeToUpperCase() {
        CollectionCategory category = service.create(" f ", " Building Fund ", " For the new hall ",
                CollectionCategory.Status.ACTIVE, 1L);

        assertEquals("F", category.getCode());
        assertEquals("Building Fund", category.getName());
        assertEquals("For the new hall", category.getDescription());
        assertEquals(ActivityLogService.CATEGORY_CREATED, activityLogService.actions.getFirst());
    }

    @Test
    void createRejectsDuplicateCode() {
        service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create("a", "Another", null, CollectionCategory.Status.ACTIVE, 1L));

        assertEquals("A category with this code already exists.", exception.getMessage());
    }

    @Test
    void createRejectsMissingCodeAndName() {
        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create(" ", " ", null, CollectionCategory.Status.ACTIVE, 1L));

        assertTrue(exception.getMessage().contains("Category code is required."));
        assertTrue(exception.getMessage().contains("Category name is required."));
    }

    @Test
    void createRejectsCodeWithSymbols() {
        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.create("A-1", "Bad", null, CollectionCategory.Status.ACTIVE, 1L));

        assertTrue(exception.getMessage().contains("Category code may only contain letters and digits (max 10)."));
    }

    @Test
    void updateRenamesCategory() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        CollectionCategory updated = service.update(category.getId(), "A", "General Fund", null,
                CollectionCategory.Status.ACTIVE, 1L);

        assertEquals("General Fund", updated.getName());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_UPDATED));
    }

    @Test
    void deleteUnusedCategory() {
        CollectionCategory category = service.create("F", "Spare", null, CollectionCategory.Status.ACTIVE, 1L);

        service.delete(category.getId(), 1L);

        assertTrue(repository.findById(category.getId()).isEmpty());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_DELETED));
    }

    @Test
    void deleteCategoryInUseIsRejected() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);
        repository.inUseIds.add(category.getId());

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.delete(category.getId(), 1L));

        assertEquals("Category is in use and cannot be deleted.", exception.getMessage());
        assertTrue(repository.findById(category.getId()).isPresent());
    }

    @Test
    void deactivateCategoryInUseIsRejected() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);
        repository.inUseIds.add(category.getId());

        CollectionCategoryService.CollectionCategoryException exception = assertThrows(
                CollectionCategoryService.CollectionCategoryException.class,
                () -> service.deactivate(category.getId(), 1L));

        assertEquals("Category is in use and cannot be deactivated.", exception.getMessage());
        assertEquals(CollectionCategory.Status.ACTIVE, repository.findById(category.getId()).orElseThrow().getStatus());
    }

    @Test
    void deactivateCategory() {
        CollectionCategory category = service.create("A", "Category A", null, CollectionCategory.Status.ACTIVE, 1L);

        service.deactivate(category.getId(), 1L);

        assertEquals(CollectionCategory.Status.INACTIVE, repository.findById(category.getId()).orElseThrow().getStatus());
        assertTrue(activityLogService.actions.contains(ActivityLogService.CATEGORY_DEACTIVATED));
    }

    private static class FakeCategoryRepository extends CollectionCategoryRepository {
        private final List<CollectionCategory> categories = new ArrayList<>();
        private final Set<Long> inUseIds = new HashSet<>();
        private long nextId = 1L;

        private FakeCategoryRepository() {
            super((DataSource) null);
        }

        @Override
        public List<CollectionCategory> findAll() {
            return List.copyOf(categories);
        }

        @Override
        public Optional<CollectionCategory> findById(long id) {
            return categories.stream().filter(category -> category.getId() == id).findFirst();
        }

        @Override
        public boolean existsByCode(String code) {
            return categories.stream().anyMatch(category -> category.getCode().equals(code));
        }

        @Override
        public boolean existsByCodeAndIdNot(String code, long id) {
            return categories.stream().anyMatch(category -> category.getCode().equals(code) && category.getId() != id);
        }

        @Override
        public boolean isInUse(long id) {
            return inUseIds.contains(id);
        }

        @Override
        public CollectionCategory save(CollectionCategory category) {
            category.setId(nextId++);
            categories.add(category);
            return category;
        }

        @Override
        public CollectionCategory update(CollectionCategory category) {
            CollectionCategory existing = findById(category.getId()).orElseThrow();
            existing.setCode(category.getCode());
            existing.setName(category.getName());
            existing.setDescription(category.getDescription());
            existing.setStatus(category.getStatus());
            existing.setUpdatedAt(category.getUpdatedAt());
            return existing;
        }

        @Override
        public void updateStatus(long id, CollectionCategory.Status status, LocalDateTime updatedAt) {
            findById(id).orElseThrow().setStatus(status);
        }

        @Override
        public void delete(long id) {
            categories.removeIf(category -> category.getId() == id);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> actions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logCategoryAction(long userId, String action, String categoryCode) {
            actions.add(action);
        }
    }
}
