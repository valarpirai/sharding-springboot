package com.valarpirai.example.entity;

import com.valarpirai.sharding.annotation.ShardedEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Ticket entity - sharded by account_id.
 * Main business entity representing support tickets.
 */
@Entity
@Table(name = "tickets")
@ShardedEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId; // Tenant isolation field

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId; // Reference to User.id (no FK constraint)

    @Column(name = "responder_id")
    private Long responderId; // Reference to User.id (no FK constraint), can be null

    @Column(name = "status_id", nullable = false)
    private Long statusId; // Reference to Status.id (no FK constraint)

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "category")
    private String category;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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
     * Check if ticket is active (not deleted).
     */
    public boolean isActive() {
        return !deleted;
    }

    /**
     * Soft delete the ticket.
     */
    public void delete() {
        this.deleted = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if ticket is assigned to a responder.
     */
    public boolean isAssigned() {
        return responderId != null;
    }

    /**
     * Check if ticket is resolved (has resolved_at timestamp).
     */
    public boolean isResolved() {
        return resolvedAt != null;
    }

    /**
     * Assign ticket to a responder.
     */
    public void assignTo(Long userId) {
        this.responderId = userId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Unassign ticket (remove responder).
     */
    public void unassign() {
        this.responderId = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Change ticket status.
     */
    public void changeStatus(Long newStatusId) {
        this.statusId = newStatusId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark ticket as resolved.
     */
    public void markResolved() {
        LocalDateTime now = LocalDateTime.now();
        this.resolvedAt = now;
        this.updatedAt = now;
    }

    /**
     * Mark ticket as unresolved.
     */
    public void markUnresolved() {
        this.resolvedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update ticket priority.
     */
    public void updatePriority(Priority newPriority) {
        this.priority = newPriority;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get priority level as integer for sorting (lower number = higher priority).
     */
    public int getPriorityLevel() {
        switch (priority) {
            case URGENT: return 1;
            case HIGH: return 2;
            case MEDIUM: return 3;
            case LOW: return 4;
            default: return 5;
        }
    }
}