package com.churchmanagement.service;

import com.churchmanagement.dto.PermissionDto;
import com.churchmanagement.dto.RolePermissionUpdateRequest;
import com.churchmanagement.entity.Role;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.RoleRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.validation.RoleValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RolePermissionService {
    public static final String ROLE_MENU_VIEW_PERMISSION = "role.menu.view";
    public static final String ROLE_CREATE_PERMISSION = "role.create";
    public static final String ROLE_UPDATE_PERMISSION = "role.update";
    public static final String ROLE_DELETE_PERMISSION = "role.delete";
    private static final String ADMIN_ROLE_NAME = "Admin";
    private static final Set<String> ADMIN_CRITICAL_PERMISSIONS = Set.of("role.update", "user.update");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public RolePermissionService() {
        this(new RoleRepository(), new PermissionRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public RolePermissionService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                                 ActivityLogService activityLogService, Clock clock) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<Role> findRoles() {
        requirePermission(ROLE_MENU_VIEW_PERMISSION);
        try {
            return roleRepository.findAll();
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to load roles right now. Please try again later.", exception);
        }
    }

    public List<PermissionDto> findPermissions() {
        requirePermission(ROLE_MENU_VIEW_PERMISSION);
        try {
            return permissionRepository.findAll().stream().map(PermissionDto::fromPermission).toList();
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to load permissions right now. Please try again later.",
                    exception);
        }
    }

    public Set<String> findPermissionCodesByRoleId(long roleId) {
        requirePermission(ROLE_MENU_VIEW_PERMISSION);
        try {
            ensureRoleExists(roleId);
            return permissionRepository.findPermissionCodeSetByRoleId(roleId);
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to load role permissions right now. Please try again later.",
                    exception);
        }
    }

    public Role createRole(String roleName) {
        AuthenticatedUser currentUser = requirePermission(ROLE_CREATE_PERMISSION);
        String normalizedName = normalizeRoleName(roleName);
        validateRoleName(normalizedName);

        try {
            if (roleRepository.existsByName(normalizedName)) {
                throw new RolePermissionException("A role with this name already exists.");
            }

            Role role = new Role();
            role.setRoleName(normalizedName);
            role.setStatus(Role.Status.ACTIVE);
            role.setCreatedAt(LocalDateTime.now(clock));

            Role savedRole = roleRepository.save(role);
            activityLogService.logRoleAction(currentUser.getUserId(), ActivityLogService.ROLE_CREATED,
                    savedRole.getRoleName());
            return savedRole;
        } catch (RolePermissionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to save role right now. Please try again later.", exception);
        }
    }

    public Role updateRoleName(long roleId, String roleName) {
        AuthenticatedUser currentUser = requirePermission(ROLE_UPDATE_PERMISSION);
        String normalizedName = normalizeRoleName(roleName);
        validateRoleName(normalizedName);

        try {
            ensureRoleExists(roleId);
            if (roleRepository.existsByNameAndIdNot(normalizedName, roleId)) {
                throw new RolePermissionException("A role with this name already exists.");
            }

            Role updatedRole = roleRepository.updateName(roleId, normalizedName, LocalDateTime.now(clock));
            activityLogService.logRoleAction(currentUser.getUserId(), ActivityLogService.ROLE_UPDATED,
                    updatedRole.getRoleName());
            return updatedRole;
        } catch (RolePermissionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to update role right now. Please try again later.", exception);
        }
    }

    public void activateRole(long roleId) {
        updateStatus(roleId, Role.Status.ACTIVE, ActivityLogService.ROLE_ACTIVATED);
    }

    public void deactivateRole(long roleId) {
        updateStatus(roleId, Role.Status.INACTIVE, ActivityLogService.ROLE_DEACTIVATED);
    }

    public void updateRolePermissions(RolePermissionUpdateRequest request) {
        AuthenticatedUser currentUser = requirePermission(ROLE_UPDATE_PERMISSION);
        if (request == null || request.getRoleId() == null) {
            throw new RolePermissionException("Select a role before saving permissions.");
        }

        long roleId = request.getRoleId();
        Set<String> selectedPermissions = normalizePermissionCodes(request.getPermissionCodes());

        try {
            Role role = ensureRoleExists(roleId);
            if (!permissionRepository.allPermissionCodesExist(selectedPermissions)) {
                throw new RolePermissionException("One or more selected permissions are not recognized by the system.");
            }
            validatePermissionUpdate(role, selectedPermissions);

            permissionRepository.replaceRolePermissions(roleId, selectedPermissions);
            activityLogService.logRoleAction(currentUser.getUserId(),
                    ActivityLogService.ROLE_PERMISSIONS_UPDATED, role.getRoleName());
        } catch (RolePermissionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to update role permissions right now. Please try again later.",
                    exception);
        }
    }

    private void updateStatus(long roleId, Role.Status status, String action) {
        AuthenticatedUser currentUser = requirePermission(ROLE_DELETE_PERMISSION);
        try {
            Role role = ensureRoleExists(roleId);
            if (isAdminRole(role) && status == Role.Status.INACTIVE) {
                throw new RolePermissionException("Admin role cannot be deactivated.");
            }
            if (status == Role.Status.INACTIVE
                    && permissionRepository.findPermissionCodeSetByRoleId(roleId).contains(ROLE_UPDATE_PERMISSION)
                    && roleRepository.countActiveRolesWithPermission(ROLE_UPDATE_PERMISSION) <= 1) {
                throw new RolePermissionException("At least one active role must keep role.update permission.");
            }

            roleRepository.updateStatus(roleId, status, LocalDateTime.now(clock));
            activityLogService.logRoleAction(currentUser.getUserId(), action, role.getRoleName());
        } catch (RolePermissionException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new RolePermissionException("Unable to update role status right now. Please try again later.",
                    exception);
        }
    }

    private void validatePermissionUpdate(Role role, Set<String> selectedPermissions) {
        if (isAdminRole(role) && !selectedPermissions.containsAll(ADMIN_CRITICAL_PERMISSIONS)) {
            throw new RolePermissionException("Critical permissions cannot be removed from the Admin role.");
        }

        boolean removesRoleUpdate = !selectedPermissions.contains(ROLE_UPDATE_PERMISSION);
        if (role.getStatus() == Role.Status.ACTIVE && removesRoleUpdate
                && roleRepository.countActiveRolesWithPermissionExcluding(role.getId(), ROLE_UPDATE_PERMISSION) == 0) {
            throw new RolePermissionException("At least one active role must keep role.update permission.");
        }
    }

    private AuthenticatedUser requirePermission(String permissionCode) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new RolePermissionException("Please sign in to manage roles and permissions."));
        try {
            new PermissionGuard(currentUser).require(permissionCode);
            return currentUser;
        } catch (SecurityException exception) {
            throw new RolePermissionException("You do not have permission to perform this action.", exception);
        }
    }

    private Role ensureRoleExists(long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new RolePermissionException("Role could not be found."));
    }

    private void validateRoleName(String roleName) {
        List<String> errors = RoleValidator.validateName(roleName);
        if (!errors.isEmpty()) {
            throw new RolePermissionException(String.join("\n", errors));
        }
    }

    private String normalizeRoleName(String roleName) {
        return roleName == null ? "" : roleName.strip();
    }

    private Set<String> normalizePermissionCodes(List<String> permissionCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (permissionCodes == null) {
            return normalized;
        }
        for (String permissionCode : permissionCodes) {
            if (permissionCode != null && !permissionCode.isBlank()) {
                normalized.add(permissionCode.strip());
            }
        }
        return normalized;
    }

    private boolean isAdminRole(Role role) {
        return role != null && ADMIN_ROLE_NAME.equalsIgnoreCase(role.getRoleName());
    }

    public static class RolePermissionException extends RuntimeException {
        public RolePermissionException(String message) {
            super(message);
        }

        public RolePermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
