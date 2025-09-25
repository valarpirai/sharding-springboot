package com.valarpirai.example.repository;

import com.valarpirai.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entities.
 * This repository operates on sharded databases based on tenant context.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email within the current tenant.
     */
    Optional<User> findByAccountIdAndEmailAndDeletedFalse(Long accountId, String email);

    /**
     * Find user by email within the current tenant (parameter order compatible).
     */
    Optional<User> findByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);

    /**
     * Find all active users within the current tenant.
     */
    List<User> findByAccountIdAndDeletedFalseOrderByCreatedAtDesc(Long accountId);

    /**
     * Find all active users within the current tenant (without ordering).
     */
    List<User> findByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Find active users within the current tenant by role.
     */
    List<User> findByAccountIdAndRoleIdAndDeletedFalse(Long accountId, Long roleId);

    /**
     * Check if email exists within the current tenant.
     */
    boolean existsByAccountIdAndEmailAndDeletedFalse(Long accountId, String email);

    /**
     * Check if email exists within the current tenant (parameter order compatible).
     */
    boolean existsByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);

    /**
     * Find user by ID within the current tenant.
     */
    Optional<User> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * Count active users in the current tenant.
     */
    long countByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Find users by their active status within the current tenant.
     */
    List<User> findByAccountIdAndActiveAndDeletedFalseOrderByCreatedAtDesc(Long accountId, Boolean active);

    /**
     * Search users by name within the current tenant.
     */
    @Query("SELECT u FROM User u WHERE u.accountId = :accountId AND u.deleted = false " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY u.createdAt DESC")
    List<User> searchByNameOrEmail(@Param("accountId") Long accountId, @Param("searchTerm") String searchTerm);

    /**
     * Find users by multiple IDs within the current tenant.
     */
    List<User> findByIdInAndAccountIdAndDeletedFalse(List<Long> ids, Long accountId);
}