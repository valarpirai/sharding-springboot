package com.valarpirai.example.service;

import com.valarpirai.example.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for account validation operations.
 * Used by filters and other components that need to validate account existence.
 */
@Service
public class AccountValidationService {

    private static final Logger logger = LoggerFactory.getLogger(AccountValidationService.class);

    private final AccountRepository accountRepository;

    public AccountValidationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Check if an account exists and is active.
     *
     * @param accountId the account ID to validate
     * @return true if account exists and is not deleted
     */
    public boolean isAccountValid(Long accountId) {
        if (accountId == null) {
            logger.debug("Account ID is null, validation failed");
            return false;
        }

        try {
            boolean exists = accountRepository.existsByIdAndDeletedFalse(accountId);
            logger.debug("Account {} validation result: {}", accountId, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Error validating account {}: {}", accountId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if an account exists regardless of deleted status.
     *
     * @param accountId the account ID to check
     * @return true if account exists (even if deleted)
     */
    public boolean accountExists(Long accountId) {
        if (accountId == null) {
            return false;
        }

        try {
            return accountRepository.existsById(accountId);
        } catch (Exception e) {
            logger.error("Error checking account existence {}: {}", accountId, e.getMessage(), e);
            return false;
        }
    }
}