package com.valarpirai.sharding.lookup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for shared behaviour defined in AbstractSqlProvider.
 * Uses a minimal concrete subclass that applies no overrides (plain defaults).
 */
class AbstractSqlProviderTest {

    // Minimal concrete implementation using all defaults — no database-specific clauses
    private static class DefaultProvider extends AbstractSqlProvider {
        @Override public String getDatabaseType() { return "Default"; }
        @Override public String getTableExistsQuery(String t) { return ""; }
        @Override public String getCurrentDatabaseFunction() { return ""; }
        @Override public boolean supports(String url) { return false; }
    }

    private final AbstractSqlProvider provider = new DefaultProvider();

    @Test
    void commonColumns_areSharedConstant() {
        String cols = AbstractSqlProvider.COMMON_COLUMNS;
        assertTrue(cols.contains("tenant_id BIGINT NOT NULL"));
        assertTrue(cols.contains("shard_id VARCHAR(255) NOT NULL"));
        assertTrue(cols.contains("region VARCHAR(255)"));
        assertTrue(cols.contains("shard_status VARCHAR(50) DEFAULT 'ACTIVE'"));
        assertTrue(cols.contains("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(cols.contains("PRIMARY KEY (tenant_id)"));
    }

    @Test
    void getTimestampWithCurrentDefaultColumn_isShared() {
        assertEquals("TIMESTAMP DEFAULT CURRENT_TIMESTAMP", provider.getTimestampWithCurrentDefaultColumn());
    }

    @Test
    void getCreateTenantShardMappingTableSql_containsCommonColumns() {
        String sql = provider.getCreateTenantShardMappingTableSql();
        assertTrue(sql.startsWith("CREATE TABLE tenant_shard_mapping ("),
                "Should use default prefix (no IF NOT EXISTS)");
        assertTrue(sql.contains(AbstractSqlProvider.COMMON_COLUMNS));
        assertTrue(sql.endsWith(")"), "Default has no table options suffix");
    }

    @Test
    void getCreateIndexesSql_producesThreeIndexes() {
        String[] indexes = provider.getCreateIndexesSql();
        assertEquals(3, indexes.length);
    }

    @Test
    void getCreateIndexesSql_defaultPrefixIsCreateIndex() {
        String[] indexes = provider.getCreateIndexesSql();
        for (String index : indexes) {
            assertTrue(index.startsWith("CREATE INDEX "),
                    "Default prefix should be 'CREATE INDEX '");
            assertFalse(index.contains("IF NOT EXISTS"),
                    "Default should not include IF NOT EXISTS");
        }
    }

    @Test
    void getCreateIndexesSql_coversExpectedColumns() {
        String[] indexes = provider.getCreateIndexesSql();
        assertEquals("CREATE INDEX idx_shard_id ON tenant_shard_mapping (shard_id)", indexes[0]);
        assertEquals("CREATE INDEX idx_shard_status ON tenant_shard_mapping (shard_status)", indexes[1]);
        assertEquals("CREATE INDEX idx_region ON tenant_shard_mapping (region)", indexes[2]);
    }

    @Test
    void tableOptions_hookAllowsSuffix() {
        AbstractSqlProvider withOptions = new DefaultProvider() {
            @Override protected String tableOptions() { return " ENGINE=InnoDB"; }
        };
        assertTrue(withOptions.getCreateTenantShardMappingTableSql().endsWith(") ENGINE=InnoDB"));
    }

    @Test
    void createTablePrefix_hookAllowsIfNotExists() {
        AbstractSqlProvider withIfNotExists = new DefaultProvider() {
            @Override protected String createTablePrefix() { return "IF NOT EXISTS "; }
        };
        assertTrue(withIfNotExists.getCreateTenantShardMappingTableSql()
                .startsWith("CREATE TABLE IF NOT EXISTS tenant_shard_mapping"));
    }

    @Test
    void createIndexPrefix_hookAllowsIfNotExists() {
        AbstractSqlProvider withIfNotExists = new DefaultProvider() {
            @Override protected String createIndexPrefix() { return "CREATE INDEX IF NOT EXISTS "; }
        };
        String[] indexes = withIfNotExists.getCreateIndexesSql();
        for (String index : indexes) {
            assertTrue(index.startsWith("CREATE INDEX IF NOT EXISTS "));
        }
    }
}
