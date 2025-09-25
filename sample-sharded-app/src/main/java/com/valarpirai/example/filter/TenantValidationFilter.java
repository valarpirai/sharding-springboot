package com.valarpirai.example.filter;

import com.valarpirai.example.repository.AccountRepository;
import com.valarpirai.sharding.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter to validate tenant (account-id) from request headers and set tenant context.
 * This filter runs before any business logic to ensure proper tenant isolation.
 */
@Component
@Order(1)
public class TenantValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TenantValidationFilter.class);

    private static final String ACCOUNT_ID_HEADER = "account-id";
    private static final String CONTENT_TYPE_JSON = "application/json";

    // Paths that don't require tenant validation
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/signup",           // Account signup doesn't require existing tenant
        "/api/auth/login",       // Login may not have tenant context initially
        "/swagger-ui",           // Swagger UI
        "/v3/api-docs",          // OpenAPI docs
        "/actuator"              // Health checks and monitoring
    );

    private final AccountRepository accountRepository;

    public TenantValidationFilter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestPath = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        logger.debug("Processing request: {} {}", method, requestPath);

        try {
            // Skip tenant validation for excluded paths
            if (isExcludedPath(requestPath)) {
                logger.debug("Skipping tenant validation for excluded path: {}", requestPath);
                chain.doFilter(request, response);
                return;
            }

            // Extract account-id from headers
            String accountIdHeader = httpRequest.getHeader(ACCOUNT_ID_HEADER);

            if (accountIdHeader == null || accountIdHeader.trim().isEmpty()) {
                logger.warn("Missing {} header for request: {} {}", ACCOUNT_ID_HEADER, method, requestPath);
                sendErrorResponse(httpResponse, HttpStatus.BAD_REQUEST,
                    "Missing required header: " + ACCOUNT_ID_HEADER);
                return;
            }

            Long accountId;
            try {
                accountId = Long.valueOf(accountIdHeader.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} header value '{}' for request: {} {}",
                    ACCOUNT_ID_HEADER, accountIdHeader, method, requestPath);
                sendErrorResponse(httpResponse, HttpStatus.BAD_REQUEST,
                    "Invalid " + ACCOUNT_ID_HEADER + " header value");
                return;
            }

            // Validate that account exists and is active
            if (!accountRepository.existsByIdAndDeletedFalse(accountId)) {
                logger.warn("Account {} not found or inactive for request: {} {}",
                    accountId, method, requestPath);
                sendErrorResponse(httpResponse, HttpStatus.NOT_FOUND,
                    "Account not found or inactive");
                return;
            }

            // Set tenant context for the sharding library
            TenantContext.setTenantId(accountId);
            logger.debug("Set tenant context to account_id: {} for request: {} {}",
                accountId, method, requestPath);

            // Continue with the request
            chain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error in tenant validation filter", e);
            sendErrorResponse(httpResponse, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error during tenant validation");
        } finally {
            // Always clear tenant context after request processing
            TenantContext.clear();
            logger.debug("Cleared tenant context after request: {} {}", method, requestPath);
        }
    }

    /**
     * Check if the request path should be excluded from tenant validation.
     */
    private boolean isExcludedPath(String requestPath) {
        return EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith);
    }

    /**
     * Send JSON error response.
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message)
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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Initialized TenantValidationFilter");
    }

    @Override
    public void destroy() {
        logger.info("Destroyed TenantValidationFilter");
    }
}