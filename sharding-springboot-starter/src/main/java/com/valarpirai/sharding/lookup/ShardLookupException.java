package com.valarpirai.sharding.lookup;

/**
 * Exception thrown when shard lookup operations fail.
 */
public class ShardLookupException extends RuntimeException {

    public ShardLookupException(String message) {
        super(message);
    }

    public ShardLookupException(String message, Throwable cause) {
        super(message, cause);
    }

    public ShardLookupException(Throwable cause) {
        super(cause);
    }
}