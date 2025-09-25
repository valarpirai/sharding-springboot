package com.valarpirai.example.repository;

import com.valarpirai.example.entity.Ticket;
import com.valarpirai.example.entity.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Ticket entities.
 * This repository operates on sharded databases based on tenant context.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Find all active tickets within the current tenant.
     */
    Page<Ticket> findByAccountIdAndDeletedFalseOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    /**
     * Find all active tickets within the current tenant (without pagination).
     */
    List<Ticket> findByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Find tickets by requester within the current tenant.
     */
    Page<Ticket> findByAccountIdAndRequesterIdAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Long requesterId, Pageable pageable);

    /**
     * Find tickets by responder within the current tenant.
     */
    Page<Ticket> findByAccountIdAndResponderIdAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Long responderId, Pageable pageable);

    /**
     * Find unassigned tickets within the current tenant.
     */
    Page<Ticket> findByAccountIdAndResponderIdIsNullAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Pageable pageable);

    /**
     * Find tickets by status within the current tenant.
     */
    Page<Ticket> findByAccountIdAndStatusIdAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Long statusId, Pageable pageable);

    /**
     * Find tickets by priority within the current tenant.
     */
    Page<Ticket> findByAccountIdAndPriorityAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Priority priority, Pageable pageable);

    /**
     * Find ticket by ID within the current tenant.
     */
    Optional<Ticket> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * Count active tickets in the current tenant.
     */
    long countByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Count tickets by status in the current tenant.
     */
    long countByAccountIdAndStatusIdAndDeletedFalse(Long accountId, Long statusId);

    /**
     * Count tickets by requester in the current tenant.
     */
    long countByAccountIdAndRequesterIdAndDeletedFalse(Long accountId, Long requesterId);

    /**
     * Count tickets by responder in the current tenant.
     */
    long countByAccountIdAndResponderIdAndDeletedFalse(Long accountId, Long responderId);

    /**
     * Count unassigned tickets in the current tenant.
     */
    long countByAccountIdAndResponderIdIsNullAndDeletedFalse(Long accountId);

    /**
     * Search tickets by subject or description within the current tenant.
     */
    @Query("SELECT t FROM Ticket t WHERE t.accountId = :accountId AND t.deleted = false " +
           "AND (LOWER(t.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY t.createdAt DESC")
    Page<Ticket> searchBySubjectOrDescription(@Param("accountId") Long accountId,
                                            @Param("searchTerm") String searchTerm,
                                            Pageable pageable);

    /**
     * Find tickets by category within the current tenant.
     */
    Page<Ticket> findByAccountIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, String category, Pageable pageable);

    /**
     * Find tickets by category and subcategory within the current tenant.
     */
    Page<Ticket> findByAccountIdAndCategoryAndSubcategoryAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, String category, String subcategory, Pageable pageable);

    /**
     * Find resolved tickets (has resolved_at) within the current tenant.
     */
    Page<Ticket> findByAccountIdAndResolvedAtIsNotNullAndDeletedFalseOrderByResolvedAtDesc(
        Long accountId, Pageable pageable);

    /**
     * Find unresolved tickets (no resolved_at) within the current tenant.
     */
    Page<Ticket> findByAccountIdAndResolvedAtIsNullAndDeletedFalseOrderByCreatedAtDesc(
        Long accountId, Pageable pageable);

    /**
     * Get ticket statistics by priority.
     */
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t " +
           "WHERE t.accountId = :accountId AND t.deleted = false " +
           "GROUP BY t.priority")
    List<Object[]> getTicketCountByPriority(@Param("accountId") Long accountId);

    /**
     * Get ticket statistics by status.
     */
    @Query("SELECT t.statusId, COUNT(t) FROM Ticket t " +
           "WHERE t.accountId = :accountId AND t.deleted = false " +
           "GROUP BY t.statusId")
    List<Object[]> getTicketCountByStatus(@Param("accountId") Long accountId);

    /**
     * Find tickets created by or assigned to a specific user.
     */
    @Query("SELECT t FROM Ticket t WHERE t.accountId = :accountId AND t.deleted = false " +
           "AND (t.requesterId = :userId OR t.responderId = :userId) " +
           "ORDER BY t.createdAt DESC")
    Page<Ticket> findByUserInvolvement(@Param("accountId") Long accountId,
                                     @Param("userId") Long userId,
                                     Pageable pageable);

    /**
     * Find filtered tickets with optional parameters.
     */
    @Query("SELECT t FROM Ticket t WHERE t.accountId = :accountId AND t.deleted = false " +
           "AND (:statusId IS NULL OR t.statusId = :statusId) " +
           "AND (:category IS NULL OR t.category = :category) " +
           "AND (:requesterId IS NULL OR t.requesterId = :requesterId) " +
           "AND (:responderId IS NULL OR t.responderId = :responderId) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findFilteredTickets(@Param("accountId") Long accountId,
                                   @Param("statusId") Long statusId,
                                   @Param("category") String category,
                                   @Param("requesterId") Long requesterId,
                                   @Param("responderId") Long responderId);
}