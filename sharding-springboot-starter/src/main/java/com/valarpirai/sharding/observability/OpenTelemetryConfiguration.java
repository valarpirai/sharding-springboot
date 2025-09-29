package com.valarpirai.sharding.observability;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal OpenTelemetry configuration for the sharding library.
 * Enables @WithSpan annotations without explicit bean configuration.
 * OpenTelemetry auto-configuration will handle instrumentation when available.
 */
@Configuration
@ConditionalOnClass(OpenTelemetry.class)
public class OpenTelemetryConfiguration {

    public static final String INSTRUMENTATION_NAME = "com.valarpirai.sharding";
    public static final String INSTRUMENTATION_VERSION = "1.0.0";

    // This empty configuration class allows @WithSpan annotations to work
    // when OpenTelemetry is on the classpath. The actual instrumentation
    // is handled by OpenTelemetry's auto-instrumentation agent or
    // spring-boot-starter-actuator's OpenTelemetry integration.
}