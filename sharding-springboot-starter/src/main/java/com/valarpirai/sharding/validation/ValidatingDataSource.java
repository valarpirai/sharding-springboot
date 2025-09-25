package com.valarpirai.sharding.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DataSource proxy that validates SQL queries for tenant filtering.
 * Wraps connections with validation logic for sharded entity operations.
 */
public class ValidatingDataSource extends AbstractDataSource {

    private static final Logger logger = LoggerFactory.getLogger(ValidatingDataSource.class);

    private final DataSource targetDataSource;
    private final QueryValidator queryValidator;
    private final ThreadLocal<Boolean> shardedEntityContext = new ThreadLocal<>();
    private final ThreadLocal<String> tableNameContext = new ThreadLocal<>();

    public ValidatingDataSource(DataSource targetDataSource, QueryValidator queryValidator) {
        this.targetDataSource = targetDataSource;
        this.queryValidator = queryValidator;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = targetDataSource.getConnection();
        return wrapConnection(connection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = targetDataSource.getConnection(username, password);
        return wrapConnection(connection);
    }

    /**
     * Set context for sharded entity operations.
     *
     * @param forShardedEntity true if operation is for sharded entity
     * @param tableName optional table name for better validation
     */
    public void setValidationContext(boolean forShardedEntity, String tableName) {
        shardedEntityContext.set(forShardedEntity);
        tableNameContext.set(tableName);
        logger.trace("Set validation context: sharded={}, table={}", forShardedEntity, tableName);
    }

    /**
     * Clear the validation context.
     */
    public void clearValidationContext() {
        shardedEntityContext.remove();
        tableNameContext.remove();
        logger.trace("Cleared validation context");
    }

    /**
     * Get the current sharded entity context.
     *
     * @return true if current operation is for sharded entity
     */
    public boolean isShardedEntityContext() {
        Boolean context = shardedEntityContext.get();
        return context != null ? context : false;
    }

    /**
     * Get the current table name context.
     *
     * @return the table name or null
     */
    public String getTableNameContext() {
        return tableNameContext.get();
    }

    /**
     * Wrap a connection with validation logic.
     */
    private Connection wrapConnection(Connection connection) {
        ValidatingConnectionProxy proxy = new ValidatingConnectionProxy(
                connection,
                queryValidator,
                this::isShardedEntityContext,
                this::getTableNameContext
        );
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class[]{Connection.class},
                proxy
        );
    }

    /**
     * Get the underlying target DataSource.
     *
     * @return the target DataSource
     */
    public DataSource getTargetDataSource() {
        return targetDataSource;
    }

    /**
     * Execute an operation with explicit validation context.
     *
     * @param forShardedEntity whether operation is for sharded entity
     * @param tableName optional table name
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws SQLException if operation fails
     */
    public <T> T executeWithContext(boolean forShardedEntity, String tableName,
                                   SqlOperation<T> operation) throws SQLException {
        boolean previousShardedContext = isShardedEntityContext();
        String previousTableContext = getTableNameContext();

        try {
            setValidationContext(forShardedEntity, tableName);
            return operation.execute(this);
        } finally {
            if (previousShardedContext || previousTableContext != null) {
                setValidationContext(previousShardedContext, previousTableContext);
            } else {
                clearValidationContext();
            }
        }
    }

    /**
     * Functional interface for SQL operations.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute(DataSource dataSource) throws SQLException;
    }
}