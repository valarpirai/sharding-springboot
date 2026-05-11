# Liquibase Migration Guide for Sharded Spring Boot Application

## Overview

This guide explains how to use the Liquibase-based schema migration system for managing database changes across multiple shards in your Spring Boot application.

## Table of Contents

1. [Features](#features)
2. [Configuration](#configuration)
3. [Migration Strategies](#migration-strategies)
4. [Creating Migrations](#creating-migrations)
5. [Running Migrations](#running-migrations)
6. [Rollback](#rollback)
7. [Best Practices](#best-practices)
8. [API Reference](#api-reference)
9. [Idempotency](#idempotency)

---

## Features

- ✅ **Multiple Migration Strategies**: Sequential, Parallel, Wave, and Canary
- ✅ **Dual Database Support**: Separate migrations for global DB and sharded DBs
- ✅ **Progress Tracking**: Real-time monitoring of migration progress
- ✅ **Rollback Support**: Safe rollback mechanisms with validation
- ✅ **Failure Handling**: Fail-fast mode and error recovery
- ✅ **REST API**: Complete API for migration management
- ✅ **Database Agnostic**: Works with PostgreSQL, MySQL, and others

---

## Configuration

### Enable Migrations

Add the following to your `application.properties`:

```properties
# Enable migration management
app.sharding.migration.enabled=true

# Migration file paths
app.sharding.migration.global-change-log-path=db/changelog/global/master-changelog.xml
app.sharding.migration.sharded-change-log-path=db/changelog/sharded/master-changelog.xml

# Default strategy
app.sharding.migration.default-strategy=WAVE

# Parallel execution settings
app.sharding.migration.parallel-threads=5

# Wave strategy settings
app.sharding.migration.wave-size=5
app.sharding.migration.wave-delay-seconds=30

# Canary strategy settings
app.sharding.migration.canary-validation-minutes=5
app.sharding.migration.canary-rollout-strategy=WAVE

# Error handling
app.sharding.migration.fail-fast=true
app.sharding.migration.validate-before-migration=true

# Rollback (enable with caution)
app.sharding.migration.allow-rollback=false
```

### Disable Automatic Migrations

To prevent migrations from running automatically on startup:

```properties
app.sharding.migration.enabled=false
```

You can then trigger migrations manually via REST API.

---

## Migration Strategies

### 1. SEQUENTIAL

Migrates shards one at a time.

**Pros:**
- Safest approach
- Easy to monitor
- Minimal load on infrastructure

**Cons:**
- Slowest option
- Can take hours for many shards

**Use Case:** Production environments with strict change control

### 2. PARALLEL

Migrates all shards simultaneously.

**Pros:**
- Fastest approach
- Completes quickly even with many shards

**Cons:**
- Highest risk
- Heavy load on infrastructure
- Difficult to troubleshoot failures

**Use Case:** Development/staging environments, off-peak maintenance windows

### 3. WAVE

Migrates shards in batches/waves.

**Pros:**
- Balanced speed and safety
- Controlled load on infrastructure
- Progressive rollout

**Cons:**
- Moderate complexity
- Requires tuning wave size

**Use Case:** **Recommended for production** - offers best balance

**Configuration:**
```properties
app.sharding.migration.wave-size=5           # Number of shards per wave
app.sharding.migration.wave-delay-seconds=30 # Wait time between waves
```

### 4. CANARY

Tests migration on one shard first, then proceeds with others.

**Pros:**
- Safest production approach
- Early failure detection
- Validation period before full rollout

**Cons:**
- Longest total time
- Requires monitoring during canary phase

**Use Case:** Critical production changes, major schema updates

**Configuration:**
```properties
app.sharding.migration.canary-validation-minutes=5     # Wait after canary
app.sharding.migration.canary-rollout-strategy=WAVE   # PARALLEL or WAVE
```

---

## Creating Migrations

### Directory Structure

```
src/main/resources/
└── db/
    └── changelog/
        ├── global/
        │   ├── master-changelog.xml
        │   ├── v1.0.0-initial-schema.xml
        │   └── v1.1.0-add-account-status.xml
        └── sharded/
            ├── master-changelog.xml
            ├── v1.0.0-initial-schema.xml
            └── v1.1.0-add-ticket-priority.xml
```

### Master Changelog

The master changelog includes all version-specific changelogs:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">

    <include file="db/changelog/sharded/v1.0.0-initial-schema.xml"/>
    <include file="db/changelog/sharded/v1.1.0-add-ticket-priority.xml"/>
    <!-- Add new versions here -->

</databaseChangeLog>
```

### Creating a New Migration

1. **Create a new XML file** with naming convention: `vX.Y.Z-description.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">

    <changeSet id="10" author="your-name" context="sharded">
        <comment>Add email_verified column to users</comment>

        <addColumn tableName="users">
            <column name="email_verified" type="BOOLEAN" defaultValueBoolean="false">
                <constraints nullable="false"/>
            </column>
        </addColumn>

        <createIndex indexName="idx_users_email_verified" tableName="users">
            <column name="email_verified"/>
        </createIndex>

        <rollback>
            <dropIndex indexName="idx_users_email_verified" tableName="users"/>
            <dropColumn tableName="users" columnName="email_verified"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

2. **Include in master changelog**:

```xml
<include file="db/changelog/sharded/v1.2.0-add-email-verification.xml"/>
```

### ChangeSet Best Practices

- **Unique IDs**: Use sequential numeric IDs or timestamps
- **Context**: Use `context="global"` or `context="sharded"`
- **Author**: Track who created the changeset
- **Comment**: Clear description of changes
- **Rollback**: Always provide rollback instructions
- **Idempotent**: Changes should be safe to run multiple times

---

## Running Migrations

### Via REST API

The migration service exposes REST endpoints for management.

#### Execute Migration

```bash
# Using default strategy (WAVE)
curl -X POST http://localhost:8080/api/admin/migrations/execute

# Using specific strategy
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=CANARY"
```

**Response:**
```json
{
  "startTime": "2025-01-15T10:00:00Z",
  "endTime": "2025-01-15T10:05:30Z",
  "strategy": "WAVE",
  "totalShards": 10,
  "successCount": 10,
  "failureCount": 0,
  "skippedCount": 0,
  "totalExecutionTimeMs": 330000,
  "results": [
    {
      "shardId": "shard1",
      "status": "SUCCESS",
      "changeSetExecuted": 3,
      "targetVersion": "v1.2.0",
      "executionTimeMs": 1500
    }
  ]
}
```

#### Monitor Progress

```bash
# Get progress for all shards
curl http://localhost:8080/api/admin/migrations/progress

# Get progress for specific shard
curl http://localhost:8080/api/admin/migrations/progress/shard1
```

**Response:**
```json
{
  "shard1": {
    "shardId": "shard1",
    "status": "IN_PROGRESS",
    "currentVersion": "v1.1.0",
    "targetVersion": "v1.2.0",
    "totalChangeSets": 5,
    "executedChangeSets": 2,
    "progressPercentage": 40.0,
    "elapsedTimeMs": 3000,
    "currentChangeSet": "Add email_verified column"
  }
}
```

#### Check Status

```bash
# Get status summary
curl http://localhost:8080/api/admin/migrations/status

# Check if migrations are running
curl http://localhost:8080/api/admin/migrations/running
```

### Via Code

Inject `LiquibaseMigrationOrchestrator` in your service:

```java
@Service
public class MyMigrationService {

    @Autowired
    private LiquibaseMigrationOrchestrator orchestrator;

    public void runMigrations() {
        MigrationReport report = orchestrator.migrateAll(MigrationStrategy.WAVE);

        if (report.isFullySuccessful()) {
            log.info("All migrations completed successfully");
        } else {
            log.error("Some migrations failed: {}", report.getFailedShards());
        }
    }
}
```

---

## Rollback

### Enable Rollback

**⚠️ CAUTION**: Rollback should only be enabled when absolutely necessary.

```properties
app.sharding.migration.allow-rollback=true
```

### Rollback by Count

Rollback the last N changesets:

```bash
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "COUNT",
    "count": 2
  }'
```

### Rollback by Tag

Rollback to a specific tag:

```bash
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TAG",
    "tag": "v1.1.0"
  }'
```

### Rollback Specific Shards

```bash
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "COUNT",
    "count": 1,
    "shardIds": ["shard1", "shard2"]
  }'
```

### Creating Tags

Add tags in your changesets:

```xml
<changeSet id="20" author="dev" context="sharded">
    <tagDatabase tag="v1.1.0"/>
</changeSet>
```

---

## Best Practices

### 1. Testing Migrations

Always test migrations in a staging environment first:

```properties
# In staging
app.sharding.migration.default-strategy=PARALLEL  # Fast testing
```

### 2. Progressive Rollout

Use CANARY for critical production changes:

```bash
# Test on canary first
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=CANARY"

# Monitor canary for 10-15 minutes
# If successful, the system automatically proceeds with remaining shards
```

### 3. Monitoring

Monitor migration progress in real-time:

```bash
# In a loop
while true; do
  curl http://localhost:8080/api/admin/migrations/status
  sleep 5
done
```

### 4. Failure Handling

When migrations fail:

1. **Check logs** for specific error messages
2. **Review failed changesets** in the error report
3. **Fix the issue** in your changelog
4. **Re-run migration** (Liquibase tracks completed changesets)

### 5. Dry Run

Test migrations without making changes:

```properties
app.sharding.migration.dry-run=true
```

### 6. Backup Before Migration

Always backup databases before running migrations:

```bash
# Example for PostgreSQL
pg_dump -h localhost -U user -d shard1_db > shard1_backup.sql
```

### 7. Off-Peak Execution

Schedule migrations during low-traffic periods:

```java
@Scheduled(cron = "0 0 2 * * SUN")  // 2 AM every Sunday
public void weeklyMigration() {
    orchestrator.migrateAll(MigrationStrategy.WAVE);
}
```

---

## API Reference

### Migration Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/migrations/execute?strategy={strategy}` | Execute migrations |
| GET | `/api/admin/migrations/progress` | Get all progress |
| GET | `/api/admin/migrations/progress/{shardId}` | Get shard progress |
| GET | `/api/admin/migrations/status` | Get status summary |
| GET | `/api/admin/migrations/running` | Check if running |
| POST | `/api/admin/migrations/rollback` | Rollback migrations |
| DELETE | `/api/admin/migrations/progress` | Clear progress tracking |
| GET | `/api/admin/migrations/health` | Health check |

### Migration Strategies

| Strategy | Description | Speed | Safety | Use Case |
|----------|-------------|-------|--------|----------|
| SEQUENTIAL | One at a time | ⭐ | ⭐⭐⭐⭐⭐ | High-risk changes |
| PARALLEL | All at once | ⭐⭐⭐⭐⭐ | ⭐⭐ | Dev/staging |
| WAVE | Batches | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | **Production (recommended)** |
| CANARY | Test first | ⭐⭐ | ⭐⭐⭐⭐⭐ | Critical changes |

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `app.sharding.migration.enabled` | `false` | Enable migration management |
| `app.sharding.migration.default-strategy` | `WAVE` | Default execution strategy |
| `app.sharding.migration.parallel-threads` | `5` | Thread pool size |
| `app.sharding.migration.wave-size` | `5` | Shards per wave |
| `app.sharding.migration.wave-delay-seconds` | `30` | Delay between waves |
| `app.sharding.migration.canary-validation-minutes` | `5` | Canary wait time |
| `app.sharding.migration.fail-fast` | `true` | Stop on first failure |
| `app.sharding.migration.allow-rollback` | `false` | Enable rollback |

---

## Troubleshooting

### Issue: Migrations Not Running

**Cause**: Migration feature not enabled

**Solution**:
```properties
app.sharding.migration.enabled=true
```

### Issue: Rollback Disabled

**Cause**: Rollback protection enabled

**Solution**:
```properties
app.sharding.migration.allow-rollback=true
```

### Issue: Changelog Not Found

**Cause**: Incorrect file path

**Solution**: Verify paths match your resources directory:
```properties
app.sharding.migration.sharded-change-log-path=db/changelog/sharded/master-changelog.xml
```

### Issue: Migration Stuck

**Cause**: Lock not released

**Solution**:
```sql
-- Clear Liquibase lock (PostgreSQL)
UPDATE DATABASECHANGELOGLOCK SET LOCKED = FALSE WHERE ID = 1;
```

### Issue: Partial Failure in Wave

**Cause**: Some shards failed in a wave

**Solution**:
1. Fix the issue
2. Re-run migration (completed shards will be skipped automatically)

---

## Examples

### Example 1: Weekly Schema Update

```java
@Component
public class WeeklyMigrationTask {

    @Autowired
    private LiquibaseMigrationOrchestrator orchestrator;

    @Scheduled(cron = "0 0 3 * * SUN")
    public void runWeeklyMigration() {
        log.info("Starting weekly migration");

        MigrationReport report = orchestrator.migrateAll(MigrationStrategy.WAVE);

        if (!report.isFullySuccessful()) {
            // Send alert
            alertService.sendAlert("Migration failed for " +
                report.getFailureCount() + " shards");
        }
    }
}
```

### Example 2: Canary Deployment

```bash
#!/bin/bash
# deploy-schema.sh

# 1. Run canary migration
curl -X POST "http://localhost:8080/api/admin/migrations/execute?strategy=CANARY"

# 2. Wait for completion
while [ "$(curl -s http://localhost:8080/api/admin/migrations/running)" == "true" ]; do
  echo "Migration in progress..."
  sleep 10
done

# 3. Check results
curl http://localhost:8080/api/admin/migrations/status
```

### Example 3: Emergency Rollback

```bash
#!/bin/bash
# emergency-rollback.sh

# Enable rollback
# (Requires app.sharding.migration.allow-rollback=true)

# Rollback last changeset on all shards
curl -X POST http://localhost:8080/api/admin/migrations/rollback \
  -H "Content-Type: application/json" \
  -d '{
    "type": "COUNT",
    "count": 1
  }'
```

---

## Idempotency

### TL;DR

- Database changes: **idempotent** — Liquibase skips already-executed changesets
- Concurrent execution: **prevented** — application-level lock returns HTTP 409
- Partial failure recovery: **safe** — re-run applies only to failed/pending shards

### How Liquibase Ensures Idempotency

Before executing a changeset, Liquibase checks `DATABASECHANGELOG`. If the changeset ID already exists it is skipped. Each database (global + all shards) has its own changelog table.

```bash
# First execution
curl -X POST "localhost:8080/api/admin/migrations/execute"
# Executes 5 new changesets on each shard

# Second execution
curl -X POST "localhost:8080/api/admin/migrations/execute"
# All shards return SKIPPED (0 changesets executed)
```

### Concurrent Execution Prevention

The orchestrator holds an application-level lock for the duration of a migration run:

```java
if (!lockManager.tryAcquireLock()) {
    throw new MigrationException(
        "Migration already in progress. Cannot start concurrent migration.");
}
```

A second request during an active migration returns HTTP 409:
```json
{ "error": "MIGRATION_IN_PROGRESS", "message": "Migration already in progress..." }
```

Liquibase also maintains its own per-database `DATABASECHANGELOGLOCK` table, preventing two Liquibase instances from modifying the same database simultaneously.

### Partial Failure Recovery

Failed shards can be safely retried. Already-migrated shards are skipped automatically:

```
Wave 1: shard1–5 SKIPPED (already executed)
Wave 2: shard6–10 SKIPPED (already executed)
Wave 3: shard11 SKIPPED, shard12 SUCCESS (retried), shard13–15 SUCCESS
```

### Non-Idempotent Aspects

| Aspect | Idempotent? | Notes |
|--------|-------------|-------|
| Database changes | YES | Liquibase DATABASECHANGELOG |
| Concurrent execution | PREVENTED | Application lock |
| Per-database locking | PROTECTED | Liquibase DATABASECHANGELOGLOCK |
| Partial failure recovery | SAFE | Re-run skips completed shards |
| Execution time | NO | Each run checks all shards |
| Progress tracking | OVERWRITTEN | ConcurrentHashMap reset per run |
| Audit logs | NO | Each API call logged separately |

### Idempotency Best Practices

**Write rollback scripts:**
```xml
<changeSet id="10" author="dev">
    <addColumn tableName="users">
        <column name="email_verified" type="BOOLEAN"/>
    </addColumn>
    <rollback>
        <dropColumn tableName="users" columnName="email_verified"/>
    </rollback>
</changeSet>
```

**Use preconditions as extra safety:**
```xml
<changeSet id="11" author="dev">
    <preConditions onFail="MARK_RAN">
        <not><columnExists tableName="users" columnName="email_verified"/></not>
    </preConditions>
    <addColumn tableName="users">
        <column name="email_verified" type="BOOLEAN"/>
    </addColumn>
</changeSet>
```

**Never modify an already-executed changeset** — Liquibase detects the checksum mismatch and fails. Create a new changeset instead.

**Use unique, sequential changeset IDs** — reusing IDs causes conflicts.

**Multi-instance deployments**: Liquibase's DB lock handles concurrent app instances, but prefer running migrations from a single designated instance or CI/CD pipeline.

---

## Additional Resources

- [Liquibase Documentation](https://docs.liquibase.com)
- [Spring Boot Liquibase Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.liquibase)
- [Database Refactoring Best Practices](https://www.liquibase.org/get-started/best-practices)

---

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review application logs for error details
3. Consult the Liquibase documentation
4. Contact your DevOps team for production migrations

---

**Last Updated**: January 2025
**Version**: 1.0.0
