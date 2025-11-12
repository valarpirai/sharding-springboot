package com.valarpirai.example.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valarpirai.example.dto.UserCreateRequest;
import com.valarpirai.example.dto.UserUpdateRequest;
import com.valarpirai.example.entity.global.Account;
import com.valarpirai.example.entity.sharded.Role;
import com.valarpirai.example.entity.sharded.User;
import com.valarpirai.example.integration.BaseIntegrationTest;
import com.valarpirai.example.repository.global.AccountRepository;
import com.valarpirai.example.repository.sharded.RoleRepository;
import com.valarpirai.example.repository.sharded.UserRepository;
import com.valarpirai.sharding.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API integration tests for UserController.
 * Tests REST endpoints for user management with multi-tenant isolation.
 */
@AutoConfigureMockMvc
class UserControllerApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Account tenant1;
    private Account tenant2;
    private Role adminRole1;
    private Role adminRole2;

    @BeforeEach
    void setUp() {
        // Create test tenants with unique emails to avoid conflicts
        String uniqueId = String.valueOf(System.currentTimeMillis());
        tenant1 = createAccount("API Test Tenant 1", "api1+" + uniqueId + "@test.com");
        tenant2 = createAccount("API Test Tenant 2", "api2+" + uniqueId + "@test.com");

        // Setup shard mappings
        tenantShardMappingRepository.createMapping(tenant1.getId(), "shard1", "us-east-1");
        tenantShardMappingRepository.createMapping(tenant2.getId(), "shard2", "us-west-2");

        // Create admin roles
        adminRole1 = executeInTenantContext(tenant1.getId(), () ->
                createRole(tenant1.getId(), "ADMIN", 0xFFFFFFFFL)
        );

        adminRole2 = executeInTenantContext(tenant2.getId(), () ->
                createRole(tenant2.getId(), "ADMIN", 0xFFFFFFFFL)
        );
    }

    @Test
    @DisplayName("GET /api/users - Should return users for tenant")
    @Transactional
    void shouldGetUsersForTenant() throws Exception {
        // Create users for tenant1
        executeInTenantContext(tenant1.getId(), () -> {
            createUser(tenant1.getId(), "user1@tenant1.com", "User", "One", adminRole1.getId());
            createUser(tenant1.getId(), "user2@tenant1.com", "User", "Two", adminRole1.getId());
            return null;
        });

        // Request users with tenant1 header
        mockMvc.perform(get("/api/users")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder("user1@tenant1.com", "user2@tenant1.com")))
                .andExpect(jsonPath("$[*].accountId", everyItem(is(tenant1.getId().intValue()))));
    }

    @Test
    @DisplayName("GET /api/users - Should return empty list for tenant with no users")
    @Transactional
    void shouldReturnEmptyListForTenantWithNoUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Should return user by ID for same tenant")
    @Transactional
    void shouldGetUserByIdForSameTenant() throws Exception {
        // Create user for tenant1
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Test", "User", adminRole1.getId());
            return user.getId();
        });

        // Request user with tenant1 header
        mockMvc.perform(get("/api/users/" + userId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId.intValue())))
                .andExpect(jsonPath("$.email", is("user@tenant1.com")))
                .andExpect(jsonPath("$.firstName", is("Test")))
                .andExpect(jsonPath("$.lastName", is("User")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Should return 404 when accessing other tenant's user")
    @Transactional
    void shouldReturn404WhenAccessingOtherTenantUser() throws Exception {
        // Create user for tenant1
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Test", "User", adminRole1.getId());
            return user.getId();
        });

        // Try to access with tenant2 header
        mockMvc.perform(get("/api/users/" + userId)
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/users - Should create user for tenant")
    @Transactional
    void shouldCreateUserForTenant() throws Exception {
        UserCreateRequest request = new UserCreateRequest();
        request.setEmail("newuser@tenant1.com");
        request.setPassword("SecurePass123!");
        request.setFirstName("New");
        request.setLastName("User");
        request.setRoleId(adminRole1.getId());

        mockMvc.perform(post("/api/users")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("newuser@tenant1.com")))
                .andExpect(jsonPath("$.firstName", is("New")))
                .andExpect(jsonPath("$.lastName", is("User")))
                .andExpect(jsonPath("$.accountId", is(tenant1.getId().intValue())))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("POST /api/users - Should reject duplicate email within same tenant")
    @Transactional
    void shouldRejectDuplicateEmailWithinTenant() throws Exception {
        // Create existing user
        executeInTenantContext(tenant1.getId(), () -> {
            createUser(tenant1.getId(), "existing@tenant1.com", "Existing", "User", adminRole1.getId());
            return null;
        });

        // Try to create user with same email
        UserCreateRequest request = new UserCreateRequest();
        request.setEmail("existing@tenant1.com");
        request.setPassword("SecurePass123!");
        request.setFirstName("Duplicate");
        request.setLastName("User");
        request.setRoleId(adminRole1.getId());

        mockMvc.perform(post("/api/users")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users - Should allow same email across different tenants")
    @Transactional
    void shouldAllowSameEmailAcrossDifferentTenants() throws Exception {
        // Create user for tenant1
        executeInTenantContext(tenant1.getId(), () -> {
            createUser(tenant1.getId(), "shared@example.com", "User", "One", adminRole1.getId());
            return null;
        });

        // Create user with same email for tenant2 (should succeed)
        UserCreateRequest request = new UserCreateRequest();
        request.setEmail("shared@example.com");
        request.setPassword("SecurePass123!");
        request.setFirstName("User");
        request.setLastName("Two");
        request.setRoleId(adminRole2.getId());

        mockMvc.perform(post("/api/users")
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("shared@example.com")))
                .andExpect(jsonPath("$.accountId", is(tenant2.getId().intValue())));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Should update user for same tenant")
    @Transactional
    void shouldUpdateUserForSameTenant() throws Exception {
        // Create user
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Original", "Name", adminRole1.getId());
            return user.getId();
        });

        // Update user
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFirstName("Updated");
        request.setLastName("Name");

        mockMvc.perform(put("/api/users/" + userId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Updated")))
                .andExpect(jsonPath("$.lastName", is("Name")));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Should return 404 when updating other tenant's user")
    @Transactional
    void shouldReturn404WhenUpdatingOtherTenantUser() throws Exception {
        // Create user for tenant1
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Test", "User", adminRole1.getId());
            return user.getId();
        });

        // Try to update with tenant2 header
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFirstName("Malicious");
        request.setLastName("Update");

        mockMvc.perform(put("/api/users/" + userId)
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Should soft delete user for same tenant")
    @Transactional
    void shouldSoftDeleteUserForSameTenant() throws Exception {
        // Create user
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Test", "User", adminRole1.getId());
            return user.getId();
        });

        // Delete user
        mockMvc.perform(delete("/api/users/" + userId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify user is soft-deleted (should not appear in active users list)
        mockMvc.perform(get("/api/users")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Should return 404 when deleting other tenant's user")
    @Transactional
    void shouldReturn404WhenDeletingOtherTenantUser() throws Exception {
        // Create user for tenant1
        Long userId = executeInTenantContext(tenant1.getId(), () -> {
            User user = createUser(tenant1.getId(), "user@tenant1.com", "Test", "User", adminRole1.getId());
            return user.getId();
        });

        // Try to delete with tenant2 header
        mockMvc.perform(delete("/api/users/" + userId)
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/users - Should require tenant header")
    void shouldRequireTenantHeader() throws Exception {
        mockMvc.perform(get("/api/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
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
