package com.valarpirai.sharding.validation;

/**
 * Exception thrown when SQL query validation fails.
 */
public class QueryValidationException extends RuntimeException {

    public QueryValidationException(String message) {
        super(message);
    }

    public QueryValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public QueryValidationException(Throwable cause) {
        super(cause);
    }
}