package com.valarpirai.sharding.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TenantContext.
 */
class TenantContextTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void testSetAndGetTenantId() {
        // Given
        Long tenantId = 1001L;

        // When
        TenantContext.setTenantId(tenantId);

        // Then
        assertEquals(tenantId, TenantContext.getTenantId());
        assertTrue(TenantContext.hasTenantContext());
    }

    @Test
    void testSetAndGetTenantInfo() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, false);

        // When
        TenantContext.setTenantInfo(tenantInfo);

        // Then
        assertEquals(tenantInfo, TenantContext.getTenantInfo());
        assertEquals(1001L, TenantContext.getTenantId());
        assertFalse(TenantContext.isReadOnlyMode());
    }

    @Test
    void testReadOnlyMode() {
        // Given
        TenantContext.setTenantId(1001L);

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
        TenantContext.setTenantId(1001L);
        TenantContext.setReadOnlyMode(true);

        // When
        TenantContext.clear();

        // Then
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getTenantInfo());
        assertFalse(TenantContext.hasTenantContext());
        assertFalse(TenantContext.isReadOnlyMode());
    }

    @Test
    void testExecuteInTenantContextWithSupplier() {
        // Given
        Long tenantId = 1001L;
        String expectedResult = "test-result";

        // Ensure context is clear initially
        TenantContext.clear();

        // When
        String result = TenantContext.executeInTenantContext(tenantId, () -> {
            // Verify context is set within execution
            assertEquals(tenantId, TenantContext.getTenantId());
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        // Context should be cleared after execution
        assertFalse(TenantContext.hasTenantContext());
    }

    @Test
    void testExecuteInTenantContextWithRunnable() {
        // Given
        Long tenantId = 1001L;
        AtomicReference<Long> capturedTenantId = new AtomicReference<>();

        // Ensure context is clear initially
        TenantContext.clear();

        // When
        TenantContext.executeInTenantContext(tenantId, () -> {
            // Verify context is set within execution
            capturedTenantId.set(TenantContext.getTenantId());
        });

        // Then
        assertEquals(tenantId, capturedTenantId.get());
        // Context should be cleared after execution
        assertFalse(TenantContext.hasTenantContext());
    }

    @Test
    void testExecuteInTenantContextPreservesExistingContext() {
        // Given
        Long existingTenantId = 1001L;
        Long newTenantId = 2001L;

        // Set existing context
        TenantContext.setTenantId(existingTenantId);
        TenantContext.setReadOnlyMode(true);

        // When
        String result = TenantContext.executeInTenantContext(newTenantId, () -> {
            // Verify new context is set
            assertEquals(newTenantId, TenantContext.getTenantId());
            assertFalse(TenantContext.isReadOnlyMode()); // Should reset to default
            return "success";
        });

        // Then
        assertEquals("success", result);
        // Original context should be restored
        assertEquals(existingTenantId, TenantContext.getTenantId());
        assertTrue(TenantContext.isReadOnlyMode());
    }

    @Test
    void testExecuteInTenantContextWithException() {
        // Given
        Long tenantId = 1001L;
        RuntimeException expectedException = new RuntimeException("test exception");

        // Ensure context is clear initially
        TenantContext.clear();

        // When/Then
        RuntimeException actualException = assertThrows(RuntimeException.class, () -> {
            TenantContext.executeInTenantContext(tenantId, () -> {
                assertEquals(tenantId, TenantContext.getTenantId());
                throw expectedException;
            });
        });

        assertEquals(expectedException, actualException);
        // Context should be cleared even after exception
        assertFalse(TenantContext.hasTenantContext());
    }

    @Test
    void testThreadIsolation() throws Exception {
        // Given
        Long mainThreadTenantId = 1001L;
        Long otherThreadTenantId = 2001L;
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // Set context in main thread
            TenantContext.setTenantId(mainThreadTenantId);

            // When - execute in different thread
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Other thread should not see main thread's context
                assertFalse(TenantContext.hasTenantContext());
                assertNull(TenantContext.getTenantId());

                // Set different context in other thread
                TenantContext.setTenantId(otherThreadTenantId);
                assertEquals(otherThreadTenantId, TenantContext.getTenantId());
            }, executor);

            future.get();

            // Then - main thread context should be unchanged
            assertEquals(mainThreadTenantId, TenantContext.getTenantId());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testGetTenantIdOrThrow() {
        // Given - no context set
        TenantContext.clear();

        // When/Then - should throw exception
        assertThrows(IllegalStateException.class, () -> {
            TenantContext.getTenantIdOrThrow("Test operation requires tenant context");
        });

        // Given - context set
        Long tenantId = 1001L;
        TenantContext.setTenantId(tenantId);

        // When/Then - should return tenant ID
        assertEquals(tenantId, TenantContext.getTenantIdOrThrow("Test operation"));
    }

    @Test
    void testToString() {
        // Given
        TenantContext.clear();

        // When - no context
        String result1 = TenantContext.toString();

        // Then
        assertTrue(result1.contains("No tenant context"));

        // Given - with context
        TenantContext.setTenantId(1001L);
        TenantContext.setReadOnlyMode(true);

        // When
        String result2 = TenantContext.toString();

        // Then
        assertTrue(result2.contains("1001"));
        assertTrue(result2.contains("readOnly=true"));
    }
}