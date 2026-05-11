package com.valarpirai.sharding.lookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseSqlProviderFactory.
 */
class DatabaseSqlProviderFactoryTest {

    private DatabaseSqlProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DatabaseSqlProviderFactory();
    }

    // ---- OCP: extension without modification ----

    @Test
    void customProvider_canBeRegisteredWithoutModifyingFactory() {
        DatabaseSqlProvider oracle = new DatabaseSqlProvider() {
            @Override public String getDatabaseType() { return "Oracle"; }
            @Override public String getTableExistsQuery(String t) { return ""; }
            @Override public String getCreateTenantShardMappingTableSql() { return ""; }
            @Override public String[] getCreateIndexesSql() { return new String[0]; }
            @Override public String getCurrentDatabaseFunction() { return ""; }
            @Override public String getTimestampWithCurrentDefaultColumn() { return ""; }
            @Override public boolean supports(String url) {
                return url != null && url.toLowerCase().contains("oracle");
            }
        };

        DatabaseSqlProviderFactory extended = new DatabaseSqlProviderFactory(
            List.of(new H2SqlProvider(), new MySQLSqlProvider(), new PostgreSQLSqlProvider(), oracle)
        );

        DatabaseSqlProvider found = extended.getProvider("jdbc:oracle:thin:@localhost:1521:xe");
        assertEquals("Oracle", found.getDatabaseType());
        assertEquals(4, extended.getSupportedDatabases().size());
        assertTrue(extended.getSupportedDatabases().contains("Oracle"));
    }

    @Test
    void injectionConstructor_preservesOrder() {
        DatabaseSqlProviderFactory ordered = new DatabaseSqlProviderFactory(
            List.of(new H2SqlProvider(), new MySQLSqlProvider(), new PostgreSQLSqlProvider())
        );
        List<String> dbs = ordered.getSupportedDatabases();
        assertEquals(List.of("H2", "MySQL", "PostgreSQL"), dbs);
    }

    @Test
    void noArgConstructor_registersBuiltInProviders() {
        List<String> dbs = factory.getSupportedDatabases();
        assertEquals(3, dbs.size());
        assertTrue(dbs.containsAll(List.of("H2", "MySQL", "PostgreSQL")));
    }

    @Test
    void testGetProviderForMySQLUrl() {
        // Given
        String mysqlUrl = "jdbc:mysql://localhost:3306/testdb";

        // When
        DatabaseSqlProvider provider = factory.getProvider(mysqlUrl);

        // Then
        assertInstanceOf(MySQLSqlProvider.class, provider);
        assertEquals("MySQL", provider.getDatabaseType());
    }

    @Test
    void testGetProviderForMariaDBUrl() {
        // Given
        String mariadbUrl = "jdbc:mariadb://localhost:3306/testdb";

        // When
        DatabaseSqlProvider provider = factory.getProvider(mariadbUrl);

        // Then
        assertInstanceOf(MySQLSqlProvider.class, provider);
        assertEquals("MySQL", provider.getDatabaseType());
    }

    @Test
    void testGetProviderForPostgreSQLUrl() {
        // Given
        String postgresqlUrl = "jdbc:postgresql://localhost:5432/testdb";

        // When
        DatabaseSqlProvider provider = factory.getProvider(postgresqlUrl);

        // Then
        assertInstanceOf(PostgreSQLSqlProvider.class, provider);
        assertEquals("PostgreSQL", provider.getDatabaseType());
    }

    @Test
    void testGetProviderForPostgresUrl() {
        // Given
        String postgresUrl = "jdbc:postgres://localhost:5432/testdb";

        // When
        DatabaseSqlProvider provider = factory.getProvider(postgresUrl);

        // Then
        assertInstanceOf(PostgreSQLSqlProvider.class, provider);
        assertEquals("PostgreSQL", provider.getDatabaseType());
    }

    @Test
    void testGetProviderForUnsupportedUrl() {
        // Given
        String unsupportedUrl = "jdbc:oracle:thin:@localhost:1521:testdb";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getProvider(unsupportedUrl);
        });

        assertTrue(exception.getMessage().contains("Unsupported database URL"));
        assertTrue(exception.getMessage().contains("jdbc:oracle"));
        assertTrue(exception.getMessage().contains("H2, MySQL, PostgreSQL"));
    }

    @Test
    void testGetProviderForNullUrl() {
        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getProvider(null);
        });

        assertTrue(exception.getMessage().contains("Unsupported database URL"));
    }

    @Test
    void testGetProviderForEmptyUrl() {
        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getProvider("");
        });

        assertTrue(exception.getMessage().contains("Unsupported database URL"));
    }

    @Test
    void testGetProviderCaseInsensitive() {
        // Test case insensitive matching
        DatabaseSqlProvider mysqlProvider1 = factory.getProvider("JDBC:MYSQL://HOST:3306/DB");
        DatabaseSqlProvider mysqlProvider2 = factory.getProvider("jdbc:mysql://host:3306/db");

        assertInstanceOf(MySQLSqlProvider.class, mysqlProvider1);
        assertInstanceOf(MySQLSqlProvider.class, mysqlProvider2);

        DatabaseSqlProvider pgProvider1 = factory.getProvider("JDBC:POSTGRESQL://HOST:5432/DB");
        DatabaseSqlProvider pgProvider2 = factory.getProvider("jdbc:postgresql://host:5432/db");

        assertInstanceOf(PostgreSQLSqlProvider.class, pgProvider1);
        assertInstanceOf(PostgreSQLSqlProvider.class, pgProvider2);
    }

    @Test
    void testGetSupportedDatabases() {
        // When
        List<String> supportedDatabases = factory.getSupportedDatabases();

        // Then
        assertEquals(3, supportedDatabases.size());
        assertTrue(supportedDatabases.contains("H2"));
        assertTrue(supportedDatabases.contains("MySQL"));
        assertTrue(supportedDatabases.contains("PostgreSQL"));
    }

    @Test
    void testGetSupportedDatabasesIsImmutable() {
        // When
        List<String> supportedDatabases = factory.getSupportedDatabases();

        // Then - should not be able to modify the returned list
        assertThrows(UnsupportedOperationException.class, () -> {
            supportedDatabases.add("NewDB");
        });
    }

    @Test
    void testProviderSelectionPriority() {
        // Test that the first matching provider is selected
        // MySQL provider should be selected first for mysql URLs
        String mysqlUrl = "jdbc:mysql://localhost:3306/testdb";
        DatabaseSqlProvider provider1 = factory.getProvider(mysqlUrl);
        DatabaseSqlProvider provider2 = factory.getProvider(mysqlUrl);

        // Both should return MySQL provider instances
        assertInstanceOf(MySQLSqlProvider.class, provider1);
        assertInstanceOf(MySQLSqlProvider.class, provider2);
    }
}