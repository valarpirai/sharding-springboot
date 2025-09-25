package com.valarpirai.example.entity;

import com.valarpirai.sharding.annotation.ShardedEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Status entity - sharded by account_id.
 * Represents ticket status definitions for each tenant.
 */
@Entity
@Table(name = "status")
@ShardedEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId; // Tenant isolation field

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "color", length = 7)
    private String color; // Hex color code like #28a745

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @Column(name = "position", nullable = false)
    private Integer position = 0;

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
     * Check if status is active (not deleted).
     */
    public boolean isActive() {
        return !deleted;
    }

    /**
     * Soft delete the status.
     */
    public void delete() {
        this.deleted = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this status represents a closed/resolved state.
     */
    public boolean isClosedStatus() {
        return isClosed;
    }

    /**
     * Check if this is the default status for new tickets.
     */
    public boolean isDefaultStatus() {
        return isDefault;
    }

    /**
     * Set this status as the default (and unset others - handled by service layer).
     */
    public void setAsDefault() {
        this.isDefault = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Unset this status as default.
     */
    public void unsetAsDefault() {
        this.isDefault = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update the position of this status.
     */
    public void updatePosition(Integer newPosition) {
        this.position = newPosition;
        this.updatedAt = LocalDateTime.now();
    }
}