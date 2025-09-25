package com.valarpirai.example.filter;

import com.valarpirai.example.service.AccountValidationService;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import com.valarpirai.sharding.routing.ConnectionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Filter to pre-resolve shard information based on tenant (account-id) from request headers.
 * This filter runs before tenant validation and sets complete shard context upfront.
 *
 * The resolved shard information is stored in TenantContext and used by RoutingDataSource
 * to make routing decisions without dynamic shard lookups during query execution.
 */
@Component
@Order(0) // Run before TenantValidationFilter (Order 1)
public class ShardSelectorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ShardSelectorFilter.class);

    private static final String ACCOUNT_ID_HEADER = "account-id";
    private static final String CONTENT_TYPE_JSON = "application/json";

    // Paths that don't require shard resolution
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/signup",           // Account signup doesn't require existing tenant
        "/swagger-ui",           // Swagger UI
        "/v3/api-docs",          // OpenAPI docs
        "/actuator"              // Health checks and monitoring
    );

    private final ShardLookupService shardLookupService;
    private final AccountValidationService accountValidationService;
    private final ConnectionRouter connectionRouter;

    public ShardSelectorFilter(ShardLookupService shardLookupService,
                              AccountValidationService accountValidationService,
                              ConnectionRouter connectionRouter) {
        this.shardLookupService = shardLookupService;
        this.accountValidationService = accountValidationService;
        this.connectionRouter = connectionRouter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Processing shard selection for request: {} {}", method, requestPath);

        try {
            // Skip shard resolution for excluded paths
            if (shouldNotFilter(request)) {
                logger.debug("Skipping shard resolution for excluded path: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }

            // Extract account-id from headers
            String accountIdHeader = request.getHeader(ACCOUNT_ID_HEADER);

            if (accountIdHeader == null || accountIdHeader.trim().isEmpty()) {
                logger.debug("No {} header found for request: {} {} - will use global DB",
                    ACCOUNT_ID_HEADER, method, requestPath);
                // Continue without setting tenant context - will use global DB
                filterChain.doFilter(request, response);
                return;
            }

            Long accountId;
            try {
                accountId = Long.valueOf(accountIdHeader.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} header value '{}' for request: {} {}",
                    ACCOUNT_ID_HEADER, accountIdHeader, method, requestPath);
                sendErrorResponse(response, HttpStatus.BAD_REQUEST,
                    "Invalid " + ACCOUNT_ID_HEADER + " header value");
                return;
            }

            // Validate that account exists and is active
            if (!accountValidationService.isAccountValid(accountId)) {
                logger.warn("Account {} not found or inactive for request: {} {}",
                    accountId, method, requestPath);
                sendErrorResponse(response, HttpStatus.NOT_FOUND,
                    "Account not found or inactive");
                return;
            }

            // Resolve shard information for the tenant
            try {
                Optional<TenantShardMapping> mappingOpt = shardLookupService.findShardByTenantId(accountId);
                if (!mappingOpt.isPresent() || !mappingOpt.get().isActive()) {
                    logger.warn("No active shard mapping found for account: {}", accountId);
                    sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                        "Tenant shard configuration not found");
                    return;
                }

                TenantShardMapping mapping = mappingOpt.get();
                String shardId = mapping.getShardId();
                DataSource shardDataSource = connectionRouter.getShardDataSource(shardId, false);

                // Create TenantInfo with pre-resolved shard information
                TenantInfo tenantInfo = new TenantInfo(accountId, shardId, false);
                tenantInfo.setShardDataSource(shardDataSource);

                // Set complete tenant context
                TenantContext.setTenantInfo(tenantInfo);

                logger.debug("Set shard context - tenant: {}, shard: {} for request: {} {}",
                    accountId, shardId, method, requestPath);

            } catch (Exception e) {
                logger.error("Failed to resolve shard for account {}: {}", accountId, e.getMessage(), e);
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resolve tenant shard information");
                return;
            }

            // Continue with the request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error in shard selector filter", e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error during shard selection");
        } finally {
            // Always clear tenant context after request processing
            TenantContext.clear();
            logger.debug("Cleared shard context after request: {} {}", method, requestPath);
        }
    }

    /**
     * Override shouldNotFilter to determine which requests should skip shard resolution.
     * This method is called by OncePerRequestFilter to decide if the filter should be applied.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestPath = request.getRequestURI();
        return isExcludedPath(requestPath);
    }

    /**
     * Check if the request path should be excluded from shard resolution.
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
}