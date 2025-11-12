package com.valarpirai.example.integration;

import com.valarpirai.example.entity.global.Account;
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
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify multi-tenant data isolation.
 * Tests that data from one tenant cannot be accessed by another tenant.
 */
class MultiTenantDataIsolationTest extends BaseIntegrationTest {

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

    private Account tenant1Account;
    private Account tenant2Account;
    private Account tenant3Account;

    @BeforeEach
    void setUp() {
        // Create test accounts in global database
        tenant1Account = createAccount("Tenant 1 Corp", "admin@tenant1.com");
        tenant2Account = createAccount("Tenant 2 Inc", "admin@tenant2.com");
        tenant3Account = createAccount("Tenant 3 LLC", "admin@tenant3.com");

        // Assign tenants to shards
        tenantShardMappingRepository.createMapping(tenant1Account.getId(), "shard1", "us-east-1");
        tenantShardMappingRepository.createMapping(tenant2Account.getId(), "shard2", "us-west-2");
        tenantShardMappingRepository.createMapping(tenant3Account.getId(), "shard1", "us-east-1");
    }

    @Test
    @DisplayName("Should isolate user data between tenants on same shard")
    @Transactional
    void shouldIsolateUserDataBetweenTenantsOnSameShard() {
        // Tenant 1 creates users
        List<User> tenant1Users = TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            Role adminRole = createRole(tenant1Account.getId(), "ADMIN", 0xFFFFFFFFL);

            User user1 = createUser(tenant1Account.getId(), "alice@tenant1.com", "Alice", "Smith", adminRole.getId());
            User user2 = createUser(tenant1Account.getId(), "bob@tenant1.com", "Bob", "Johnson", adminRole.getId());

            return userRepository.findByAccountIdAndDeletedFalse(tenant1Account.getId());
        });

        // Tenant 3 creates users (same shard as Tenant 1)
        List<User> tenant3Users = TenantContext.executeInTenantContext(tenant3Account.getId(), () -> {
            Role adminRole = createRole(tenant3Account.getId(), "ADMIN", 0xFFFFFFFFL);

            User user1 = createUser(tenant3Account.getId(), "charlie@tenant3.com", "Charlie", "Brown", adminRole.getId());
            User user2 = createUser(tenant3Account.getId(), "diana@tenant3.com", "Diana", "Prince", adminRole.getId());

            return userRepository.findByAccountIdAndDeletedFalse(tenant3Account.getId());
        });

        // Verify Tenant 1 can only see their own users
        assertThat(tenant1Users).hasSize(2);
        assertThat(tenant1Users).allMatch(user -> user.getAccountId().equals(tenant1Account.getId()));
        assertThat(tenant1Users).extracting(User::getEmail)
                .containsExactlyInAnyOrder("alice@tenant1.com", "bob@tenant1.com");

        // Verify Tenant 3 can only see their own users
        assertThat(tenant3Users).hasSize(2);
        assertThat(tenant3Users).allMatch(user -> user.getAccountId().equals(tenant3Account.getId()));
        assertThat(tenant3Users).extracting(User::getEmail)
                .containsExactlyInAnyOrder("charlie@tenant3.com", "diana@tenant3.com");

        // Verify total isolation - Tenant 1 cannot see Tenant 3's users
        List<User> tenant1UsersAgain = TenantContext.executeInTenantContext(tenant1Account.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(tenant1Account.getId())
        );
        assertThat(tenant1UsersAgain).hasSize(2);
        assertThat(tenant1UsersAgain).noneMatch(user -> user.getEmail().contains("@tenant3.com"));
    }

    @Test
    @DisplayName("Should isolate ticket data between tenants on different shards")
    @Transactional
    void shouldIsolateTicketDataBetweenTenantsOnDifferentShards() {
        // Setup Tenant 1 (shard1)
        List<Ticket> tenant1Tickets = TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            Role adminRole = createRole(tenant1Account.getId(), "ADMIN", 0xFFFFFFFFL);
            Status openStatus = createStatus(tenant1Account.getId(), "Open", true);
            User user = createUser(tenant1Account.getId(), "user1@tenant1.com", "User", "One", adminRole.getId());

            Ticket ticket1 = createTicket(tenant1Account.getId(), "Tenant 1 - Issue 1", user.getId(), openStatus.getId());
            Ticket ticket2 = createTicket(tenant1Account.getId(), "Tenant 1 - Issue 2", user.getId(), openStatus.getId());

            return ticketRepository.findByAccountIdAndDeletedFalse(tenant1Account.getId());
        });

        // Setup Tenant 2 (shard2 - different shard)
        List<Ticket> tenant2Tickets = TenantContext.executeInTenantContext(tenant2Account.getId(), () -> {
            Role adminRole = createRole(tenant2Account.getId(), "ADMIN", 0xFFFFFFFFL);
            Status openStatus = createStatus(tenant2Account.getId(), "Open", true);
            User user = createUser(tenant2Account.getId(), "user1@tenant2.com", "User", "Two", adminRole.getId());

            Ticket ticket1 = createTicket(tenant2Account.getId(), "Tenant 2 - Issue 1", user.getId(), openStatus.getId());
            Ticket ticket2 = createTicket(tenant2Account.getId(), "Tenant 2 - Issue 2", user.getId(), openStatus.getId());
            Ticket ticket3 = createTicket(tenant2Account.getId(), "Tenant 2 - Issue 3", user.getId(), openStatus.getId());

            return ticketRepository.findByAccountIdAndDeletedFalse(tenant2Account.getId());
        });

        // Verify Tenant 1 data isolation
        assertThat(tenant1Tickets).hasSize(2);
        assertThat(tenant1Tickets).allMatch(ticket -> ticket.getAccountId().equals(tenant1Account.getId()));
        assertThat(tenant1Tickets).extracting(Ticket::getSubject)
                .allMatch(subject -> subject.startsWith("Tenant 1"));

        // Verify Tenant 2 data isolation
        assertThat(tenant2Tickets).hasSize(3);
        assertThat(tenant2Tickets).allMatch(ticket -> ticket.getAccountId().equals(tenant2Account.getId()));
        assertThat(tenant2Tickets).extracting(Ticket::getSubject)
                .allMatch(subject -> subject.startsWith("Tenant 2"));
    }

    @Test
    @DisplayName("Should prevent cross-tenant queries even with explicit ID")
    @Transactional
    void shouldPreventCrossTenantQueriesWithExplicitId() {
        // Tenant 1 creates a user
        Long tenant1UserId = TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            Role adminRole = createRole(tenant1Account.getId(), "ADMIN", 0xFFFFFFFFL);
            User user = createUser(tenant1Account.getId(), "secret@tenant1.com", "Secret", "User", adminRole.getId());
            return user.getId();
        });

        // Tenant 2 tries to access Tenant 1's user by ID (should fail or return empty)
        User tenant2AttemptedUser = TenantContext.executeInTenantContext(tenant2Account.getId(), () ->
                userRepository.findById(tenant1UserId).orElse(null)
        );

        // Verify Tenant 2 cannot see Tenant 1's user
        // The repository should enforce tenant isolation via account_id filtering
        assertThat(tenant2AttemptedUser).isNull();
    }

    @Test
    @DisplayName("Should maintain isolation across multiple concurrent operations")
    @Transactional
    void shouldMaintainIsolationAcrossConcurrentOperations() {
        // Simulate concurrent operations from multiple tenants

        // Tenant 1 operations
        TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            Role role = createRole(tenant1Account.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant1Account.getId(), "user1@tenant1.com", "User", "1T1", role.getId());
            createUser(tenant1Account.getId(), "user2@tenant1.com", "User", "2T1", role.getId());
            return null;
        });

        // Tenant 2 operations
        TenantContext.executeInTenantContext(tenant2Account.getId(), () -> {
            Role role = createRole(tenant2Account.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant2Account.getId(), "user1@tenant2.com", "User", "1T2", role.getId());
            createUser(tenant2Account.getId(), "user2@tenant2.com", "User", "2T2", role.getId());
            createUser(tenant2Account.getId(), "user3@tenant2.com", "User", "3T2", role.getId());
            return null;
        });

        // Tenant 3 operations
        TenantContext.executeInTenantContext(tenant3Account.getId(), () -> {
            Role role = createRole(tenant3Account.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(tenant3Account.getId(), "user1@tenant3.com", "User", "1T3", role.getId());
            return null;
        });

        // Verify each tenant sees only their own data
        Long tenant1Count = TenantContext.executeInTenantContext(tenant1Account.getId(), () ->
                (long) userRepository.findByAccountIdAndDeletedFalse(tenant1Account.getId()).size()
        );

        Long tenant2Count = TenantContext.executeInTenantContext(tenant2Account.getId(), () ->
                (long) userRepository.findByAccountIdAndDeletedFalse(tenant2Account.getId()).size()
        );

        Long tenant3Count = TenantContext.executeInTenantContext(tenant3Account.getId(), () ->
                (long) userRepository.findByAccountIdAndDeletedFalse(tenant3Account.getId()).size()
        );

        assertThat(tenant1Count).isEqualTo(2);
        assertThat(tenant2Count).isEqualTo(3);
        assertThat(tenant3Count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should isolate role and status configurations per tenant")
    @Transactional
    void shouldIsolateRoleAndStatusConfigurationsPerTenant() {
        // Tenant 1 creates custom roles and statuses
        TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            createRole(tenant1Account.getId(), "CUSTOM_ADMIN", 0xFFFFFFFFL);
            createRole(tenant1Account.getId(), "CUSTOM_AGENT", 0xFFF0000L);
            createStatus(tenant1Account.getId(), "Custom Open", true);
            createStatus(tenant1Account.getId(), "Custom Closed", false);
            return null;
        });

        // Tenant 2 creates different custom roles and statuses
        TenantContext.executeInTenantContext(tenant2Account.getId(), () -> {
            createRole(tenant2Account.getId(), "SUPER_USER", 0xFFFFFFFFL);
            createStatus(tenant2Account.getId(), "In Review", false);
            return null;
        });

        // Verify Tenant 1 sees only their configurations
        List<Role> tenant1Roles = TenantContext.executeInTenantContext(tenant1Account.getId(), () ->
                roleRepository.findByAccountId(tenant1Account.getId())
        );
        List<Status> tenant1Statuses = TenantContext.executeInTenantContext(tenant1Account.getId(), () ->
                statusRepository.findByAccountId(tenant1Account.getId())
        );

        assertThat(tenant1Roles).hasSize(2);
        assertThat(tenant1Roles).extracting(Role::getName)
                .containsExactlyInAnyOrder("CUSTOM_ADMIN", "CUSTOM_AGENT");
        assertThat(tenant1Statuses).hasSize(2);
        assertThat(tenant1Statuses).extracting(Status::getName)
                .containsExactlyInAnyOrder("Custom Open", "Custom Closed");

        // Verify Tenant 2 sees only their configurations
        List<Role> tenant2Roles = TenantContext.executeInTenantContext(tenant2Account.getId(), () ->
                roleRepository.findByAccountId(tenant2Account.getId())
        );
        List<Status> tenant2Statuses = TenantContext.executeInTenantContext(tenant2Account.getId(), () ->
                statusRepository.findByAccountId(tenant2Account.getId())
        );

        assertThat(tenant2Roles).hasSize(1);
        assertThat(tenant2Roles).extracting(Role::getName)
                .containsExactly("SUPER_USER");
        assertThat(tenant2Statuses).hasSize(1);
        assertThat(tenant2Statuses).extracting(Status::getName)
                .containsExactly("In Review");
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
        ticket.setPriority("MEDIUM");
        return ticketRepository.save(ticket);
    }
}
