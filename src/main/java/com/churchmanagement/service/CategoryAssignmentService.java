package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.CategoryAssignmentRepository;
import com.churchmanagement.repository.CollectionCategoryRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CategoryAssignmentService {
    public static final String NOT_ASSIGNED_MESSAGE =
            "Categories are not assigned for this branch. Assign them in the branch's Categories tab.";
    static final String DEFAULT_CATEGORY_CODE = "A";

    private final CategoryAssignmentRepository assignmentRepository;
    private final CollectionCategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final DataSource dataSource;

    public CategoryAssignmentService() {
        this(new CategoryAssignmentRepository(), new CollectionCategoryRepository(), new ActivityLogService(),
                Clock.systemDefaultZone(), DatabaseConfig.getDataSource());
    }

    public CategoryAssignmentService(CategoryAssignmentRepository assignmentRepository,
                                     CollectionCategoryRepository categoryRepository,
                                     ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this.assignmentRepository = assignmentRepository;
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.dataSource = dataSource;
    }

    public ChurchCategoryAssignments findForChurch(long churchId, int year) {
        try (Connection connection = dataSource.getConnection()) {
            List<CategoryAssignment> rows = assignmentRepository.findByChurchAndYear(churchId, year, connection);
            int receiptCount = assignmentRepository.countReceiptsForYear(churchId, year, connection);
            Integer carriedForwardFrom = null;
            if (rows.isEmpty()) {
                OptionalInt previousYear = assignmentRepository.findLatestYearBefore(churchId, year, connection);
                if (previousYear.isPresent()) {
                    carriedForwardFrom = previousYear.getAsInt();
                    rows = assignmentRepository.findByChurchAndYear(churchId, carriedForwardFrom, connection);
                }
            }
            boolean locked = receiptCount > 0;
            return new ChurchCategoryAssignments(year, toMap(rows), locked,
                    locked ? "Locked: " + receiptCount + " receipt(s) exist for " + year + "." : null,
                    carriedForwardFrom);
        } catch (SQLException | DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    /** Years that have assignments, plus the current and the next year, in ascending order. */
    public List<Integer> selectableYears(long churchId) {
        int currentYear = LocalDate.now(clock).getYear();
        try (Connection connection = dataSource.getConnection()) {
            TreeSet<Integer> years = new TreeSet<>(assignmentRepository.findYears(churchId, connection));
            years.add(currentYear);
            years.add(currentYear + 1);
            return List.copyOf(years);
        } catch (SQLException | DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load category years right now.", exception);
        }
    }

    /**
     * The branch parts that use {@code categoryId} in any accounting year from {@code fromYear} to {@code toYear},
     * keyed by church id. A year without its own assignments uses the most recent earlier year, as receipts do.
     */
    public Map<Long, Set<CollectionType>> partsInCategory(long categoryId, int fromYear, int toYear) {
        try (Connection connection = dataSource.getConnection()) {
            // Rows come oldest first, so each put leaves the latest assignment up to that year.
            Map<Long, Map<CollectionType, Long>> effective = new HashMap<>();
            Map<Long, Set<CollectionType>> parts = new HashMap<>();
            List<CategoryAssignment> rows = assignmentRepository.findAllUpToYear(toYear, connection);
            int index = 0;
            for (int year = fromYear; year <= toYear; year++) {
                while (index < rows.size() && rows.get(index).accountingYear() <= year) {
                    CategoryAssignment row = rows.get(index++);
                    effective.computeIfAbsent(row.churchId(), id -> new EnumMap<>(CollectionType.class))
                            .put(row.collectionType(), row.categoryId());
                }
                effective.forEach((churchId, categories) -> categories.forEach((type, assigned) -> {
                    if (assigned == categoryId) {
                        parts.computeIfAbsent(churchId, id -> EnumSet.noneOf(CollectionType.class)).add(type);
                    }
                }));
            }
            return parts;
        } catch (SQLException | DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load category assignments right now.", exception);
        }
    }

    /** Id of the default category (A) when it exists and is active; null otherwise (the user then picks one). */
    public Long defaultCategoryId() {
        try {
            return categoryRepository.findByCode(DEFAULT_CATEGORY_CODE)
                    .filter(CollectionCategory::isActive)
                    .map(CollectionCategory::getId)
                    .orElse(null);
        } catch (DatabaseException exception) {
            throw new CategoryAssignmentException("Unable to load categories right now.", exception);
        }
    }

    public void saveAssignments(long churchId, String churchCode, int year, Map<CollectionType, Long> categoryIds,
                                long userId) {
        validateSelection(categoryIds);
        Connection connection = null;
        boolean previousAutoCommit = true;
        Map<CollectionType, Long> previous;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            previous = toMap(assignmentRepository.findByChurchAndYearForUpdate(churchId, year, connection));
            if (assignmentRepository.countReceiptsForYear(churchId, year, connection) > 0) {
                throw new CategoryAssignmentException(
                        "Categories for " + year + " are locked because receipts already exist.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            for (CollectionType type : CollectionType.values()) {
                assignmentRepository.upsert(churchId, year, type, categoryIds.get(type), now, connection);
            }
            connection.commit();
        } catch (CategoryAssignmentException exception) {
            rollback(connection);
            throw exception;
        } catch (SQLException | DatabaseException exception) {
            rollback(connection);
            throw new CategoryAssignmentException("Unable to save categories right now. Please try again later.", exception);
        } finally {
            close(connection, previousAutoCommit);
        }
        activityLogService.logCategoryAssignmentsUpdated(userId, churchCode, year, describe(previous),
                describe(categoryIds));
    }

    /** Saves a new branch's first assignments on the caller's transaction; null means the default category (A) for all types. */
    public void assignInitial(long churchId, int year, Map<CollectionType, Long> categoryIds, Connection connection) {
        Map<CollectionType, Long> selection = categoryIds == null ? defaultSelection() : categoryIds;
        validateSelection(selection);
        LocalDateTime now = LocalDateTime.now(clock);
        for (CollectionType type : CollectionType.values()) {
            assignmentRepository.upsert(churchId, year, type, selection.get(type), now, connection);
        }
    }

    /**
     * The category for each collection type of a receipt in {@code year}, on the receipt's transaction.
     * Carries the most recent earlier year forward when the year has no assignments yet.
     */
    public ResolvedCategories resolveForReceipt(long churchId, int year, Connection connection) {
        List<CategoryAssignment> rows = assignmentRepository.findByChurchAndYearForUpdate(churchId, year, connection);
        Map<CollectionType, Long> categoryIds = new EnumMap<>(CollectionType.class);
        categoryIds.putAll(toMap(rows));
        if (categoryIds.size() == CollectionType.values().length) {
            return new ResolvedCategories(categoryIds, null);
        }

        OptionalInt previousYear = assignmentRepository.findLatestYearBefore(churchId, year, connection);
        if (previousYear.isEmpty()) {
            throw new CategoryAssignmentException(NOT_ASSIGNED_MESSAGE);
        }
        Map<CollectionType, Long> source = toMap(
                assignmentRepository.findByChurchAndYear(churchId, previousYear.getAsInt(), connection));
        LocalDateTime now = LocalDateTime.now(clock);
        for (CollectionType type : CollectionType.values()) {
            if (categoryIds.containsKey(type)) {
                continue;
            }
            Long categoryId = source.get(type);
            if (categoryId == null) {
                throw new CategoryAssignmentException(NOT_ASSIGNED_MESSAGE);
            }
            assignmentRepository.upsert(churchId, year, type, categoryId, now, connection);
            categoryIds.put(type, categoryId);
        }
        return new ResolvedCategories(categoryIds, previousYear.getAsInt());
    }

    private Map<CollectionType, Long> defaultSelection() {
        Long defaultId = defaultCategoryId();
        if (defaultId == null) {
            throw new CategoryAssignmentException(
                    "Default category " + DEFAULT_CATEGORY_CODE + " is not available. Choose the branch's categories.");
        }
        Map<CollectionType, Long> selection = new EnumMap<>(CollectionType.class);
        for (CollectionType type : CollectionType.values()) {
            selection.put(type, defaultId);
        }
        return selection;
    }

    private void validateSelection(Map<CollectionType, Long> categoryIds) {
        for (CollectionType type : CollectionType.values()) {
            Long categoryId = categoryIds == null ? null : categoryIds.get(type);
            if (categoryId == null) {
                throw new CategoryAssignmentException("Choose a category for " + type.getDisplayLabel() + ".");
            }
            CollectionCategory category;
            try {
                category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new CategoryAssignmentException("Selected category could not be found."));
            } catch (DatabaseException exception) {
                throw new CategoryAssignmentException("Unable to check categories right now.", exception);
            }
            if (!category.isActive()) {
                throw new CategoryAssignmentException(
                        "Category " + category.getCode() + " is inactive and cannot be assigned.");
            }
        }
    }

    private static Map<CollectionType, Long> toMap(List<CategoryAssignment> rows) {
        return rows.stream().collect(Collectors.toMap(CategoryAssignment::collectionType,
                CategoryAssignment::categoryId, (left, right) -> left, () -> new EnumMap<>(CollectionType.class)));
    }

    private String describe(Map<CollectionType, Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return "(not set)";
        }
        StringBuilder text = new StringBuilder();
        for (CollectionType type : CollectionType.values()) {
            Long id = categoryIds.get(type);
            String code = id == null ? "-" : categoryRepository.findById(id).map(CollectionCategory::getCode).orElse("?");
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(type.getDisplayLabel()).append('=').append(code);
        }
        return text.toString();
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure is more useful to the caller.
        }
    }

    private void close(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(previousAutoCommit);
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while closing.
        }
    }

    public record ResolvedCategories(Map<CollectionType, Long> categoryIds, Integer carriedForwardFrom) {
    }

    public static class CategoryAssignmentException extends RuntimeException {
        public CategoryAssignmentException(String message) {
            super(message);
        }

        public CategoryAssignmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
