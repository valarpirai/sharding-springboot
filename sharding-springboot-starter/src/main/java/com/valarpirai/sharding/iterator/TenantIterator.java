package com.valarpirai.sharding.iterator;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.exception.TenantIteratorException;
import com.valarpirai.sharding.lookup.ITenantShardMappingReadRepo;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import com.valarpirai.sharding.routing.ShardDataSourceRouter;
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
 * Processes tenants in batches across all shards or a specific shard.
 * Handles TenantContext setup per tenant automatically.
 */
@Component
public class TenantIterator {

    private static final Logger logger = LoggerFactory.getLogger(TenantIterator.class);
    private static final int DEFAULT_BATCH_SIZE = 10;

    private final ITenantShardMappingReadRepo shardLookupService;
    private final ShardDataSourceRouter shardAwareDataSourceDelegate;

    public TenantIterator(ITenantShardMappingReadRepo shardLookupService,
                          ShardDataSourceRouter shardAwareDataSourceDelegate) {
        this.shardLookupService = shardLookupService;
        this.shardAwareDataSourceDelegate = shardAwareDataSourceDelegate;
    }

    /** Process all active tenants using the default batch size. */
    public void processAllTenants(Consumer<Long> processor) {
        processAllTenants(processor, DEFAULT_BATCH_SIZE);
    }

    /** Process all active tenants in batches of {@code batchSize}. */
    public void processAllTenants(Consumer<Long> processor, int batchSize) {
        List<TenantShardMapping> active = getActiveMappings();
        logger.info("Starting tenant processing: {} active tenants, batch size: {}", active.size(), batchSize);
        int count = processBatches(active, processor, batchSize);
        logger.info("Completed processing all {} tenants", count);
    }

    /** Process all active tenants asynchronously using the default batch size. */
    public CompletableFuture<Void> processAllTenantsAsync(Consumer<Long> processor) {
        return processAllTenantsAsync(processor, DEFAULT_BATCH_SIZE, ForkJoinPool.commonPool());
    }

    /** Process all active tenants asynchronously, dispatching one task per batch to {@code executor}. */
    public CompletableFuture<Void> processAllTenantsAsync(Consumer<Long> processor, int batchSize,
                                                           Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<TenantShardMapping> active = getActiveMappings();
                logger.info("Starting async tenant processing: {} tenants, batch size: {}",
                        active.size(), batchSize);

                List<List<TenantShardMapping>> batches = sliceIntoBatches(active, batchSize);
                int totalBatches = batches.size();

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < batches.size(); i++) {
                    List<TenantShardMapping> batch = batches.get(i);
                    final int batchNum = i + 1;
                    futures.add(CompletableFuture.runAsync(() -> {
                        logger.debug("Processing async batch {} of {} ({} tenants)",
                                batchNum, totalBatches, batch.size());
                        for (TenantShardMapping mapping : batch) {
                            processTenantInContext(mapping, processor);
                        }
                        logger.debug("Completed async batch {} of {}", batchNum, totalBatches);
                    }, executor));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                logger.info("Completed async processing of all {} tenants", active.size());

            } catch (Exception e) {
                logger.error("Error in async tenant processing", e);
                throw new TenantIteratorException("Failed to process tenants asynchronously", e);
            }
        }, executor);
    }

    /** Process active tenants in a specific shard using the default batch size. */
    public void processTenantsInShard(String shardId, Consumer<Long> processor) {
        processTenantsInShard(shardId, processor, DEFAULT_BATCH_SIZE);
    }

    /** Process active tenants in a specific shard in batches of {@code batchSize}. */
    public void processTenantsInShard(String shardId, Consumer<Long> processor, int batchSize) {
        if (shardId == null) throw new IllegalArgumentException("Shard ID cannot be null");

        List<TenantShardMapping> shardMappings = getActiveMappings().stream()
                .filter(m -> shardId.equals(m.getShardId()))
                .collect(Collectors.toList());

        if (shardMappings.isEmpty()) {
            logger.info("No active tenants found in shard: {}", shardId);
            return;
        }

        logger.info("Processing {} active tenants in shard: {}", shardMappings.size(), shardId);
        int count = processBatches(shardMappings, processor, batchSize);
        logger.info("Completed processing {} tenants in shard: {}", count, shardId);
    }

    /** Returns a lazy batch iterator over all active tenant IDs. */
    public Iterator<List<Long>> createBatchIterator(int batchSize) {
        List<Long> allTenants = getActiveMappings().stream()
                .map(TenantShardMapping::getTenantId)
                .collect(Collectors.toList());
        return new BatchIterator<>(allTenants, batchSize);
    }

    /** Apply {@code mapper} to each active tenant and collect non-null results. */
    public <T> List<T> mapAllTenants(Function<Long, T> mapper) {
        return mapAllTenants(mapper, DEFAULT_BATCH_SIZE);
    }

    /** Apply {@code mapper} to each active tenant in batches and collect non-null results. */
    public <T> List<T> mapAllTenants(Function<Long, T> mapper, int batchSize) {
        List<T> results = new ArrayList<>();
        // TenantContext is already set by processAllTenants → processTenantInContext
        processAllTenants(tenantId -> {
            T result = mapper.apply(tenantId);
            if (result != null) results.add(result);
        }, batchSize);
        return results;
    }

    /** Returns counts of active, inactive, and total tenants. */
    public TenantProcessingStats getProcessingStats() {
        List<TenantShardMapping> all = shardLookupService.findAllMappings();
        long active = all.stream().filter(TenantShardMapping::isActive).count();
        return new TenantProcessingStats(active, all.size() - active, all.size());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<TenantShardMapping> getActiveMappings() {
        return shardLookupService.findAllMappings().stream()
                .filter(TenantShardMapping::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Slices {@code items} into sequential sub-lists of at most {@code batchSize} each.
     * Extracted to eliminate the identical slicing arithmetic in sync and async paths.
     */
    private <T> List<List<T>> sliceIntoBatches(List<T> items, int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("Batch size must be positive");
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            batches.add(items.subList(i, Math.min(i + batchSize, items.size())));
        }
        return batches;
    }

    /**
     * Iterates {@code mappings} in sequential batches, calling {@code processor} for each
     * tenant within its resolved TenantContext.
     *
     * @return total number of tenants processed
     */
    private int processBatches(List<TenantShardMapping> mappings, Consumer<Long> processor, int batchSize) {
        List<List<TenantShardMapping>> batches = sliceIntoBatches(mappings, batchSize);
        int processedCount = 0;
        for (int i = 0; i < batches.size(); i++) {
            List<TenantShardMapping> batch = batches.get(i);
            logger.debug("Processing batch {} of {} ({} tenants)", i + 1, batches.size(), batch.size());
            for (TenantShardMapping mapping : batch) {
                processTenantInContext(mapping, processor);
                processedCount++;
            }
            logger.info("Completed batch {} of {} — {} tenants processed so far",
                    i + 1, batches.size(), processedCount);
        }
        return processedCount;
    }

    private void processTenantInContext(TenantShardMapping mapping, Consumer<Long> processor) {
        try {
            String shardId = mapping.getShardId();
            javax.sql.DataSource ds = shardAwareDataSourceDelegate.getShardDataSource(shardId, false);
            TenantInfo tenantInfo = new TenantInfo(mapping.getTenantId(), shardId, false, ds);
            TenantContext.executeInTenantContext(tenantInfo, () -> processor.accept(mapping.getTenantId()));
        } catch (Exception e) {
            logger.error("Error processing tenant {}: {}", mapping.getTenantId(), e.getMessage(), e);
            throw new TenantIteratorException("Failed to process tenant: " + mapping.getTenantId(), e);
        }
    }

    private static class BatchIterator<T> implements Iterator<List<T>> {
        private final List<T> items;
        private final int batchSize;
        private int currentIndex = 0;

        BatchIterator(List<T> items, int batchSize) {
            this.items = items;
            this.batchSize = batchSize;
        }

        @Override
        public boolean hasNext() {
            return currentIndex < items.size();
        }

        @Override
        public List<T> next() {
            int end = Math.min(currentIndex + batchSize, items.size());
            List<T> batch = new ArrayList<>(items.subList(currentIndex, end));
            currentIndex = end;
            return batch;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TenantProcessingStats {
        private final long activeTenants;
        private final long inactiveTenants;
        private final long totalTenants;
    }
}
