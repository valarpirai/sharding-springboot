package com.valarpirai.sharding.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TenantInfo.
 */
class TenantInfoTest {

    @Test
    void testConstructorAndGetters() {
        // Given
        Long tenantId = 1001L;
        boolean readOnly = true;

        // When
        TenantInfo tenantInfo = new TenantInfo(tenantId, readOnly);

        // Then
        assertEquals(tenantId, tenantInfo.getTenantId());
        assertTrue(tenantInfo.isReadOnly());
    }

    @Test
    void testConstructorWithNullTenantId() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantInfo(null, false);
        });
    }

    @Test
    void testWithReadOnlyMode() {
        // Given
        TenantInfo original = new TenantInfo(1001L, false);

        // When
        TenantInfo readOnlyVersion = original.withReadOnlyMode(true);
        TenantInfo readWriteVersion = original.withReadOnlyMode(false);

        // Then
        assertEquals(1001L, readOnlyVersion.getTenantId());
        assertTrue(readOnlyVersion.isReadOnly());

        assertEquals(1001L, readWriteVersion.getTenantId());
        assertFalse(readWriteVersion.isReadOnly());

        // Original should be unchanged
        assertFalse(original.isReadOnly());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        TenantInfo tenantInfo1 = new TenantInfo(1001L, true);
        TenantInfo tenantInfo2 = new TenantInfo(1001L, true);
        TenantInfo tenantInfo3 = new TenantInfo(1001L, false);
        TenantInfo tenantInfo4 = new TenantInfo(2001L, true);

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
        TenantInfo tenantInfo = new TenantInfo(1001L, true);

        // When
        String result = tenantInfo.toString();

        // Then
        assertTrue(result.contains("1001"));
        assertTrue(result.contains("readOnly=true"));
    }

    @Test
    void testCreateReadOnly() {
        // Given
        Long tenantId = 1001L;

        // When
        TenantInfo tenantInfo = TenantInfo.createReadOnly(tenantId);

        // Then
        assertEquals(tenantId, tenantInfo.getTenantId());
        assertTrue(tenantInfo.isReadOnly());
    }

    @Test
    void testCreateReadWrite() {
        // Given
        Long tenantId = 1001L;

        // When
        TenantInfo tenantInfo = TenantInfo.createReadWrite(tenantId);

        // Then
        assertEquals(tenantId, tenantInfo.getTenantId());
        assertFalse(tenantInfo.isReadOnly());
    }
}