package com.valarpirai.sharding.lookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class H2SqlProviderTest {

    private H2SqlProvider provider;

    @BeforeEach
    void setUp() {
        provider = new H2SqlProvider();
    }

    @Test
    void getDatabaseType() {
        assertEquals("H2", provider.getDatabaseType());
    }

    @Test
    void getTableExistsQuery_usesUpperCaseInfoSchema() {
        String query = provider.getTableExistsQuery("tenant_shard_mapping");
        assertTrue(query.contains("INFORMATION_SCHEMA.TABLES"));
        assertTrue(query.contains("UPPER(TABLE_NAME)"));
        assertTrue(query.contains("UPPER('tenant_shard_mapping')"));
    }

    @Test
    void getCurrentDatabaseFunction() {
        assertEquals("DATABASE()", provider.getCurrentDatabaseFunction());
    }

    @Test
    void getTimestampWithCurrentDefaultColumn_inheritedFromBase() {
        assertEquals("TIMESTAMP DEFAULT CURRENT_TIMESTAMP", provider.getTimestampWithCurrentDefaultColumn());
    }

    @Test
    void getCreateTenantShardMappingTableSql_usesIfNotExists() {
        String sql = provider.getCreateTenantShardMappingTableSql();
        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS tenant_shard_mapping ("),
                "H2 should include IF NOT EXISTS");
    }

    @Test
    void getCreateTenantShardMappingTableSql_containsCommonColumns() {
        String sql = provider.getCreateTenantShardMappingTableSql();
        assertTrue(sql.contains("tenant_id BIGINT NOT NULL"));
        assertTrue(sql.contains("shard_id VARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("region VARCHAR(255)"));
        assertTrue(sql.contains("shard_status VARCHAR(50) DEFAULT 'ACTIVE'"));
        assertTrue(sql.contains("PRIMARY KEY (tenant_id)"));
    }

    @Test
    void getCreateTenantShardMappingTableSql_hasNoMySQLOptions() {
        String sql = provider.getCreateTenantShardMappingTableSql();
        assertFalse(sql.contains("ENGINE=InnoDB"));
        assertFalse(sql.contains("utf8mb4"));
    }

    @Test
    void getCreateIndexesSql_usesIfNotExists() {
        String[] indexes = provider.getCreateIndexesSql();
        assertEquals(3, indexes.length);
        for (String index : indexes) {
            assertTrue(index.startsWith("CREATE INDEX IF NOT EXISTS "),
                    "H2 indexes should use IF NOT EXISTS: " + index);
        }
    }

    @Test
    void getCreateIndexesSql_coversExpectedColumns() {
        String[] indexes = provider.getCreateIndexesSql();
        assertEquals("CREATE INDEX IF NOT EXISTS idx_shard_id ON tenant_shard_mapping (shard_id)", indexes[0]);
        assertEquals("CREATE INDEX IF NOT EXISTS idx_shard_status ON tenant_shard_mapping (shard_status)", indexes[1]);
        assertEquals("CREATE INDEX IF NOT EXISTS idx_region ON tenant_shard_mapping (region)", indexes[2]);
    }

    @Test
    void supports_h2Urls() {
        assertTrue(provider.supports("jdbc:h2:mem:testdb"));
        assertTrue(provider.supports("jdbc:h2:file:/data/sample"));
        assertTrue(provider.supports("JDBC:H2:MEM:TESTDB"));
    }

    @Test
    void supports_rejectsNonH2Urls() {
        assertFalse(provider.supports("jdbc:mysql://localhost:3306/db"));
        assertFalse(provider.supports("jdbc:postgresql://localhost:5432/db"));
        assertFalse(provider.supports(null));
        assertFalse(provider.supports(""));
    }
}
