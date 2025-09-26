package com.valarpirai.sharding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpenTelemetry observability features.
 */
@ConfigurationProperties(prefix = "sharding.observability")
public class ObservabilityConfigProperties {

    /**
     * Whether OpenTelemetry observability is enabled.
     */
    private boolean enabled = true;

    /**
     * Whether to collect detailed metrics for shard operations.
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to create traces for shard operations.
     */
    private boolean tracingEnabled = true;

    /**
     * Custom instrumentation name for traces and metrics.
     */
    private String instrumentationName = "com.valarpirai.sharding";

    /**
     * Custom instrumentation version.
     */
    private String instrumentationVersion = "1.0.0";

    /**
     * Configuration for specific observability features.
     */
    private FeatureConfig features = new FeatureConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }

    public String getInstrumentationName() {
        return instrumentationName;
    }

    public void setInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
    }

    public String getInstrumentationVersion() {
        return instrumentationVersion;
    }

    public void setInstrumentationVersion(String instrumentationVersion) {
        this.instrumentationVersion = instrumentationVersion;
    }

    public FeatureConfig getFeatures() {
        return features;
    }

    public void setFeatures(FeatureConfig features) {
        this.features = features;
    }

    public static class FeatureConfig {

        /**
         * Whether to trace connection operations.
         */
        private boolean connectionTracing = true;

        /**
         * Whether to trace transaction operations.
         */
        private boolean transactionTracing = true;

        /**
         * Whether to trace shard lookup operations.
         */
        private boolean shardLookupTracing = true;

        /**
         * Whether to collect connection metrics.
         */
        private boolean connectionMetrics = true;

        /**
         * Whether to collect transaction metrics.
         */
        private boolean transactionMetrics = true;

        /**
         * Whether to collect shard lookup metrics.
         */
        private boolean shardLookupMetrics = true;

        public boolean isConnectionTracing() {
            return connectionTracing;
        }

        public void setConnectionTracing(boolean connectionTracing) {
            this.connectionTracing = connectionTracing;
        }

        public boolean isTransactionTracing() {
            return transactionTracing;
        }

        public void setTransactionTracing(boolean transactionTracing) {
            this.transactionTracing = transactionTracing;
        }

        public boolean isShardLookupTracing() {
            return shardLookupTracing;
        }

        public void setShardLookupTracing(boolean shardLookupTracing) {
            this.shardLookupTracing = shardLookupTracing;
        }

        public boolean isConnectionMetrics() {
            return connectionMetrics;
        }

        public void setConnectionMetrics(boolean connectionMetrics) {
            this.connectionMetrics = connectionMetrics;
        }

        public boolean isTransactionMetrics() {
            return transactionMetrics;
        }

        public void setTransactionMetrics(boolean transactionMetrics) {
            this.transactionMetrics = transactionMetrics;
        }

        public boolean isShardLookupMetrics() {
            return shardLookupMetrics;
        }

        public void setShardLookupMetrics(boolean shardLookupMetrics) {
            this.shardLookupMetrics = shardLookupMetrics;
        }
    }
}