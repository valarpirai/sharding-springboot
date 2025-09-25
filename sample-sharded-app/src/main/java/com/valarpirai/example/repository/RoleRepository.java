package com.valarpirai.example.repository;

import com.valarpirai.example.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Role entities.
 * This repository operates on sharded databases based on tenant context.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Find role by name within the current tenant.
     */
    Optional<Role> findByAccountIdAndNameAndDeletedFalse(Long accountId, String name);

    /**
     * Find all active roles within the current tenant.
     */
    List<Role> findByAccountIdAndDeletedFalseOrderByIsSystemRoleDescNameAsc(Long accountId);

    /**
     * Find system roles within the current tenant.
     */
    List<Role> findByAccountIdAndIsSystemRoleTrueAndDeletedFalse(Long accountId);

    /**
     * Find custom (non-system) roles within the current tenant.
     */
    List<Role> findByAccountIdAndIsSystemRoleFalseAndDeletedFalseOrderByNameAsc(Long accountId);

    /**
     * Check if role name exists within the current tenant.
     */
    boolean existsByAccountIdAndNameAndDeletedFalse(Long accountId, String name);

    /**
     * Find role by ID within the current tenant.
     */
    Optional<Role> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * Count active roles in the current tenant.
     */
    long countByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Count custom roles in the current tenant.
     */
    long countByAccountIdAndIsSystemRoleFalseAndDeletedFalse(Long accountId);

    /**
     * Find roles by multiple IDs within the current tenant.
     */
    List<Role> findByIdInAndAccountIdAndDeletedFalse(List<Long> ids, Long accountId);
}