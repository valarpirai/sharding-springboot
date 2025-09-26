package com.valarpirai.example.filter;

import com.valarpirai.example.service.AccountValidationService;
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
 * Filter to validate tenant (account-id) from request headers.
 * This filter runs after ShardSelectorFilter which has already set the tenant context
 * and resolved shard information. It validates that the tenant context matches the
 * request header and that the tenant account is valid and active.
 *
 * Uses OncePerRequestFilter to ensure the filter is executed exactly once per request,
 * even in complex servlet forwarding scenarios.
 */
@Component
@Order(1)
public class TenantValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantValidationFilter.class);

    private final AccountValidationService accountValidationService;

    public TenantValidationFilter(AccountValidationService accountValidationService) {
        this.accountValidationService = accountValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestDescription = TenantFilterUtils.getRequestDescription(request);
        logger.debug("Processing tenant validation for request: {}", requestDescription);

        try {
            // Skip tenant validation for excluded paths
            if (shouldNotFilter(request)) {
                logger.debug("Skipping tenant validation for excluded path: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // Extract and validate tenant ID from request header
            // For tenant validation, missing tenant ID is required (error if missing)
            TenantFilterUtils.TenantValidationResult validationResult =
                TenantFilterUtils.extractAndValidateTenantId(request, accountValidationService, logger, true);

            if (!validationResult.isValid()) {
                TenantFilterUtils.sendErrorResponse(response, validationResult.getErrorStatus(), validationResult.getErrorMessage());
                return;
            }

            Long accountId = validationResult.getTenantId();

            // Tenant context is already set by ShardSelectorFilter
            // Just validate that it matches our account ID
            Long contextTenantId = TenantContext.getCurrentTenantId();
            if (contextTenantId == null || !contextTenantId.equals(accountId)) {
                logger.error("Tenant context mismatch - header: {}, context: {}", accountId, contextTenantId);
                TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Tenant context validation failed");
                return;
            }

            logger.debug("Validated tenant context for account_id: {} matches request: {}",
                accountId, requestDescription);

            // Continue with the request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error in tenant validation filter", e);
            TenantFilterUtils.sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error during tenant validation");
        } finally {
            // Tenant context clearing is handled by ShardSelectorFilter
            logger.debug("Tenant validation completed for request: {}", requestDescription);
        }
    }

    /**
     * Override shouldNotFilter to determine which requests should skip tenant validation.
     * This method is called by OncePerRequestFilter to decide if the filter should be applied.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return TenantFilterUtils.isExcludedPath(request.getRequestURI());
    }
}