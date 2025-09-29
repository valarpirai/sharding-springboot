package com.valarpirai.example.filter;

import com.valarpirai.example.service.AccountValidationService;
import com.valarpirai.sharding.lookup.ShardUtils;
import com.valarpirai.sharding.context.TenantContext;
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
import java.io.IOException;

/**
 * Filter to pre-resolve shard information based on tenant (account-id) from request headers.
 * This filter runs before tenant validation and sets complete shard context upfront.
 *
 * The resolved shard information is stored in TenantContext and used by RoutingDataSource
 * to make routing decisions without dynamic shard lookups during query execution.
 */
@Component
@Order(0)
public class ShardSelectorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ShardSelectorFilter.class);

    private final ShardUtils shardUtils;
    private final AccountValidationService accountValidationService;

    public ShardSelectorFilter(ShardUtils shardUtils,
                               AccountValidationService accountValidationService) {
        this.shardUtils = shardUtils;
        this.accountValidationService = accountValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestDescription = TenantFilterUtils.getRequestDescription(request);
        logger.debug("Processing shard selection for request: {}", requestDescription);

        try {
            // Skip shard resolution for excluded paths
            if (shouldNotFilter(request)) {
                logger.debug("Skipping shard resolution for excluded path: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // Extract and validate tenant ID from request header
            // For shard selector, missing tenant ID is allowed (will use global DB)
            TenantFilterUtils.TenantValidationResult validationResult =
                    TenantFilterUtils.extractAndValidateTenantId(request, accountValidationService, logger, false);

            if (!validationResult.isValid()) {
                TenantFilterUtils.sendErrorResponse(response, validationResult.getErrorStatus(), validationResult.getErrorMessage());
                return;
            }

            Long accountId = validationResult.getTenantId();
            if (accountId == null) {
                // No tenant ID provided - continue without setting tenant context (will use global DB)
                logger.debug("No tenant ID provided for request: {} - will use global DB", requestDescription);
                filterChain.doFilter(request, response);
                return;
            }

            // Resolve shard information for the tenant using ShardUtils
            boolean resolved = shardUtils.resolveAndSetTenantContext(accountId, false);
            if (!resolved) {
                logger.warn("Failed to resolve shard for account: {}", accountId);
                TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                        "Tenant shard configuration not found or inactive");
                return;
            }

            logger.debug("Set shard context for tenant: {} for request: {}", accountId, requestDescription);

            // Continue with the request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error in shard selector filter", e);
            TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error during shard selection");
        } finally {
            // Always clear tenant context after request processing
            TenantContext.clear();
            logger.debug("Cleared shard context after request: {}", requestDescription);
        }
    }

    /**
     * Override shouldNotFilter to determine which requests should skip shard resolution.
     * This method is called by OncePerRequestFilter to decide if the filter should be applied.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return TenantFilterUtils.isExcludedPath(request.getRequestURI());
    }
}
