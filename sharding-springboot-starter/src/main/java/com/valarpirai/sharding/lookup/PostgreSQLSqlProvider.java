package com.valarpirai.sharding.lookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL-specific SQL provider for tenant_shard_mapping table operations.
 */
public class PostgreSQLSqlProvider implements DatabaseSqlProvider {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLSqlProvider.class);

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }

    @Override
    public String getTableExistsQuery(String tableName) {
        return "SELECT COUNT(*) FROM information_schema.tables " +
               "WHERE table_schema = current_schema() AND table_name = '" + tableName + "'";
    }

    @Override
    public String getCreateTenantShardMappingTableSql() {
        return "CREATE TABLE tenant_shard_mapping (" +
               "tenant_id BIGINT NOT NULL, " +
               "shard_id VARCHAR(255) NOT NULL, " +
               "region VARCHAR(255), " +
               "shard_status VARCHAR(50) DEFAULT 'ACTIVE', " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (tenant_id)" +
               ")";
    }

    @Override
    public String[] getCreateIndexesSql() {
        return new String[] {
            "CREATE INDEX idx_shard_id ON tenant_shard_mapping (shard_id)",
            "CREATE INDEX idx_shard_status ON tenant_shard_mapping (shard_status)",
            "CREATE INDEX idx_region ON tenant_shard_mapping (region)"
        };
    }

    @Override
    public String getCurrentDatabaseFunction() {
        return "current_schema()";
    }

    @Override
    public String getTimestampWithCurrentDefaultColumn() {
        return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String url = jdbcUrl.toLowerCase();
        return url.contains("postgresql") || url.contains("postgres");
    }
}