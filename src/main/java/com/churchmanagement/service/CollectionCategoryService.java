package com.churchmanagement.service;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.CollectionCategoryRepository;
import com.churchmanagement.validation.CollectionCategoryValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CollectionCategoryService {
    private final CollectionCategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public CollectionCategoryService() {
        this(new CollectionCategoryRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public CollectionCategoryService(CollectionCategoryRepository categoryRepository,
                                     ActivityLogService activityLogService, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<CollectionCategory> findAll() {
        try {
            return categoryRepository.findAll();
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    public List<CollectionCategory> findActive() {
        try {
            return categoryRepository.findActive();
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load categories right now. Please try again later.", exception);
        }
    }

    public List<CollectionCategory> search(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return findAll();
        }
        try {
            return categoryRepository.search(searchText.strip());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to search categories right now. Please try again later.", exception);
        }
    }

    public Map<Long, Integer> churchCountsForYear(int year) {
        try {
            return categoryRepository.countChurchesByCategory(year);
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to load category usage right now.", exception);
        }
    }

    public CollectionCategory create(String code, String name, String description,
                                     CollectionCategory.Status status, long userId) {
        CollectionCategory category = normalize(code, name, description, status);
        validate(category);
        try {
            if (categoryRepository.existsByCode(category.getCode())) {
                throw new CollectionCategoryException("A category with this code already exists.");
            }
            category.setCreatedAt(LocalDateTime.now(clock));
            CollectionCategory saved = categoryRepository.save(category);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_CREATED, saved.getCode());
            return saved;
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to save category right now. Please try again later.", exception);
        }
    }

    public CollectionCategory update(long id, String code, String name, String description,
                                     CollectionCategory.Status status, long userId) {
        CollectionCategory category = normalize(code, name, description, status);
        category.setId(id);
        validate(category);
        try {
            categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            if (categoryRepository.existsByCodeAndIdNot(category.getCode(), id)) {
                throw new CollectionCategoryException("A category with this code already exists.");
            }
            category.setUpdatedAt(LocalDateTime.now(clock));
            CollectionCategory updated = categoryRepository.update(category);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_UPDATED, updated.getCode());
            return updated;
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category right now. Please try again later.", exception);
        }
    }

    public void activate(long id, long userId) {
        updateStatus(id, CollectionCategory.Status.ACTIVE, userId, ActivityLogService.CATEGORY_ACTIVATED);
    }

    public void deactivate(long id, long userId) {
        try {
            if (categoryRepository.isInUse(id)) {
                throw new CollectionCategoryException("Category is in use and cannot be deactivated.");
            }
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category status right now. Please try again later.", exception);
        }
        updateStatus(id, CollectionCategory.Status.INACTIVE, userId, ActivityLogService.CATEGORY_DEACTIVATED);
    }

    public void delete(long id, long userId) {
        try {
            CollectionCategory category = categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            if (categoryRepository.isInUse(id)) {
                throw new CollectionCategoryException("Category is in use and cannot be deleted.");
            }
            categoryRepository.delete(id);
            activityLogService.logCategoryAction(userId, ActivityLogService.CATEGORY_DELETED, category.getCode());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to delete category right now. Please try again later.", exception);
        }
    }

    private void updateStatus(long id, CollectionCategory.Status status, long userId, String action) {
        try {
            CollectionCategory category = categoryRepository.findById(id)
                    .orElseThrow(() -> new CollectionCategoryException("Category could not be found."));
            categoryRepository.updateStatus(id, status, LocalDateTime.now(clock));
            activityLogService.logCategoryAction(userId, action, category.getCode());
        } catch (DatabaseException exception) {
            throw new CollectionCategoryException("Unable to update category status right now. Please try again later.", exception);
        }
    }

    private CollectionCategory normalize(String code, String name, String description, CollectionCategory.Status status) {
        String normalizedCode = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        String normalizedName = name == null ? "" : name.strip();
        String normalizedDescription = description == null || description.isBlank() ? null : description.strip();
        return new CollectionCategory(null, normalizedCode, normalizedName, normalizedDescription, status, null, null);
    }

    private void validate(CollectionCategory category) {
        List<String> errors = CollectionCategoryValidator.validateForCreateOrUpdate(
                category.getCode(), category.getName(), category.getDescription(), category.getStatus());
        if (!errors.isEmpty()) {
            throw new CollectionCategoryException(String.join("\n", errors));
        }
    }

    public static class CollectionCategoryException extends RuntimeException {
        public CollectionCategoryException(String message) {
            super(message);
        }

        public CollectionCategoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
