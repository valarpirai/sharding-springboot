package com.valarpirai.sharding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to indicate that an entity should be stored in sharded databases.
 * Entities marked with this annotation must include tenant_id or company_id in their queries.
 * Unmarked entities are considered non-sharded and will reside in the global database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardedEntity {
}