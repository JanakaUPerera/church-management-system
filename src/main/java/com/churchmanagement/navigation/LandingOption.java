package com.churchmanagement.navigation;

/** One "Open after login" choice. A null {@code itemId} means the default (Dashboard > Overview). */
public record LandingOption(String itemId, String label) {
    public static final LandingOption DEFAULT = new LandingOption(null, "Dashboard (default)");

    @Override
    public String toString() {
        return label;
    }
}
