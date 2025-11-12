package com.valarpirai.sharding.lookup;

/**
 * H2 database-specific SQL provider for testing.
 */
public class H2SqlProvider implements DatabaseSqlProvider {

    @Override
    public String getDatabaseType() {
        return "H2";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase().contains(":h2:");
    }

    @Override
    public String getTableExistsQuery(String tableName) {
        return "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
               "WHERE UPPER(TABLE_NAME) = UPPER('" + tableName + "')";
    }

    @Override
    public String getCreateTenantShardMappingTableSql() {
        return "CREATE TABLE IF NOT EXISTS tenant_shard_mapping (" +
               "tenant_id BIGINT NOT NULL, " +
               "shard_id VARCHAR(255) NOT NULL, " +
               "region VARCHAR(255), " +
               "shard_status VARCHAR(50) DEFAULT 'ACTIVE', " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (tenant_id))";
    }

    @Override
    public String[] getCreateIndexesSql() {
        return new String[]{
            "CREATE INDEX IF NOT EXISTS idx_shard_id ON tenant_shard_mapping(shard_id)",
            "CREATE INDEX IF NOT EXISTS idx_shard_status ON tenant_shard_mapping(shard_status)",
            "CREATE INDEX IF NOT EXISTS idx_region ON tenant_shard_mapping(region)"
        };
    }

    @Override
    public String getCurrentDatabaseFunction() {
        return "DATABASE()";
    }

    @Override
    public String getTimestampWithCurrentDefaultColumn() {
        return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
    }
}
