package com.valarpirai.example.repository;

import com.valarpirai.example.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Account entities.
 * This repository operates on the global database (not sharded).
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Check if account exists and is active (not deleted).
     */
    boolean existsByIdAndDeletedFalse(Long id);

    /**
     * Find account by ID if it's active (not deleted).
     */
    Optional<Account> findByIdAndDeletedFalse(Long id);

    /**
     * Find account by name if it's active.
     */
    Optional<Account> findByNameAndDeletedFalse(String name);

    /**
     * Find account by admin email if it's active.
     */
    Optional<Account> findByAdminEmailAndDeletedFalse(String adminEmail);

    /**
     * Check if account name is already taken (case insensitive).
     */
    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE LOWER(a.name) = LOWER(:name) AND a.deleted = false")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    /**
     * Check if admin email is already taken (case insensitive).
     */
    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE LOWER(a.adminEmail) = LOWER(:email) AND a.deleted = false")
    boolean existsByAdminEmailIgnoreCase(@Param("email") String email);

    /**
     * Find active accounts (not deleted).
     */
    @Query("SELECT a FROM Account a WHERE a.deleted = false ORDER BY a.createdAt DESC")
    Iterable<Account> findAllActive();
}