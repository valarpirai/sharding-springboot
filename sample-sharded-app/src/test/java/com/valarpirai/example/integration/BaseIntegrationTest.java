package com.valarpirai.example.integration;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.TenantShardMappingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

/**
 * Base class for integration tests using TestContainers.
 * Sets up PostgreSQL containers for global database and multiple shards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    // Global database container
    @Container
    protected static final PostgreSQLContainer<?> globalDb = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("global_test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(false);

    // Shard 1 container
    @Container
    protected static final PostgreSQLContainer<?> shard1Db = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("shard1_test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(false);

    // Shard 2 container
    @Container
    protected static final PostgreSQLContainer<?> shard2Db = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("shard2_test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(false);

    @Autowired
    protected TenantShardMappingRepository tenantShardMappingRepository;

    @Autowired
    protected DataSource globalDataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Configure Spring properties dynamically based on TestContainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Global database configuration
        registry.add("app.sharding.global-db.url", globalDb::getJdbcUrl);
        registry.add("app.sharding.global-db.username", globalDb::getUsername);
        registry.add("app.sharding.global-db.password", globalDb::getPassword);
        registry.add("app.sharding.global-db.driver-class-name", globalDb::getDriverClassName);

        // Shard 1 configuration
        registry.add("app.sharding.shard1.master.url", shard1Db::getJdbcUrl);
        registry.add("app.sharding.shard1.master.username", shard1Db::getUsername);
        registry.add("app.sharding.shard1.master.password", shard1Db::getPassword);
        registry.add("app.sharding.shard1.master.driver-class-name", shard1Db::getDriverClassName);
        registry.add("app.sharding.shard1.latest", () -> "true");
        registry.add("app.sharding.shard1.region", () -> "us-east-1");

        // Shard 2 configuration
        registry.add("app.sharding.shard2.master.url", shard2Db::getJdbcUrl);
        registry.add("app.sharding.shard2.master.username", shard2Db::getUsername);
        registry.add("app.sharding.shard2.master.password", shard2Db::getPassword);
        registry.add("app.sharding.shard2.master.driver-class-name", shard2Db::getDriverClassName);
        registry.add("app.sharding.shard2.latest", () -> "false");
        registry.add("app.sharding.shard2.region", () -> "us-west-2");

        // Sharding configuration
        registry.add("app.sharding.tenant-column-names", () -> "account_id");
        registry.add("app.sharding.cache.enabled", () -> "true");
        registry.add("app.sharding.cache.type", () -> "CAFFEINE");
        registry.add("app.sharding.validation.strictness", () -> "STRICT");

        // Disable Redis for tests
        registry.add("spring.data.redis.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "caffeine");

        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @BeforeAll
    static void beforeAll() {
        // Ensure containers are started
        if (!globalDb.isRunning()) {
            globalDb.start();
        }
        if (!shard1Db.isRunning()) {
            shard1Db.start();
        }
        if (!shard2Db.isRunning()) {
            shard2Db.start();
        }
    }

    @AfterEach
    void cleanupTenantContext() {
        TenantContext.clear();
    }
}
