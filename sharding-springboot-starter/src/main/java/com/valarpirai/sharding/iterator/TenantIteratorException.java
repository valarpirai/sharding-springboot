package com.valarpirai.sharding.iterator;

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