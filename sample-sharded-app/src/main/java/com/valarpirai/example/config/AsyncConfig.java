package com.valarpirai.example.config;

import com.valarpirai.sharding.async.TenantContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous task execution.
 * Ensures tenant context is propagated to async threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Task executor for demo setup background tasks.
     * Configured with TenantContextTaskDecorator to propagate tenant context.
     */
    @Bean("demoSetupTaskExecutor")
    public TaskExecutor demoSetupTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("demo-setup-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Propagate tenant context to async threads
        executor.setTaskDecorator(new TenantContextTaskDecorator());

        executor.initialize();
        return executor;
    }
}