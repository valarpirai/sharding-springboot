package com.valarpirai.sharding.lookup;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Factory for creating database-specific SQL providers based on JDBC URL.
 */
@Component
public class DatabaseSqlProviderFactory {

    private final List<DatabaseSqlProvider> providers;

    public DatabaseSqlProviderFactory() {
        this.providers = Arrays.asList(
            new MySQLSqlProvider(),
            new PostgreSQLSqlProvider()
        );
    }

    /**
     * Get the appropriate SQL provider for the given JDBC URL.
     *
     * @param jdbcUrl the JDBC URL
     * @return the database-specific SQL provider
     * @throws IllegalArgumentException if no provider supports the given URL
     */
    public DatabaseSqlProvider getProvider(String jdbcUrl) {
        return providers.stream()
            .filter(provider -> provider.supports(jdbcUrl))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported database URL: " + jdbcUrl +
                ". Supported databases: MySQL, PostgreSQL"));
    }

    /**
     * Get all supported database types.
     *
     * @return list of supported database type names
     */
    public List<String> getSupportedDatabases() {
        return providers.stream()
            .map(DatabaseSqlProvider::getDatabaseType)
            .toList();
    }
}