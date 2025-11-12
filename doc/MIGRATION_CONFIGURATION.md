# Migration Configuration Guide

## Overview

The sample application is configured to use the **custom Liquibase Migration Orchestrator** instead of Spring Boot's default auto-migration. This provides fine-grained control over when and how database migrations are executed across multiple shards.

---

## Configuration Changes

### 1. Disabled Spring Boot's Auto-Migration

**File**: `sample-sharded-app/src/main/resources/application.properties`

```properties
# Disable default auto-configuration for DataSource and Liquibase
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration

# Disable Spring Boot's automatic Liquibase execution
spring.liquibase.enabled=false
```

**Why?**
- Prevents Spring Boot from automatically running Liquibase migrations on startup
- Gives full control to our custom orchestrator
- Allows API-driven migration execution with various strategies

### 2. Custom Migration Orchestrator Configuration

```properties
# Enable migrations via our custom orchestrator
app.sharding.migration.enabled=true

# Migrate global database on startup (set to false for manual control via API)
app.sharding.migration.migrate-global-db=false

# Changelog paths
app.sharding.migration.global-change-log-path=db/changelog/global/master-changelog.xml
app.sharding.migration.sharded-change-log-path=db/changelog/sharded/master-changelog.xml

# Default migration strategy (SEQUENTIAL, PARALLEL, WAVE, CANARY)
app.sharding.migration.default-strategy=WAVE

# Parallel execution configuration
app.sharding.migration.parallel-threads=5

# Wave strategy configuration
app.sharding.migration.wave-size=5
app.sharding.migration.wave-delay-seconds=30

# Canary strategy configuration
app.sharding.migration.canary-validation-minutes=5
app.sharding.migration.canary-rollout-strategy=WAVE

# Failure handling
app.sharding.migration.fail-fast=true
app.sharding.migration.validate-before-migration=true

# Rollback support (use with caution)
app.sharding.migration.allow-rollback=false

# Dry run mode (no actual changes, just validation)
app.sharding.migration.dry-run=false

# Liquibase contexts and labels
app.sharding.migration.contexts=default
app.sharding.migration.labels=
```

---

## Migration Control

### Manual Migration via API

The custom orchestrator provides REST API endpoints for migration control:

#### 1. Execute Migrations

```bash
# Execute with default strategy (WAVE)
curl -X POST http://localhost:8080/api/admin/migrations/execute

# Execute with specific strategy
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=PARALLEL"
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=SEQUENTIAL"
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=WAVE"
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=CANARY"
```

**Response:**
```json
{
  "strategy": "WAVE",
  "totalShards": 10,
  "successCount": 10,
  "failureCount": 0,
  "skippedCount": 0,
  "totalExecutionTimeMs": 45000,
  "results": [
    {
      "shardId": "shard1",
      "status": "SUCCESS",
      "changeSetExecuted": 5,
      "currentVersion": "1.0.0",
      "targetVersion": "1.1.0",
      "executionTimeMs": 4500
    }
  ]
}
```

#### 2. Check Migration Progress

```bash
# Get progress for all shards
curl http://localhost:8080/api/admin/migrations/progress

# Get progress for specific shard
curl http://localhost:8080/api/admin/migrations/progress/shard1
```

#### 3. Check Migration Status

```bash
# Check if migrations are running
curl http://localhost:8080/api/admin/migrations/running

# Get status summary
curl http://localhost:8080/api/admin/migrations/status
```

#### 4. Health Check

```bash
curl http://localhost:8080/api/admin/migrations/health
```

**Response:**
```json
{
  "status": "UP",
  "service": "migration",
  "migrationsInProgress": "false"
}
```

---

## Migration Strategies

### 1. SEQUENTIAL
Migrates shards one at a time in order.

**Use Case**: Maximum safety, when you want to observe each shard carefully

**Pros**:
- Safest approach
- Easy to monitor
- Can stop immediately if issues detected

**Cons**:
- Slowest
- Not suitable for large number of shards

### 2. PARALLEL
Migrates all shards simultaneously.

**Use Case**: Fast migration when you're confident in the changes

**Pros**:
- Fastest approach
- Suitable for tested migrations

**Cons**:
- If something goes wrong, affects all shards
- Higher resource usage

### 3. WAVE (Recommended)
Migrates shards in waves/batches.

**Use Case**: Balance between speed and safety

**Pros**:
- Good balance of speed and safety
- Can catch issues before affecting all shards
- Configurable wave size and delay

**Cons**:
- More complex than sequential
- Requires tuning of wave parameters

**Configuration:**
```properties
app.sharding.migration.wave-size=5
app.sharding.migration.wave-delay-seconds=30
```

### 4. CANARY
Migrates a canary shard first, validates, then rolls out to others.

**Use Case**: Production migrations where validation is critical

**Pros**:
- Safest for production
- Validates on real shard before full rollout
- Can abort before affecting all shards

**Cons**:
- Slowest strategy
- Requires canary shard configuration

**Configuration:**
```properties
app.sharding.migration.canary-validation-minutes=5
app.sharding.migration.canary-rollout-strategy=WAVE
```

---

## Startup Behavior

### Current Configuration (Manual Control)

```properties
app.sharding.migration.migrate-global-db=false
```

**Behavior**:
- Application starts without running migrations
- Migrations must be triggered via API endpoints
- Full control over when migrations happen

**Advantages**:
- No startup delays
- Test application startup without migrations
- Control migration timing in production
- Can validate application state before migrating

### Alternative: Auto-Migration on Startup

If you want to enable automatic migration on startup:

```properties
app.sharding.migration.migrate-global-db=true
```

**Behavior**:
- Global database migrated automatically on startup
- Shard migrations still require API trigger
- Application waits for global DB migration to complete

**Use Case**:
- Development environments
- When global DB changes are frequent
- When you want immediate schema updates

---

## Rollback Support

### Enable Rollback

```properties
app.sharding.migration.allow-rollback=true
```

### Execute Rollback

```bash
# Rollback by count (last N changesets)
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "COUNT",
    "count": 3,
    "shardIds": ["shard1", "shard2"]
  }'

# Rollback to tag
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TAG",
    "tag": "v1.0.0",
    "shardIds": ["shard1"]
  }'
```

⚠️ **Warning**: Rollback should be used with extreme caution in production!

---

## Dry Run Mode

Test migrations without actually executing them:

```properties
app.sharding.migration.dry-run=true
```

**Behavior**:
- Validates changelog files
- Checks database connections
- Reports what would be executed
- No actual database changes

**Use Case**:
- Testing migration setup
- Validating changelog syntax
- Pre-production verification

---

## Idempotency

The migration orchestrator is **idempotent**:

- Already-executed changesets are automatically skipped
- Safe to run multiple times
- Concurrent executions are prevented via application-level locking

**Example:**
```bash
# First execution - runs 5 changesets
curl -X POST http://localhost:8080/api/admin/migrations/execute

# Second execution - all shards return SKIPPED
curl -X POST http://localhost:8080/api/admin/migrations/execute
```

See [IDEMPOTENCY.md](./IDEMPOTENCY.md) for detailed information.

---

## Best Practices

### 1. Use Wave Strategy in Production
```properties
app.sharding.migration.default-strategy=WAVE
app.sharding.migration.wave-size=5
app.sharding.migration.wave-delay-seconds=30
```

### 2. Keep Auto-Migration Disabled
```properties
app.sharding.migration.migrate-global-db=false
```
- Gives you control over when migrations happen
- Allows validation before migration
- Prevents startup delays

### 3. Monitor Migration Progress
```bash
# Poll progress during migration
while [ "$(curl -s http://localhost:8080/api/admin/migrations/running)" == "true" ]; do
  echo "Migration in progress..."
  curl -s http://localhost:8080/api/admin/migrations/progress | jq '.[] | "\(.shardId): \(.status)"'
  sleep 5
done
```

### 4. Test in Staging First
```bash
# Enable dry-run in staging
curl -X POST http://localhost:8080/api/admin/migrations/execute?strategy=WAVE

# Review results before production
```

### 5. Use Fail-Fast
```properties
app.sharding.migration.fail-fast=true
```
- Stops immediately if a shard migration fails
- Prevents cascade failures

---

## Migration Workflow

### Development Environment

1. **Create changelog files** in `src/main/resources/db/changelog/`
2. **Start application** (no auto-migration)
3. **Trigger migration via API**:
   ```bash
   curl -X POST http://localhost:8080/api/admin/migrations/execute?strategy=SEQUENTIAL
   ```
4. **Verify results**
5. **Iterate as needed**

### Production Environment

1. **Deploy new version** with changelog updates
2. **Verify application starts** (without migrations)
3. **Execute canary migration**:
   ```bash
   curl -X POST http://localhost:8080/api/admin/migrations/execute?strategy=CANARY
   ```
4. **Monitor progress**:
   ```bash
   curl http://localhost:8080/api/admin/migrations/progress
   ```
5. **Validate canary shard**
6. **Wait for automatic rollout** (if canary succeeds)

---

## Comparison: Spring Boot Auto vs Custom Orchestrator

| Feature | Spring Boot Auto | Custom Orchestrator |
|---------|-----------------|---------------------|
| **Execution Time** | On startup | On-demand via API |
| **Control** | Limited | Full control |
| **Strategies** | Single database | Multiple (SEQUENTIAL, PARALLEL, WAVE, CANARY) |
| **Multi-Shard** | ❌ No | ✅ Yes |
| **Progress Tracking** | ❌ No | ✅ Real-time API |
| **Rollback** | Manual | API-driven |
| **Dry Run** | ❌ No | ✅ Yes |
| **Idempotency** | ✅ Yes | ✅ Yes + Application Lock |
| **Monitoring** | Logs only | API + Progress tracking |

---

## Troubleshooting

### Issue: Migrations Not Available

**Check:**
```properties
app.sharding.migration.enabled=true
```

**Verify:**
```bash
curl http://localhost:8080/api/admin/migrations/health
```

### Issue: Concurrent Execution Error

**Error Response:**
```json
{
  "error": "MIGRATION_IN_PROGRESS",
  "message": "Migration already in progress. Cannot start concurrent migration."
}
```

**Solution**: Wait for current migration to complete or check status:
```bash
curl http://localhost:8080/api/admin/migrations/running
```

### Issue: Changelog Not Found

**Check paths:**
```properties
app.sharding.migration.global-change-log-path=db/changelog/global/master-changelog.xml
app.sharding.migration.sharded-change-log-path=db/changelog/sharded/master-changelog.xml
```

**Verify files exist** in `src/main/resources/`

---

## Summary

✅ **Spring Boot's auto-migration is DISABLED**
✅ **Custom orchestrator is ENABLED**
✅ **Manual control via API endpoints**
✅ **Multiple migration strategies available**
✅ **No automatic startup migrations** (unless explicitly configured)
✅ **Full observability and control**

Use the migration API endpoints to execute migrations when ready, with the strategy that best fits your needs!

---

**Related Documentation**:
- [IDEMPOTENCY.md](./IDEMPOTENCY.md) - Migration idempotency details
- [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) - Complete migration guide
- [ZERO_DOWNTIME_BEST_PRACTICES.md](./ZERO_DOWNTIME_BEST_PRACTICES.md) - Production deployment strategies
