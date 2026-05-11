package com.valarpirai.sharding.lookup;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * H2 database-specific SQL provider for testing.
 * Registered at Order(1) so it is matched before MySQL/PostgreSQL providers.
 */
@Component
@Order(1)
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
