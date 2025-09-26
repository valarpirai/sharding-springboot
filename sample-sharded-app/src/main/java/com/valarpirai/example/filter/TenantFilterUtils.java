package com.valarpirai.example.filter;

import com.valarpirai.example.service.AccountValidationService;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class containing shared logic for tenant-related filters.
 * Centralizes common validation, error handling, and path exclusion logic
 * to eliminate code duplication between ShardSelectorFilter and TenantValidationFilter.
 */
@Component
public class TenantFilterUtils {

    public static final String ACCOUNT_ID_HEADER = "account-id";
    public static final String CONTENT_TYPE_JSON = "application/json";

    // Paths that don't require tenant validation or shard resolution
    public static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/signup",           // Account signup doesn't require existing tenant
        "/swagger-ui",           // Swagger UI
        "/v3/api-docs",          // OpenAPI docs
        "/actuator"              // Health checks and monitoring
    );

    /**
     * Result of tenant ID extraction and validation.
     */
    public static class TenantValidationResult {
        private final boolean valid;
        private final Long tenantId;
        private final HttpStatus errorStatus;
        private final String errorMessage;

        public TenantValidationResult(Long tenantId) {
            this.valid = true;
            this.tenantId = tenantId;
            this.errorStatus = null;
            this.errorMessage = null;
        }

        public TenantValidationResult(HttpStatus errorStatus, String errorMessage) {
            this.valid = false;
            this.tenantId = null;
            this.errorStatus = errorStatus;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return valid; }
        public Long getTenantId() { return tenantId; }
        public HttpStatus getErrorStatus() { return errorStatus; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Extract and validate tenant ID from request header.
     *
     * @param request HTTP request
     * @param accountValidationService Service to validate account exists and is active
     * @param logger Logger for debugging and warnings
     * @param requireTenantId If true, missing tenant ID is an error; if false, it's allowed
     * @return TenantValidationResult with tenant ID or error details
     */
    public static TenantValidationResult extractAndValidateTenantId(HttpServletRequest request,
                                                                   AccountValidationService accountValidationService,
                                                                   Logger logger,
                                                                   boolean requireTenantId) {
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        // Extract account-id from headers
        String accountIdHeader = request.getHeader(ACCOUNT_ID_HEADER);

        if (accountIdHeader == null || accountIdHeader.trim().isEmpty()) {
            if (requireTenantId) {
                logger.warn("Missing {} header for request: {} {}", ACCOUNT_ID_HEADER, method, requestPath);
                return new TenantValidationResult(HttpStatus.BAD_REQUEST,
                    "Missing required header: " + ACCOUNT_ID_HEADER);
            } else {
                logger.debug("No {} header found for request: {} {} - proceeding without tenant context",
                    ACCOUNT_ID_HEADER, method, requestPath);
                return new TenantValidationResult((Long) null);
            }
        }

        Long accountId;
        try {
            accountId = Long.valueOf(accountIdHeader.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} header value '{}' for request: {} {}",
                ACCOUNT_ID_HEADER, accountIdHeader, method, requestPath);
            return new TenantValidationResult(HttpStatus.BAD_REQUEST,
                "Invalid " + ACCOUNT_ID_HEADER + " header value");
        }

        // Validate that account exists and is active
        if (!accountValidationService.isAccountValid(accountId)) {
            logger.warn("Account {} not found or inactive for request: {} {}",
                accountId, method, requestPath);
            return new TenantValidationResult(HttpStatus.NOT_FOUND,
                "Account not found or inactive");
        }

        logger.debug("Successfully validated tenant ID: {} for request: {} {}",
            accountId, method, requestPath);
        return new TenantValidationResult(accountId);
    }

    /**
     * Check if the request path should be excluded from tenant processing.
     */
    public static boolean isExcludedPath(String requestPath) {
        return EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith);
    }

    /**
     * Send standardized JSON error response.
     */
    public static void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = String.format(
            "{\"error\": \"%s\", \"message\": \"%s\", \"status\": %d}",
            status.getReasonPhrase(),
            message,
            status.value()
        );

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    /**
     * Get descriptive name for logging.
     */
    public static String getRequestDescription(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }
}