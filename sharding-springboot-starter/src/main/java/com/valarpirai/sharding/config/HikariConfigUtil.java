package com.valarpirai.sharding.config;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

/**
 * Utility class for configuring HikariCP with optimal defaults.
 * Handles property conversion and applies best practices for production use.
 */
public class HikariConfigUtil {

    private static final Logger logger = LoggerFactory.getLogger(HikariConfigUtil.class);

    /**
     * Create a HikariConfig from configuration properties with optimal defaults.
     *
     * @param hikariProps the HikariCP properties
     * @param dbProps the database connection properties
     * @param poolName the connection pool name
     * @return configured HikariConfig
     */
    public static HikariConfig createHikariConfig(HikariConfigProperties hikariProps,
                                                DatabaseConfigProperties dbProps,
                                                String poolName) {
        HikariConfig config = new HikariConfig();

        // Basic connection properties
        setBasicConnectionProperties(config, dbProps);

        // Pool sizing with optimal defaults
        setPoolSizingProperties(config, hikariProps);

        // Timeout configurations with production-ready values
        setTimeoutProperties(config, hikariProps);

        // Connection testing and validation
        setConnectionTestingProperties(config, hikariProps);

        // Pool behavior and transaction settings
        setPoolBehaviorProperties(config, hikariProps);

        // Monitoring and observability
        setMonitoringProperties(config, hikariProps, poolName);

        // Advanced configurations
        setAdvancedProperties(config, hikariProps);

        // Log configuration summary
        logConfigurationSummary(config, poolName);

        return config;
    }

    /**
     * Set basic connection properties (URL, username, password, driver).
     */
    private static void setBasicConnectionProperties(HikariConfig config, DatabaseConfigProperties dbProps) {
        config.setJdbcUrl(dbProps.getUrl());
        config.setUsername(dbProps.getUsername());
        config.setPassword(dbProps.getPassword());

        if (dbProps.getDriverClassName() != null) {
            config.setDriverClassName(dbProps.getDriverClassName());
        }
        // If no driver specified, HikariCP will auto-detect from URL
    }

    /**
     * Set pool sizing properties with intelligent defaults.
     */
    private static void setPoolSizingProperties(HikariConfig config, HikariConfigProperties props) {
        // Maximum pool size - balance between performance and resource usage
        int maxPoolSize = props.getMaximumPoolSize() != null ? props.getMaximumPoolSize() : 20;
        config.setMaximumPoolSize(maxPoolSize);

        // Minimum idle - ensure some connections are always ready
        int minIdle = props.getMinimumIdle() != null ? props.getMinimumIdle() : Math.max(2, maxPoolSize / 4);
        config.setMinimumIdle(minIdle);

        logger.debug("Pool sizing: maxPoolSize={}, minIdle={}", maxPoolSize, minIdle);
    }

    /**
     * Set timeout properties with production-optimized values.
     */
    private static void setTimeoutProperties(HikariConfig config, HikariConfigProperties props) {
        // Connection timeout - how long to wait for a connection from the pool
        Duration connTimeout = props.getConnectionTimeout() != null ? props.getConnectionTimeout() : Duration.ofSeconds(30);
        config.setConnectionTimeout(connTimeout.toMillis());

        // Idle timeout - how long a connection can sit idle before being removed
        Duration idleTimeout = props.getIdleTimeout() != null ? props.getIdleTimeout() : Duration.ofMinutes(10);
        config.setIdleTimeout(idleTimeout.toMillis());

        // Max lifetime - maximum lifetime of a connection in the pool
        Duration maxLifetime = props.getMaxLifetime() != null ? props.getMaxLifetime() : Duration.ofMinutes(30);
        config.setMaxLifetime(maxLifetime.toMillis());

        // Validation timeout - timeout for connection validation
        Duration validationTimeout = props.getValidationTimeout() != null ? props.getValidationTimeout() : Duration.ofSeconds(5);
        config.setValidationTimeout(validationTimeout.toMillis());

        logger.debug("Timeouts: connection={}ms, idle={}ms, maxLifetime={}ms, validation={}ms",
                    connTimeout.toMillis(), idleTimeout.toMillis(), maxLifetime.toMillis(), validationTimeout.toMillis());
    }

    /**
     * Set connection testing and validation properties.
     */
    private static void setConnectionTestingProperties(HikariConfig config, HikariConfigProperties props) {
        // Connection test query - use database-specific if provided
        if (props.getConnectionTestQuery() != null && !props.getConnectionTestQuery().trim().isEmpty()) {
            config.setConnectionTestQuery(props.getConnectionTestQuery());
            logger.debug("Using custom connection test query: {}", props.getConnectionTestQuery());
        }
        // Otherwise, let HikariCP use JDBC4 isValid() method

        // Leak detection - useful for development, should be disabled in production
        Duration leakThreshold = props.getLeakDetectionThreshold() != null ? props.getLeakDetectionThreshold() : Duration.ZERO;
        if (!leakThreshold.isZero()) {
            config.setLeakDetectionThreshold(leakThreshold.toMillis());
            logger.debug("Leak detection enabled with threshold: {}ms", leakThreshold.toMillis());
        }
    }

    /**
     * Set pool behavior and transaction properties.
     */
    private static void setPoolBehaviorProperties(HikariConfig config, HikariConfigProperties props) {
        // Auto commit behavior
        boolean autoCommit = props.getAutoCommit() != null ? props.getAutoCommit() : true;
        config.setAutoCommit(autoCommit);

        // Transaction isolation level
        if (props.getTransactionIsolation() != null && !props.getTransactionIsolation().trim().isEmpty()) {
            config.setTransactionIsolation(props.getTransactionIsolation());
            logger.debug("Using custom transaction isolation: {}", props.getTransactionIsolation());
        }

        // Read-only flag
        boolean readOnly = props.getReadOnly() != null ? props.getReadOnly() : false;
        config.setReadOnly(readOnly);

        // Catalog and schema
        if (props.getCatalog() != null && !props.getCatalog().trim().isEmpty()) {
            config.setCatalog(props.getCatalog());
        }
        if (props.getSchema() != null && !props.getSchema().trim().isEmpty()) {
            config.setSchema(props.getSchema());
        }

        logger.debug("Pool behavior: autoCommit={}, readOnly={}", autoCommit, readOnly);
    }

    /**
     * Set monitoring and observability properties.
     */
    private static void setMonitoringProperties(HikariConfig config, HikariConfigProperties props, String poolName) {
        // Pool name for monitoring
        String finalPoolName = props.getPoolName() != null ? props.getPoolName() : poolName;
        if (finalPoolName != null) {
            config.setPoolName(finalPoolName);
        }

        // JMX MBean registration for monitoring
        boolean registerMbeans = props.getRegisterMbeans() != null ? props.getRegisterMbeans() : true;
        config.setRegisterMbeans(registerMbeans);

        logger.debug("Monitoring: poolName={}, registerMbeans={}", finalPoolName, registerMbeans);
    }

    /**
     * Set advanced HikariCP properties.
     */
    private static void setAdvancedProperties(HikariConfig config, HikariConfigProperties props) {
        // Connection initialization SQL
        if (props.getConnectionInitSql() != null && !props.getConnectionInitSql().trim().isEmpty()) {
            config.setConnectionInitSql(props.getConnectionInitSql());
            logger.debug("Using connection initialization SQL: {}", props.getConnectionInitSql());
        }

        // Initialization fail timeout
        int initFailTimeout = props.getInitializationFailTimeout() != null ? props.getInitializationFailTimeout() : 1;
        config.setInitializationFailTimeout(initFailTimeout);

        // Isolate internal queries
        boolean isolateQueries = props.getIsolateInternalQueries() != null ? props.getIsolateInternalQueries() : false;
        config.setIsolateInternalQueries(isolateQueries);

        // Allow pool suspension
        boolean allowSuspension = props.getAllowPoolSuspension() != null ? props.getAllowPoolSuspension() : false;
        config.setAllowPoolSuspension(allowSuspension);

        logger.debug("Advanced: initFailTimeout={}, isolateQueries={}, allowSuspension={}",
                    initFailTimeout, isolateQueries, allowSuspension);
    }

    /**
     * Apply database-specific optimizations based on JDBC URL.
     */
    public static void applyDatabaseSpecificOptimizations(HikariConfig config) {
        String jdbcUrl = config.getJdbcUrl();
        if (jdbcUrl == null) {
            return;
        }

        String url = jdbcUrl.toLowerCase();

        if (url.contains("mysql")) {
            applyMySQLOptimizations(config);
        } else if (url.contains("postgresql")) {
            applyPostgreSQLOptimizations(config);
        } else if (url.contains("sqlserver")) {
            applySQLServerOptimizations(config);
        } else if (url.contains("oracle")) {
            applyOracleOptimizations(config);
        }
    }

    /**
     * Apply MySQL-specific optimizations.
     */
    private static void applyMySQLOptimizations(HikariConfig config) {
        // Use JDBC4 validation for MySQL
        if (config.getConnectionTestQuery() == null) {
            // Let HikariCP use JDBC4 isValid() - more efficient than SELECT 1
            logger.debug("Using JDBC4 validation for MySQL");
        }

        // Add MySQL-specific connection properties if none exist
        Properties props = config.getDataSourceProperties();
        if (props.isEmpty()) {
            props.setProperty("cachePrepStmts", "true");
            props.setProperty("prepStmtCacheSize", "250");
            props.setProperty("prepStmtCacheSqlLimit", "2048");
            props.setProperty("useServerPrepStmts", "true");
            logger.debug("Applied MySQL performance optimizations");
        }
    }

    /**
     * Apply PostgreSQL-specific optimizations.
     */
    private static void applyPostgreSQLOptimizations(HikariConfig config) {
        Properties props = config.getDataSourceProperties();
        if (props.isEmpty()) {
            props.setProperty("prepareThreshold", "1");
            props.setProperty("preparedStatementCacheQueries", "256");
            props.setProperty("preparedStatementCacheSizeMiB", "5");
            logger.debug("Applied PostgreSQL performance optimizations");
        }
    }

    /**
     * Apply SQL Server-specific optimizations.
     */
    private static void applySQLServerOptimizations(HikariConfig config) {
        Properties props = config.getDataSourceProperties();
        if (props.isEmpty()) {
            props.setProperty("statementPoolingCacheSize", "512");
            props.setProperty("disableStatementPooling", "false");
            logger.debug("Applied SQL Server performance optimizations");
        }
    }

    /**
     * Apply Oracle-specific optimizations.
     */
    private static void applyOracleOptimizations(HikariConfig config) {
        Properties props = config.getDataSourceProperties();
        if (props.isEmpty()) {
            props.setProperty("oracle.jdbc.implicitStatementCacheSize", "25");
            props.setProperty("oracle.jdbc.explicitStatementThreshold", "20");
            logger.debug("Applied Oracle performance optimizations");
        }
    }

    /**
     * Log configuration summary for troubleshooting.
     */
    private static void logConfigurationSummary(HikariConfig config, String poolName) {
        logger.info("HikariCP configuration for pool '{}': maxPoolSize={}, minIdle={}, " +
                   "connectionTimeout={}ms, idleTimeout={}ms, maxLifetime={}ms",
                   poolName != null ? poolName : "unnamed",
                   config.getMaximumPoolSize(),
                   config.getMinimumIdle(),
                   config.getConnectionTimeout(),
                   config.getIdleTimeout(),
                   config.getMaxLifetime());
    }

    /**
     * Validate HikariCP configuration and log warnings for potential issues.
     */
    public static void validateConfiguration(HikariConfig config) {
        // Check for potential configuration issues
        if (config.getMinimumIdle() > config.getMaximumPoolSize()) {
            logger.warn("MinimumIdle ({}) is greater than MaximumPoolSize ({}). This will be corrected automatically.",
                       config.getMinimumIdle(), config.getMaximumPoolSize());
        }

        if (config.getMaxLifetime() > 0 && config.getMaxLifetime() < Duration.ofMinutes(2).toMillis()) {
            logger.warn("MaxLifetime is less than 2 minutes. This may cause frequent connection cycling.");
        }

        if (config.getConnectionTimeout() > Duration.ofMinutes(1).toMillis()) {
            logger.warn("ConnectionTimeout is greater than 1 minute. This may cause long waits for connections.");
        }

        if (config.getLeakDetectionThreshold() > 0 && config.getLeakDetectionThreshold() < Duration.ofMinutes(2).toMillis()) {
            logger.warn("LeakDetectionThreshold is very low. This may generate false positives.");
        }
    }
}