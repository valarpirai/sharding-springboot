package com.valarpirai.example.controller;

import com.valarpirai.example.dto.UserCreateRequest;
import com.valarpirai.example.dto.UserResponse;
import com.valarpirai.example.dto.UserUpdateRequest;
import com.valarpirai.example.entity.User;
import com.valarpirai.example.service.UserService;
import com.valarpirai.sharding.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/users")
@Tag(name = "users", description = "User management operations")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get all users", description = "Retrieve all active users for the tenant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Getting all users for tenant: {}", tenantId);

        List<User> users = userService.getAllActiveUsers(tenantId);
        List<UserResponse> userResponses = users.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(userResponses);
    }

    @Operation(summary = "Get user by ID", description = "Retrieve a specific user by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Getting user {} for tenant: {}", id, tenantId);

        try {
            User user = userService.getUserById(id, tenantId);
            return ResponseEntity.ok(convertToResponse(user));
        } catch (RuntimeException e) {
            logger.warn("User not found: {} for tenant: {}", id, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Create new user", description = "Create a new user for the tenant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Creating new user for tenant: {}", tenantId);

        try {
            User user = userService.createUser(request, tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(user));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Update user", description = "Update an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Updating user {} for tenant: {}", id, tenantId);

        try {
            User user = userService.updateUser(id, request, tenantId);
            return ResponseEntity.ok(convertToResponse(user));
        } catch (RuntimeException e) {
            logger.warn("Failed to update user {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Delete user", description = "Soft delete a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        logger.info("Deleting user {} for tenant: {}", id, tenantId);

        try {
            userService.deleteUser(id, tenantId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            logger.warn("Failed to delete user {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .accountId(user.getAccountId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .roleId(user.getRoleId())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}