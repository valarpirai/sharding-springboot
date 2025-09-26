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

            // Resolve shard information for the tenant
            try {
                Optional<TenantShardMapping> mappingOpt = shardLookupService.findShardByTenantId(accountId);
                if (!mappingOpt.isPresent() || !mappingOpt.get().isActive()) {
                    logger.warn("No active shard mapping found for account: {}", accountId);
                    TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                        "Tenant shard configuration not found");
                    return;
                }

                TenantShardMapping mapping = mappingOpt.get();
                String shardId = mapping.getShardId();
                DataSource shardDataSource = connectionRouter.getShardDataSource(shardId, false);

                // Create TenantInfo with pre-resolved shard information
                TenantInfo tenantInfo = new TenantInfo(accountId, shardId, false, shardDataSource);

                // Set complete tenant context
                TenantContext.setTenantInfo(tenantInfo);

                logger.debug("Set shard context - tenant: {}, shard: {} for request: {}",
                    accountId, shardId, requestDescription);

            } catch (Exception e) {
                logger.error("Failed to resolve shard for account {}: {}", accountId, e.getMessage(), e);
                TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resolve tenant shard information");
                return;
            }

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