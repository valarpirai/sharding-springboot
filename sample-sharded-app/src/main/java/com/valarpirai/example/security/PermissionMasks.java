package com.valarpirai.example.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Utility class for working with permission bitmasks.
 * Provides predefined permission sets for default roles.
 */
public class PermissionMasks {

    // Default role permission masks
    public static final long ADMIN_PERMISSIONS;
    public static final long AGENT_PERMISSIONS;
    public static final long REQUESTER_PERMISSIONS;

    static {
        // ADMIN: All permissions
        ADMIN_PERMISSIONS = calculateMask(EnumSet.allOf(Permission.class));

        // AGENT: Support agent capabilities
        AGENT_PERMISSIONS = calculateMask(EnumSet.of(
            // Limited account access
            Permission.ACCOUNT_VIEW,

            // User viewing (not creation/editing)
            Permission.USER_VIEW_ALL,
            Permission.USER_VIEW_OWN,
            Permission.USER_EDIT_OWN,

            // Full ticket management
            Permission.TICKET_CREATE,
            Permission.TICKET_VIEW_ALL,
            Permission.TICKET_VIEW_OWN,
            Permission.TICKET_EDIT_ALL,
            Permission.TICKET_EDIT_OWN,
            Permission.TICKET_ASSIGN,
            Permission.TICKET_CHANGE_STATUS,
            Permission.TICKET_ADD_COMMENT,
            Permission.TICKET_VIEW_COMMENTS,

            // View-only role and status access
            Permission.ROLE_VIEW,
            Permission.STATUS_VIEW,

            // Basic reporting
            Permission.REPORTING_VIEW_BASIC
        ));

        // REQUESTER: Basic user capabilities
        REQUESTER_PERMISSIONS = calculateMask(EnumSet.of(
            // Basic account access
            Permission.ACCOUNT_VIEW,

            // Own user management only
            Permission.USER_VIEW_OWN,
            Permission.USER_EDIT_OWN,

            // Limited ticket access (own tickets only)
            Permission.TICKET_CREATE,
            Permission.TICKET_VIEW_OWN,
            Permission.TICKET_EDIT_OWN,
            Permission.TICKET_ADD_COMMENT,
            Permission.TICKET_VIEW_COMMENTS,

            // View-only access to statuses
            Permission.STATUS_VIEW
        ));
    }

    /**
     * Calculate bitmask for a set of permissions.
     */
    public static long calculateMask(Set<Permission> permissions) {
        long mask = 0L;
        for (Permission permission : permissions) {
            mask = permission.addTo(mask);
        }
        return mask;
    }

    /**
     * Check if a bitmask has a specific permission.
     */
    public static boolean hasPermission(long permissionsMask, Permission permission) {
        return permission.isEnabledIn(permissionsMask);
    }

    /**
     * Check if a bitmask has any of the specified permissions.
     */
    public static boolean hasAnyPermission(long permissionsMask, Permission... permissions) {
        for (Permission permission : permissions) {
            if (permission.isEnabledIn(permissionsMask)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a bitmask has all of the specified permissions.
     */
    public static boolean hasAllPermissions(long permissionsMask, Permission... permissions) {
        for (Permission permission : permissions) {
            if (!permission.isEnabledIn(permissionsMask)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get all permissions that are enabled in a bitmask.
     */
    public static Set<Permission> getEnabledPermissions(long permissionsMask) {
        Set<Permission> enabled = EnumSet.noneOf(Permission.class);
        for (Permission permission : Permission.values()) {
            if (permission.isEnabledIn(permissionsMask)) {
                enabled.add(permission);
            }
        }
        return enabled;
    }

    /**
     * Add permissions to an existing bitmask.
     */
    public static long addPermissions(long permissionsMask, Permission... permissions) {
        long result = permissionsMask;
        for (Permission permission : permissions) {
            result = permission.addTo(result);
        }
        return result;
    }

    /**
     * Remove permissions from an existing bitmask.
     */
    public static long removePermissions(long permissionsMask, Permission... permissions) {
        long result = permissionsMask;
        for (Permission permission : permissions) {
            result = permission.removeFrom(result);
        }
        return result;
    }

    /**
     * Get permission mask for a default system role.
     */
    public static long getDefaultRoleMask(String roleName) {
        switch (roleName.toUpperCase()) {
            case "ADMIN":
                return ADMIN_PERMISSIONS;
            case "AGENT":
                return AGENT_PERMISSIONS;
            case "REQUESTER":
                return REQUESTER_PERMISSIONS;
            default:
                return 0L; // No permissions for unknown roles
        }
    }
}