package com.valarpirai.example.controller;

import com.valarpirai.example.dto.LoginRequest;
import com.valarpirai.example.dto.LoginResponse;
import com.valarpirai.example.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "authentication", description = "User authentication operations")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "User login",
        description = "Authenticate user and return access token. Requires account-id header to specify tenant."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class),
                examples = @ExampleObject(
                    name = "Successful login",
                    value = "{\n" +
                           "  \"token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\n" +
                           "  \"tokenType\": \"Bearer\",\n" +
                           "  \"expiresIn\": 3600,\n" +
                           "  \"userId\": 1,\n" +
                           "  \"email\": \"admin@demo.com\",\n" +
                           "  \"fullName\": \"Admin User\",\n" +
                           "  \"roleId\": 1,\n" +
                           "  \"accountId\": 1\n" +
                           "}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or account mismatch"),
        @ApiResponse(responseCode = "403", description = "User account inactive"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @RequestHeader("account-id") Long accountId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User login credentials",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginRequest.class),
                examples = @ExampleObject(
                    name = "Login request",
                    value = "{\n" +
                           "  \"email\": \"admin@demo.com\",\n" +
                           "  \"password\": \"password123\"\n" +
                           "}"
                )
            )
        )
        @Valid @RequestBody LoginRequest request) {

        logger.info("Login attempt for email: {} in account: {}", request.getEmail(), accountId);

        try {
            LoginResponse response = authService.login(request, accountId);
            logger.info("Successful login for user: {} in account: {}", request.getEmail(), accountId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid login attempt: {} for account: {}", e.getMessage(), accountId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .message("Invalid credentials or account mismatch")
                            .build());

        } catch (SecurityException e) {
            logger.warn("Inactive user login attempt: {} for account: {}", e.getMessage(), accountId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(LoginResponse.builder()
                            .message("User account is inactive")
                            .build());

        } catch (Exception e) {
            logger.error("Error during login for account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .message("Internal server error during authentication")
                            .build());
        }
    }

    @Operation(
        summary = "Validate token",
        description = "Validate JWT token and return user information"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "401", description = "Token is invalid or expired"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/validate")
    public ResponseEntity<LoginResponse> validateToken(
        @RequestHeader("Authorization") String authorizationHeader) {

        logger.debug("Token validation request received");

        try {
            if (!authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .message("Invalid authorization header format")
                                .build());
            }

            String token = authorizationHeader.substring(7);
            LoginResponse response = authService.validateToken(token);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .message("Invalid or expired token")
                            .build());
        }
    }

    @Operation(
        summary = "Refresh token",
        description = "Refresh JWT token to extend expiration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Token is invalid or expired"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
        @RequestHeader("Authorization") String authorizationHeader) {

        logger.debug("Token refresh request received");

        try {
            if (!authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .message("Invalid authorization header format")
                                .build());
            }

            String token = authorizationHeader.substring(7);
            LoginResponse response = authService.refreshToken(token);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .message("Invalid or expired token")
                            .build());
        }
    }
}