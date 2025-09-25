package com.valarpirai.example.security;

/**
 * System permissions enum with bitmask positions.
 * Each permission corresponds to a bit position in the permissions_mask field.
 */
public enum Permission {

    // Account Management Permissions (bits 0-7)
    ACCOUNT_VIEW(0, "View account information and settings"),
    ACCOUNT_EDIT(1, "Edit account settings and configuration"),

    // User Management Permissions (bits 8-15)
    USER_CREATE(8, "Create new users within the account"),
    USER_VIEW_ALL(9, "View all users in the account"),
    USER_VIEW_OWN(10, "View own user profile and information"),
    USER_EDIT_ALL(11, "Edit any user in the account"),
    USER_EDIT_OWN(12, "Edit own user profile and information"),
    USER_DELETE(13, "Delete users from the account"),
    USER_ASSIGN_ROLE(14, "Assign roles to users"),

    // Ticket Management Permissions (bits 16-31)
    TICKET_CREATE(16, "Create new tickets"),
    TICKET_VIEW_ALL(17, "View all tickets in the account"),
    TICKET_VIEW_OWN(18, "View tickets created by or assigned to the user"),
    TICKET_EDIT_ALL(19, "Edit any ticket in the account"),
    TICKET_EDIT_OWN(20, "Edit tickets created by or assigned to the user"),
    TICKET_DELETE(21, "Delete tickets (soft delete)"),
    TICKET_ASSIGN(22, "Assign tickets to users"),
    TICKET_CHANGE_STATUS(23, "Change ticket status"),
    TICKET_ADD_COMMENT(24, "Add comments to tickets"),
    TICKET_VIEW_COMMENTS(25, "View ticket comments"),

    // Role Management Permissions (bits 32-39)
    ROLE_CREATE(32, "Create new custom roles"),
    ROLE_VIEW(33, "View roles and their permissions"),
    ROLE_EDIT(34, "Edit custom roles and their permissions"),
    ROLE_DELETE(35, "Delete custom roles"),

    // Status Management Permissions (bits 40-47)
    STATUS_CREATE(40, "Create new ticket statuses"),
    STATUS_VIEW(41, "View available ticket statuses"),
    STATUS_EDIT(42, "Edit ticket statuses and their properties"),
    STATUS_DELETE(43, "Delete ticket statuses"),
    STATUS_REORDER(44, "Change the order/position of statuses"),

    // Reporting and Analytics Permissions (bits 48-55)
    REPORTING_VIEW_BASIC(48, "View basic reports and statistics"),
    REPORTING_VIEW_ADVANCED(49, "View advanced reports and analytics"),
    REPORTING_EXPORT(50, "Export reports and data");

    private final int bitPosition;
    private final String description;
    private final long mask;

    Permission(int bitPosition, String description) {
        this.bitPosition = bitPosition;
        this.description = description;
        this.mask = 1L << bitPosition;
    }

    public int getBitPosition() {
        return bitPosition;
    }

    public String getDescription() {
        return description;
    }

    public long getMask() {
        return mask;
    }

    /**
     * Check if a permission bitmask has this permission enabled.
     */
    public boolean isEnabledIn(long permissionsMask) {
        return (permissionsMask & mask) != 0;
    }

    /**
     * Add this permission to a bitmask.
     */
    public long addTo(long permissionsMask) {
        return permissionsMask | mask;
    }

    /**
     * Remove this permission from a bitmask.
     */
    public long removeFrom(long permissionsMask) {
        return permissionsMask & ~mask;
    }
}