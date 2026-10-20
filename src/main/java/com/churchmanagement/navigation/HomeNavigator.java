package com.churchmanagement.navigation;

/** Lets the Home screen ask the main window to open a module. */
@FunctionalInterface
public interface HomeNavigator {
    void openModule(NavModule module);
}
