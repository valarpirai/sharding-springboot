package com.valarpirai.sharding.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration for the sharding library.
 * Provides observability through distributed tracing and metrics collection.
 */
@Configuration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(name = "sharding.observability.enabled", matchIfMissing = true)
public class OpenTelemetryConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryConfiguration.class);

    public static final String INSTRUMENTATION_NAME = "com.valarpirai.sharding";
    public static final String INSTRUMENTATION_VERSION = "1.0.0";

    // Attribute keys for consistent tagging
    public static final AttributeKey<String> SHARD_ID = AttributeKey.stringKey("shard.id");
    public static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("tenant.id");
    public static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey("operation.type");
    public static final AttributeKey<String> DATA_SOURCE_TYPE = AttributeKey.stringKey("datasource.type");
    public static final AttributeKey<String> QUERY_TYPE = AttributeKey.stringKey("query.type");
    public static final AttributeKey<Boolean> CACHE_HIT = AttributeKey.booleanKey("cache.hit");

    /**
     * Tracer for distributed tracing of sharding operations.
     */
    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(name = "shardingTracer")
    public Tracer shardingTracer(OpenTelemetry openTelemetry) {
        logger.info("Creating OpenTelemetry tracer for sharding operations");
        return openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    /**
     * Meter for collecting metrics about sharding operations.
     */
    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(name = "shardingMeter")
    public Meter shardingMeter(OpenTelemetry openTelemetry) {
        logger.info("Creating OpenTelemetry meter for sharding metrics");
        return openTelemetry.getMeter(INSTRUMENTATION_NAME);
    }

    /**
     * Counter for tracking shard lookup operations.
     */
    @Bean
    @ConditionalOnBean(Meter.class)
    @ConditionalOnMissingBean(name = "shardLookupCounter")
    public LongCounter shardLookupCounter(Meter meter) {
        return meter
            .counterBuilder("sharding.shard_lookups")
            .setDescription("Number of shard lookup operations")
            .setUnit("1")
            .build();
    }

    /**
     * Counter for tracking database connection operations.
     */
    @Bean
    @ConditionalOnBean(Meter.class)
    @ConditionalOnMissingBean(name = "connectionCounter")
    public LongCounter connectionCounter(Meter meter) {
        return meter
            .counterBuilder("sharding.connections")
            .setDescription("Number of database connections obtained")
            .setUnit("1")
            .build();
    }

    /**
     * Counter for tracking transaction operations.
     */
    @Bean
    @ConditionalOnBean(Meter.class)
    @ConditionalOnMissingBean(name = "transactionCounter")
    public LongCounter transactionCounter(Meter meter) {
        return meter
            .counterBuilder("sharding.transactions")
            .setDescription("Number of transaction operations")
            .setUnit("1")
            .build();
    }

    /**
     * Histogram for tracking shard lookup latency.
     */
    @Bean
    @ConditionalOnBean(Meter.class)
    @ConditionalOnMissingBean(name = "shardLookupLatency")
    public LongHistogram shardLookupLatency(Meter meter) {
        return meter
            .histogramBuilder("sharding.shard_lookup_duration")
            .setDescription("Duration of shard lookup operations")
            .setUnit("ms")
            .ofLongs()
            .build();
    }

    /**
     * Histogram for tracking connection acquisition latency.
     */
    @Bean
    @ConditionalOnBean(Meter.class)
    @ConditionalOnMissingBean(name = "connectionAcquisitionLatency")
    public LongHistogram connectionAcquisitionLatency(Meter meter) {
        return meter
            .histogramBuilder("sharding.connection_acquisition_duration")
            .setDescription("Duration of database connection acquisition")
            .setUnit("ms")
            .ofLongs()
            .build();
    }

    /**
     * Utility methods for creating common attributes.
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingObservabilityUtils observabilityUtils() {
        return new ShardingObservabilityUtils();
    }

    /**
     * Utility class for creating consistent OpenTelemetry attributes and spans.
     */
    public static class ShardingObservabilityUtils {

        /**
         * Create attributes for shard operations.
         */
        public Attributes createShardAttributes(String shardId, Long tenantId, String operationType) {
            Attributes attributes = Attributes.of(OPERATION_TYPE, operationType);

            if (shardId != null) {
                attributes = attributes.toBuilder().put(SHARD_ID, shardId).build();
            }

            if (tenantId != null) {
                attributes = attributes.toBuilder().put(TENANT_ID, tenantId.toString()).build();
            }

            return attributes;
        }

        /**
         * Create attributes for datasource operations.
         */
        public Attributes createDataSourceAttributes(String shardId, String dataSourceType, String queryType) {
            return Attributes.of(
                SHARD_ID, shardId != null ? shardId : "global",
                DATA_SOURCE_TYPE, dataSourceType,
                QUERY_TYPE, queryType
            );
        }

        /**
         * Create attributes for cache operations.
         */
        public Attributes createCacheAttributes(String operationType, boolean cacheHit) {
            return Attributes.of(
                OPERATION_TYPE, operationType,
                CACHE_HIT, cacheHit
            );
        }

        /**
         * Create attributes for transaction operations.
         */
        public Attributes createTransactionAttributes(String shardId, Long tenantId, String transactionType) {
            Attributes attributes = Attributes.of(OPERATION_TYPE, transactionType);

            if (shardId != null) {
                attributes = attributes.toBuilder().put(SHARD_ID, shardId).build();
            }

            if (tenantId != null) {
                attributes = attributes.toBuilder().put(TENANT_ID, tenantId.toString()).build();
            }

            return attributes;
        }
    }
}