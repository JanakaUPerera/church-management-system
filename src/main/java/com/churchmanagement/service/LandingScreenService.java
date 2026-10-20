package com.churchmanagement.service;

import com.churchmanagement.config.PrimaryMachine;
import com.churchmanagement.navigation.LandingOption;
import com.churchmanagement.navigation.NavItem;
import com.churchmanagement.navigation.NavModule;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.navigation.NavigationRegistry;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and saves a user's "Open after login" screen (users.landing_screen). */
public class LandingScreenService {
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final boolean primaryMachine;

    public LandingScreenService() {
        this(new UserRepository(), new PermissionRepository(), PrimaryMachine.isPrimary());
    }

    public LandingScreenService(UserRepository userRepository, PermissionRepository permissionRepository,
                                boolean primaryMachine) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.primaryMachine = primaryMachine;
    }

    public List<LandingOption> optionsFor(AuthenticatedUser user) {
        NavigationAccess access = new NavigationAccess(user, primaryMachine);
        List<LandingOption> options = new ArrayList<>();
        options.add(LandingOption.DEFAULT);
        for (NavItem item : access.allVisibleItems()) {
            if (!NavigationRegistry.OVERVIEW_ITEM_ID.equals(item.id())) {
                String module = access.moduleOf(item).map(NavModule::title).orElse("");
                options.add(new LandingOption(item.id(), module + " › " + item.title()));
            }
        }
        return options;
    }

    public List<LandingOption> optionsForRole(long roleId, String roleName) {
        List<String> permissions = permissionRepository.findPermissionCodesByRoleId(roleId);
        return optionsFor(new AuthenticatedUser(0L, "", "", roleId, roleName, permissions));
    }

    public Optional<String> findForUser(long userId) {
        return userRepository.findLandingScreen(userId);
    }

    public void saveOwn(String itemId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new LandingScreenException("Please sign in to change your start screen."));
        String normalized = normalize(itemId);
        userRepository.updateLandingScreen(currentUser.getUserId(), normalized);
        AuthContext.setCurrentUser(currentUser.withLandingScreen(normalized));
    }

    public void saveForUser(long userId, String itemId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new LandingScreenException("Please sign in to update users."));
        if (!new PermissionGuard(currentUser).can("user.update")) {
            throw new LandingScreenException("You do not have permission to update users.");
        }
        userRepository.updateLandingScreen(userId, normalize(itemId));
    }

    private String normalize(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String stripped = itemId.strip();
        if (NavigationRegistry.findItem(stripped).isEmpty()) {
            throw new LandingScreenException("Unknown start screen.");
        }
        return stripped;
    }

    public static class LandingScreenException extends RuntimeException {
        public LandingScreenException(String message) {
            super(message);
        }
    }
}
