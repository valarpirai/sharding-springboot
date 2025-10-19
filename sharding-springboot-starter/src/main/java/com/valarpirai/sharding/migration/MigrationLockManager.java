package com.valarpirai.sharding.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages locks to prevent concurrent migration execution.
 */
@Slf4j
@Component
public class MigrationLockManager {

    private final ReentrantLock migrationLock = new ReentrantLock();
    private final AtomicBoolean migrationInProgress = new AtomicBoolean(false);

    /**
     * Try to acquire migration lock.
     *
     * @return true if lock acquired, false if migration already in progress
     */
    public boolean tryAcquireLock() {
        if (migrationInProgress.get()) {
            log.warn("Migration already in progress, cannot start another");
            return false;
        }

        if (migrationLock.tryLock()) {
            migrationInProgress.set(true);
            log.info("Migration lock acquired");
            return true;
        }

        log.warn("Failed to acquire migration lock");
        return false;
    }

    /**
     * Release migration lock.
     */
    public void releaseLock() {
        if (migrationLock.isHeldByCurrentThread()) {
            migrationInProgress.set(false);
            migrationLock.unlock();
            log.info("Migration lock released");
        }
    }

    /**
     * Check if migration is currently in progress.
     */
    public boolean isMigrationInProgress() {
        return migrationInProgress.get();
    }

    /**
     * Force release lock (use only in emergency).
     */
    public void forceRelease() {
        migrationInProgress.set(false);
        if (migrationLock.isLocked()) {
            try {
                migrationLock.unlock();
                log.warn("Migration lock forcefully released");
            } catch (IllegalMonitorStateException e) {
                log.error("Cannot force release lock not held by current thread", e);
            }
        }
    }
}
