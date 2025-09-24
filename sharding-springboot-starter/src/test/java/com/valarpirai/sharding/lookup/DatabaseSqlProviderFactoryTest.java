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
        String unsupportedUrl = "jdbc:h2:mem:testdb";

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getProvider(unsupportedUrl);
        });

        assertTrue(exception.getMessage().contains("Unsupported database URL"));
        assertTrue(exception.getMessage().contains("jdbc:h2:mem:testdb"));
        assertTrue(exception.getMessage().contains("MySQL, PostgreSQL"));
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
        assertEquals(2, supportedDatabases.size());
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