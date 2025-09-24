package com.valarpirai.sharding.lookup;

/**
 * Interface for database-specific SQL operations.
 * Provides database-agnostic SQL generation for different database types.
 */
public interface DatabaseSqlProvider {

    /**
     * Get the database type name.
     *
     * @return database type (e.g., "MySQL", "PostgreSQL")
     */
    String getDatabaseType();

    /**
     * Get SQL to check if a table exists.
     *
     * @param tableName the table name to check
     * @return SQL query to check table existence
     */
    String getTableExistsQuery(String tableName);

    /**
     * Get SQL to create the tenant_shard_mapping table.
     *
     * @return SQL statement to create the table
     */
    String getCreateTenantShardMappingTableSql();

    /**
     * Get SQL to create indexes for the tenant_shard_mapping table.
     *
     * @return array of SQL statements to create indexes
     */
    String[] getCreateIndexesSql();

    /**
     * Get the current database/schema name function.
     *
     * @return SQL expression for current database name
     */
    String getCurrentDatabaseFunction();

    /**
     * Get the timestamp with default current timestamp column definition.
     *
     * @return SQL column definition for timestamp with current timestamp default
     */
    String getTimestampWithCurrentDefaultColumn();

    /**
     * Check if this provider supports the given JDBC URL.
     *
     * @param jdbcUrl the JDBC URL to check
     * @return true if this provider supports the database
     */
    boolean supports(String jdbcUrl);
}