package com.valarpirai.sharding.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for rolling back migrations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackRequest {

    /**
     * Type of rollback to perform.
     */
    private RollbackType type;

    /**
     * Number of changesets to rollback (for COUNT type).
     */
    private Integer count;

    /**
     * Target tag to rollback to (for TAG type).
     */
    private String tag;

    /**
     * Target date to rollback to (for DATE type).
     */
    private String date;

    /**
     * Specific shards to rollback (null means all shards).
     */
    private java.util.List<String> shardIds;

    public enum RollbackType {
        /**
         * Rollback a specific number of changesets.
         */
        COUNT,

        /**
         * Rollback to a specific tag.
         */
        TAG,

        /**
         * Rollback to a specific date.
         */
        DATE
    }
}
