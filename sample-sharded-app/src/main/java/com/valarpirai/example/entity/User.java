package com.valarpirai.example.entity;

import com.valarpirai.sharding.annotation.ShardedEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * User entity - sharded by account_id.
 * Represents users within a tenant account.
 */
@Entity
@Table(name = "users")
@ShardedEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId; // Tenant isolation field

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "role_id", nullable = false)
    private Long roleId; // Reference to Role.id (no FK constraint)

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

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
     * Get full name of the user.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Check if user is active and not deleted.
     */
    public boolean isActive() {
        return active && !deleted;
    }

    /**
     * Soft delete the user.
     */
    public void delete() {
        this.deleted = true;
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Deactivate the user (without deleting).
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Activate the user.
     */
    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update last login timestamp.
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}