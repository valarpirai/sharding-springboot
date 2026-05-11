package com.valarpirai.sharding.lookup;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating database-specific SQL providers based on JDBC URL.
 *
 * When running inside a Spring context, all {@link DatabaseSqlProvider} beans are
 * injected automatically (ordered by {@code @Order}). To support a new database,
 * create a {@code @Component} that implements {@code DatabaseSqlProvider} — no
 * changes to this class are required.
 *
 * Outside Spring (e.g. tests), the no-arg constructor registers the built-in
 * H2, MySQL, and PostgreSQL providers in priority order.
 */
@Component
public class DatabaseSqlProviderFactory {

    private final List<DatabaseSqlProvider> providers;

    /** Spring constructor — receives all DatabaseSqlProvider beans, ordered by @Order. */
    public DatabaseSqlProviderFactory(List<DatabaseSqlProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** No-arg constructor for direct instantiation outside a Spring context. */
    public DatabaseSqlProviderFactory() {
        this(List.of(new H2SqlProvider(), new MySQLSqlProvider(), new PostgreSQLSqlProvider()));
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
                ". Supported databases: " + getSupportedDatabases()));
    }

    /**
     * Get all supported database type names, in detection priority order.
     */
    public List<String> getSupportedDatabases() {
        return providers.stream()
            .map(DatabaseSqlProvider::getDatabaseType)
            .toList();
    }
}
