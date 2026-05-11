package com.valarpirai.sharding.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShardConfigTest {

    @Test
    void statusActiveConstant_value() {
        assertEquals("ACTIVE", ShardConfig.STATUS_ACTIVE);
    }

    @Test
    void isActive_trueWhenStatusIsACTIVE() {
        ShardConfig config = new ShardConfig();
        config.setStatus("ACTIVE");
        assertTrue(config.isActive());
    }

    @Test
    void isActive_caseInsensitive() {
        ShardConfig config = new ShardConfig();
        config.setStatus("active");
        assertTrue(config.isActive());
        config.setStatus("Active");
        assertTrue(config.isActive());
        config.setStatus("ACTIVE");
        assertTrue(config.isActive());
    }

    @Test
    void isActive_falseForMaintenance() {
        ShardConfig config = new ShardConfig();
        config.setStatus("MAINTENANCE");
        assertFalse(config.isActive());
    }

    @Test
    void isActive_falseForReadonly() {
        ShardConfig config = new ShardConfig();
        config.setStatus("READONLY");
        assertFalse(config.isActive());
    }

    @Test
    void isActive_falseForNull() {
        ShardConfig config = new ShardConfig();
        config.setStatus(null);
        assertFalse(config.isActive());
    }

    @Test
    void defaultStatus_isActive() {
        ShardConfig config = new ShardConfig();
        assertTrue(config.isActive(), "Default status should be ACTIVE");
    }
}
