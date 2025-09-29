package com.valarpirai.sharding.exception;

/**
 * Exception thrown when tenant iteration operations fail.
 */
public class TenantIteratorException extends RuntimeException {

    public TenantIteratorException(String message) {
        super(message);
    }

    public TenantIteratorException(String message, Throwable cause) {
        super(message, cause);
    }

    public TenantIteratorException(Throwable cause) {
        super(cause);
    }
}