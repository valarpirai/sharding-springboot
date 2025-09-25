package com.valarpirai.example.controller;

import com.valarpirai.example.dto.SignupRequest;
import com.valarpirai.example.dto.SignupResponse;
import com.valarpirai.example.service.AccountSignupService;
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

/**
 * Controller for account signup.
 * No tenant validation required as we're creating new accounts.
 */
@RestController
@RequestMapping("/api/signup")
@Tag(name = "signup", description = "Account signup and registration")
public class SignupController {

    private static final Logger logger = LoggerFactory.getLogger(SignupController.class);

    private final AccountSignupService signupService;

    public SignupController(AccountSignupService signupService) {
        this.signupService = signupService;
    }

    /**
     * Create a new account with admin user and demo environment.
     */
    @Operation(
        summary = "Create new account",
        description = "Creates a new tenant account with admin user. Automatically sets up demo environment with default roles, statuses, sample user, and sample tickets in the background."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Account created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SignupResponse.class),
                examples = @ExampleObject(
                    name = "Successful signup",
                    value = "{\n" +
                           "  \"accountId\": 1,\n" +
                           "  \"accountName\": \"Demo Company\",\n" +
                           "  \"adminEmail\": \"admin@demo.com\",\n" +
                           "  \"adminUserId\": 1,\n" +
                           "  \"adminUserName\": \"Admin User\",\n" +
                           "  \"createdAt\": \"2023-12-01T10:30:00\",\n" +
                           "  \"message\": \"Account created successfully. Demo environment is being set up in the background.\",\n" +
                           "  \"demoSetupInProgress\": true\n" +
                           "}"
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request data or account/email already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error during account creation")
    })
    @PostMapping
    public ResponseEntity<SignupResponse> createAccount(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Account signup information",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SignupRequest.class),
                examples = @ExampleObject(
                    name = "Signup request",
                    value = "{\n" +
                           "  \"accountName\": \"Demo Company\",\n" +
                           "  \"adminEmail\": \"admin@demo.com\",\n" +
                           "  \"password\": \"password123\"\n" +
                           "}"
                )
            )
        )
        @Valid @RequestBody SignupRequest request) {

        logger.info("Received signup request for account: {}", request.getAccountName());

        try {
            SignupResponse response = signupService.createAccount(request);
            logger.info("Successfully created account: {} with ID: {}",
                       response.getAccountName(), response.getAccountId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid signup request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SignupResponse.builder()
                            .message("Signup failed: " + e.getMessage())
                            .demoSetupInProgress(false)
                            .build());

        } catch (Exception e) {
            logger.error("Error during account signup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SignupResponse.builder()
                            .message("Internal server error during account creation")
                            .demoSetupInProgress(false)
                            .build());
        }
    }

    /**
     * Health check endpoint for signup service.
     */
    @Operation(
        summary = "Signup service health check",
        description = "Check if the signup service is available and ready to accept new account registrations."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy and ready"),
        @ApiResponse(responseCode = "503", description = "Service is unavailable")
    })
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Signup service is healthy and ready");
    }
}