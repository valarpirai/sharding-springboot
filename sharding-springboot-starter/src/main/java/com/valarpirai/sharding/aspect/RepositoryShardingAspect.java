package com.valarpirai.sharding.aspect;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP aspect that routes repository operations based on entity sharding annotations.
 *
 * - If entity has @ShardedEntity → use shard DataSource from TenantContext
 * - If entity has no @ShardedEntity → use global DataSource
 *
 * This aspect works with pre-resolved TenantContext set by ShardSelectorFilter.
 */
@Aspect
@Component
@Order(1) // Run before transaction aspects
public class RepositoryShardingAspect {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryShardingAspect.class);

    private final ThreadLocal<Boolean> forceGlobalDataSource = new ThreadLocal<>();

    /**
     * Cache to store repository class to entity class mappings.
     * Key: Repository class name, Value: Entity class (or null if not found)
     */
    private final ConcurrentHashMap<String, Class<?>> repositoryEntityCache = new ConcurrentHashMap<>();

    /**
     * Cache to store entity class to sharding annotation mappings.
     * Key: Entity class name, Value: Boolean indicating if @ShardedEntity is present
     */
    private final ConcurrentHashMap<String, Boolean> entityShardingCache = new ConcurrentHashMap<>();

    /**
     * Comprehensive repository interception using EVERYTHING possible:
     * 1. Package-based execution
     * 2. Target-based interception
     * 3. Interface hierarchy execution
     * 4. Annotation-based interception
     * 5. Bean-based interception for Spring Data repositories
     */
    @Around("execution(* com.valarpirai.example.repository..*(..)) || " +
            "target(org.springframework.data.repository.Repository) || " +
            "target(org.springframework.data.jpa.repository.JpaRepository) || " +
            "target(org.springframework.data.repository.CrudRepository) || " +
            "execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..)) || " +
            "execution(* org.springframework.data.repository.Repository+.*(..)) || " +
            "execution(* org.springframework.data.repository.CrudRepository+.*(..)) || " +
            "@target(org.springframework.stereotype.Repository) || " +
            "@within(org.springframework.stereotype.Repository) || " +
            "bean(*Repository)")
    public Object routeRepositoryOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Class<?> repositoryClass = joinPoint.getTarget().getClass();
            String repositoryClassName = repositoryClass.getName();

            logger.info("=== ASPECT TRIGGERED === Processing repository operation on: {}", repositoryClassName);
            logger.info("Method: {}", joinPoint.getSignature().toShortString());
            logger.info("Target class: {}", repositoryClass);
            logger.info("Target interfaces: {}", String.join(", ",
                java.util.Arrays.stream(repositoryClass.getInterfaces()).map(Class::getSimpleName).toArray(String[]::new)));
            logger.info("Current thread: {}", Thread.currentThread().getName());

            // Check cache first for performance
            Boolean cachedShardingResult = getCachedShardingDecision(repositoryClassName);
            boolean isShardedEntity;

            if (cachedShardingResult != null) {
                // Cache hit - use cached result
                isShardedEntity = cachedShardingResult;
                logger.info("CACHE HIT for repository {}: sharded={}", repositoryClassName, isShardedEntity);
            } else {
                // Cache miss - determine entity class and sharding status
                logger.info("CACHE MISS - determining entity class and sharding status...");
                Class<?> entityClass = determineEntityClassWithCache(repositoryClass, joinPoint);
                isShardedEntity = determineShardingStatusWithCache(entityClass);

                // Cache the final decision for this repository
                cacheShardingDecision(repositoryClassName, isShardedEntity);

                if (entityClass != null) {
                    logger.info("REPOSITORY ANALYSIS: Repository {} -> Entity {} -> @ShardedEntity: {}",
                               repositoryClass.getSimpleName(), entityClass.getSimpleName(), isShardedEntity);
                } else {
                    logger.warn("ENTITY DETECTION FAILED: Could not determine entity class for repository: {} - assuming non-sharded",
                               repositoryClass.getSimpleName());
                }
            }

            // Set routing context for DataSource selection
            logger.info("ROUTING DECISION: Setting routing context - useShardDataSource={}", isShardedEntity);
            setRoutingContext(isShardedEntity);

            // Log current TenantContext state
            TenantInfo tenantInfo = TenantContext.getTenantInfo();
            if (tenantInfo != null) {
                logger.info("TENANT CONTEXT: tenantId={}, shardId={}, readOnly={}, hasShardDataSource={}",
                           tenantInfo.tenantId(), tenantInfo.shardId(), tenantInfo.readOnlyMode(),
                           tenantInfo.shardDataSource() != null);
            } else {
                logger.warn("TENANT CONTEXT: No TenantContext available!");
            }

            // Log the routing context we just set
            Boolean forceGlobal = forceGlobalDataSource.get();
            logger.info("ROUTING STATE: forceGlobalDataSource ThreadLocal = {}", forceGlobal);

            // Proceed with the repository method call
            logger.info("EXECUTING: Repository method call...");
            Object result = joinPoint.proceed();
            logger.info("COMPLETED: Repository method execution finished");
            return result;

        } finally {
            // Always clear routing context after the operation
            clearRoutingContext();
        }
    }


    /**
     * Get cached sharding decision for a repository.
     */
    private Boolean getCachedShardingDecision(String repositoryClassName) {
        // Check if we have a direct cache hit for the sharding decision
        return entityShardingCache.get(repositoryClassName);
    }

    /**
     * Cache the sharding decision for a repository.
     */
    private void cacheShardingDecision(String repositoryClassName, boolean isShardedEntity) {
        entityShardingCache.put(repositoryClassName, isShardedEntity);
        logger.trace("Cached sharding decision for repository {}: {}", repositoryClassName, isShardedEntity);
    }

    /**
     * Determine entity class using cache and multiple detection approaches.
     */
    private Class<?> determineEntityClassWithCache(Class<?> repositoryClass, ProceedingJoinPoint joinPoint) {
        String repositoryClassName = repositoryClass.getName();

        // Check repository -> entity cache first
        Class<?> cachedEntityClass = repositoryEntityCache.get(repositoryClassName);
        if (cachedEntityClass != null) {
            logger.debug("Cache hit for repository->entity mapping {}: {}",
                       repositoryClassName, cachedEntityClass.getSimpleName());
            return cachedEntityClass;
        }

        // Cache miss - try multiple approaches to determine entity class
        Class<?> entityClass = null;

        logger.debug("Cache miss for repository {}, trying detection approaches...", repositoryClassName);

        // Approach 1: Try to get entity class from repository class
        logger.debug("Approach 1: Trying getEntityClass for {}", repositoryClassName);
        entityClass = getEntityClass(repositoryClass);
        if (entityClass != null) {
            logger.debug("Approach 1 SUCCESS: Found entity {} for repository {}",
                       entityClass.getSimpleName(), repositoryClassName);
        } else {
            logger.debug("Approach 1 FAILED: No entity found via getEntityClass");
        }

        // Approach 2: If failed, try to get from method signature
        if (entityClass == null) {
            logger.debug("Approach 2: Trying getEntityClassFromMethod for {}", repositoryClassName);
            entityClass = getEntityClassFromMethod(joinPoint);
            if (entityClass != null) {
                logger.debug("Approach 2 SUCCESS: Found entity {} from method signature",
                           entityClass.getSimpleName());
            } else {
                logger.debug("Approach 2 FAILED: No entity found from method signature");
            }
        }

        // Approach 3: If still failed, try to get original repository interface
        if (entityClass == null) {
            logger.debug("Approach 3: Trying getEntityClassFromOriginalInterface for {}", repositoryClassName);
            entityClass = getEntityClassFromOriginalInterface(repositoryClass);
            if (entityClass != null) {
                logger.debug("Approach 3 SUCCESS: Found entity {} from original interface",
                           entityClass.getSimpleName());
            } else {
                logger.debug("Approach 3 FAILED: No entity found from original interface");
            }
        }

        // Cache the result (even if null to avoid repeated expensive lookups)
        repositoryEntityCache.put(repositoryClassName, entityClass);

        if (entityClass != null) {
            logger.debug("Discovered and cached entity class {} for repository {}",
                       entityClass.getSimpleName(), repositoryClassName);
        } else {
            logger.debug("Could not determine entity class for repository: {} - cached null result",
                       repositoryClassName);
        }

        return entityClass;
    }

    /**
     * Determine sharding status using cache.
     */
    private boolean determineShardingStatusWithCache(Class<?> entityClass) {
        if (entityClass == null) {
            return false; // Default to non-sharded for unknown entities
        }

        String entityClassName = entityClass.getName();

        // Check entity -> sharding status cache
        Boolean cachedShardingStatus = entityShardingCache.get(entityClassName);
        if (cachedShardingStatus != null) {
            logger.trace("Cache hit for entity sharding status {}: {}",
                       entityClassName, cachedShardingStatus);
            return cachedShardingStatus;
        }

        // Cache miss - check annotation
        logger.debug("Checking @ShardedEntity annotation on entity class: {}", entityClassName);
        boolean isShardedEntity = entityClass.isAnnotationPresent(ShardedEntity.class);
        logger.debug("Entity {} has @ShardedEntity: {}", entityClassName, isShardedEntity);

        // Cache the result
        entityShardingCache.put(entityClassName, isShardedEntity);
        logger.debug("Cached sharding status for entity {}: {}",
                   entityClassName, isShardedEntity);

        return isShardedEntity;
    }

    /**
     * Try to extract entity class from method signature.
     */
    private Class<?> getEntityClassFromMethod(ProceedingJoinPoint joinPoint) {
        try {
            // For methods like save(Entity), findById(ID), etc.
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                Object firstArg = args[0];
                if (firstArg != null) {
                    Class<?> argClass = firstArg.getClass();
                    // Check if this argument class has @ShardedEntity
                    if (argClass.isAnnotationPresent(ShardedEntity.class)) {
                        logger.trace("Found entity class from method argument: {}", argClass.getSimpleName());
                        return argClass;
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Error extracting entity class from method: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Try to find the original repository interface declaration.
     */
    private Class<?> getEntityClassFromOriginalInterface(Class<?> repositoryClass) {
        try {
            // Look through all interfaces, including those from superclasses
            Class<?> currentClass = repositoryClass;
            while (currentClass != null) {
                for (Class<?> interfaceClass : currentClass.getInterfaces()) {
                    if (interfaceClass.getPackage() != null &&
                        !interfaceClass.getPackage().getName().startsWith("org.springframework") &&
                        JpaRepository.class.isAssignableFrom(interfaceClass)) {

                        // This might be our custom repository interface
                        Type[] genericInterfaces = interfaceClass.getGenericInterfaces();
                        for (Type genericInterface : genericInterfaces) {
                            Class<?> entityClass = extractEntityFromGenericInterface(genericInterface);
                            if (entityClass != null) {
                                logger.trace("Found entity class from custom interface: {}", entityClass.getSimpleName());
                                return entityClass;
                            }
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (Exception e) {
            logger.trace("Error extracting entity class from original interface: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Set routing context to indicate whether to use shard or global DataSource.
     */
    private void setRoutingContext(boolean forShardedEntity) {
        logger.info("SET ROUTING CONTEXT: forShardedEntity={}", forShardedEntity);

        if (forShardedEntity) {
            // For sharded entities, ensure TenantContext has shard information
            TenantInfo tenantInfo = TenantContext.getTenantInfo();
            if (tenantInfo == null || tenantInfo.shardDataSource() == null) {
                logger.warn("ROUTING CONTEXT: Sharded entity operation attempted without proper tenant context. " +
                          "Ensure ShardSelectorFilter has set complete TenantInfo. " +
                          "tenantInfo={}, hasShardDataSource={}",
                          tenantInfo, tenantInfo != null ? tenantInfo.shardDataSource() != null : "N/A");
                // Force global DataSource as fallback
                forceGlobalDataSource.set(true);
                logger.info("ROUTING CONTEXT: Set forceGlobalDataSource=true (fallback for missing tenant context)");
            } else {
                logger.info("ROUTING CONTEXT: Using shard DataSource for sharded entity operation");
                forceGlobalDataSource.remove(); // Use shard DataSource
                logger.info("ROUTING CONTEXT: Removed forceGlobalDataSource ThreadLocal (allow shard routing)");
            }
        } else {
            // For non-sharded entities, always use global DataSource
            logger.info("ROUTING CONTEXT: Using global DataSource for non-sharded entity operation");
            forceGlobalDataSource.set(true);
            logger.info("ROUTING CONTEXT: Set forceGlobalDataSource=true (non-sharded entity)");
        }

        // Double-check the ThreadLocal state
        Boolean currentState = forceGlobalDataSource.get();
        logger.info("ROUTING CONTEXT FINAL STATE: forceGlobalDataSource ThreadLocal = {}", currentState);
    }

    /**
     * Clear the routing context.
     */
    private void clearRoutingContext() {
        forceGlobalDataSource.remove();
        logger.trace("Cleared repository routing context");
    }

    /**
     * Check if global DataSource should be forced.
     * This is used by RoutingDataSource to make routing decisions.
     */
    public boolean shouldUseGlobalDataSource() {
        Boolean forceGlobal = forceGlobalDataSource.get();
        return forceGlobal != null && forceGlobal;
    }

    /**
     * Get cache statistics for monitoring and troubleshooting.
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(
            repositoryEntityCache.size(),
            entityShardingCache.size()
        );
    }

    /**
     * Clear all caches. Useful for testing or when repository structure changes.
     */
    public void clearCaches() {
        int repositoryMappings = repositoryEntityCache.size();
        int shardingMappings = entityShardingCache.size();

        repositoryEntityCache.clear();
        entityShardingCache.clear();

        logger.info("Cleared repository sharding caches: {} repository mappings, {} sharding mappings",
                   repositoryMappings, shardingMappings);
    }

    /**
     * Cache statistics for monitoring.
     */
    public static class CacheStatistics {
        private final int repositoryEntityMappings;
        private final int entityShardingMappings;

        public CacheStatistics(int repositoryEntityMappings, int entityShardingMappings) {
            this.repositoryEntityMappings = repositoryEntityMappings;
            this.entityShardingMappings = entityShardingMappings;
        }

        public int getRepositoryEntityMappings() {
            return repositoryEntityMappings;
        }

        public int getEntityShardingMappings() {
            return entityShardingMappings;
        }

        @Override
        public String toString() {
            return String.format("CacheStatistics{repositoryMappings=%d, shardingMappings=%d}",
                               repositoryEntityMappings, entityShardingMappings);
        }
    }

    /**
     * Extract the entity class from a repository interface using reflection.
     * Handles Spring's repository proxies and traverses the type hierarchy.
     */
    private Class<?> getEntityClass(Class<?> repositoryClass) {
        if (repositoryClass == null) {
            return null;
        }

        try {
            logger.info("!!! GETENTITYCLASS CALLED !!! Stack trace to understand call path for repository: {}", repositoryClass.getName());
            logger.info("!!! STACK TRACE !!! {}", Thread.currentThread().getStackTrace()[3].toString());
            logger.debug("Attempting to extract entity class from repository: {}", repositoryClass.getName());

            // Handle Spring proxy classes - look at interfaces first
            logger.debug("Step 1: Extracting entity from interfaces of {}", repositoryClass.getName());
            Class<?> entityClass = extractEntityFromInterfaces(repositoryClass);
            if (entityClass != null) {
                logger.debug("Step 1 SUCCESS: Found entity {} from interfaces", entityClass.getSimpleName());
                return entityClass;
            }
            logger.debug("Step 1 FAILED: No entity found from interfaces");

            // Try to get the actual target class from Spring proxies
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(repositoryClass);
            if (targetClass != repositoryClass) {
                logger.trace("Resolved proxy {} to target class {}",
                           repositoryClass.getSimpleName(), targetClass.getSimpleName());
                entityClass = extractEntityFromInterfaces(targetClass);
                if (entityClass != null) {
                    return entityClass;
                }
            }

            // Try superclass hierarchy for more complex proxy scenarios
            entityClass = extractEntityFromSuperclasses(repositoryClass);
            if (entityClass != null) {
                return entityClass;
            }

            logger.debug("Could not determine entity class for repository: {} (type: {})",
                       repositoryClass.getSimpleName(), repositoryClass.getName());
            return null;

        } catch (Exception e) {
            logger.warn("Error extracting entity class from repository {}: {}",
                       repositoryClass.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Extract entity class from all interfaces of a class.
     */
    private Class<?> extractEntityFromInterfaces(Class<?> clazz) {
        // Get all interfaces including those from superclasses
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> interfaceClass : interfaces) {
            Class<?> entityClass = extractEntityFromInterface(interfaceClass);
            if (entityClass != null) {
                return entityClass;
            }
        }

        // Also check generic interfaces with type parameters
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            Class<?> entityClass = extractEntityFromGenericInterface(genericInterface);
            if (entityClass != null) {
                return entityClass;
            }
        }

        return null;
    }

    /**
     * Extract entity class from superclass hierarchy.
     */
    private Class<?> extractEntityFromSuperclasses(Class<?> clazz) {
        Class<?> currentClass = clazz.getSuperclass();
        while (currentClass != null && currentClass != Object.class) {
            Class<?> entityClass = extractEntityFromInterfaces(currentClass);
            if (entityClass != null) {
                return entityClass;
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    /**
     * Extract entity class from a specific interface class.
     */
    private Class<?> extractEntityFromInterface(Class<?> interfaceClass) {
        if (interfaceClass == null) {
            return null;
        }

        // Check if this interface extends JpaRepository directly
        if (JpaRepository.class.isAssignableFrom(interfaceClass)) {
            // Get generic superinterfaces to find type parameters
            Type[] genericInterfaces = interfaceClass.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                Class<?> entityClass = extractEntityFromGenericInterface(genericInterface);
                if (entityClass != null) {
                    return entityClass;
                }
            }

            // Also check the interface itself if it has generic type information
            Type genericSuperclass = interfaceClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                return extractEntityFromParameterizedType((ParameterizedType) genericSuperclass);
            }
        }

        return null;
    }

    /**
     * Extract entity class from a generic interface if it's a JpaRepository.
     */
    private Class<?> extractEntityFromGenericInterface(Type genericInterface) {
        if (genericInterface instanceof ParameterizedType) {
            return extractEntityFromParameterizedType((ParameterizedType) genericInterface);
        }
        return null;
    }

    /**
     * Extract entity class from a ParameterizedType (generic interface/class).
     */
    private Class<?> extractEntityFromParameterizedType(ParameterizedType parameterizedType) {
        try {
            Type rawType = parameterizedType.getRawType();

            if (rawType instanceof Class && JpaRepository.class.isAssignableFrom((Class<?>) rawType)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    Type firstTypeArg = actualTypeArguments[0];

                    if (firstTypeArg instanceof Class) {
                        Class<?> entityClass = (Class<?>) firstTypeArg;
                        logger.trace("Found entity class: {} from repository interface", entityClass.getSimpleName());
                        return entityClass;
                    } else {
                        logger.trace("First type argument is not a Class: {}", firstTypeArg);
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Error extracting from parameterized type: {}", e.getMessage());
        }
        return null;
    }
}
