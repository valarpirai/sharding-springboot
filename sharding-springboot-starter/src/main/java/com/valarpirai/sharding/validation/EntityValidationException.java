package com.valarpirai.sharding.validation;

/**
 * Exception thrown when entity validation fails during auto-configuration.
 */
public class EntityValidationException extends RuntimeException {

    public EntityValidationException(String message) {
        super(message);
    }

    public EntityValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityValidationException(Throwable cause) {
        super(cause);
    }
}