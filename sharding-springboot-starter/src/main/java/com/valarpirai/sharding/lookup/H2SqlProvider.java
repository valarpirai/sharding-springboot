package com.valarpirai.sharding.lookup;

/**
 * H2 database-specific SQL provider for testing.
 */
public class H2SqlProvider extends AbstractSqlProvider {

    @Override
    public String getDatabaseType() {
        return "H2";
    }

    @Override
    public String getTableExistsQuery(String tableName) {
        return "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
               "WHERE UPPER(TABLE_NAME) = UPPER('" + tableName + "')";
    }

    @Override
    public String getCurrentDatabaseFunction() {
        return "DATABASE()";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase().contains(":h2:");
    }

    @Override
    protected String createTablePrefix() {
        return "IF NOT EXISTS ";
    }

    @Override
    protected String createIndexPrefix() {
        return "CREATE INDEX IF NOT EXISTS ";
    }
}
