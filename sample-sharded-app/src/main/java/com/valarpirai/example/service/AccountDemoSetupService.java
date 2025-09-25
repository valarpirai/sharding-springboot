package com.valarpirai.example.service;

import com.valarpirai.example.entity.*;
import com.valarpirai.example.repository.*;
import com.valarpirai.example.entity.*;
import com.valarpirai.example.repository.*;
import com.valarpirai.example.security.PermissionMasks;
import com.valarpirai.sharding.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Service for setting up demo environment after account creation.
 * All operations run asynchronously in the background.
 */
@Service
public class AccountDemoSetupService {

    private static final Logger logger = LoggerFactory.getLogger(AccountDemoSetupService.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final StatusRepository statusRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountDemoSetupService(AccountRepository accountRepository,
                                 RoleRepository roleRepository,
                                 StatusRepository statusRepository,
                                 UserRepository userRepository,
                                 TicketRepository ticketRepository,
                                 PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.statusRepository = statusRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Setup demo environment asynchronously.
     */
    @Async("demoSetupTaskExecutor")
    public CompletableFuture<Void> setupDemoEnvironmentAsync(Long accountId) {
        logger.info("Starting background demo setup for account: {}", accountId);

        try {
            // Validate account exists
            if (!accountRepository.existsByIdAndDeletedFalse(accountId)) {
                throw new IllegalArgumentException("Account not found: " + accountId);
            }

            // Execute all operations within tenant context
            TenantContext.executeInTenantContext(accountId, () -> {
                setupDemoEnvironment(accountId);
                return null;
            });

            logger.info("Completed background demo setup for account: {}", accountId);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("Failed to setup demo environment for account: {}", accountId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Setup demo environment within tenant context.
     */
    @Transactional
    public void setupDemoEnvironment(Long accountId) {
        logger.info("Setting up demo environment for account: {}", accountId);

        // 1. Create remaining default roles (ADMIN already exists)
        createDefaultRoles(accountId);

        // 2. Create default statuses
        createDefaultStatuses(accountId);

        // 3. Create sample requester user
        User sampleUser = createSampleUser(accountId);

        // 4. Create sample tickets
        createSampleTickets(accountId, sampleUser.getId());

        logger.info("Demo environment setup completed for account: {}", accountId);
    }

    /**
     * Create AGENT and REQUESTER roles (ADMIN already created during signup).
     */
    private void createDefaultRoles(Long accountId) {
        logger.debug("Creating default roles for account: {}", accountId);

        // Create AGENT role
        if (!roleRepository.existsByAccountIdAndNameAndDeletedFalse(accountId, "AGENT")) {
            Role agentRole = Role.builder()
                    .accountId(accountId)
                    .name("AGENT")
                    .permissionsMask(PermissionMasks.AGENT_PERMISSIONS)
                    .isSystemRole(true)
                    .build();
            roleRepository.save(agentRole);
            logger.debug("Created AGENT role for account: {}", accountId);
        }

        // Create REQUESTER role
        if (!roleRepository.existsByAccountIdAndNameAndDeletedFalse(accountId, "REQUESTER")) {
            Role requesterRole = Role.builder()
                    .accountId(accountId)
                    .name("REQUESTER")
                    .permissionsMask(PermissionMasks.REQUESTER_PERMISSIONS)
                    .isSystemRole(true)
                    .build();
            roleRepository.save(requesterRole);
            logger.debug("Created REQUESTER role for account: {}", accountId);
        }
    }

    /**
     * Create default ticket statuses.
     */
    private void createDefaultStatuses(Long accountId) {
        logger.debug("Creating default statuses for account: {}", accountId);

        // Status configurations
        Object[][] statusConfigs = {
            {"Open", "Newly created ticket", "#28a745", true, false, 1},
            {"In Progress", "Ticket is being worked on", "#ffc107", false, false, 2},
            {"Pending", "Waiting for customer response", "#fd7e14", false, false, 3},
            {"Resolved", "Issue has been resolved", "#6f42c1", false, true, 4},
            {"Closed", "Ticket is closed", "#6c757d", false, true, 5}
        };

        for (Object[] config : statusConfigs) {
            String name = (String) config[0];
            if (!statusRepository.existsByAccountIdAndNameAndDeletedFalse(accountId, name)) {
                Status status = Status.builder()
                        .accountId(accountId)
                        .name(name)
                        .description((String) config[1])
                        .color((String) config[2])
                        .isDefault((Boolean) config[3])
                        .isClosed((Boolean) config[4])
                        .position((Integer) config[5])
                        .build();
                statusRepository.save(status);
                logger.debug("Created status '{}' for account: {}", name, accountId);
            }
        }
    }

    /**
     * Create sample requester user (Andrea).
     */
    private User createSampleUser(Long accountId) {
        logger.debug("Creating sample user for account: {}", accountId);

        // Get REQUESTER role
        Role requesterRole = roleRepository.findByAccountIdAndNameAndDeletedFalse(accountId, "REQUESTER")
                .orElseThrow(() -> new IllegalStateException("REQUESTER role not found"));

        // Check if user already exists
        if (userRepository.existsByAccountIdAndEmailAndDeletedFalse(accountId, "andrea@example.com")) {
            return userRepository.findByAccountIdAndEmailAndDeletedFalse(accountId, "andrea@example.com")
                    .orElseThrow();
        }

        User sampleUser = User.builder()
                .accountId(accountId)
                .email("andrea@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .firstName("Andrea")
                .lastName("Sample")
                .roleId(requesterRole.getId())
                .active(true)
                .build();

        sampleUser = userRepository.save(sampleUser);
        logger.debug("Created sample user '{}' for account: {}", sampleUser.getEmail(), accountId);
        return sampleUser;
    }

    /**
     * Create sample tickets.
     */
    private void createSampleTickets(Long accountId, Long requesterId) {
        logger.debug("Creating sample tickets for account: {}", accountId);

        // Get default status (Open)
        Status openStatus = statusRepository.findByAccountIdAndIsDefaultTrueAndDeletedFalse(accountId)
                .orElseThrow(() -> new IllegalStateException("Default status not found"));

        // Get In Progress status
        Status inProgressStatus = statusRepository.findByAccountIdAndNameAndDeletedFalse(accountId, "In Progress")
                .orElse(openStatus); // Fallback to open if not found

        // Ticket configurations
        Object[][] ticketConfigs = {
            {
                "Login Issues",
                "I'm having trouble logging into my account. The system keeps saying my password is incorrect, but I'm sure it's right. Could you please help me reset it or check if there's an issue with my account?",
                Priority.HIGH,
                "Technical",
                "Authentication",
                openStatus.getId()
            },
            {
                "Feature Request: Dark Mode",
                "It would be great if the application had a dark mode option. I work late hours and the bright interface strains my eyes. Many modern applications have this feature and it would really improve the user experience.",
                Priority.MEDIUM,
                "Enhancement",
                "UI/UX",
                openStatus.getId()
            },
            {
                "Bug: Dashboard Loading Slow",
                "The main dashboard is taking a very long time to load, especially in the morning when I first log in. It sometimes takes up to 30 seconds to display all the widgets. This is impacting my productivity.",
                Priority.LOW,
                "Performance",
                "Speed",
                inProgressStatus.getId()
            }
        };

        for (Object[] config : ticketConfigs) {
            Ticket ticket = Ticket.builder()
                    .accountId(accountId)
                    .subject((String) config[0])
                    .description((String) config[1])
                    .requesterId(requesterId)
                    .statusId((Long) config[5])
                    .priority((Priority) config[2])
                    .category((String) config[3])
                    .subcategory((String) config[4])
                    .build();

            ticketRepository.save(ticket);
            logger.debug("Created sample ticket '{}' for account: {}", ticket.getSubject(), accountId);
        }
    }
}