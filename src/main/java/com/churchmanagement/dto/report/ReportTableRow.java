package com.churchmanagement.dto.report;

import java.util.LinkedHashMap;
import java.util.Map;

public interface ReportTableRow {
    LinkedHashMap<String, Object> columns();

    default String searchText() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : columns().entrySet()) {
            if (entry.getValue() != null) {
                builder.append(entry.getValue()).append(' ');
            }
        }
        return builder.toString().toLowerCase();
    }

    default Long detailId() {
        return null;
    }
}
