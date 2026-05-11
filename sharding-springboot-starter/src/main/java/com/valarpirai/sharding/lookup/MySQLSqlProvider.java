package com.valarpirai.sharding.lookup;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * MySQL-specific SQL provider for tenant_shard_mapping table operations.
 */
@Component
@Order(2)
public class MySQLSqlProvider extends AbstractSqlProvider {

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }

    @Override
    public String getTableExistsQuery(String tableName) {
        return "SELECT COUNT(*) FROM information_schema.tables " +
               "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'";
    }

    @Override
    public String getCurrentDatabaseFunction() {
        return "DATABASE()";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        if (jdbcUrl == null) return false;
        String url = jdbcUrl.toLowerCase();
        return url.contains("mysql") || url.contains("mariadb");
    }

    @Override
    protected String tableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }
}
