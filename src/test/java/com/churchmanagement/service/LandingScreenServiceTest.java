package com.churchmanagement.service;

import com.churchmanagement.navigation.LandingOption;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LandingScreenServiceTest {
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final LandingScreenService service = new LandingScreenService(userRepository,
            new FakePermissionRepository(), true);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void optionsStartWithDefaultThenVisibleScreens() {
        List<LandingOption> options = service.optionsFor(clerk());

        assertEquals(LandingOption.DEFAULT, options.getFirst());
        assertEquals(List.of("Dashboard (default)", "Collections › Receipt"),
                options.stream().map(LandingOption::label).toList());
    }

    @Test
    void optionsForRoleUseThatRolesPermissions() {
        List<LandingOption> options = service.optionsForRole(2L, "Clerk");

        assertEquals(Arrays.asList(null, "collections.receipt"),
                options.stream().map(LandingOption::itemId).toList());
    }

    @Test
    void saveOwnStoresAndUpdatesSession() {
        AuthContext.setCurrentUser(clerk());

        service.saveOwn("collections.receipt");

        assertEquals(5L, userRepository.savedUserId);
        assertEquals("collections.receipt", userRepository.savedItemId);
        assertEquals("collections.receipt", AuthContext.getCurrentUser().orElseThrow().getLandingScreen());
    }

    @Test
    void saveOwnBlankMeansDefault() {
        AuthContext.setCurrentUser(clerk());

        service.saveOwn("  ");

        assertNull(userRepository.savedItemId);
        assertEquals(5L, userRepository.savedUserId);
    }

    @Test
    void rejectsUnknownScreen() {
        AuthContext.setCurrentUser(clerk());

        assertThrows(LandingScreenService.LandingScreenException.class, () -> service.saveOwn("no.such"));
    }

    @Test
    void saveForUserNeedsUserUpdatePermission() {
        AuthContext.setCurrentUser(clerk());

        LandingScreenService.LandingScreenException exception = assertThrows(
                LandingScreenService.LandingScreenException.class,
                () -> service.saveForUser(9L, "collections.receipt"));

        assertEquals("You do not have permission to update users.", exception.getMessage());
    }

    @Test
    void saveForUserWithPermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(1L, "manager", "Manager", 3L, "Manager",
                List.of("user.update")));

        service.saveForUser(9L, "collections.receipt");

        assertEquals(9L, userRepository.savedUserId);
        assertEquals("collections.receipt", userRepository.savedItemId);
    }

    @Test
    void findForUserReadsRepository() {
        userRepository.stored = "branches.churches";

        assertEquals(Optional.of("branches.churches"), service.findForUser(9L));
    }

    private static AuthenticatedUser clerk() {
        return new AuthenticatedUser(5L, "clerk", "Clerk", 2L, "Clerk", List.of("receipt.entry.menu.view"));
    }

    private static class FakeUserRepository extends UserRepository {
        private Long savedUserId;
        private String savedItemId = "unset";
        private String stored;

        private FakeUserRepository() {
            super((DataSource) null);
        }

        @Override
        public void updateLandingScreen(long userId, String itemId) {
            savedUserId = userId;
            savedItemId = itemId;
        }

        @Override
        public Optional<String> findLandingScreen(long userId) {
            return Optional.ofNullable(stored);
        }
    }

    private static class FakePermissionRepository extends PermissionRepository {
        private FakePermissionRepository() {
            super((DataSource) null);
        }

        @Override
        public List<String> findPermissionCodesByRoleId(long roleId) {
            return List.of("receipt.entry.menu.view");
        }
    }
}
