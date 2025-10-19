package com.valarpirai.sharding.migration;

/**
 * Exception thrown during migration operations.
 */
public class MigrationException extends RuntimeException {

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
