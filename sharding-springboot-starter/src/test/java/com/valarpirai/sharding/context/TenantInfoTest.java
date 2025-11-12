package com.valarpirai.sharding.context;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TenantInfo.
 */
class TenantInfoTest {

    private final DataSource mockDataSource = Mockito.mock(DataSource.class);

    @Test
    void testConstructorAndGetters() {
        // Given
        Long tenantId = 1001L;
        String shardId = "shard1";
        boolean readOnly = true;

        // When
        TenantInfo tenantInfo = new TenantInfo(tenantId, shardId, readOnly, mockDataSource);

        // Then
        assertEquals(tenantId, tenantInfo.tenantId());
        assertEquals(shardId, tenantInfo.shardId());
        assertTrue(tenantInfo.readOnlyMode());
        assertEquals(mockDataSource, tenantInfo.shardDataSource());
    }

    @Test
    void testConstructorWithNullTenantId() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantInfo(null, "shard1", false, mockDataSource);
        });
    }

    @Test
    void testConstructorWithNullShardId() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantInfo(1001L, null, false, mockDataSource);
        });
    }

    @Test
    void testConstructorWithNullDataSource() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantInfo(1001L, "shard1", false, null);
        });
    }

    @Test
    void testWithReadOnlyMode() {
        // Given
        TenantInfo original = new TenantInfo(1001L, "shard1", false, mockDataSource);

        // When
        TenantInfo readOnlyVersion = original.withReadOnlyMode(true);
        TenantInfo readWriteVersion = original.withReadOnlyMode(false);

        // Then
        assertEquals(1001L, readOnlyVersion.tenantId());
        assertTrue(readOnlyVersion.readOnlyMode());

        assertEquals(1001L, readWriteVersion.tenantId());
        assertFalse(readWriteVersion.readOnlyMode());

        // Original should be unchanged
        assertFalse(original.readOnlyMode());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        TenantInfo tenantInfo1 = new TenantInfo(1001L, "shard1", true, mockDataSource);
        TenantInfo tenantInfo2 = new TenantInfo(1001L, "shard1", true, mockDataSource);
        TenantInfo tenantInfo3 = new TenantInfo(1001L, "shard1", false, mockDataSource);
        TenantInfo tenantInfo4 = new TenantInfo(2001L, "shard2", true, mockDataSource);

        // Then
        assertEquals(tenantInfo1, tenantInfo2);
        assertEquals(tenantInfo1.hashCode(), tenantInfo2.hashCode());

        assertNotEquals(tenantInfo1, tenantInfo3); // Different readOnly
        assertNotEquals(tenantInfo1, tenantInfo4); // Different tenantId

        assertNotEquals(tenantInfo1, null);
        assertNotEquals(tenantInfo1, "string");
    }

    @Test
    void testToString() {
        // Given
        TenantInfo tenantInfo = new TenantInfo(1001L, "shard1", true, mockDataSource);

        // When
        String result = tenantInfo.toString();

        // Then
        assertTrue(result.contains("1001"));
        assertTrue(result.contains("shard1"));
    }

    @Test
    void testCreateReadOnly() {
        // Given
        Long tenantId = 1001L;
        String shardId = "shard1";

        // When
        TenantInfo tenantInfo = TenantInfo.createReadOnly(tenantId, shardId, mockDataSource);

        // Then
        assertEquals(tenantId, tenantInfo.tenantId());
        assertEquals(shardId, tenantInfo.shardId());
        assertTrue(tenantInfo.readOnlyMode());
        assertEquals(mockDataSource, tenantInfo.shardDataSource());
    }

    @Test
    void testCreate() {
        // Given
        Long tenantId = 1001L;
        String shardId = "shard1";

        // When
        TenantInfo tenantInfo = TenantInfo.create(tenantId, shardId, mockDataSource);

        // Then
        assertEquals(tenantId, tenantInfo.tenantId());
        assertEquals(shardId, tenantInfo.shardId());
        assertFalse(tenantInfo.readOnlyMode());
        assertEquals(mockDataSource, tenantInfo.shardDataSource());
    }
}