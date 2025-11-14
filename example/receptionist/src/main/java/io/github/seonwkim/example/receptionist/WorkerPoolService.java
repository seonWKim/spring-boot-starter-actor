package io.github.seonwkim.example.receptionist;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.receptionist.Listing;
import io.github.seonwkim.core.receptionist.ServiceKey;
import io.github.seonwkim.core.receptionist.SpringReceptionistService;
import io.github.seonwkim.example.receptionist.DataProcessorWorker.ProcessBatch;
import io.github.seonwkim.example.receptionist.DataProcessorWorker.ProcessingResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that manages a dynamic pool of worker actors using the Receptionist.
 *
 * <p>This service demonstrates:
 * <ul>
 *   <li>Registering workers with the receptionist
 *   <li>Finding available workers
 *   <li>Round-robin load balancing across workers
 *   <li>Monitoring worker pool changes
 *   <li>Dynamic scaling (adding/removing workers)
 * </ul>
 */
@Service
public class WorkerPoolService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerPoolService.class);

    // Service key for data processor workers
    private static final ServiceKey<DataProcessorWorker.Command> WORKER_POOL_KEY =
            ServiceKey.create(DataProcessorWorker.Command.class, "data-processor-pool");

    private final SpringActorSystem actorSystem;
    private final SpringReceptionistService receptionist;
    private final AtomicInteger workerCounter = new AtomicInteger(0);
    private final AtomicInteger batchCounter = new AtomicInteger(0);
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private int currentPoolSize = 0;

    public WorkerPoolService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        this.receptionist = actorSystem.receptionist();
    }

    @PostConstruct
    public void init() {
        // Subscribe to worker pool changes
        receptionist.subscribe(WORKER_POOL_KEY, this::onWorkerPoolChanged);

        // Start with an initial pool of 3 workers
        logger.info("Initializing worker pool with 3 workers...");
        try {
            for (int i = 0; i < 3; i++) {
                addWorker();
                Thread.sleep(100); // Small delay to ensure clean startup
            }
        } catch (Exception e) {
            logger.error("Error initializing worker pool", e);
        }
    }

    /**
     * Callback invoked when the worker pool changes (workers added/removed).
     */
    private void onWorkerPoolChanged(Listing<DataProcessorWorker.Command> listing) {
        int newSize = listing.size();
        logger.info("Worker pool changed: {} workers available (was: {})", newSize, currentPoolSize);
        currentPoolSize = newSize;

        if (listing.isEmpty()) {
            logger.warn("Worker pool is empty! No workers available for processing.");
        }
    }

    /**
     * Adds a new worker to the pool.
     */
    public CompletionStage<String> addWorker() {
        int workerId = workerCounter.incrementAndGet();
        String actorId = "worker-" + workerId;

        logger.info("Adding worker: {}", actorId);

        return actorSystem
                .actor(DataProcessorWorker.class)
                .withId(actorId)
                .spawn()
                .thenApply(workerRef -> {
                    receptionist.register(WORKER_POOL_KEY, workerRef);
                    logger.info("Worker {} registered with receptionist", actorId);
                    return actorId;
                });
    }

    /**
     * Processes a batch of data using the worker pool with round-robin load balancing.
     */
    public CompletionStage<ProcessingResult> processBatch(int recordCount) {
        String batchId = "batch-" + batchCounter.incrementAndGet();

        return receptionist.find(WORKER_POOL_KEY).thenCompose(listing -> {
            if (listing.isEmpty()) {
                CompletableFuture<ProcessingResult> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("No workers available in the pool"));
                return future;
            }

            // Get workers as a list for round-robin selection
            List<SpringActorRef<DataProcessorWorker.Command>> workers = new ArrayList<>(listing.getServiceInstances());

            // Select worker using round-robin
            int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % workers.size());
            SpringActorRef<DataProcessorWorker.Command> selectedWorker = workers.get(index);

            logger.info(
                    "Submitting batch {} ({} records) to worker pool (selected index {})", batchId, recordCount, index);

            // Send work to the selected worker
            return selectedWorker
                    .ask(new ProcessBatch(batchId, recordCount))
                    .withTimeout(Duration.ofSeconds(30))
                    .execute();
        });
    }

    /**
     * Processes multiple batches concurrently across the worker pool.
     */
    public CompletionStage<List<ProcessingResult>> processBatches(List<Integer> batchSizes) {
        logger.info("Processing {} batches concurrently", batchSizes.size());

        List<CompletionStage<ProcessingResult>> futures =
                batchSizes.stream().map(this::processBatch).collect(Collectors.toList());

        return CompletableFuture.allOf(futures.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream()
                        .map(f -> f.toCompletableFuture().join())
                        .collect(Collectors.toList()));
    }

    /**
     * Gets current pool status.
     */
    public CompletionStage<PoolStatus> getPoolStatus() {
        return receptionist.find(WORKER_POOL_KEY).thenApply(listing -> {
            Set<SpringActorRef<DataProcessorWorker.Command>> workers = listing.getServiceInstances();
            List<String> workerIds = workers.stream()
                    .map(ref -> ref.getUnderlying().path().name())
                    .collect(Collectors.toList());

            return new PoolStatus(workers.size(), workerIds);
        });
    }

    /**
     * Status information about the worker pool.
     */
    public static class PoolStatus {
        public final int workerCount;
        public final List<String> workerIds;

        public PoolStatus(int workerCount, List<String> workerIds) {
            this.workerCount = workerCount;
            this.workerIds = workerIds;
        }
    }
}
