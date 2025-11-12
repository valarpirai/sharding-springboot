package com.valarpirai.example.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valarpirai.example.dto.TicketCreateRequest;
import com.valarpirai.example.dto.TicketUpdateRequest;
import com.valarpirai.example.entity.global.Account;
import com.valarpirai.example.entity.sharded.Role;
import com.valarpirai.example.entity.sharded.Status;
import com.valarpirai.example.entity.sharded.Ticket;
import com.valarpirai.example.entity.sharded.User;
import com.valarpirai.example.integration.BaseIntegrationTest;
import com.valarpirai.example.repository.global.AccountRepository;
import com.valarpirai.example.repository.sharded.*;
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
 * API integration tests for TicketController.
 * Tests ticket management endpoints with multi-tenant isolation.
 */
@AutoConfigureMockMvc
class TicketControllerApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StatusRepository statusRepository;

    private Account tenant1;
    private Account tenant2;
    private User requester1;
    private User requester2;
    private Status openStatus1;
    private Status openStatus2;

    @BeforeEach
    void setUp() {
        // Create test tenants
        tenant1 = createAccount("Ticket Test Tenant 1", "ticket1@test.com");
        tenant2 = createAccount("Ticket Test Tenant 2", "ticket2@test.com");

        // Setup shard mappings
        tenantShardMappingRepository.createMapping(tenant1.getId(), "shard1", "us-east-1");
        tenantShardMappingRepository.createMapping(tenant2.getId(), "shard2", "us-west-2");

        // Create roles and users for tenant1
        requester1 = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Role role = createRole(tenant1.getId(), "REQUESTER", 0xFFFFL);
            openStatus1 = createStatus(tenant1.getId(), "Open", true);
            return createUser(tenant1.getId(), "requester1@tenant1.com", "Requester", "One", role.getId());
        });

        // Create roles and users for tenant2
        requester2 = TenantContext.executeInTenantContext(tenant2.getId(), () -> {
            Role role = createRole(tenant2.getId(), "REQUESTER", 0xFFFFL);
            openStatus2 = createStatus(tenant2.getId(), "Open", true);
            return createUser(tenant2.getId(), "requester2@tenant2.com", "Requester", "Two", role.getId());
        });
    }

    @Test
    @DisplayName("GET /api/tickets - Should return tickets for tenant")
    @Transactional
    void shouldGetTicketsForTenant() throws Exception {
        // Create tickets for tenant1
        TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            createTicket(tenant1.getId(), "Ticket 1", requester1.getId(), openStatus1.getId());
            createTicket(tenant1.getId(), "Ticket 2", requester1.getId(), openStatus1.getId());
            createTicket(tenant1.getId(), "Ticket 3", requester1.getId(), openStatus1.getId());
            return null;
        });

        // Request tickets with tenant1 header
        mockMvc.perform(get("/api/tickets")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].subject", containsInAnyOrder("Ticket 1", "Ticket 2", "Ticket 3")))
                .andExpect(jsonPath("$[*].accountId", everyItem(is(tenant1.getId().intValue()))));
    }

    @Test
    @DisplayName("GET /api/tickets/{id} - Should return ticket by ID for same tenant")
    @Transactional
    void shouldGetTicketByIdForSameTenant() throws Exception {
        // Create ticket for tenant1
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Test Ticket", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Request ticket with tenant1 header
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(ticketId.intValue())))
                .andExpect(jsonPath("$.subject", is("Test Ticket")))
                .andExpect(jsonPath("$.accountId", is(tenant1.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/tickets/{id} - Should return 404 when accessing other tenant's ticket")
    @Transactional
    void shouldReturn404WhenAccessingOtherTenantTicket() throws Exception {
        // Create ticket for tenant1
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Private Ticket", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Try to access with tenant2 header
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/tickets - Should create ticket for tenant")
    @Transactional
    void shouldCreateTicketForTenant() throws Exception {
        TicketCreateRequest request = new TicketCreateRequest();
        request.setSubject("New Ticket");
        request.setDescription("This is a new ticket");
        request.setRequesterId(requester1.getId());
        request.setStatusId(openStatus1.getId());
        request.setPriority("HIGH");

        mockMvc.perform(post("/api/tickets")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject", is("New Ticket")))
                .andExpect(jsonPath("$.description", is("This is a new ticket")))
                .andExpect(jsonPath("$.priority", is("HIGH")))
                .andExpect(jsonPath("$.accountId", is(tenant1.getId().intValue())));
    }

    @Test
    @DisplayName("POST /api/tickets - Should enforce requester belongs to same tenant")
    @Transactional
    void shouldEnforceRequesterBelongsToSameTenant() throws Exception {
        TicketCreateRequest request = new TicketCreateRequest();
        request.setSubject("Cross-tenant Ticket");
        request.setDescription("Attempting to use requester from another tenant");
        request.setRequesterId(requester2.getId()); // Tenant 2's requester
        request.setStatusId(openStatus1.getId());
        request.setPriority("MEDIUM");

        mockMvc.perform(post("/api/tickets")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/tickets/{id} - Should update ticket for same tenant")
    @Transactional
    void shouldUpdateTicketForSameTenant() throws Exception {
        // Create ticket
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Original Subject", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Update ticket
        TicketUpdateRequest request = new TicketUpdateRequest();
        request.setSubject("Updated Subject");
        request.setDescription("Updated description");
        request.setPriority("HIGH");

        mockMvc.perform(put("/api/tickets/" + ticketId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is("Updated Subject")))
                .andExpect(jsonPath("$.description", is("Updated description")))
                .andExpect(jsonPath("$.priority", is("HIGH")));
    }

    @Test
    @DisplayName("PUT /api/tickets/{id} - Should return 404 when updating other tenant's ticket")
    @Transactional
    void shouldReturn404WhenUpdatingOtherTenantTicket() throws Exception {
        // Create ticket for tenant1
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Secure Ticket", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Try to update with tenant2 header
        TicketUpdateRequest request = new TicketUpdateRequest();
        request.setSubject("Malicious Update");

        mockMvc.perform(put("/api/tickets/" + ticketId)
                        .header("account-id", tenant2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/tickets/{id} - Should soft delete ticket for same tenant")
    @Transactional
    void shouldSoftDeleteTicketForSameTenant() throws Exception {
        // Create ticket
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Ticket to Delete", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Delete ticket
        mockMvc.perform(delete("/api/tickets/" + ticketId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify ticket is soft-deleted
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/tickets/requester/{requesterId} - Should get tickets by requester")
    @Transactional
    void shouldGetTicketsByRequester() throws Exception {
        // Create tickets for requester1
        TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            createTicket(tenant1.getId(), "Ticket A", requester1.getId(), openStatus1.getId());
            createTicket(tenant1.getId(), "Ticket B", requester1.getId(), openStatus1.getId());
            return null;
        });

        // Get tickets by requester
        mockMvc.perform(get("/api/tickets/requester/" + requester1.getId())
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].requesterId", everyItem(is(requester1.getId().intValue()))));
    }

    @Test
    @DisplayName("GET /api/tickets/status/{statusId} - Should get tickets by status")
    @Transactional
    void shouldGetTicketsByStatus() throws Exception {
        // Create tickets with different statuses
        TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            createTicket(tenant1.getId(), "Open Ticket 1", requester1.getId(), openStatus1.getId());
            createTicket(tenant1.getId(), "Open Ticket 2", requester1.getId(), openStatus1.getId());

            Status closedStatus = createStatus(tenant1.getId(), "Closed", false);
            createTicket(tenant1.getId(), "Closed Ticket", requester1.getId(), closedStatus.getId());
            return null;
        });

        // Get open tickets
        mockMvc.perform(get("/api/tickets/status/" + openStatus1.getId())
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].statusId", everyItem(is(openStatus1.getId().intValue()))));
    }

    @Test
    @DisplayName("GET /api/tickets/priority/{priority} - Should get tickets by priority")
    @Transactional
    void shouldGetTicketsByPriority() throws Exception {
        // Create tickets with different priorities
        TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket high1 = createTicket(tenant1.getId(), "High 1", requester1.getId(), openStatus1.getId());
            high1.setPriority("HIGH");
            ticketRepository.save(high1);

            Ticket high2 = createTicket(tenant1.getId(), "High 2", requester1.getId(), openStatus1.getId());
            high2.setPriority("HIGH");
            ticketRepository.save(high2);

            Ticket low = createTicket(tenant1.getId(), "Low", requester1.getId(), openStatus1.getId());
            low.setPriority("LOW");
            ticketRepository.save(low);
            return null;
        });

        // Get high priority tickets
        mockMvc.perform(get("/api/tickets/priority/HIGH")
                        .header("account-id", tenant1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].priority", everyItem(is("HIGH"))));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/assign - Should assign ticket to agent")
    @Transactional
    void shouldAssignTicketToAgent() throws Exception {
        // Create agent user
        Long agentId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Role agentRole = createRole(tenant1.getId(), "AGENT", 0xFFFFFL);
            User agent = createUser(tenant1.getId(), "agent@tenant1.com", "Agent", "One", agentRole.getId());
            return agent.getId();
        });

        // Create ticket
        Long ticketId = TenantContext.executeInTenantContext(tenant1.getId(), () -> {
            Ticket ticket = createTicket(tenant1.getId(), "Unassigned Ticket", requester1.getId(), openStatus1.getId());
            return ticket.getId();
        });

        // Assign ticket
        mockMvc.perform(post("/api/tickets/" + ticketId + "/assign")
                        .header("account-id", tenant1.getId())
                        .param("agentId", agentId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responderId", is(agentId.intValue())));
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
