# OpenTelemetry Integration Guide for Sharding Library

## 🎯 **Overview**

The Galaxy Sharding library includes minimal OpenTelemetry support through @WithSpan annotations for distributed tracing. Metrics collection and complex instrumentation have been simplified to keep the library lightweight.

## 📊 **What Gets Traced and Measured**

### **🔄 Automatic Instrumentation with @WithSpan**

The library automatically traces critical operations using `@WithSpan` annotations:

#### **RoutingDataSource Operations**
- `sharding.datasource.get_connection` - Connection acquisition
- `sharding.datasource.get_connection_with_credentials` - Connection with credentials
- `sharding.datasource.determine_target` - Shard routing decisions

#### **Transaction Management Operations**
- Transaction operations are handled automatically by Spring's dual DataSource configuration
- No custom transaction manager instrumentation in simplified setup

#### **ShardLookupService Operations**
- `sharding.shard_lookup.find_by_tenant_id` - Tenant-to-shard lookups
- `sharding.shard_lookup.create_mapping` - New tenant mapping creation
- `sharding.shard_lookup.get_latest_shard_id` - Latest shard queries

### **📈 Metrics Collection**

**Note**: Manual metrics collection has been removed from the library to keep it lightweight.

#### **Available via OpenTelemetry Auto-Instrumentation**
When using OpenTelemetry auto-instrumentation or Spring Boot starter, you'll get:
- JVM metrics (memory, GC, threads)
- HTTP request metrics
- Database connection pool metrics (HikariCP)
- Spring transaction metrics

#### **Custom Metrics**
If you need specific sharding metrics, you can add them in your application:
```java
@Component
public class ShardingMetrics {
    private final Counter shardLookups;

    public ShardingMetrics(MeterRegistry meterRegistry) {
        this.shardLookups = Counter.builder("sharding.lookups")
            .description("Shard lookup operations")
            .register(meterRegistry);
    }
}

## ⚙️ **Configuration**

### **Basic Setup**

Add OpenTelemetry to your Spring Boot application:

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>1.32.0-alpha</version>
</dependency>
```

### **Application Properties**

```properties
# Enable/disable observability features
sharding.observability.enabled=true
sharding.observability.metrics-enabled=true
sharding.observability.tracing-enabled=true

# Custom instrumentation details
sharding.observability.instrumentation-name=com.yourcompany.sharding
sharding.observability.instrumentation-version=2.0.0

# Fine-grained feature control
sharding.observability.features.connection-tracing=true
sharding.observability.features.transaction-tracing=true
sharding.observability.features.shard-lookup-tracing=true
sharding.observability.features.connection-metrics=true
sharding.observability.features.transaction-metrics=true
sharding.observability.features.shard-lookup-metrics=true

# Standard OpenTelemetry configuration
otel.service.name=ticket-management-system
otel.service.version=1.0.0
otel.exporter.otlp.endpoint=http://localhost:4317
otel.exporter.otlp.protocol=grpc
```

### **YAML Configuration**

```yaml
sharding:
  observability:
    enabled: true
    metrics-enabled: true
    tracing-enabled: true
    instrumentation-name: "com.yourcompany.sharding"
    instrumentation-version: "2.0.0"
    features:
      connection-tracing: true
      transaction-tracing: true
      shard-lookup-tracing: true
      connection-metrics: true
      transaction-metrics: true
      shard-lookup-metrics: true

otel:
  service:
    name: "ticket-management-system"
    version: "1.0.0"
  exporter:
    otlp:
      endpoint: "http://localhost:4317"
      protocol: "grpc"
```

## 🚀 **Usage Examples**

### **Automatic Tracing**

No code changes needed! The library automatically traces all sharding operations:

```java
@Service
public class UserService {

    @Transactional  // ← Automatically traced as "sharding.transaction.get_transaction"
    public User createUser(UserRequest request, Long tenantId) {
        // Connection acquisition automatically traced as "sharding.datasource.get_connection"
        // Shard lookup automatically traced as "sharding.shard_lookup.find_by_tenant_id"
        return userRepository.save(new User(request));
    }
}
```

### **Custom Tracing (Optional)**

You can add custom spans to complement automatic tracing:

```java
@Service
public class UserService {

    @Autowired
    private Tracer tracer;

    @Transactional
    public List<User> processUserBatch(List<UserRequest> requests, Long tenantId) {
        Span batchSpan = tracer.spanBuilder("user.batch_processing")
            .setAttribute("batch.size", requests.size())
            .setAttribute("tenant.id", tenantId.toString())
            .startSpan();

        try (Scope scope = batchSpan.makeCurrent()) {
            return requests.stream()
                .map(request -> userRepository.save(new User(request)))
                .collect(Collectors.toList());
        } catch (Exception e) {
            batchSpan.recordException(e);
            throw e;
        } finally {
            batchSpan.end();
        }
    }
}
```

## 📊 **Monitoring and Alerting**

### **Key Metrics to Monitor**

#### **Performance Metrics**
```promql
# Connection acquisition latency
histogram_quantile(0.95, sharding_connection_acquisition_duration_bucket)

# Shard lookup latency
histogram_quantile(0.95, sharding_shard_lookup_duration_bucket)

# Connection success rate
rate(sharding_connections{operation_type="success"}[5m]) /
rate(sharding_connections[5m])
```

#### **Error Metrics**
```promql
# Transaction rollback rate
rate(sharding_transactions{operation_type="error"}[5m])

# Shard lookup failures
rate(sharding_shard_lookups{operation_type="error"}[5m])

# Connection failures
rate(sharding_connections{operation_type="error"}[5m])
```

### **Distributed Tracing**

View complete request flows across shards:

1. **Jaeger UI**: `http://localhost:16686`
2. **Zipkin UI**: `http://localhost:9411`
3. **Application traces** show:
   - Which shard was selected
   - Connection acquisition time
   - Transaction boundaries
   - Query execution within shards
   - Cache hit/miss patterns

## 🔧 **Integration with Observability Tools**

### **Prometheus + Grafana**

```yaml
# docker-compose.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus
    ports: ["9090:9090"]

  grafana:
    image: grafana/grafana
    ports: ["3000:3000"]

  otel-collector:
    image: otel/opentelemetry-collector-contrib
    ports: ["4317:4317", "8889:8889"]
```

### **Sample Grafana Queries**

```promql
# Shard distribution
sum by (shard_id) (rate(sharding_connections[5m]))

# Tenant activity
sum by (tenant_id) (rate(sharding_shard_lookups[5m]))

# Connection pool utilization
sharding_connection_acquisition_duration_sum / sharding_connection_acquisition_duration_count
```

## 🎯 **Best Practices**

### **Production Recommendations**

1. **Sampling Configuration**
```properties
otel.traces.sampler=traceidratio
otel.traces.sampler.arg=0.1  # Sample 10% of traces
```

2. **Resource Attributes**
```properties
otel.resource.attributes=service.name=ticket-system,service.version=1.0.0,deployment.environment=production
```

3. **Batch Export Configuration**
```properties
otel.exporter.otlp.metrics.temporality.preference=CUMULATIVE
otel.metric.export.interval=30000  # Export every 30 seconds
```

### **Development Setup**

1. **Full Tracing**
```properties
otel.traces.sampler=always_on  # Trace everything in development
```

2. **Local Export**
```properties
otel.exporter.otlp.endpoint=http://localhost:4317
otel.logs.exporter=logging
otel.metrics.exporter=logging
```

## 🚨 **Troubleshooting**

### **Common Issues**

#### **No Traces Appearing**
```bash
# Check if OpenTelemetry is properly configured
curl -i http://localhost:8080/actuator/metrics | grep sharding

# Verify OTLP collector is running
docker logs otel-collector
```

#### **High Overhead**
```properties
# Reduce sampling rate
otel.traces.sampler=traceidratio
otel.traces.sampler.arg=0.01

# Disable specific features
sharding.observability.features.connection-metrics=false
```

#### **Missing Shard Information**
```java
// Ensure tenant context is set before operations
TenantContext.setTenantInfo(new TenantInfo(tenantId, shardId, false));
```

The OpenTelemetry integration provides deep visibility into your sharded application's performance and behavior, enabling proactive monitoring and rapid troubleshooting! 🚀