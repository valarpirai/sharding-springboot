package com.valarpirai.sharding.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Dynamic proxy for JDBC Connection that intercepts statement creation
 * and validates SQL queries for tenant filtering requirements.
 */
public class ValidatingConnectionProxy implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ValidatingConnectionProxy.class);

    private final Connection targetConnection;
    private final QueryValidator queryValidator;
    private final Supplier<Boolean> shardedEntityContextSupplier;
    private final Supplier<String> tableNameContextSupplier;

    public ValidatingConnectionProxy(Connection targetConnection,
                                   QueryValidator queryValidator,
                                   Supplier<Boolean> shardedEntityContextSupplier,
                                   Supplier<String> tableNameContextSupplier) {
        this.targetConnection = targetConnection;
        this.queryValidator = queryValidator;
        this.shardedEntityContextSupplier = shardedEntityContextSupplier;
        this.tableNameContextSupplier = tableNameContextSupplier;
    }

    /**
     * Create a validating connection proxy.
     *
     * @param connection the target connection
     * @param queryValidator the query validator
     * @param shardedEntityContextSupplier supplier for sharded entity context
     * @param tableNameContextSupplier supplier for table name context
     * @return proxied connection
     */
    public static Connection create(Connection connection,
                                  QueryValidator queryValidator,
                                  Supplier<Boolean> shardedEntityContextSupplier,
                                  Supplier<String> tableNameContextSupplier) {
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new ValidatingConnectionProxy(
                        connection,
                        queryValidator,
                        shardedEntityContextSupplier,
                        tableNameContextSupplier
                )
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Intercept statement creation methods
        if (isStatementCreationMethod(methodName)) {
            return handleStatementCreation(method, args);
        }

        // For all other methods, delegate to the target connection
        return method.invoke(targetConnection, args);
    }

    /**
     * Handle statement creation and wrap with validation.
     */
    private Object handleStatementCreation(Method method, Object[] args) throws Throwable {
        Object statement = method.invoke(targetConnection, args);

        // Wrap statements with validation logic
        if (statement instanceof PreparedStatement) {
            return wrapPreparedStatement((PreparedStatement) statement, args);
        } else if (statement instanceof Statement) {
            return wrapStatement((Statement) statement);
        }

        return statement;
    }

    /**
     * Wrap a PreparedStatement with validation.
     */
    private PreparedStatement wrapPreparedStatement(PreparedStatement preparedStatement, Object[] args) {
        // First argument should be the SQL string
        String sql = args != null && args.length > 0 ? (String) args[0] : null;

        return (PreparedStatement) Proxy.newProxyInstance(
                preparedStatement.getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                new ValidatingStatementProxy(
                        preparedStatement,
                        sql,
                        queryValidator,
                        shardedEntityContextSupplier,
                        tableNameContextSupplier
                )
        );
    }

    /**
     * Wrap a regular Statement with validation.
     */
    private Statement wrapStatement(Statement statement) {
        return (Statement) Proxy.newProxyInstance(
                statement.getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                new ValidatingStatementProxy(
                        statement,
                        null, // SQL will be provided at execution time
                        queryValidator,
                        shardedEntityContextSupplier,
                        tableNameContextSupplier
                )
        );
    }

    /**
     * Check if method is a statement creation method.
     */
    private boolean isStatementCreationMethod(String methodName) {
        switch (methodName) {
            case "createStatement":
            case "prepareStatement":
            case "prepareCall":
                return true;
            default:
                return false;
        }
    }

    /**
     * Proxy for validating Statement and PreparedStatement operations.
     */
    private static class ValidatingStatementProxy implements InvocationHandler {

        private static final Logger logger = LoggerFactory.getLogger(ValidatingStatementProxy.class);

        private final Object targetStatement;
        private final String preparedSql;
        private final QueryValidator queryValidator;
        private final Supplier<Boolean> shardedEntityContextSupplier;
        private final Supplier<String> tableNameContextSupplier;

        public ValidatingStatementProxy(Object targetStatement,
                                      String preparedSql,
                                      QueryValidator queryValidator,
                                      Supplier<Boolean> shardedEntityContextSupplier,
                                      Supplier<String> tableNameContextSupplier) {
            this.targetStatement = targetStatement;
            this.preparedSql = preparedSql;
            this.queryValidator = queryValidator;
            this.shardedEntityContextSupplier = shardedEntityContextSupplier;
            this.tableNameContextSupplier = tableNameContextSupplier;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Intercept SQL execution methods
            if (isExecutionMethod(methodName)) {
                validateSqlExecution(method, args);
            }

            // Delegate to target statement
            return method.invoke(targetStatement, args);
        }

        /**
         * Validate SQL before execution.
         */
        private void validateSqlExecution(Method method, Object[] args) {
            String sql = getSqlFromExecution(method, args);
            if (sql != null) {
                boolean isShardedEntity = shardedEntityContextSupplier.get();
                String tableName = tableNameContextSupplier.get();

                logger.trace("Validating SQL execution: method={}, sharded={}, table={}, sql={}",
                           method.getName(), isShardedEntity, tableName, sanitizeSqlForLog(sql));

                try {
                    queryValidator.validateQuery(sql, tableName, isShardedEntity);
                } catch (QueryValidationException e) {
                    logger.error("SQL validation failed for method {}: {}", method.getName(), e.getMessage());
                    throw e;
                }
            }
        }

        /**
         * Extract SQL from execution method call.
         */
        private String getSqlFromExecution(Method method, Object[] args) {
            String methodName = method.getName();

            // For PreparedStatement, use the prepared SQL
            if (targetStatement instanceof PreparedStatement && preparedSql != null) {
                return preparedSql;
            }

            // For Statement execution methods, get SQL from arguments
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }

            // For batch operations, we might not have direct SQL access
            if (methodName.contains("Batch")) {
                logger.debug("Batch execution detected, SQL validation may be limited");
                return null;
            }

            return null;
        }

        /**
         * Check if method is a SQL execution method.
         */
        private boolean isExecutionMethod(String methodName) {
            switch (methodName) {
                case "execute":
                case "executeQuery":
                case "executeUpdate":
                case "executeBatch":
                case "executeLargeBatch":
                case "executeLargeUpdate":
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Sanitize SQL for logging.
         */
        private String sanitizeSqlForLog(String sql) {
            if (sql == null) return "null";
            return sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
        }
    }
}