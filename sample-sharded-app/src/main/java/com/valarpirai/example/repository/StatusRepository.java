package com.valarpirai.example.repository;

import com.valarpirai.example.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Status entities.
 * This repository operates on sharded databases based on tenant context.
 */
@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {

    /**
     * Find status by name within the current tenant.
     */
    Optional<Status> findByAccountIdAndNameAndDeletedFalse(Long accountId, String name);

    /**
     * Find all active statuses within the current tenant ordered by position.
     */
    List<Status> findByAccountIdAndDeletedFalseOrderByPosition(Long accountId);

    /**
     * Find default status within the current tenant.
     */
    Optional<Status> findByAccountIdAndIsDefaultTrueAndDeletedFalse(Long accountId);

    /**
     * Find closed statuses within the current tenant.
     */
    List<Status> findByAccountIdAndIsClosedTrueAndDeletedFalseOrderByPosition(Long accountId);

    /**
     * Find open (non-closed) statuses within the current tenant.
     */
    List<Status> findByAccountIdAndIsClosedFalseAndDeletedFalseOrderByPosition(Long accountId);

    /**
     * Check if status name exists within the current tenant.
     */
    boolean existsByAccountIdAndNameAndDeletedFalse(Long accountId, String name);

    /**
     * Find status by ID within the current tenant.
     */
    Optional<Status> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * Count active statuses in the current tenant.
     */
    long countByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Find next available position for a new status.
     */
    @Query("SELECT COALESCE(MAX(s.position), 0) + 1 FROM Status s WHERE s.accountId = :accountId AND s.deleted = false")
    Integer findNextPosition(@Param("accountId") Long accountId);

    /**
     * Find statuses with position greater than or equal to the given position.
     */
    List<Status> findByAccountIdAndPositionGreaterThanEqualAndDeletedFalseOrderByPosition(
        Long accountId, Integer position);

    /**
     * Check if there are any default statuses in the current tenant.
     */
    boolean existsByAccountIdAndIsDefaultTrueAndDeletedFalse(Long accountId);

    /**
     * Find statuses by multiple IDs within the current tenant.
     */
    List<Status> findByIdInAndAccountIdAndDeletedFalse(List<Long> ids, Long accountId);
}