package com.churchmanagement.validation;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.util.WeekUtil;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ReceiptValidator {
    private ReceiptValidator() {
    }

    public static List<String> validateForCreate(CreateReceiptRequest request, LocalDate today, boolean lateSubmission,
                                                 boolean lateSubmissionReasonRequired, DayOfWeek identifierDay) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Church is required.");
            errors.add("Week end date is required.");
            errors.add("Date of the church service is required.");
            errors.add("Submitted by name is required.");
            errors.add("At least one collection item is required.");
            return errors;
        }

        validateHeader(request, today, lateSubmission, lateSubmissionReasonRequired, identifierDay, errors);
        validateItems(request.getItems(), errors);
        return errors;
    }

    private static void validateHeader(CreateReceiptRequest request, LocalDate today, boolean lateSubmission,
                                       boolean lateSubmissionReasonRequired, DayOfWeek identifierDay,
                                       List<String> errors) {
        if (request.getChurchId() == null) {
            errors.add("Church is required.");
        }

        LocalDate weekStart = request.getWeekStartDate();
        LocalDate weekEnd = request.getWeekEndDate();
        if (weekEnd == null) {
            errors.add("Week end date is required.");
        } else {
            if (!WeekUtil.isIdentifierDay(weekEnd, identifierDay)) {
                errors.add("Week end date must be a " + WeekUtil.displayName(identifierDay) + ".");
            }
            if (weekEnd.isAfter(WeekUtil.currentIdentifier(today, identifierDay))) {
                errors.add("Future weeks are not allowed.");
            }
        }

        if (weekStart == null || !WeekUtil.isWeekStartDay(weekStart, identifierDay)) {
            errors.add("Week start date must be a " + WeekUtil.displayName(identifierDay.plus(1)) + ".");
        }

        if (weekStart != null && weekEnd != null && !weekEnd.equals(weekStart.plusDays(6))) {
            errors.add("Week end date must be 6 days after week start date.");
        }

        LocalDate churchServiceDate = request.getChurchServiceDate();
        if (churchServiceDate == null) {
            errors.add("Date of the church service is required.");
        } else if (churchServiceDate.isAfter(today)) {
            errors.add("Date of the church service cannot be in the future.");
        } else if (weekStart != null && weekEnd != null
                && (churchServiceDate.isBefore(weekStart) || churchServiceDate.isAfter(weekEnd))) {
            errors.add("Date of the church service must be within the selected week.");
        }

        if (request.getSubmittedByName() == null || request.getSubmittedByName().isBlank()) {
            errors.add("Submitted by name is required.");
        }

        if (lateSubmission && lateSubmissionReasonRequired
                && (request.getLateSubmissionReason() == null || request.getLateSubmissionReason().isBlank())) {
            errors.add("Late submission reason is required for back week receipts.");
        }
    }

    private static void validateItems(List<ReceiptItemDto> items, List<String> errors) {
        if (items == null || items.isEmpty()) {
            errors.add("At least one collection item is required.");
            return;
        }

        Set<CollectionType> seenTypes = EnumSet.noneOf(CollectionType.class);
        for (ReceiptItemDto item : items) {
            if (item == null || item.getCollectionType() == null || !seenTypes.add(item.getCollectionType())) {
                errors.add("Duplicate collection type is not allowed.");
            }

            if (item == null || item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be greater than zero.");
            } else if (item.getAmount().stripTrailingZeros().scale() > 2) {
                errors.add("Amount must have a maximum of two decimal places.");
            }
        }
    }
}
