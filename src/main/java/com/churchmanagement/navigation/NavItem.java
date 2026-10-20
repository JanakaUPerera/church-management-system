package com.churchmanagement.navigation;

import com.churchmanagement.dto.report.ReportGroup;

import java.util.List;

/**
 * One screen in the navigation registry. {@code id} is stored in users.landing_screen, so it must
 * never be renamed. {@code section} groups items in the sidebar of large modules (may be null).
 */
public record NavItem(String id, String title, String description, String viewPath, String icon,
                      String activityAction, String section, ReportGroup reportGroup,
                      List<String> permissionCodes) {
    public NavItem {
        permissionCodes = List.copyOf(permissionCodes);
    }

    public String[] permissionCodeArray() {
        return permissionCodes.toArray(String[]::new);
    }
}
