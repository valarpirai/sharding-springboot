package com.valarpirai.sharding.lookup;

/**
 * PostgreSQL-specific SQL provider for tenant_shard_mapping table operations.
 */
public class PostgreSQLSqlProvider extends AbstractSqlProvider {

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
    public String getCurrentDatabaseFunction() {
        return "current_schema()";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        if (jdbcUrl == null) return false;
        String url = jdbcUrl.toLowerCase();
        return url.contains("postgresql") || url.contains("postgres");
    }
}
