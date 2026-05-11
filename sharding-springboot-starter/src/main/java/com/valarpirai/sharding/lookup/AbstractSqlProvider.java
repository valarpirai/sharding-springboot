package com.valarpirai.sharding.lookup;

/**
 * Base class for database-specific SQL providers.
 * Encapsulates the shared table structure and index definitions for tenant_shard_mapping.
 * Subclasses override hook methods to supply database-specific clauses.
 */
public abstract class AbstractSqlProvider implements DatabaseSqlProvider {

    static final String COMMON_COLUMNS =
            "tenant_id BIGINT NOT NULL, " +
            "shard_id VARCHAR(255) NOT NULL, " +
            "region VARCHAR(255), " +
            "shard_status VARCHAR(50) DEFAULT 'ACTIVE', " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "PRIMARY KEY (tenant_id)";

    @Override
    public String getTimestampWithCurrentDefaultColumn() {
        return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
    }

    @Override
    public String getCreateTenantShardMappingTableSql() {
        return "CREATE TABLE " + createTablePrefix() + "tenant_shard_mapping (" +
               COMMON_COLUMNS + ")" + tableOptions();
    }

    @Override
    public String[] getCreateIndexesSql() {
        String prefix = createIndexPrefix();
        return new String[] {
            prefix + "idx_shard_id ON tenant_shard_mapping (shard_id)",
            prefix + "idx_shard_status ON tenant_shard_mapping (shard_status)",
            prefix + "idx_region ON tenant_shard_mapping (region)"
        };
    }

    /**
     * Optional prefix inserted between CREATE TABLE and the table name.
     * Override to return "IF NOT EXISTS " for databases that support it.
     */
    protected String createTablePrefix() {
        return "";
    }

    /**
     * Optional suffix appended after the closing parenthesis of CREATE TABLE.
     * Override to add engine/charset options (e.g. MySQL).
     */
    protected String tableOptions() {
        return "";
    }

    /**
     * The CREATE INDEX statement prefix including trailing space.
     * Override to return "CREATE INDEX IF NOT EXISTS " for databases that support it.
     */
    protected String createIndexPrefix() {
        return "CREATE INDEX ";
    }
}
