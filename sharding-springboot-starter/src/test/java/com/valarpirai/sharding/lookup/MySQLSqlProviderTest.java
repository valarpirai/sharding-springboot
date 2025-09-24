package com.valarpirai.sharding.lookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MySQLSqlProvider.
 */
class MySQLSqlProviderTest {

    private MySQLSqlProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MySQLSqlProvider();
    }

    @Test
    void testGetDatabaseType() {
        assertEquals("MySQL", provider.getDatabaseType());
    }

    @Test
    void testGetTableExistsQuery() {
        // Given
        String tableName = "test_table";

        // When
        String query = provider.getTableExistsQuery(tableName);

        // Then
        assertTrue(query.contains("information_schema.tables"));
        assertTrue(query.contains("DATABASE()"));
        assertTrue(query.contains(tableName));
        assertEquals("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'test_table'", query);
    }

    @Test
    void testGetCreateTenantShardMappingTableSql() {
        // When
        String sql = provider.getCreateTenantShardMappingTableSql();

        // Then
        assertTrue(sql.contains("CREATE TABLE tenant_shard_mapping"));
        assertTrue(sql.contains("tenant_id BIGINT NOT NULL"));
        assertTrue(sql.contains("shard_id VARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("region VARCHAR(255)"));
        assertTrue(sql.contains("shard_status VARCHAR(50) DEFAULT 'ACTIVE'"));
        assertTrue(sql.contains("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("PRIMARY KEY (tenant_id)"));
        assertTrue(sql.contains("ENGINE=InnoDB"));
        assertTrue(sql.contains("utf8mb4"));
    }

    @Test
    void testGetCreateIndexesSql() {
        // When
        String[] indexes = provider.getCreateIndexesSql();

        // Then
        assertEquals(3, indexes.length);

        assertEquals("CREATE INDEX idx_shard_id ON tenant_shard_mapping (shard_id)", indexes[0]);
        assertEquals("CREATE INDEX idx_shard_status ON tenant_shard_mapping (shard_status)", indexes[1]);
        assertEquals("CREATE INDEX idx_region ON tenant_shard_mapping (region)", indexes[2]);
    }

    @Test
    void testGetCurrentDatabaseFunction() {
        // When
        String function = provider.getCurrentDatabaseFunction();

        // Then
        assertEquals("DATABASE()", function);
    }

    @Test
    void testGetTimestampWithCurrentDefaultColumn() {
        // When
        String column = provider.getTimestampWithCurrentDefaultColumn();

        // Then
        assertEquals("TIMESTAMP DEFAULT CURRENT_TIMESTAMP", column);
    }

    @Test
    void testSupportsWithMySQLUrls() {
        // Test various MySQL URL formats
        assertTrue(provider.supports("jdbc:mysql://localhost:3306/testdb"));
        assertTrue(provider.supports("jdbc:mysql://host:3306/db"));
        assertTrue(provider.supports("JDBC:MYSQL://HOST:3306/DB")); // Case insensitive
        assertTrue(provider.supports("jdbc:mysql://host/db?useSSL=false"));
    }

    @Test
    void testSupportsWithMariaDBUrls() {
        // Test MariaDB URLs (should also be supported)
        assertTrue(provider.supports("jdbc:mariadb://localhost:3306/testdb"));
        assertTrue(provider.supports("jdbc:mariadb://host:3306/db"));
        assertTrue(provider.supports("JDBC:MARIADB://HOST:3306/DB")); // Case insensitive
    }

    @Test
    void testSupportsWithNonMySQLUrls() {
        // Test non-MySQL URLs
        assertFalse(provider.supports("jdbc:postgresql://localhost:5432/testdb"));
        assertFalse(provider.supports("jdbc:h2:mem:testdb"));
        assertFalse(provider.supports("jdbc:oracle:thin:@localhost:1521:xe"));
        assertFalse(provider.supports("jdbc:sqlserver://localhost:1433;databaseName=testdb"));
    }

    @Test
    void testSupportsWithNullUrl() {
        // Test null URL
        assertFalse(provider.supports(null));
    }

    @Test
    void testSupportsWithEmptyUrl() {
        // Test empty URL
        assertFalse(provider.supports(""));
        assertFalse(provider.supports("   "));
    }

    @Test
    void testSupportsWithInvalidUrl() {
        // Test invalid URL
        assertFalse(provider.supports("not-a-jdbc-url"));
        assertFalse(provider.supports("http://example.com"));
    }
}