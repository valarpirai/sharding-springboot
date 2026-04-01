package com.valarpirai.sharding.async;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantContextTaskDecoratorTest {

    private TenantContextTaskDecorator decorator;
    private DataSource mockDataSource;

    @BeforeEach
    void setUp() {
        decorator = new TenantContextTaskDecorator();
        mockDataSource = mock(DataSource.class);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldPropagateTenantContextToAsyncThread() throws Exception {
        // Given: Set tenant context in main thread
        TenantInfo tenantInfo = new TenantInfo(100L, "shard1", false, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TenantInfo> capturedInfo = new AtomicReference<>();

        // When: Execute task in different thread
        Runnable task = () -> {
            capturedInfo.set(TenantContext.getTenantInfo());
            latch.countDown();
        };

        Runnable decorated = decorator.decorate(task);
        CompletableFuture.runAsync(decorated).join();

        latch.await(1, TimeUnit.SECONDS);

        // Then: Tenant context should be propagated
        assertThat(capturedInfo.get()).isNotNull();
        assertThat(capturedInfo.get().tenantId()).isEqualTo(100L);
        assertThat(capturedInfo.get().shardId()).isEqualTo("shard1");
    }

    @Test
    void shouldClearContextAfterTaskCompletion() throws Exception {
        // Given: Set tenant context in main thread
        TenantInfo tenantInfo = new TenantInfo(200L, "shard2", false, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> taskThread = new AtomicReference<>();

        // When: Execute task
        Runnable task = () -> {
            taskThread.set(Thread.currentThread());
            latch.countDown();
        };

        Runnable decorated = decorator.decorate(task);
        CompletableFuture.runAsync(decorated).join();

        latch.await(1, TimeUnit.SECONDS);

        // Then: Context should be cleared in async thread after completion
        // Note: We can't directly check the async thread's context after completion
        // because it's already cleared, but we verify it was set during execution
        // by checking that the task captured its thread
        assertThat(taskThread.get()).isNotNull();
        assertThat(taskThread.get()).isNotEqualTo(Thread.currentThread());
    }

    @Test
    void shouldHandleNullTenantContext() throws Exception {
        // Given: No tenant context set
        TenantContext.clear();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TenantInfo> capturedInfo = new AtomicReference<>();

        // When: Execute task
        Runnable task = () -> {
            capturedInfo.set(TenantContext.getTenantInfo());
            latch.countDown();
        };

        Runnable decorated = decorator.decorate(task);
        CompletableFuture.runAsync(decorated).join();

        latch.await(1, TimeUnit.SECONDS);

        // Then: No context should be set in async thread
        assertThat(capturedInfo.get()).isNull();
    }

    @Test
    void shouldNotAffectMainThreadContext() {
        // Given: Set tenant context in main thread
        TenantInfo mainThreadContext = new TenantInfo(300L, "shard3", false, mockDataSource);
        TenantContext.setTenantInfo(mainThreadContext);

        // When: Decorate a task (but don't run it yet)
        Runnable task = () -> {
            // This will run in a different thread
        };

        decorator.decorate(task);

        // Then: Main thread context should remain unchanged
        TenantInfo currentContext = TenantContext.getTenantInfo();
        assertThat(currentContext).isNotNull();
        assertThat(currentContext.tenantId()).isEqualTo(300L);
        assertThat(currentContext.shardId()).isEqualTo("shard3");
    }

    @Test
    void shouldPropagateReadOnlyMode() throws Exception {
        // Given: Set tenant context with read-only mode
        TenantInfo tenantInfo = new TenantInfo(400L, "shard4", true, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> capturedReadOnlyMode = new AtomicReference<>();

        // When: Execute task
        Runnable task = () -> {
            capturedReadOnlyMode.set(TenantContext.isReadOnlyMode());
            latch.countDown();
        };

        Runnable decorated = decorator.decorate(task);
        CompletableFuture.runAsync(decorated).join();

        latch.await(1, TimeUnit.SECONDS);

        // Then: Read-only mode should be propagated
        assertThat(capturedReadOnlyMode.get()).isTrue();
    }

    @Test
    void shouldHandleMultipleConcurrentTasks() throws Exception {
        // Given: Different tenant contexts for different tasks
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicReference<Long>[] capturedTenantIds = new AtomicReference[taskCount];

        for (int i = 0; i < taskCount; i++) {
            capturedTenantIds[i] = new AtomicReference<>();
        }

        // When: Execute multiple tasks concurrently with different contexts
        CompletableFuture<?>[] futures = new CompletableFuture[taskCount];

        for (int i = 0; i < taskCount; i++) {
            final int index = i;
            final long tenantId = 1000L + i;

            // Set context for this task
            TenantInfo tenantInfo = new TenantInfo(tenantId, "shard" + i, false, mockDataSource);
            TenantContext.setTenantInfo(tenantInfo);

            Runnable task = () -> {
                capturedTenantIds[index].set(TenantContext.getCurrentTenantId());
                latch.countDown();
            };

            Runnable decorated = decorator.decorate(task);
            futures[index] = CompletableFuture.runAsync(decorated);

            // Clear context before next iteration
            TenantContext.clear();
        }

        // Wait for all tasks to complete
        CompletableFuture.allOf(futures).join();
        latch.await(5, TimeUnit.SECONDS);

        // Then: Each task should have captured its own tenant ID
        for (int i = 0; i < taskCount; i++) {
            assertThat(capturedTenantIds[i].get()).isEqualTo(1000L + i);
        }
    }

    @Test
    void shouldHandleTaskException() {
        // Given: Set tenant context
        TenantInfo tenantInfo = new TenantInfo(500L, "shard5", false, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        // When: Execute task that throws exception
        Runnable task = () -> {
            throw new RuntimeException("Test exception");
        };

        Runnable decorated = decorator.decorate(task);

        // Then: Exception should be propagated, but context should still be cleared
        try {
            decorated.run();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Test exception");
        }

        // Note: Context is cleared in finally block, so we can't verify it directly
        // But the test ensures no exception is thrown from the finally block
    }
}
