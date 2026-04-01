package com.valarpirai.sharding.async;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * TaskDecorator that propagates TenantContext across async thread boundaries.
 *
 * <p>Use this decorator with Spring's async task executors to ensure that
 * tenant context is available in @Async methods and other async operations.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * &#64;Bean
 * public TaskExecutor taskExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new TenantContextTaskDecorator());
 *     return executor;
 * }
 * </pre>
 *
 * @see TenantContext
 * @see org.springframework.scheduling.annotation.Async
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextTaskDecorator.class);

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // Capture tenant context from the calling thread
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        if (tenantInfo == null) {
            logger.trace("No tenant context to propagate for async task");
            return runnable;
        }

        logger.trace("Propagating tenant context to async task: {}", tenantInfo);

        return () -> {
            try {
                // Set the captured context in the async thread
                TenantContext.setTenantInfo(tenantInfo);
                logger.trace("Tenant context set in async task: {}", tenantInfo);

                // Execute the actual task
                runnable.run();
            } finally {
                // Always clean up the context after task completion
                TenantContext.clear();
                logger.trace("Tenant context cleared after async task completion");
            }
        };
    }
}
