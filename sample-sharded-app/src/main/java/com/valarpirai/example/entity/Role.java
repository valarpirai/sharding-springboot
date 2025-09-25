package com.valarpirai.example.entity;

import com.valarpirai.example.security.Permission;
import com.valarpirai.sharding.annotation.ShardedEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Role entity - sharded by account_id.
 * Represents user roles with permission bitmasks.
 */
@Entity
@Table(name = "roles")
@ShardedEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId; // Tenant isolation field

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "permissions_mask", nullable = false)
    private Long permissionsMask = 0L;

    @Column(name = "is_system_role", nullable = false)
    private Boolean isSystemRole = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if role is active (not deleted).
     */
    public boolean isActive() {
        return !deleted;
    }

    /**
     * Soft delete the role.
     */
    public void delete() {
        this.deleted = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this role has a specific permission.
     */
    public boolean hasPermission(Permission permission) {
        return permission.isEnabledIn(this.permissionsMask);
    }

    /**
     * Add a permission to this role.
     */
    public void addPermission(Permission permission) {
        this.permissionsMask = permission.addTo(this.permissionsMask);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Remove a permission from this role.
     */
    public void removePermission(Permission permission) {
        this.permissionsMask = permission.removeFrom(this.permissionsMask);
        this.updatedAt = LocalDateTime.now();
    }
}