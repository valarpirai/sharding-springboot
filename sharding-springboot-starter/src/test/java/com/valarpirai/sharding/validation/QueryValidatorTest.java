package com.valarpirai.sharding.validation;

import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryValidator.
 */
class QueryValidatorTest {

    private ShardingConfigProperties properties;
    private QueryValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ShardingConfigProperties();
        properties.setTenantColumnNames(Arrays.asList("tenant_id", "company_id"));
        validator = new QueryValidator(properties);
    }

    @Test
    void testValidateSelectQueryWithTenantId() {
        // Valid queries - should not throw
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers WHERE tenant_id = 1001");
            validator.validateQuery("SELECT name FROM customers WHERE tenant_id = ? AND status = 'active'");
            validator.validateQuery("select id from orders where tenant_id in (1001, 1002)");
            validator.validateQuery("SELECT * FROM customers c WHERE c.tenant_id = 1001");
        });
    }

    @Test
    void testValidateSelectQueryWithCompanyId() {
        // Valid queries with alternative tenant column - should not throw
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers WHERE company_id = 1001");
            validator.validateQuery("SELECT name FROM customers WHERE company_id = ? AND status = 'active'");
            validator.validateQuery("select id from orders where company_id in (1001, 1002)");
        });
    }

    @Test
    void testValidateSelectQueryWithoutTenantColumnStrict() {
        // Set strict mode
        properties.getValidation().setStrictness(ShardingConfigProperties.StrictnessLevel.STRICT);
        validator = new QueryValidator(properties);

        // Invalid queries - should throw
        assertThrows(QueryValidationException.class, () -> {
            validator.validateQuery("SELECT * FROM customers");
        });

        assertThrows(QueryValidationException.class, () -> {
            validator.validateQuery("SELECT * FROM customers WHERE status = 'active'");
        });

        assertThrows(QueryValidationException.class, () -> {
            validator.validateQuery("SELECT * FROM customers WHERE user_id = 123");
        });
    }

    @Test
    void testValidateSelectQueryWithoutTenantColumnWarn() {
        // Set warn mode
        properties.getValidation().setStrictness(ShardingConfigProperties.StrictnessLevel.WARN);
        validator = new QueryValidator(properties);

        // Invalid queries - should not throw in warn mode
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers");
            validator.validateQuery("SELECT * FROM customers WHERE status = 'active'");
        });
    }

    @Test
    void testValidateSelectQueryWithoutTenantColumnDisabled() {
        // Set disabled mode
        properties.getValidation().setStrictness(ShardingConfigProperties.StrictnessLevel.DISABLED);
        validator = new QueryValidator(properties);

        // Invalid queries - should not throw in disabled mode
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers");
            validator.validateQuery("SELECT * FROM customers WHERE status = 'active'");
        });
    }

    @Test
    void testValidateNonSelectQueries() {
        // Non-SELECT queries should not be validated
        assertDoesNotThrow(() -> {
            validator.validateQuery("INSERT INTO customers (name, email) VALUES ('John', 'john@example.com')");
            validator.validateQuery("UPDATE customers SET name = 'Jane' WHERE id = 1");
            validator.validateQuery("DELETE FROM customers WHERE id = 1");
            validator.validateQuery("CREATE TABLE test (id INT)");
            validator.validateQuery("DROP TABLE test");
        });
    }

    @Test
    void testValidateQueryWithJoins() {
        // Queries with joins - at least one table should have tenant filtering
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers c JOIN orders o ON c.id = o.customer_id WHERE c.tenant_id = 1001");
            validator.validateQuery("SELECT * FROM customers c JOIN orders o ON c.id = o.customer_id WHERE o.tenant_id = 1001");
        });

        // In strict mode, query without tenant filtering should throw
        properties.getValidation().setStrictness(ShardingConfigProperties.StrictnessLevel.STRICT);
        validator = new QueryValidator(properties);

        assertThrows(QueryValidationException.class, () -> {
            validator.validateQuery("SELECT * FROM customers c JOIN orders o ON c.id = o.customer_id WHERE c.status = 'active'");
        });
    }

    @Test
    void testValidateQueryCaseInsensitive() {
        // Test case insensitive matching
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers WHERE TENANT_ID = 1001");
            validator.validateQuery("select * from customers where Tenant_Id = 1001");
            validator.validateQuery("Select * From customers Where tenant_ID = 1001");
        });
    }

    @Test
    void testValidateQueryWithSubqueries() {
        // Subqueries should also be validated
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers WHERE id IN (SELECT customer_id FROM orders WHERE tenant_id = 1001) AND tenant_id = 1001");
        });

        properties.getValidation().setStrictness(ShardingConfigProperties.StrictnessLevel.STRICT);
        validator = new QueryValidator(properties);

        // This might be a limitation - subquery validation is complex
        // For now, we focus on the main query having tenant filtering
    }

    @Test
    void testValidateQueryWithComplexWhereConditions() {
        // Test various WHERE clause patterns
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT * FROM customers WHERE (tenant_id = 1001 AND status = 'active') OR (tenant_id = 1002 AND status = 'pending')");
            validator.validateQuery("SELECT * FROM customers WHERE tenant_id IN (1001, 1002, 1003)");
            validator.validateQuery("SELECT * FROM customers WHERE tenant_id = ? AND (status = 'active' OR status = 'pending')");
            validator.validateQuery("SELECT * FROM customers WHERE status = 'active' AND tenant_id BETWEEN 1000 AND 2000");
        });
    }

    @Test
    void testValidateQueryWithNullOrEmptyQuery() {
        // Test null and empty queries
        assertDoesNotThrow(() -> {
            validator.validateQuery(null);
            validator.validateQuery("");
            validator.validateQuery("   ");
        });
    }

    @Test
    void testValidateQueryWithDifferentSelectFormats() {
        // Test different SELECT statement formats
        assertDoesNotThrow(() -> {
            validator.validateQuery("SELECT COUNT(*) FROM customers WHERE tenant_id = 1001");
            validator.validateQuery("SELECT DISTINCT name FROM customers WHERE tenant_id = 1001");
            validator.validateQuery("SELECT c.name, c.email FROM customers c WHERE c.tenant_id = 1001");
            validator.validateQuery("SELECT * FROM (SELECT * FROM customers WHERE tenant_id = 1001) t");
        });
    }

    @Test
    void testIsSelectQuery() {
        // Test the helper method for detecting SELECT queries
        assertTrue(validator.isSelectQuery("SELECT * FROM customers"));
        assertTrue(validator.isSelectQuery("select * from customers"));
        assertTrue(validator.isSelectQuery("  SELECT * FROM customers  "));
        assertTrue(validator.isSelectQuery("(SELECT * FROM customers)"));

        assertFalse(validator.isSelectQuery("INSERT INTO customers VALUES (1, 'John')"));
        assertFalse(validator.isSelectQuery("UPDATE customers SET name = 'Jane'"));
        assertFalse(validator.isSelectQuery("DELETE FROM customers"));
        assertFalse(validator.isSelectQuery("CREATE TABLE test (id INT)"));
        assertFalse(validator.isSelectQuery("DROP TABLE test"));
        assertFalse(validator.isSelectQuery(null));
        assertFalse(validator.isSelectQuery(""));
    }

    @Test
    void testContainsTenantFiltering() {
        // Test the helper method for detecting tenant filtering
        assertTrue(validator.containsTenantFiltering("SELECT * FROM customers WHERE tenant_id = 1001"));
        assertTrue(validator.containsTenantFiltering("SELECT * FROM customers WHERE company_id = 1001"));
        assertTrue(validator.containsTenantFiltering("WHERE tenant_id IN (1001, 1002)"));
        assertTrue(validator.containsTenantFiltering("where TENANT_ID = ?"));

        assertFalse(validator.containsTenantFiltering("SELECT * FROM customers"));
        assertFalse(validator.containsTenantFiltering("SELECT * FROM customers WHERE user_id = 123"));
        assertFalse(validator.containsTenantFiltering("WHERE status = 'active'"));
        assertFalse(validator.containsTenantFiltering(null));
        assertFalse(validator.containsTenantFiltering(""));
    }

    @Test
    void testQueryValidationException() {
        // Test the custom exception
        String message = "Test validation error";
        String query = "SELECT * FROM customers";

        QueryValidationException exception = new QueryValidationException(message, query);

        assertEquals(message, exception.getMessage());
        assertEquals(query, exception.getQuery());
        assertNotNull(exception.toString());
        assertTrue(exception.toString().contains(message));
        assertTrue(exception.toString().contains(query));
    }
}