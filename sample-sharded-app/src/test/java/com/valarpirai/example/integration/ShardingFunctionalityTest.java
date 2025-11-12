package com.valarpirai.example.integration;

import com.valarpirai.example.entity.global.Account;
import com.valarpirai.example.entity.sharded.Role;
import com.valarpirai.example.entity.sharded.User;
import com.valarpirai.example.repository.global.AccountRepository;
import com.valarpirai.example.repository.sharded.RoleRepository;
import com.valarpirai.example.repository.sharded.UserRepository;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardUtils;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for sharding functionality.
 * Tests shard assignment, routing, migration, and shard statistics.
 */
class ShardingFunctionalityTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ShardUtils shardUtils;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should automatically assign new tenants to latest shard")
    @Transactional
    void shouldAutoAssignNewTenantsToLatestShard() {
        // Create a new account
        Account account = createAccount("New Startup Inc", "admin@newstartup.com");

        // Assign to latest shard
        TenantShardMapping mapping = shardUtils.assignTenantToLatestShard(account.getId());

        // Verify assignment
        assertThat(mapping).isNotNull();
        assertThat(mapping.getTenantId()).isEqualTo(account.getId());
        assertThat(mapping.getShardId()).isEqualTo("shard1"); // shard1 is marked as latest
        assertThat(mapping.getShardStatus()).isEqualTo("ACTIVE");

        // Verify mapping persisted
        Optional<TenantShardMapping> retrieved = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getShardId()).isEqualTo("shard1");
    }

    @Test
    @DisplayName("Should correctly route operations to assigned shard")
    @Transactional
    void shouldCorrectlyRouteOperationsToAssignedShard() {
        // Create two accounts on different shards
        Account account1 = createAccount("Shard1 Company", "admin@shard1.com");
        Account account2 = createAccount("Shard2 Company", "admin@shard2.com");

        tenantShardMappingRepository.createMapping(account1.getId(), "shard1", "us-east-1");
        tenantShardMappingRepository.createMapping(account2.getId(), "shard2", "us-west-2");

        // Create data for account1 (should go to shard1)
        TenantContext.executeInTenantContext(account1.getId(), () -> {
            Role role = createRole(account1.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(account1.getId(), "user@shard1.com", "User", "Shard1", role.getId());
            return null;
        });

        // Create data for account2 (should go to shard2)
        TenantContext.executeInTenantContext(account2.getId(), () -> {
            Role role = createRole(account2.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(account2.getId(), "user@shard2.com", "User", "Shard2", role.getId());
            return null;
        });

        // Verify data is on correct shards by checking shard mapping
        Optional<TenantShardMapping> mapping1 = tenantShardMappingRepository.findShardByTenantId(account1.getId());
        Optional<TenantShardMapping> mapping2 = tenantShardMappingRepository.findShardByTenantId(account2.getId());

        assertThat(mapping1).isPresent();
        assertThat(mapping1.get().getShardId()).isEqualTo("shard1");
        assertThat(mapping2).isPresent();
        assertThat(mapping2.get().getShardId()).isEqualTo("shard2");

        // Verify data retrieval
        List<User> account1Users = TenantContext.executeInTenantContext(account1.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(account1.getId())
        );
        List<User> account2Users = TenantContext.executeInTenantContext(account2.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(account2.getId())
        );

        assertThat(account1Users).hasSize(1);
        assertThat(account1Users.get(0).getEmail()).isEqualTo("user@shard1.com");
        assertThat(account2Users).hasSize(1);
        assertThat(account2Users.get(0).getEmail()).isEqualTo("user@shard2.com");
    }

    @Test
    @DisplayName("Should support tenant migration between shards")
    @Transactional
    void shouldSupportTenantMigrationBetweenShards() {
        // Create account initially on shard1
        Account account = createAccount("Migration Test Corp", "admin@migrationtest.com");
        tenantShardMappingRepository.createMapping(account.getId(), "shard1", "us-east-1");

        // Create some data on shard1
        TenantContext.executeInTenantContext(account.getId(), () -> {
            Role role = createRole(account.getId(), "ADMIN", 0xFFFFFFFFL);
            createUser(account.getId(), "user1@migration.com", "User", "One", role.getId());
            createUser(account.getId(), "user2@migration.com", "User", "Two", role.getId());
            return null;
        });

        // Verify initial shard assignment
        Optional<TenantShardMapping> initialMapping = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(initialMapping).isPresent();
        assertThat(initialMapping.get().getShardId()).isEqualTo("shard1");

        // Migrate to shard2
        boolean migrated = shardUtils.moveTenantToShard(account.getId(), "shard2");
        assertThat(migrated).isTrue();

        // Verify new shard assignment
        Optional<TenantShardMapping> newMapping = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(newMapping).isPresent();
        assertThat(newMapping.get().getShardId()).isEqualTo("shard2");

        // Note: In a real scenario, you would need to migrate the actual data
        // This test focuses on the mapping migration
    }

    @Test
    @DisplayName("Should maintain shard statistics across multiple tenants")
    @Transactional
    void shouldMaintainShardStatisticsAcrossMultipleTenants() {
        // Create multiple accounts and distribute across shards
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Account account = createAccount("Company " + i, "admin" + i + "@company.com");
            accounts.add(account);

            // Alternate between shards
            String shardId = (i % 2 == 0) ? "shard2" : "shard1";
            String region = (i % 2 == 0) ? "us-west-2" : "us-east-1";
            tenantShardMappingRepository.createMapping(account.getId(), shardId, region);
        }

        // Get shard statistics
        ShardUtils.ShardStatistics stats = shardUtils.getShardStatistics();

        // Verify statistics
        assertThat(stats).isNotNull();
        assertThat(stats.totalTenants()).isGreaterThanOrEqualTo(10);
        assertThat(stats.totalShards()).isEqualTo(2);
        assertThat(stats.getShardDistribution()).containsKeys("shard1", "shard2");

        // Verify distribution (should be roughly 50/50 for this test)
        Map<String, Long> distribution = stats.getShardDistribution();
        assertThat(distribution.get("shard1")).isGreaterThanOrEqualTo(4L);
        assertThat(distribution.get("shard2")).isGreaterThanOrEqualTo(4L);
    }

    @Test
    @DisplayName("Should handle cache operations correctly")
    @Transactional
    void shouldHandleCacheOperationsCorrectly() {
        // Create account and mapping
        Account account = createAccount("Cache Test Corp", "admin@cachetest.com");
        tenantShardMappingRepository.createMapping(account.getId(), "shard1", "us-east-1");

        // First lookup - should hit database
        Optional<TenantShardMapping> firstLookup = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(firstLookup).isPresent();

        // Second lookup - should hit cache (we can't directly verify cache hit, but we test the functionality)
        Optional<TenantShardMapping> secondLookup = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(secondLookup).isPresent();
        assertThat(secondLookup.get().getShardId()).isEqualTo(firstLookup.get().getShardId());

        // Evict from cache
        tenantShardMappingRepository.evictFromCache(account.getId());

        // Lookup after eviction
        Optional<TenantShardMapping> thirdLookup = tenantShardMappingRepository.findShardByTenantId(account.getId());
        assertThat(thirdLookup).isPresent();
        assertThat(thirdLookup.get().getShardId()).isEqualTo("shard1");
    }

    @Test
    @DisplayName("Should support cache warm-up for multiple tenants")
    @Transactional
    void shouldSupportCacheWarmUpForMultipleTenants() {
        // Create multiple accounts
        List<Long> tenantIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Account account = createAccount("Warmup Company " + i, "admin" + i + "@warmup.com");
            tenantIds.add(account.getId());
            tenantShardMappingRepository.createMapping(account.getId(), "shard1", "us-east-1");
        }

        // Clear cache
        tenantShardMappingRepository.clearCache();

        // Warm up cache
        tenantShardMappingRepository.warmUpCache(tenantIds);

        // All subsequent lookups should hit cache
        for (Long tenantId : tenantIds) {
            Optional<TenantShardMapping> mapping = tenantShardMappingRepository.findShardByTenantId(tenantId);
            assertThat(mapping).isPresent();
            assertThat(mapping.get().getShardId()).isEqualTo("shard1");
        }
    }

    @Test
    @DisplayName("Should throw exception when tenant context is not set")
    void shouldThrowExceptionWhenTenantContextNotSet() {
        // Ensure tenant context is clear
        TenantContext.clear();

        // Attempting to query sharded data without tenant context should fail
        assertThatThrownBy(() -> userRepository.findAll())
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should support multiple tenants on same shard with proper isolation")
    @Transactional
    void shouldSupportMultipleTenantsOnSameShardWithIsolation() {
        // Create 5 accounts, all on shard1
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Account account = createAccount("Shard1 Company " + i, "admin" + i + "@shard1co.com");
            accounts.add(account);
            tenantShardMappingRepository.createMapping(account.getId(), "shard1", "us-east-1");

            // Create users for each account
            TenantContext.executeInTenantContext(account.getId(), () -> {
                Role role = createRole(account.getId(), "ADMIN", 0xFFFFFFFFL);
                for (int j = 1; j <= 3; j++) {
                    createUser(account.getId(), "user" + j + "@company" + i + ".com",
                            "User" + j, "Company" + i, role.getId());
                }
                return null;
            });
        }

        // Verify each tenant has exactly 3 users
        for (Account account : accounts) {
            List<User> users = TenantContext.executeInTenantContext(account.getId(), () ->
                    userRepository.findByAccountIdAndDeletedFalse(account.getId())
            );
            assertThat(users).hasSize(3);
            assertThat(users).allMatch(user -> user.getAccountId().equals(account.getId()));
        }

        // Verify total isolation - no cross-tenant visibility
        Account firstAccount = accounts.get(0);
        Account lastAccount = accounts.get(accounts.size() - 1);

        List<User> firstAccountUsers = TenantContext.executeInTenantContext(firstAccount.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(firstAccount.getId())
        );

        List<User> lastAccountUsers = TenantContext.executeInTenantContext(lastAccount.getId(), () ->
                userRepository.findByAccountIdAndDeletedFalse(lastAccount.getId())
        );

        // Verify no overlap in user emails
        List<String> firstEmails = firstAccountUsers.stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
        List<String> lastEmails = lastAccountUsers.stream()
                .map(User::getEmail)
                .collect(Collectors.toList());

        assertThat(firstEmails).doesNotContainAnyElementsOf(lastEmails);
    }

    @Test
    @DisplayName("Should get latest shard ID correctly")
    void shouldGetLatestShardIdCorrectly() {
        String latestShardId = tenantShardMappingRepository.getLatestShardId();

        assertThat(latestShardId).isNotNull();
        assertThat(latestShardId).isEqualTo("shard1"); // Based on test configuration
    }

    @Test
    @DisplayName("Should retrieve all tenant-shard mappings")
    @Transactional
    void shouldRetrieveAllTenantShardMappings() {
        // Create several accounts with mappings
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Account account = createAccount("All Mappings Company " + i, "admin" + i + "@allmappings.com");
            accounts.add(account);
            String shardId = (i % 2 == 0) ? "shard2" : "shard1";
            tenantShardMappingRepository.createMapping(account.getId(), shardId, "us-east-1");
        }

        // Retrieve all mappings
        List<TenantShardMapping> allMappings = tenantShardMappingRepository.findAllMappings();

        // Verify we have at least the mappings we created
        assertThat(allMappings).hasSizeGreaterThanOrEqualTo(5);

        // Verify our created accounts are in the mappings
        List<Long> mappedTenantIds = allMappings.stream()
                .map(TenantShardMapping::getTenantId)
                .collect(Collectors.toList());

        for (Account account : accounts) {
            assertThat(mappedTenantIds).contains(account.getId());
        }
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
}
