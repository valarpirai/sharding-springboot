package com.valarpirai.sharding.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TenantContext.
 */
class TenantContextTest {

    private final DataSource mockDataSource = Mockito.mock(DataSource.class);

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void testSetAndGetTenantInfo() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);

        // When
        TenantContext.setTenantInfo(tenantInfo);

        // Then
        assertEquals(tenantInfo, TenantContext.getTenantInfo());
        assertEquals(1001L, TenantContext.getCurrentTenantId());
        assertEquals("shard1", TenantContext.getCurrentShardId());
        assertFalse(TenantContext.isReadOnlyMode());
    }

    @Test
    void testReadOnlyMode() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        // When
        TenantContext.setReadOnlyMode(true);

        // Then
        assertTrue(TenantContext.isReadOnlyMode());

        // When
        TenantContext.setReadOnlyMode(false);

        // Then
        assertFalse(TenantContext.isReadOnlyMode());
    }

    @Test
    void testClear() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", true, mockDataSource);
        TenantContext.setTenantInfo(tenantInfo);

        // When
        TenantContext.clear();

        // Then
        assertNull(TenantContext.getCurrentTenantId());
        assertNull(TenantContext.getCurrentShardId());
        assertNull(TenantContext.getTenantInfo());
        assertFalse(TenantContext.isReadOnlyMode());
    }

    @Test
    void testExecuteInTenantContextWithSupplier() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);
        String expectedResult = "test-result";

        // Ensure context is clear initially
        TenantContext.clear();

        // When
        String result = TenantContext.executeInTenantContext(tenantInfo, () -> {
            // Verify context is set within execution
            assertEquals(1001L, TenantContext.getCurrentTenantId());
            assertEquals("shard1", TenantContext.getCurrentShardId());
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        // Context should be cleared after execution
        assertNull(TenantContext.getTenantInfo());
    }

    @Test
    void testExecuteInTenantContextWithRunnable() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);
        AtomicReference<Long> capturedTenantId = new AtomicReference<>();

        // Ensure context is clear initially
        TenantContext.clear();

        // When
        TenantContext.executeInTenantContext(tenantInfo, () -> {
            // Verify context is set within execution
            capturedTenantId.set(TenantContext.getCurrentTenantId());
        });

        // Then
        assertEquals(1001L, capturedTenantId.get());
        // Context should be cleared after execution
        assertNull(TenantContext.getTenantInfo());
    }

    @Test
    void testExecuteInTenantContextPreservesExistingContext() {
        // Given
        TenantInfo existingInfo = new TenantInfo(1001L, "shard1", true, mockDataSource);
        TenantInfo newInfo = new TenantInfo(2001L, "shard2", false, mockDataSource);

        // Set existing context
        TenantContext.setTenantInfo(existingInfo);

        // When
        String result = TenantContext.executeInTenantContext(newInfo, () -> {
            // Verify new context is set
            assertEquals(2001L, TenantContext.getCurrentTenantId());
            assertEquals("shard2", TenantContext.getCurrentShardId());
            assertFalse(TenantContext.isReadOnlyMode());
            return "success";
        });

        // Then
        assertEquals("success", result);
        // Original context should be restored
        assertEquals(1001L, TenantContext.getCurrentTenantId());
        assertEquals("shard1", TenantContext.getCurrentShardId());
        assertTrue(TenantContext.isReadOnlyMode());
    }

    @Test
    void testExecuteInTenantContextWithException() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);
        RuntimeException expectedException = new RuntimeException("test exception");

        // Ensure context is clear initially
        TenantContext.clear();

        // When/Then
        RuntimeException actualException = assertThrows(RuntimeException.class, () -> {
            TenantContext.executeInTenantContext(tenantInfo, () -> {
                assertEquals(1001L, TenantContext.getCurrentTenantId());
                throw expectedException;
            });
        });

        assertEquals(expectedException, actualException);
        // Context should be cleared even after exception
        assertNull(TenantContext.getTenantInfo());
    }

    @Test
    void testThreadIsolation() throws Exception {
        // Given
        TenantInfo mainThreadInfo = new TenantInfo(1001L, "shard1", false, mockDataSource);
        TenantInfo otherThreadInfo = new TenantInfo(2001L, "shard2", false, mockDataSource);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // Set context in main thread
            TenantContext.setTenantInfo(mainThreadInfo);

            // When - execute in different thread
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Other thread should not see main thread's context
                assertNull(TenantContext.getTenantInfo());
                assertNull(TenantContext.getCurrentTenantId());

                // Set different context in other thread
                TenantContext.setTenantInfo(otherThreadInfo);
                assertEquals(2001L, TenantContext.getCurrentTenantId());
            }, executor);

            future.get();

            // Then - main thread context should be unchanged
            assertEquals(1001L, TenantContext.getCurrentTenantId());
            assertEquals("shard1", TenantContext.getCurrentShardId());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testSetTenantIdThrowsUnsupportedOperation() {
        // The deprecated setTenantId method should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            TenantContext.setTenantId(1001L);
        });
    }

    @Test
    void testGetCurrentTenantIdWhenNoContext() {
        // Given - no context set
        TenantContext.clear();

        // When/Then
        assertNull(TenantContext.getCurrentTenantId());
        assertNull(TenantContext.getCurrentShardId());
    }
}
