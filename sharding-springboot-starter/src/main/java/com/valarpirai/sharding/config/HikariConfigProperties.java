package com.valarpirai.sharding.config;

import lombok.Data;
import java.time.Duration;

/**
 * Configuration properties for HikariCP connection pool settings.
 * Supports comprehensive HikariCP configuration options.
 */
@Data
public class HikariConfigProperties {

    // Optimal defaults based on HikariCP best practices
    // Pool sizing - Conservative defaults for production use
    private Integer maximumPoolSize = 20;  // Good balance for most applications
    private Integer minimumIdle = 5;       // Maintain some idle connections

    // Connection timeouts - Optimized for reliability
    private Duration connectionTimeout = Duration.ofSeconds(30);    // Standard timeout
    private Duration idleTimeout = Duration.ofMinutes(10);         // Remove idle connections after 10 min
    private Duration maxLifetime = Duration.ofMinutes(30);         // Connection max lifetime
    private Duration validationTimeout = Duration.ofSeconds(5);    // Quick validation

    // Connection testing - Optimal defaults for production
    private String connectionTestQuery = null;  // Database-specific, set by driver
    private Duration leakDetectionThreshold = Duration.ZERO;  // Disabled by default, enable in dev

    // Pool behavior - Production-ready defaults
    private Boolean autoCommit = true;          // Standard JDBC behavior
    private String transactionIsolation = null; // Use database default
    private Boolean readOnly = false;          // Default to read-write
    private String catalog = null;             // Use connection default
    private String schema = null;              // Use connection default

    // JDBC driver properties
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    private String password;

    // Connection initialization - Production defaults
    private String connectionInitSql = null;           // No initialization SQL by default
    private Integer initializationFailTimeout = 1;     // Fast fail for misconfigured pools

    // Pool monitoring - Enable for observability
    private Boolean registerMbeans = true;             // Enable JMX monitoring
    private String poolName = null;                    // Auto-generated if not specified

    // Advanced options - Conservative defaults
    private Boolean isolateInternalQueries = false;    // Standard behavior
    private Boolean allowPoolSuspension = false;       // Disable suspension by default
}