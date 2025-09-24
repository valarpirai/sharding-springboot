package com.valarpirai.sharding.validation;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for sharded entities to ensure they have proper tenant column configuration.
 * Validates entities during auto-configuration startup.
 */
@Component
public class EntityValidator {

    private static final Logger logger = LoggerFactory.getLogger(EntityValidator.class);

    private final ShardingConfigProperties shardingConfig;
    private final ApplicationContext applicationContext;

    public EntityValidator(ShardingConfigProperties shardingConfig, ApplicationContext applicationContext) {
        this.shardingConfig = shardingConfig;
        this.applicationContext = applicationContext;
    }

    /**
     * Validate all entities in the application context.
     * This method is called during auto-configuration.
     *
     * @throws EntityValidationException if validation fails
     */
    public void validateAllEntities() {
        logger.info("Starting entity validation for sharded entities");

        Set<Class<?>> entityClasses = findAllEntityClasses();
        List<EntityValidationError> errors = new ArrayList<>();

        for (Class<?> entityClass : entityClasses) {
            try {
                validateEntity(entityClass);
            } catch (EntityValidationException e) {
                errors.add(new EntityValidationError(entityClass, e.getMessage()));
                logger.error("Entity validation failed for {}: {}", entityClass.getSimpleName(), e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            String errorSummary = formatValidationErrors(errors);
            logger.error("Entity validation failed:\n{}", errorSummary);
            throw new EntityValidationException("Entity validation failed for " + errors.size() + " entities:\n" + errorSummary);
        }

        logger.info("Entity validation completed successfully for {} entities", entityClasses.size());
    }

    /**
     * Validate a specific entity class.
     *
     * @param entityClass the entity class to validate
     * @throws EntityValidationException if validation fails
     */
    public void validateEntity(Class<?> entityClass) {
        if (!isJpaEntity(entityClass)) {
            return; // Skip non-JPA entities
        }

        boolean isShardedEntity = entityClass.isAnnotationPresent(ShardedEntity.class);

        logger.debug("Validating entity: {} (sharded: {})", entityClass.getSimpleName(), isShardedEntity);

        if (isShardedEntity) {
            validateShardedEntity(entityClass);
        }

        logger.debug("Entity validation passed for: {}", entityClass.getSimpleName());
    }

    /**
     * Validate that a sharded entity has proper tenant column configuration.
     */
    private void validateShardedEntity(Class<?> entityClass) {
        List<String> tenantColumns = shardingConfig.getTenantColumnNames();
        boolean hasTenantColumn = false;
        String foundColumnName = null;
        String foundFieldName = null;

        // Check all fields for tenant columns
        for (Field field : getAllFields(entityClass)) {
            String columnName = getColumnName(field);
            String fieldName = field.getName();

            // Check if field name or column name matches any configured tenant column names
            if (isTenantColumn(fieldName, columnName, tenantColumns)) {
                if (hasTenantColumn) {
                    throw new EntityValidationException(
                        String.format("Entity %s has multiple tenant columns: %s and %s. Only one tenant column is allowed.",
                                     entityClass.getSimpleName(), foundFieldName, fieldName));
                }
                hasTenantColumn = true;
                foundColumnName = columnName;
                foundFieldName = fieldName;

                // Validate tenant field type
                validateTenantFieldType(entityClass, field);

                // Validate tenant field is not nullable (if using @Column)
                validateTenantFieldNullability(entityClass, field);
            }
        }

        if (!hasTenantColumn) {
            throw new EntityValidationException(
                String.format("Sharded entity %s must have one of the configured tenant columns: %s. " +
                             "Add a field with @Column annotation using one of these names, or name the field directly as one of these values.",
                             entityClass.getSimpleName(), tenantColumns));
        }

        logger.debug("Sharded entity {} has valid tenant column: {} (field: {})",
                    entityClass.getSimpleName(), foundColumnName, foundFieldName);
    }

    /**
     * Check if a field name or column name matches configured tenant column names.
     */
    private boolean isTenantColumn(String fieldName, String columnName, List<String> tenantColumns) {
        return tenantColumns.stream().anyMatch(tenantCol ->
            tenantCol.equalsIgnoreCase(fieldName) || tenantCol.equalsIgnoreCase(columnName));
    }

    /**
     * Validate that the tenant field has the correct type.
     */
    private void validateTenantFieldType(Class<?> entityClass, Field field) {
        Class<?> fieldType = field.getType();

        // Accept Long, long, Integer, int, String for tenant ID
        if (!isValidTenantFieldType(fieldType)) {
            throw new EntityValidationException(
                String.format("Tenant field %s in entity %s has invalid type %s. " +
                             "Tenant field must be one of: Long, long, Integer, int, String",
                             field.getName(), entityClass.getSimpleName(), fieldType.getSimpleName()));
        }
    }

    /**
     * Validate that the tenant field is not nullable.
     */
    private void validateTenantFieldNullability(Class<?> entityClass, Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && columnAnnotation.nullable()) {
            logger.warn("Tenant field {} in entity {} is marked as nullable. " +
                       "Consider making it non-nullable for better data integrity.",
                       field.getName(), entityClass.getSimpleName());
        }

        // Check for @NotNull annotation
        if (field.getAnnotation(javax.validation.constraints.NotNull.class) == null &&
            field.getAnnotation(org.jetbrains.annotations.NotNull.class) == null) {
            logger.info("Consider adding @NotNull annotation to tenant field {} in entity {} for validation",
                       field.getName(), entityClass.getSimpleName());
        }
    }

    /**
     * Check if a field type is valid for tenant ID.
     */
    private boolean isValidTenantFieldType(Class<?> fieldType) {
        return fieldType == Long.class || fieldType == long.class ||
               fieldType == Integer.class || fieldType == int.class ||
               fieldType == String.class;
    }

    /**
     * Get the column name for a field (from @Column annotation or field name).
     */
    private String getColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name();
        }
        return field.getName();
    }

    /**
     * Get all fields including inherited fields.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }

    /**
     * Check if a class is a JPA entity.
     */
    private boolean isJpaEntity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Entity.class);
    }

    /**
     * Find all entity classes in the application context.
     */
    private Set<Class<?>> findAllEntityClasses() {
        Set<Class<?>> entityClasses = new HashSet<>();

        // Get all bean definition names
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Class<?> beanClass = applicationContext.getType(beanName);
                if (beanClass != null && isJpaEntity(beanClass)) {
                    entityClasses.add(beanClass);
                }
            } catch (Exception e) {
                logger.debug("Could not determine type for bean: {}", beanName);
            }
        }

        // Also scan packages if entity classes are found via package scanning
        // This is a fallback for cases where entities might not be Spring beans
        entityClasses.addAll(scanForEntityClasses());

        logger.debug("Found {} JPA entity classes", entityClasses.size());
        return entityClasses;
    }

    /**
     * Scan for entity classes using class path scanning.
     * This is a simplified implementation - in practice, you might want to use
     * Spring's ClassPathScanningCandidateComponentProvider.
     */
    private Set<Class<?>> scanForEntityClasses() {
        // For now, return empty set. In a full implementation, you would:
        // 1. Use ClassPathScanningCandidateComponentProvider
        // 2. Scan configured entity packages
        // 3. Filter for @Entity annotated classes
        return new HashSet<>();
    }

    /**
     * Format validation errors for display.
     */
    private String formatValidationErrors(List<EntityValidationError> errors) {
        return errors.stream()
            .map(error -> "- " + error.getEntityClass().getSimpleName() + ": " + error.getMessage())
            .collect(Collectors.joining("\n"));
    }

    /**
     * Get validation summary for logging/monitoring.
     */
    public EntityValidationSummary getValidationSummary() {
        Set<Class<?>> entityClasses = findAllEntityClasses();

        long shardedEntities = entityClasses.stream()
            .filter(clazz -> clazz.isAnnotationPresent(ShardedEntity.class))
            .count();

        long nonShardedEntities = entityClasses.size() - shardedEntities;

        return new EntityValidationSummary(
            entityClasses.size(),
            shardedEntities,
            nonShardedEntities,
            shardingConfig.getTenantColumnNames()
        );
    }

    /**
     * Entity validation error container.
     */
    public static class EntityValidationError {
        private final Class<?> entityClass;
        private final String message;

        public EntityValidationError(Class<?> entityClass, String message) {
            this.entityClass = entityClass;
            this.message = message;
        }

        public Class<?> getEntityClass() { return entityClass; }
        public String getMessage() { return message; }
    }

    /**
     * Entity validation summary.
     */
    public static class EntityValidationSummary {
        private final long totalEntities;
        private final long shardedEntities;
        private final long nonShardedEntities;
        private final List<String> tenantColumnNames;

        public EntityValidationSummary(long totalEntities, long shardedEntities,
                                     long nonShardedEntities, List<String> tenantColumnNames) {
            this.totalEntities = totalEntities;
            this.shardedEntities = shardedEntities;
            this.nonShardedEntities = nonShardedEntities;
            this.tenantColumnNames = tenantColumnNames;
        }

        public long getTotalEntities() { return totalEntities; }
        public long getShardedEntities() { return shardedEntities; }
        public long getNonShardedEntities() { return nonShardedEntities; }
        public List<String> getTenantColumnNames() { return tenantColumnNames; }

        @Override
        public String toString() {
            return "EntityValidationSummary{" +
                   "totalEntities=" + totalEntities +
                   ", shardedEntities=" + shardedEntities +
                   ", nonShardedEntities=" + nonShardedEntities +
                   ", tenantColumnNames=" + tenantColumnNames +
                   '}';
        }
    }
}