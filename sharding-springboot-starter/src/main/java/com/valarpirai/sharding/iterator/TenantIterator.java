package com.valarpirai.sharding.iterator;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Iterator for processing tenants in batches.
 * Useful for background jobs that need to operate across all tenants.
 */
@Component
public class TenantIterator {

    private static final Logger logger = LoggerFactory.getLogger(TenantIterator.class);
    private static final int DEFAULT_BATCH_SIZE = 10;

    private final ShardLookupService shardLookupService;

    public TenantIterator(ShardLookupService shardLookupService) {
        this.shardLookupService = shardLookupService;
    }

    /**
     * Process all active tenants in batches with default batch size.
     *
     * @param processor function to process each tenant
     */
    public void processAllTenants(Consumer<Long> processor) {
        processAllTenants(processor, DEFAULT_BATCH_SIZE);
    }

    /**
     * Process all active tenants in batches.
     *
     * @param processor function to process each tenant
     * @param batchSize number of tenants to process in each batch
     */
    public void processAllTenants(Consumer<Long> processor, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        logger.info("Starting tenant processing with batch size: {}", batchSize);

        List<TenantShardMapping> allMappings = shardLookupService.findAllMappings()
                .stream()
                .filter(TenantShardMapping::isActive)
                .collect(Collectors.toList());

        logger.info("Found {} active tenants to process", allMappings.size());

        int totalBatches = (int) Math.ceil((double) allMappings.size() / batchSize);
        int processedCount = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, allMappings.size());

            List<TenantShardMapping> batch = allMappings.subList(startIndex, endIndex);
            logger.debug("Processing batch {} of {} (tenants: {})",
                        batchIndex + 1, totalBatches, batch.size());

            for (TenantShardMapping mapping : batch) {
                processTenantInContext(mapping, processor);
                processedCount++;
            }

            logger.info("Completed batch {} of {} - processed {} tenants so far",
                       batchIndex + 1, totalBatches, processedCount);
        }

        logger.info("Completed processing all {} tenants", processedCount);
    }

    /**
     * Process tenants in parallel with default batch size and parallelism.
     *
     * @param processor function to process each tenant
     */
    public CompletableFuture<Void> processAllTenantsAsync(Consumer<Long> processor) {
        return processAllTenantsAsync(processor, DEFAULT_BATCH_SIZE, ForkJoinPool.commonPool());
    }

    /**
     * Process tenants in parallel with specified batch size and executor.
     *
     * @param processor function to process each tenant
     * @param batchSize number of tenants per batch
     * @param executor executor for parallel processing
     */
    public CompletableFuture<Void> processAllTenantsAsync(Consumer<Long> processor, int batchSize, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<TenantShardMapping> allMappings = shardLookupService.findAllMappings()
                        .stream()
                        .filter(TenantShardMapping::isActive)
                        .collect(Collectors.toList());

                logger.info("Starting async tenant processing: {} tenants, batch size: {}",
                           allMappings.size(), batchSize);

                List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                int totalBatches = (int) Math.ceil((double) allMappings.size() / batchSize);

                for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                    int startIndex = batchIndex * batchSize;
                    int endIndex = Math.min(startIndex + batchSize, allMappings.size());
                    List<TenantShardMapping> batch = allMappings.subList(startIndex, endIndex);
                    final int currentBatch = batchIndex + 1;

                    CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                        logger.debug("Processing async batch {} of {} (tenants: {})",
                                    currentBatch, totalBatches, batch.size());

                        for (TenantShardMapping mapping : batch) {
                            processTenantInContext(mapping, processor);
                        }

                        logger.debug("Completed async batch {} of {}", currentBatch, totalBatches);
                    }, executor);

                    batchFutures.add(batchFuture);
                }

                // Wait for all batches to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                logger.info("Completed async processing of all {} tenants", allMappings.size());

            } catch (Exception e) {
                logger.error("Error in async tenant processing", e);
                throw new TenantIteratorException("Failed to process tenants asynchronously", e);
            }
        }, executor);
    }

    /**
     * Process tenants in a specific shard.
     *
     * @param shardId the shard identifier
     * @param processor function to process each tenant
     */
    public void processTenantsInShard(String shardId, Consumer<Long> processor) {
        processTenantsInShard(shardId, processor, DEFAULT_BATCH_SIZE);
    }

    /**
     * Process tenants in a specific shard with batch size.
     *
     * @param shardId the shard identifier
     * @param processor function to process each tenant
     * @param batchSize number of tenants per batch
     */
    public void processTenantsInShard(String shardId, Consumer<Long> processor, int batchSize) {
        if (shardId == null) {
            throw new IllegalArgumentException("Shard ID cannot be null");
        }

        logger.info("Processing tenants in shard: {} with batch size: {}", shardId, batchSize);

        List<TenantShardMapping> shardMappings = shardLookupService.findAllMappings()
                .stream()
                .filter(mapping -> shardId.equals(mapping.getShardId()))
                .filter(TenantShardMapping::isActive)
                .collect(Collectors.toList());

        logger.info("Found {} active tenants in shard: {}", shardMappings.size(), shardId);

        if (shardMappings.isEmpty()) {
            logger.info("No tenants found in shard: {}", shardId);
            return;
        }

        int totalBatches = (int) Math.ceil((double) shardMappings.size() / batchSize);
        int processedCount = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, shardMappings.size());

            List<TenantShardMapping> batch = shardMappings.subList(startIndex, endIndex);
            logger.debug("Processing shard {} batch {} of {} (tenants: {})",
                        shardId, batchIndex + 1, totalBatches, batch.size());

            for (TenantShardMapping mapping : batch) {
                processTenantInContext(mapping, processor);
                processedCount++;
            }

            logger.debug("Completed shard {} batch {} of {} - processed {} tenants so far",
                        shardId, batchIndex + 1, totalBatches, processedCount);
        }

        logger.info("Completed processing {} tenants in shard: {}", processedCount, shardId);
    }

    /**
     * Create a batch iterator for custom processing logic.
     *
     * @param batchSize the batch size
     * @return iterator over tenant batches
     */
    public Iterator<List<Long>> createBatchIterator(int batchSize) {
        List<Long> allTenants = shardLookupService.findAllMappings()
                .stream()
                .filter(TenantShardMapping::isActive)
                .map(TenantShardMapping::getTenantId)
                .collect(Collectors.toList());

        return new BatchIterator<>(allTenants, batchSize);
    }

    /**
     * Process tenants with a mapping function that returns results.
     *
     * @param mapper function that processes each tenant and returns a result
     * @param <T> the result type
     * @return list of results from processing all tenants
     */
    public <T> List<T> mapAllTenants(Function<Long, T> mapper) {
        return mapAllTenants(mapper, DEFAULT_BATCH_SIZE);
    }

    /**
     * Process tenants with a mapping function that returns results.
     *
     * @param mapper function that processes each tenant and returns a result
     * @param batchSize number of tenants per batch
     * @param <T> the result type
     * @return list of results from processing all tenants
     */
    public <T> List<T> mapAllTenants(Function<Long, T> mapper, int batchSize) {
        List<T> results = new ArrayList<>();

        processAllTenants(tenantId -> {
            T result = TenantContext.executeInTenantContext(tenantId, () -> mapper.apply(tenantId));
            if (result != null) {
                results.add(result);
            }
        }, batchSize);

        return results;
    }

    /**
     * Get tenant processing statistics.
     *
     * @return statistics about tenants available for processing
     */
    public TenantProcessingStats getProcessingStats() {
        List<TenantShardMapping> allMappings = shardLookupService.findAllMappings();

        long activeTenants = allMappings.stream()
                .filter(TenantShardMapping::isActive)
                .count();

        long inactiveTenants = allMappings.size() - activeTenants;

        return new TenantProcessingStats(activeTenants, inactiveTenants, allMappings.size());
    }

    /**
     * Process a tenant within its proper tenant context.
     */
    private void processTenantInContext(TenantShardMapping mapping, Consumer<Long> processor) {
        TenantContext.executeInTenantContext(mapping.getTenantId(), () -> {
            try {
                // Set shard information in context
                TenantContext.setTenantInfo(mapping.getTenantId(), mapping.getShardId());
                processor.accept(mapping.getTenantId());
            } catch (Exception e) {
                logger.error("Error processing tenant {}: {}", mapping.getTenantId(), e.getMessage(), e);
                throw new TenantIteratorException("Failed to process tenant: " + mapping.getTenantId(), e);
            }
        });
    }

    /**
     * Generic batch iterator implementation.
     */
    private static class BatchIterator<T> implements Iterator<List<T>> {
        private final List<T> items;
        private final int batchSize;
        private int currentIndex = 0;

        public BatchIterator(List<T> items, int batchSize) {
            this.items = items;
            this.batchSize = batchSize;
        }

        @Override
        public boolean hasNext() {
            return currentIndex < items.size();
        }

        @Override
        public List<T> next() {
            int endIndex = Math.min(currentIndex + batchSize, items.size());
            List<T> batch = items.subList(currentIndex, endIndex);
            currentIndex = endIndex;
            return new ArrayList<>(batch);
        }
    }

    /**
     * Statistics about tenant processing.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TenantProcessingStats {
        private final long activeTenants;
        private final long inactiveTenants;
        private final long totalTenants;
    }
}