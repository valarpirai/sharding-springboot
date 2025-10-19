package com.example.controller;

import com.valarpirai.sharding.migration.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for managing database migrations across shards.
 * Only enabled when migration feature is enabled.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/migrations")
@RequiredArgsConstructor
@Tag(name = "Migration Management", description = "APIs for managing schema migrations across shards")
@ConditionalOnProperty(prefix = "app.sharding.migration", name = "enabled", havingValue = "true")
public class MigrationController {

    private final LiquibaseMigrationOrchestrator migrationOrchestrator;
    private final MigrationProgressTracker progressTracker;

    /**
     * Execute migrations on all shards using specified strategy.
     * This endpoint is idempotent - already-executed changesets will be skipped.
     * However, concurrent executions are prevented for safety.
     */
    @PostMapping("/execute")
    @Operation(summary = "Execute migrations", description = "Run migrations on all shards using the specified strategy")
    public ResponseEntity<?> executeMigration(
            @RequestParam(defaultValue = "WAVE") MigrationStrategy strategy) {

        log.info("Received migration request with strategy: {}", strategy);

        try {
            MigrationReport report = migrationOrchestrator.migrateAll(strategy);
            return ResponseEntity.ok(report);
        } catch (MigrationException e) {
            if (e.getMessage().contains("already in progress")) {
                log.warn("Migration already in progress");
                return ResponseEntity.status(409) // Conflict
                        .body(Map.of(
                                "error", "MIGRATION_IN_PROGRESS",
                                "message", e.getMessage()
                        ));
            }
            log.error("Migration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Migration execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get real-time progress of ongoing migrations.
     */
    @GetMapping("/progress")
    @Operation(summary = "Get migration progress", description = "Get real-time progress of all ongoing migrations")
    public ResponseEntity<Map<String, MigrationProgress>> getProgress() {
        return ResponseEntity.ok(progressTracker.getAllProgress());
    }

    /**
     * Get progress for a specific shard.
     */
    @GetMapping("/progress/{shardId}")
    @Operation(summary = "Get shard migration progress", description = "Get migration progress for a specific shard")
    public ResponseEntity<MigrationProgress> getShardProgress(@PathVariable String shardId) {
        MigrationProgress progress = progressTracker.getProgress(shardId);

        if (progress == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(progress);
    }

    /**
     * Get status summary of migrations.
     */
    @GetMapping("/status")
    @Operation(summary = "Get migration status", description = "Get summary of migration statuses across all shards")
    public ResponseEntity<Map<MigrationStatus, Long>> getStatusSummary() {
        return ResponseEntity.ok(progressTracker.getStatusSummary());
    }

    /**
     * Check if migrations are currently running.
     */
    @GetMapping("/running")
    @Operation(summary = "Check if migrations running", description = "Check if any migrations are currently in progress")
    public ResponseEntity<Boolean> isMigrationRunning() {
        return ResponseEntity.ok(progressTracker.hasMigrationsInProgress());
    }

    /**
     * Clear migration progress tracking.
     */
    @DeleteMapping("/progress")
    @Operation(summary = "Clear progress tracking", description = "Clear all migration progress tracking data")
    public ResponseEntity<Void> clearProgress() {
        progressTracker.clear();
        return ResponseEntity.noContent().build();
    }

    /**
     * Rollback migrations.
     */
    @PostMapping("/rollback")
    @Operation(summary = "Rollback migrations", description = "Rollback migrations on specified shards (use with caution)")
    public ResponseEntity<?> rollbackMigration(@RequestBody RollbackRequest request) {
        log.warn("Received rollback request: type={}, count={}, tag={}",
                 request.getType(), request.getCount(), request.getTag());

        try {
            MigrationReport report = migrationOrchestrator.rollback(request);
            return ResponseEntity.ok(report);
        } catch (MigrationException e) {
            if (e.getMessage().contains("already in progress")) {
                log.warn("Migration/rollback already in progress");
                return ResponseEntity.status(409) // Conflict
                        .body(Map.of(
                                "error", "OPERATION_IN_PROGRESS",
                                "message", e.getMessage()
                        ));
            }
            log.error("Rollback failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Rollback execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint for migration service.
     */
    @GetMapping("/health")
    @Operation(summary = "Migration service health", description = "Check health of migration service")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "migration",
                "migrationsInProgress", String.valueOf(progressTracker.hasMigrationsInProgress())
        ));
    }
}
