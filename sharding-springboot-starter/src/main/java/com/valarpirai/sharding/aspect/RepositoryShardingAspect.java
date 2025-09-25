package com.valarpirai.sharding.aspect;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.routing.RoutingDataSource;
import com.valarpirai.sharding.validation.ValidatingDataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * AOP aspect that automatically sets sharded entity context for repository operations.
 * This aspect intercepts all JpaRepository method calls and determines whether the
 * target entity is sharded based on the @ShardedEntity annotation.
 *
 * The aspect provides automatic context management so application developers don't
 * need to manually set sharded entity context before database operations.
 */
@Aspect
@Component
@Order(1) // Ensure this runs before any other aspects
public class RepositoryShardingAspect {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryShardingAspect.class);

    @Autowired
    private DataSource primaryDataSource;

    /**
     * Intercept all method calls on JpaRepository implementations.
     * This pointcut matches any method call on objects that implement JpaRepository.
     */
    @Around("target(org.springframework.data.jpa.repository.JpaRepository)")
    public Object setShardingContext(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean isShardedEntity = false;

        try {
            // Determine if the repository handles a sharded entity
            Class<?> repositoryClass = joinPoint.getTarget().getClass();
            Class<?> entityClass = getEntityClass(repositoryClass);

            if (entityClass != null) {
                isShardedEntity = entityClass.isAnnotationPresent(ShardedEntity.class);
                logger.debug("Repository operation on {}: sharded={}",
                           entityClass.getSimpleName(), isShardedEntity);
            } else {
                logger.debug("Could not determine entity class for repository: {}",
                           repositoryClass.getSimpleName());
            }

            // Set sharded entity context before the repository operation
            setShardedEntityContextOnDataSource(isShardedEntity);

            // Proceed with the repository method call
            return joinPoint.proceed();

        } finally {
            // Always clear context after the operation to prevent context leakage
            clearShardedEntityContextOnDataSource();
        }
    }

    /**
     * Extract the entity class from a repository interface using reflection.
     * This method traverses the type hierarchy to find JpaRepository generic types.
     *
     * @param repositoryClass the repository class (usually a proxy)
     * @return the entity class or null if not found
     */
    private Class<?> getEntityClass(Class<?> repositoryClass) {
        // Look through all interfaces to find JpaRepository
        for (Type genericInterface : repositoryClass.getGenericInterfaces()) {
            Class<?> entityClass = extractEntityFromGenericInterface(genericInterface);
            if (entityClass != null) {
                return entityClass;
            }
        }

        // If not found in interfaces, check superclass (for proxy classes)
        Class<?> superclass = repositoryClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return getEntityClass(superclass);
        }

        return null;
    }

    /**
     * Extract entity class from a generic interface if it's a JpaRepository.
     */
    private Class<?> extractEntityFromGenericInterface(Type genericInterface) {
        if (genericInterface instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
            Type rawType = parameterizedType.getRawType();

            if (rawType instanceof Class && JpaRepository.class.isAssignableFrom((Class<?>) rawType)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                    return (Class<?>) actualTypeArguments[0];
                }
            }
        }
        return null;
    }

    /**
     * Set sharded entity context on the RoutingDataSource.
     * This method navigates through the DataSource wrapper hierarchy to reach RoutingDataSource.
     *
     * @param forShardedEntity true if the entity is marked with @ShardedEntity
     */
    private void setShardedEntityContextOnDataSource(boolean forShardedEntity) {
        try {
            RoutingDataSource routingDs = extractRoutingDataSource();
            if (routingDs != null) {
                routingDs.setShardedEntityContext(forShardedEntity);
                logger.trace("Set sharded entity context: {}", forShardedEntity);
            }
        } catch (Exception e) {
            logger.warn("Failed to set sharded entity context: {}", e.getMessage());
        }
    }

    /**
     * Clear sharded entity context on the RoutingDataSource.
     */
    private void clearShardedEntityContextOnDataSource() {
        try {
            RoutingDataSource routingDs = extractRoutingDataSource();
            if (routingDs != null) {
                routingDs.clearShardedEntityContext();
                logger.trace("Cleared sharded entity context");
            }
        } catch (Exception e) {
            logger.warn("Failed to clear sharded entity context: {}", e.getMessage());
        }
    }

    /**
     * Extract RoutingDataSource from the primary DataSource wrapper hierarchy.
     * The primary DataSource is typically: ValidatingDataSource -> RoutingDataSource
     *
     * @return RoutingDataSource instance or null if not found
     */
    private RoutingDataSource extractRoutingDataSource() {
        if (primaryDataSource instanceof ValidatingDataSource) {
            ValidatingDataSource validatingDs = (ValidatingDataSource) primaryDataSource;
            DataSource targetDs = validatingDs.getTargetDataSource();

            if (targetDs instanceof RoutingDataSource) {
                return (RoutingDataSource) targetDs;
            }
        } else if (primaryDataSource instanceof RoutingDataSource) {
            // Direct RoutingDataSource without validation wrapper
            return (RoutingDataSource) primaryDataSource;
        }

        logger.warn("Could not extract RoutingDataSource from primary DataSource: {}",
                   primaryDataSource.getClass().getSimpleName());
        return null;
    }
}