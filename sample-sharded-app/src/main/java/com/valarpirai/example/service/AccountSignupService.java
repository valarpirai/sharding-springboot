package com.valarpirai.example.service;

import com.valarpirai.example.dto.SignupRequest;
import com.valarpirai.example.dto.SignupResponse;
import com.valarpirai.example.entity.Account;
import com.valarpirai.example.entity.Role;
import com.valarpirai.example.entity.User;
import com.valarpirai.example.repository.AccountRepository;
import com.valarpirai.example.repository.RoleRepository;
import com.valarpirai.example.repository.UserRepository;
import com.valarpirai.example.security.PermissionMasks;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.ShardUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling account signup and initial setup.
 */
@Service
public class AccountSignupService {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignupService.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ShardLookupService shardLookupService;
    private final ShardUtils shardUtils;
    private final PasswordEncoder passwordEncoder;
    private final AccountDemoSetupService demoSetupService;

    public AccountSignupService(AccountRepository accountRepository,
                              RoleRepository roleRepository,
                              UserRepository userRepository,
                              ShardLookupService shardLookupService,
                              ShardUtils shardUtils,
                              PasswordEncoder passwordEncoder,
                              AccountDemoSetupService demoSetupService) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.shardLookupService = shardLookupService;
        this.shardUtils = shardUtils;
        this.passwordEncoder = passwordEncoder;
        this.demoSetupService = demoSetupService;
    }

    /**
     * Create a new account with admin user and trigger background demo setup.
     */
    @Transactional
    public SignupResponse createAccount(SignupRequest request) {
        logger.info("Creating new account: {}", request.getAccountName());

        // Validate uniqueness
        validateAccountUniqueness(request);

        // 1. Create account in global database
        Account account = Account.builder()
                .name(request.getAccountName())
                .adminEmail(request.getAdminEmail())
                .build();
        account = accountRepository.save(account);
        logger.info("Created account with ID: {}", account.getId());

        // 2. Get latest shard and create tenant-shard mapping
        String latestShardId = shardUtils.getLatestShard();
        shardLookupService.createMapping(account.getId(), latestShardId, "us-east-1");
        logger.info("Mapped account {} to latest shard: {}", account.getId(), latestShardId);

        // 3. Set tenant context for subsequent operations
        TenantContext.setTenantId(account.getId());

        try {
            // 4. Create ADMIN role first (needed for admin user)
            Role adminRole = createAdminRole(account.getId());
            logger.info("Created ADMIN role with ID: {}", adminRole.getId());

            // 5. Create admin user
            User adminUser = createAdminUser(account, request, adminRole.getId());
            logger.info("Created admin user with ID: {}", adminUser.getId());

            // 6. Trigger background demo setup
            demoSetupService.setupDemoEnvironmentAsync(account.getId());
            logger.info("Triggered background demo setup for account: {}", account.getId());

            // 7. Return success response
            return SignupResponse.success(
                account.getId(),
                account.getName(),
                account.getAdminEmail(),
                adminUser.getId(),
                adminUser.getFullName(),
                account.getCreatedAt()
            );

        } catch (Exception e) {
            logger.error("Error during account creation for account: {}", account.getId(), e);
            // Clean up: mark account as deleted if user creation fails
            account.delete();
            accountRepository.save(account);
            throw new RuntimeException("Failed to create account: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Validate that account name and email are unique.
     */
    private void validateAccountUniqueness(SignupRequest request) {
        if (accountRepository.existsByNameIgnoreCase(request.getAccountName())) {
            throw new IllegalArgumentException("Account name '" + request.getAccountName() + "' is already taken");
        }

        if (accountRepository.existsByAdminEmailIgnoreCase(request.getAdminEmail())) {
            throw new IllegalArgumentException("Admin email '" + request.getAdminEmail() + "' is already registered");
        }
    }

    /**
     * Create the ADMIN role for the new account.
     */
    private Role createAdminRole(Long accountId) {
        Role adminRole = Role.builder()
                .accountId(accountId)
                .name("ADMIN")
                .permissionsMask(PermissionMasks.ADMIN_PERMISSIONS)
                .isSystemRole(true)
                .build();

        return roleRepository.save(adminRole);
    }

    /**
     * Create the admin user for the new account.
     */
    private User createAdminUser(Account account, SignupRequest request, Long adminRoleId) {
        // Extract first and last name from email (simple approach)
        String[] nameParts = extractNameFromEmail(request.getAdminEmail());

        User adminUser = User.builder()
                .accountId(account.getId())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .roleId(adminRoleId)
                .active(true)
                .build();

        return userRepository.save(adminUser);
    }

    /**
     * Extract first and last name from email address.
     * Simple heuristic: use part before @ as first name, "User" as last name.
     */
    private String[] extractNameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@'));

        // Split on common separators
        if (localPart.contains(".")) {
            String[] parts = localPart.split("\\.", 2);
            return new String[]{capitalize(parts[0]), capitalize(parts[1])};
        } else if (localPart.contains("_")) {
            String[] parts = localPart.split("_", 2);
            return new String[]{capitalize(parts[0]), capitalize(parts[1])};
        } else {
            return new String[]{capitalize(localPart), "User"};
        }
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}