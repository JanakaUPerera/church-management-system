package com.churchmanagement.service;

import com.churchmanagement.dto.RolePermissionUpdateRequest;
import com.churchmanagement.entity.Permission;
import com.churchmanagement.entity.Role;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.RoleRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionServiceTest {
    private FakeRoleRepository roleRepository;
    private FakePermissionRepository permissionRepository;
    private FakeActivityLogService activityLogService;
    private RolePermissionService service;

    @BeforeEach
    void setUp() {
        roleRepository = new FakeRoleRepository();
        permissionRepository = new FakePermissionRepository();
        roleRepository.permissionRepository = permissionRepository;
        activityLogService = new FakeActivityLogService();
        service = new RolePermissionService(roleRepository, permissionRepository, activityLogService,
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(1L, "admin", "System Administrator", 1L, "Admin",
                List.of("role.menu.view", "role.create", "role.update", "role.delete", "user.update")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createRole() {
        Role role = service.createRole("Treasurer");

        assertEquals("Treasurer", role.getRoleName());
        assertEquals(Role.Status.ACTIVE, role.getStatus());
        assertEquals(ActivityLogService.ROLE_CREATED, activityLogService.loggedActions.getLast());
    }

    @Test
    void rejectDuplicateRole() {
        RolePermissionService.RolePermissionException exception = assertThrows(
                RolePermissionService.RolePermissionException.class,
                () -> service.createRole("Admin"));

        assertEquals("A role with this name already exists.", exception.getMessage());
    }

    @Test
    void updateRole() {
        Role role = service.updateRoleName(2L, "Receipt Clerk");

        assertEquals("Receipt Clerk", role.getRoleName());
        assertEquals(ActivityLogService.ROLE_UPDATED, activityLogService.loggedActions.getLast());
    }

    @Test
    void deactivateRole() {
        service.deactivateRole(2L);

        assertEquals(Role.Status.INACTIVE, roleRepository.roles.get(2L).getStatus());
        assertEquals(ActivityLogService.ROLE_DEACTIVATED, activityLogService.loggedActions.getLast());
    }

    @Test
    void blockAdminRoleDeactivation() {
        RolePermissionService.RolePermissionException exception = assertThrows(
                RolePermissionService.RolePermissionException.class,
                () -> service.deactivateRole(1L));

        assertEquals("Admin role cannot be deactivated.", exception.getMessage());
    }

    @Test
    void assignPermissions() {
        service.updateRolePermissions(new RolePermissionUpdateRequest(2L, List.of("receipt.create", "report.view")));

        assertTrue(permissionRepository.rolePermissions.get(2L).contains("receipt.create"));
        assertTrue(permissionRepository.rolePermissions.get(2L).contains("report.view"));
        assertEquals(ActivityLogService.ROLE_PERMISSIONS_UPDATED, activityLogService.loggedActions.getLast());
    }

    @Test
    void removePermissions() {
        permissionRepository.rolePermissions.put(2L, new LinkedHashSet<>(List.of("receipt.create", "report.view")));

        service.updateRolePermissions(new RolePermissionUpdateRequest(2L, List.of("receipt.create")));

        assertTrue(permissionRepository.rolePermissions.get(2L).contains("receipt.create"));
        assertFalse(permissionRepository.rolePermissions.get(2L).contains("report.view"));
    }

    @Test
    void blockRemovingLastRoleUpdateAccess() {
        AuthContext.setCurrentUser(new AuthenticatedUser(2L, "manager", "Manager", 2L, "User",
                List.of("role.update")));
        permissionRepository.rolePermissions.put(1L, new LinkedHashSet<>(List.of("user.update")));
        permissionRepository.rolePermissions.put(2L, new LinkedHashSet<>(List.of("role.update")));

        RolePermissionService.RolePermissionException exception = assertThrows(
                RolePermissionService.RolePermissionException.class,
                () -> service.updateRolePermissions(new RolePermissionUpdateRequest(2L, List.of("receipt.create"))));

        assertEquals("At least one active role must keep role.update permission.", exception.getMessage());
    }

    @Test
    void blockRemovingCriticalAdminPermission() {
        RolePermissionService.RolePermissionException exception = assertThrows(
                RolePermissionService.RolePermissionException.class,
                () -> service.updateRolePermissions(new RolePermissionUpdateRequest(1L, List.of("user.update"))));

        assertEquals("Critical permissions cannot be removed from the Admin role.", exception.getMessage());
    }

    @Test
    void userWithoutRoleMenuViewRejected() {
        AuthContext.setCurrentUser(new AuthenticatedUser(2L, "user", "Standard User", 2L, "User",
                List.of("receipt.create")));

        RolePermissionService.RolePermissionException exception = assertThrows(
                RolePermissionService.RolePermissionException.class,
                () -> service.findRoles());

        assertEquals("You do not have permission to perform this action.", exception.getMessage());
    }

    private static class FakeRoleRepository extends RoleRepository {
        private final Map<Long, Role> roles = new LinkedHashMap<>();
        private long nextId = 3L;
        private FakePermissionRepository permissionRepository;

        private FakeRoleRepository() {
            super((DataSource) null);
            roles.put(1L, new Role(1L, "Admin", Role.Status.ACTIVE, LocalDateTime.now(), null));
            roles.put(2L, new Role(2L, "User", Role.Status.ACTIVE, LocalDateTime.now(), null));
        }

        @Override
        public List<Role> findAll() {
            return new ArrayList<>(roles.values());
        }

        @Override
        public Optional<Role> findById(long id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public boolean existsByName(String roleName) {
            return roles.values().stream().anyMatch(role -> role.getRoleName().equalsIgnoreCase(roleName));
        }

        @Override
        public boolean existsByNameAndIdNot(String roleName, long id) {
            return roles.values().stream()
                    .anyMatch(role -> !role.getId().equals(id) && role.getRoleName().equalsIgnoreCase(roleName));
        }

        @Override
        public Role save(Role role) {
            role.setId(nextId++);
            roles.put(role.getId(), role);
            return role;
        }

        @Override
        public Role updateName(long id, String roleName, LocalDateTime updatedAt) {
            Role role = roles.get(id);
            role.setRoleName(roleName);
            role.setUpdatedAt(updatedAt);
            return role;
        }

        @Override
        public void updateStatus(long id, Role.Status status, LocalDateTime updatedAt) {
            Role role = roles.get(id);
            role.setStatus(status);
            role.setUpdatedAt(updatedAt);
        }

        @Override
        public int countActiveRolesWithPermission(String permissionCode) {
            return countActiveRolesWithPermissionExcluding(-1L, permissionCode);
        }

        @Override
        public int countActiveRolesWithPermissionExcluding(long excludedRoleId, String permissionCode) {
            int count = 0;
            for (Role role : roles.values()) {
                if (role.getId() == excludedRoleId || role.getStatus() != Role.Status.ACTIVE) {
                    continue;
                }
                if (permissionRepository.rolePermissions.getOrDefault(role.getId(), Set.of()).contains(permissionCode)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static class FakePermissionRepository extends PermissionRepository {
        private final List<Permission> permissions = List.of(
                new Permission(1L, "role.update", "Roles & Permissions", "Update roles and manage role permissions", LocalDateTime.now()),
                new Permission(2L, "user.update", "User Management", "Update users and reset passwords", LocalDateTime.now()),
                new Permission(3L, "receipt.create", "Receipts", "Create receipts", LocalDateTime.now()),
                new Permission(4L, "report.view", "Reports", "View reports", LocalDateTime.now())
        );
        private final Map<Long, Set<String>> rolePermissions = new LinkedHashMap<>();

        private FakePermissionRepository() {
            super((DataSource) null);
            rolePermissions.put(1L, new LinkedHashSet<>(List.of("role.update", "user.update")));
            rolePermissions.put(2L, new LinkedHashSet<>(List.of("receipt.create")));
        }

        @Override
        public List<Permission> findAll() {
            return permissions;
        }

        @Override
        public Set<String> findPermissionCodeSetByRoleId(long roleId) {
            return new LinkedHashSet<>(rolePermissions.getOrDefault(roleId, Set.of()));
        }

        @Override
        public boolean allPermissionCodesExist(Collection<String> permissionCodes) {
            Set<String> knownCodes = new LinkedHashSet<>();
            for (Permission permission : permissions) {
                knownCodes.add(permission.getPermissionCode());
            }
            return knownCodes.containsAll(permissionCodes);
        }

        @Override
        public void replaceRolePermissions(long roleId, Collection<String> permissionCodes) {
            rolePermissions.put(roleId, new LinkedHashSet<>(permissionCodes));
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> loggedActions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logRoleAction(long userId, String action, String roleName) {
            loggedActions.add(action);
        }
    }
}
