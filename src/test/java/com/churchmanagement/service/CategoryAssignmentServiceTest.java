package com.churchmanagement.service;

import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.repository.CategoryAssignmentRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryAssignmentServiceTest {
    private static final long CHURCH = 10L;
    private static final long A = 1L;
    private static final long C = 3L;
    private static final long E = 5L;
    private static final long INACTIVE = 9L;

    private final FakeAssignmentRepository assignments = new FakeAssignmentRepository();
    private final FakeCategoryRepository categories = new FakeCategoryRepository();
    private final FakeActivityLogService activityLog = new FakeActivityLogService();
    private final RecordingDataSource dataSource = new RecordingDataSource();
    private final CategoryAssignmentService service = new CategoryAssignmentService(assignments, categories,
            activityLog, Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("UTC")), dataSource);

    @Test
    void yearWithoutReceiptsIsUnlocked() {
        assignAll(2026, A);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2026);

        assertFalse(result.locked());
        assertEquals(A, result.categoryIds().get(CollectionType.TITHES));
    }

    @Test
    void yearWithReceiptsIsLockedWithReason() {
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 14);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2026);

        assertTrue(result.locked());
        assertEquals("Locked: 14 receipt(s) exist for 2026.", result.lockReason());
    }

    @Test
    void cancelledReceiptsAlsoLockTheYear() {
        // countReceiptsForYear counts every status; the repository SQL has no status filter (see Step 4).
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 1);

        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2026, allOf(C), 1L));

        assertEquals("Categories for 2026 are locked because receipts already exist.", exception.getMessage());
    }

    @Test
    void futureYearStaysEditableWhileCurrentYearIsLocked() {
        assignAll(2026, A);
        assignments.receiptCounts.put(2026, 5);

        service.saveAssignments(CHURCH, "0012", 2027, allOf(C), 1L);

        assertEquals(C, assignments.get(2027, CollectionType.OFFERTORY));
        assertTrue(dataSource.committed());
        assertEquals(1, activityLog.assignmentUpdates);
    }

    @Test
    void saveRejectsInactiveCategory() {
        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2027, allOf(INACTIVE), 1L));

        assertEquals("Category Z is inactive and cannot be assigned.", exception.getMessage());
    }

    @Test
    void saveRejectsMissingCollectionType() {
        Map<CollectionType, Long> partial = new HashMap<>(allOf(A));
        partial.remove(CollectionType.OTHER_DONATIONS);

        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.saveAssignments(CHURCH, "0012", 2027, partial, 1L));

        assertEquals("Choose a category for Other Donations.", exception.getMessage());
    }

    @Test
    void resolveUsesExistingAssignments() {
        assignAll(2026, A);
        assignments.put(2026, CollectionType.OFFERTORY, E);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2026, null);

        assertEquals(E, resolved.categoryIds().get(CollectionType.OFFERTORY));
        assertEquals(A, resolved.categoryIds().get(CollectionType.TITHES));
        assertNull(resolved.carriedForwardFrom());
    }

    @Test
    void resolveCarriesForwardFromPreviousYear() {
        assignAll(2026, A);
        assignments.put(2026, CollectionType.OFFERTORY, E);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2027, null);

        assertEquals(2026, resolved.carriedForwardFrom());
        assertEquals(E, assignments.get(2027, CollectionType.OFFERTORY));
        assertEquals(A, assignments.get(2027, CollectionType.TITHES));
    }

    @Test
    void resolveCarriesForwardFromOlderYearWhenAYearWasSkipped() {
        assignAll(2025, C);

        CategoryAssignmentService.ResolvedCategories resolved = service.resolveForReceipt(CHURCH, 2027, null);

        assertEquals(2025, resolved.carriedForwardFrom());
        assertEquals(C, assignments.get(2027, CollectionType.TITHES));
    }

    @Test
    void resolveWithoutAnyAssignmentIsRejected() {
        CategoryAssignmentService.CategoryAssignmentException exception = assertThrows(
                CategoryAssignmentService.CategoryAssignmentException.class,
                () -> service.resolveForReceipt(CHURCH, 2026, null));

        assertEquals(CategoryAssignmentService.NOT_ASSIGNED_MESSAGE, exception.getMessage());
    }

    @Test
    void findForChurchShowsCarryForwardPreviewForEmptyYear() {
        assignAll(2026, A);

        ChurchCategoryAssignments result = service.findForChurch(CHURCH, 2027);

        assertEquals(2026, result.carriedForwardFrom());
        assertEquals(A, result.categoryIds().get(CollectionType.TITHES));
        assertFalse(result.locked());
    }

    @Test
    void assignInitialDefaultsToCategoryA() {
        service.assignInitial(CHURCH, 2026, null, null);

        for (CollectionType type : CollectionType.values()) {
            assertEquals(A, assignments.get(2026, type));
        }
    }

    @Test
    void selectableYearsIncludeCurrentAndNextYear() {
        assignAll(2025, A);

        assertEquals(List.of(2025, 2026, 2027), service.selectableYears(CHURCH));
    }

    private void assignAll(int year, long categoryId) {
        for (CollectionType type : CollectionType.values()) {
            assignments.put(year, type, categoryId);
        }
    }

    private static Map<CollectionType, Long> allOf(long categoryId) {
        Map<CollectionType, Long> map = new HashMap<>();
        for (CollectionType type : CollectionType.values()) {
            map.put(type, categoryId);
        }
        return map;
    }

    private static class FakeAssignmentRepository extends CategoryAssignmentRepository {
        private final List<CategoryAssignment> rows = new ArrayList<>();
        private final Map<Integer, Integer> receiptCounts = new HashMap<>();

        private FakeAssignmentRepository() {
            super((DataSource) null);
        }

        void put(int year, CollectionType type, long categoryId) {
            rows.removeIf(row -> row.accountingYear() == year && row.collectionType() == type);
            rows.add(new CategoryAssignment(CHURCH, type, categoryId, year));
        }

        Long get(int year, CollectionType type) {
            return rows.stream()
                    .filter(row -> row.accountingYear() == year && row.collectionType() == type)
                    .map(CategoryAssignment::categoryId)
                    .findFirst().orElse(null);
        }

        @Override
        public List<CategoryAssignment> findByChurchAndYear(long churchId, int year, Connection connection) {
            return rows.stream().filter(row -> row.churchId() == churchId && row.accountingYear() == year).toList();
        }

        @Override
        public List<CategoryAssignment> findByChurchAndYearForUpdate(long churchId, int year, Connection connection) {
            return findByChurchAndYear(churchId, year, connection);
        }

        @Override
        public OptionalInt findLatestYearBefore(long churchId, int year, Connection connection) {
            return rows.stream()
                    .filter(row -> row.churchId() == churchId && row.accountingYear() < year)
                    .mapToInt(CategoryAssignment::accountingYear)
                    .max();
        }

        @Override
        public List<Integer> findYears(long churchId, Connection connection) {
            return rows.stream().map(CategoryAssignment::accountingYear).distinct().sorted().toList();
        }

        @Override
        public int countReceiptsForYear(long churchId, int year, Connection connection) {
            return receiptCounts.getOrDefault(year, 0);
        }

        @Override
        public void upsert(long churchId, int year, CollectionType type, long categoryId, LocalDateTime now,
                           Connection connection) {
            put(year, type, categoryId);
        }
    }

    private static class FakeCategoryRepository extends CollectionCategoryRepository {
        private final List<CollectionCategory> categories = List.of(
                category(A, "A", CollectionCategory.Status.ACTIVE),
                category(C, "C", CollectionCategory.Status.ACTIVE),
                category(E, "E", CollectionCategory.Status.ACTIVE),
                category(INACTIVE, "Z", CollectionCategory.Status.INACTIVE));

        private FakeCategoryRepository() {
            super((DataSource) null);
        }

        private static CollectionCategory category(long id, String code, CollectionCategory.Status status) {
            return new CollectionCategory(id, code, "Category " + code, null, status, LocalDateTime.now(), null);
        }

        @Override
        public Optional<CollectionCategory> findById(long id) {
            return categories.stream().filter(category -> category.getId() == id).findFirst();
        }

        @Override
        public Optional<CollectionCategory> findByCode(String code) {
            return categories.stream().filter(category -> category.getCode().equals(code)).findFirst();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private int assignmentUpdates;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logCategoryAssignmentsUpdated(long userId, String churchCode, int year, String oldValue,
                                                  String newValue) {
            assignmentUpdates++;
        }
    }
}
