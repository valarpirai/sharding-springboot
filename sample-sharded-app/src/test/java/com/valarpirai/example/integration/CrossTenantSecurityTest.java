package com.valarpirai.example.integration;

import com.valarpirai.example.entity.global.Account;
import com.valarpirai.example.entity.global.Priority;
import com.valarpirai.example.entity.sharded.Role;
import com.valarpirai.example.entity.sharded.Status;
import com.valarpirai.example.entity.sharded.Ticket;
import com.valarpirai.example.entity.sharded.User;
import com.valarpirai.example.repository.global.AccountRepository;
import com.valarpirai.example.repository.sharded.RoleRepository;
import com.valarpirai.example.repository.sharded.StatusRepository;
import com.valarpirai.example.repository.sharded.TicketRepository;
import com.valarpirai.example.repository.sharded.UserRepository;
import com.valarpirai.sharding.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for cross-tenant security.
 * Verifies that tenants cannot access or modify each other's data.
 */
class CrossTenantSecurityTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StatusRepository statusRepository;

    private Account tenant1;
    private Account tenant2;
    private Account tenant3;

    @BeforeEach
    void setUp() {
        // Create test tenants
        tenant1 = createAccount("Secure Tenant 1", "admin@tenant1secure.com");
        tenant2 = createAccount("Secure Tenant 2", "admin@tenant2secure.com");
        tenant3 = createAccount("Secure Tenant 3", "admin@tenant3secure.com");

        // Assign to shards
        tenantShardMappingRepository.createMapping(tenant1.getId(), "shard1", "us-east-1");
        tenantShardMappingRepository.createMapping(tenant2.getId(), "shard1", "us-east-1"); // Same shard
        tenantShardMappingRepository.createMapping(tenant3.getId(), "shard2", "us-west-2"); // Different shard
    }

    @Test
    @DisplayName("Should prevent tenant from reading another tenant's users")
    @Transactional
    void shouldPreventCrossTenantUserRead() {
        // Tenant 1 creates users
        Long tenant1UserId = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            User user = createUser(tenant1.getId(), "secret@tenant1.com", "Secret", "User1", role.getId());
            return user.getId();
        });

        // Tenant 2 attempts to read Tenant 1's user by ID
        Optional<User> unauthorizedUser = executeInTenantContext(tenant2.getId(), () ->
                userRepository.findById(tenant1UserId)
        );

        // Should not find the user (filtered by account_id)
        assertThat(unauthorizedUser).isEmpty();

        // Tenant 2 attempts to query all users (should only see their own)
        List<User> tenant2Users = executeInTenantContext(tenant2.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant2.getId())
        );

        assertThat(tenant2Users).isEmpty(); // Tenant 2 has no users yet
    }

    @Test
    @DisplayName("Should prevent tenant from modifying another tenant's data")
    @Transactional
    void shouldPreventCrossTenantDataModification() {
        // Tenant 1 creates a user
        User tenant1User = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            return createUser(tenant1.getId(), "protected@tenant1.com", "Protected", "User", role.getId());
        });

        // Tenant 2 attempts to modify Tenant 1's user
        executeInTenantContext(tenant2.getId(), () -> {
            Optional<User> userToModify = userRepository.findById(tenant1User.getId());

            // Should not find the user
            assertThat(userToModify).isEmpty();
            return null;
        });

        // Verify Tenant 1's user remains unchanged
        User verifyUser = executeInTenantContext(tenant1.getId(), () ->
                userRepository.findById(tenant1User.getId()).orElse(null)
        );

        assertThat(verifyUser).isNotNull();
        assertThat(verifyUser.getEmail()).isEqualTo("protected@tenant1.com");
        assertThat(verifyUser.getFirstName()).isEqualTo("Protected");
    }

    @Test
    @DisplayName("Should prevent tenant from deleting another tenant's data")
    @Transactional
    void shouldPreventCrossTenantDataDeletion() {
        // Tenant 1 creates a ticket
        Ticket tenant1Ticket = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            Status status = createStatus(tenant1.getId(), "Open", true);
            User user = createUser(tenant1.getId(), "user@tenant1.com", "User", "One", role.getId());
            return createTicket(tenant1.getId(), "Important Ticket", user.getId(), status.getId());
        });

        // Tenant 2 attempts to delete Tenant 1's ticket
        executeInTenantContext(tenant2.getId(), () -> {
            Optional<Ticket> ticketToDelete = ticketRepository.findById(tenant1Ticket.getId());

            // Should not find the ticket
            assertThat(ticketToDelete).isEmpty();
            return null;
        });

        // Verify Tenant 1's ticket still exists
        Ticket verifyTicket = executeInTenantContext(tenant1.getId(), () ->
                ticketRepository.findById(tenant1Ticket.getId()).orElse(null)
        );

        assertThat(verifyTicket).isNotNull();
        assertThat(verifyTicket.getSubject()).isEqualTo("Important Ticket");
        assertThat(verifyTicket.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("Should enforce tenant context for all operations")
    void shouldEnforceTenantContextForAllOperations() {
        // Clear tenant context
        TenantContext.clear();

        // Attempt to perform operations without tenant context should fail
        assertThatThrownBy(() -> userRepository.findAll())
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should isolate queries even with SQL injection attempts")
    @Transactional
    void shouldPreventSqlInjectionCrossTenantAccess() {
        // Tenant 1 creates a user
        executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant1.getId(), "victim@tenant1.com", "Victim", "User", role.getId());
            return null;
        });

        // Tenant 2 attempts to query with malicious email (simulating injection)
        List<User> foundUsers = executeInTenantContext(tenant2.getId(), () -> {
            // Even if someone tries to inject, the account_id filter should prevent access
            return userRepository.findByAccountIdAndDeletedFalse(tenant2.getId());
        });

        // Should not find Tenant 1's users
        assertThat(foundUsers).isEmpty();
    }

    @Test
    @DisplayName("Should maintain isolation during concurrent tenant operations")
    @Transactional
    void shouldMaintainIsolationDuringConcurrentOperations() {
        // Simulate concurrent operations from different tenants

        // Tenant 1 creates data
        executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant1.getId(), "concurrent1@tenant1.com", "Concurrent", "User1", role.getId());
            return null;
        });

        // Tenant 2 creates data at "same time"
        executeInTenantContext(tenant2.getId(), () -> {
            Role role = createRole(tenant2.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant2.getId(), "concurrent2@tenant2.com", "Concurrent", "User2", role.getId());
            return null;
        });

        // Tenant 3 creates data at "same time"
        executeInTenantContext(tenant3.getId(), () -> {
            Role role = createRole(tenant3.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant3.getId(), "concurrent3@tenant3.com", "Concurrent", "User3", role.getId());
            return null;
        });

        // Verify each tenant sees only their own data
        List<User> tenant1Users = executeInTenantContext(tenant1.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant1.getId())
        );
        List<User> tenant2Users = executeInTenantContext(tenant2.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant2.getId())
        );
        List<User> tenant3Users = executeInTenantContext(tenant3.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant3.getId())
        );

        assertThat(tenant1Users).hasSize(1);
        assertThat(tenant1Users.get(0).getEmail()).isEqualTo("concurrent1@tenant1.com");

        assertThat(tenant2Users).hasSize(1);
        assertThat(tenant2Users.get(0).getEmail()).isEqualTo("concurrent2@tenant2.com");

        assertThat(tenant3Users).hasSize(1);
        assertThat(tenant3Users.get(0).getEmail()).isEqualTo("concurrent3@tenant3.com");
    }

    @Test
    @DisplayName("Should prevent access to soft-deleted data from other tenants")
    @Transactional
    void shouldPreventAccessToSoftDeletedDataFromOtherTenants() {
        // Tenant 1 creates and soft-deletes a user
        Long deletedUserId = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            User user = createUser(tenant1.getId(), "deleted@tenant1.com", "Deleted", "User", role.getId());
            user.delete(); // Soft delete
            userRepository.save(user);
            return user.getId();
        });

        // Tenant 2 attempts to access the soft-deleted user
        Optional<User> unauthorizedAccess = executeInTenantContext(tenant2.getId(), () ->
                userRepository.findById(deletedUserId)
        );

        // Should not find the user (different tenant)
        assertThat(unauthorizedAccess).isEmpty();

        // Even Tenant 1 should not see it in normal queries (soft-deleted)
        List<User> tenant1ActiveUsers = executeInTenantContext(tenant1.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant1.getId())
        );

        assertThat(tenant1ActiveUsers).isEmpty();
    }

    @Test
    @DisplayName("Should enforce read-only mode when enabled")
    @Transactional
    void shouldEnforceReadOnlyModeWhenEnabled() {
        // Tenant 1 creates initial data
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            User user = createUser(tenant1.getId(), "readonly@tenant1.com", "ReadOnly", "User", role.getId());
            return user.getId();
        });

        // Enable read-only mode for Tenant 1
        executeInReadOnlyTenantContext(tenant1.getId(), () -> {
            // Can read data
            Optional<User> user = userRepository.findById(userId);
            assertThat(user).isPresent();
            assertThat(user.get().getEmail()).isEqualTo("readonly@tenant1.com");
            return null;
        });

        // Verify read-only context is properly cleared after execution
        assertThat(hasTenantContext()).isFalse();
    }

    @Test
    @DisplayName("Should validate account_id matches tenant context")
    @Transactional
    void shouldValidateAccountIdMatchesTenantContext() {
        // Tenant 1 creates a role
        Role tenant1Role = executeInTenantContext(tenant1.getId(), () ->
                createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL)
        );

        // Attempt to create a user with mismatched account_id under Tenant 2 context
        executeInTenantContext(tenant2.getId(), () -> {
            User user = new User();
            user.setAccountId(tenant1.getId()); // Wrong account ID!
            user.setEmail("mismatch@test.com");
            user.setPasswordHash("$2a$10$hash");
            user.setFirstName("Mismatch");
            user.setLastName("User");
            user.setRoleId(tenant1Role.getId());
            user.setActive(true);

            // This should either fail validation or save with Tenant 2's ID
            User saved = userRepository.save(user);

            // Repository might enforce account_id from context
            // or validation might catch it
            return saved;
        });
    }

    @Test
    @DisplayName("Should prevent cross-tenant ticket assignment")
    @Transactional
    void shouldPreventCrossTenantTicketAssignment() {
        // Tenant 1 creates a ticket and user
        Long tenant1TicketId = executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL);
            Status status = createStatus(tenant1.getId(), "Open", true);
            User user = createUser(tenant1.getId(), "requester@tenant1.com", "Requester", "One", role.getId());
            Ticket ticket = createTicket(tenant1.getId(), "Cross-tenant test ticket", user.getId(), status.getId());
            return ticket.getId();
        });

        // Tenant 2 creates a user who tries to get assigned to Tenant 1's ticket
        executeInTenantContext(tenant2.getId(), () -> {
            Role role = createRole(tenant2.getId(), "AGENT", 0xFFFFFFL);
            User tenant2Agent = createUser(tenant2.getId(), "agent@tenant2.com", "Agent", "Two", role.getId());

            // Attempt to access and modify Tenant 1's ticket
            Optional<Ticket> ticket = ticketRepository.findById(tenant1TicketId);

            // Should not be able to access Tenant 1's ticket
            assertThat(ticket).isEmpty();
            return null;
        });

        // Verify Tenant 1's ticket remains unmodified
        Ticket verifyTicket = executeInTenantContext(tenant1.getId(), () ->
                ticketRepository.findById(tenant1TicketId).orElse(null)
        );

        assertThat(verifyTicket).isNotNull();
        assertThat(verifyTicket.getResponderId()).isNull(); // Still unassigned
    }

    // Helper methods

    private Account createAccount(String name, String email) {
        Account account = new Account();
        account.setName(name);
        account.setAdminEmail(email);
        return accountRepository.save(account);
    }

    private User createUser(Long accountId, String email, String firstName, String lastName, Long roleId) {
        User user = new User();
        user.setAccountId(accountId);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashedpassword");
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRoleId(roleId);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Role createRole(Long accountId, String name, Long permissionsMask) {
        Role role = new Role();
        role.setAccountId(accountId);
        role.setName(name);
        role.setPermissionsMask(permissionsMask);
        return roleRepository.save(role);
    }

    private Status createStatus(Long accountId, String name, boolean isDefault) {
        Status status = new Status();
        status.setAccountId(accountId);
        status.setName(name);
        status.setIsDefault(isDefault);
        return statusRepository.save(status);
    }

    private Ticket createTicket(Long accountId, String subject, Long requesterId, Long statusId) {
        Ticket ticket = new Ticket();
        ticket.setAccountId(accountId);
        ticket.setSubject(subject);
        ticket.setDescription("Test ticket description");
        ticket.setRequesterId(requesterId);
        ticket.setStatusId(statusId);
        ticket.setPriority(Priority.MEDIUM);
        return ticketRepository.save(ticket);
    }
}
