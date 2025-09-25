package com.valarpirai.example.controller;

import com.valarpirai.example.dto.TicketCreateRequest;
import com.valarpirai.example.dto.TicketResponse;
import com.valarpirai.example.dto.TicketUpdateRequest;
import com.valarpirai.example.entity.Ticket;
import com.valarpirai.example.service.TicketService;
import com.valarpirai.sharding.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "tickets", description = "Ticket management operations")
public class TicketController {

    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "Get all tickets", description = "Retrieve all active tickets for the tenant with optional filtering")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tickets retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<TicketResponse>> getAllTickets(
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) Long statusId,
            @Parameter(description = "Filter by category") @RequestParam(required = false) String category,
            @Parameter(description = "Filter by requester ID") @RequestParam(required = false) Long requesterId,
            @Parameter(description = "Filter by responder ID") @RequestParam(required = false) Long responderId) {

        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Getting all tickets for tenant: {}", tenantId);

        List<Ticket> tickets = ticketService.getAllActiveTickets(tenantId, statusId, category, requesterId, responderId);
        List<TicketResponse> ticketResponses = tickets.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ticketResponses);
    }

    @Operation(summary = "Get ticket by ID", description = "Retrieve a specific ticket by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket found"),
        @ApiResponse(responseCode = "404", description = "Ticket not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Getting ticket {} for tenant: {}", id, tenantId);

        try {
            Ticket ticket = ticketService.getTicketById(id, tenantId);
            return ResponseEntity.ok(convertToResponse(ticket));
        } catch (RuntimeException e) {
            logger.warn("Ticket not found: {} for tenant: {}", id, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Create new ticket", description = "Create a new ticket for the tenant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Ticket created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketCreateRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Creating new ticket for tenant: {}", tenantId);

        try {
            Ticket ticket = ticketService.createTicket(request, tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(ticket));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid ticket creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Update ticket", description = "Update an existing ticket")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Ticket not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Updating ticket {} for tenant: {}", id, tenantId);

        try {
            Ticket ticket = ticketService.updateTicket(id, request, tenantId);
            return ResponseEntity.ok(convertToResponse(ticket));
        } catch (RuntimeException e) {
            logger.warn("Failed to update ticket {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Delete ticket", description = "Soft delete a ticket")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Ticket deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Ticket not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Deleting ticket {} for tenant: {}", id, tenantId);

        try {
            ticketService.deleteTicket(id, tenantId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            logger.warn("Failed to delete ticket {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Assign ticket to responder", description = "Assign a ticket to a responder")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket assigned successfully"),
        @ApiResponse(responseCode = "404", description = "Ticket not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}/assign/{responderId}")
    public ResponseEntity<TicketResponse> assignTicket(
            @PathVariable Long id,
            @PathVariable Long responderId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Assigning ticket {} to responder {} for tenant: {}", id, responderId, tenantId);

        try {
            Ticket ticket = ticketService.assignTicket(id, responderId, tenantId);
            return ResponseEntity.ok(convertToResponse(ticket));
        } catch (RuntimeException e) {
            logger.warn("Failed to assign ticket {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private TicketResponse convertToResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .accountId(ticket.getAccountId())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .requesterId(ticket.getRequesterId())
                .responderId(ticket.getResponderId())
                .statusId(ticket.getStatusId())
                .category(ticket.getCategory())
                .subcategory(ticket.getSubcategory())
                .priority(ticket.getPriority())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}