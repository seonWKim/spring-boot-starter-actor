package io.github.seonwkim.example.receptionist;

import io.github.seonwkim.example.receptionist.DataProcessorWorker.ProcessingResult;
import io.github.seonwkim.example.receptionist.WorkerPoolService.PoolStatus;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller demonstrating the Receptionist-based worker pool.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /pool/status - Get current pool status
 *   <li>POST /pool/workers - Add a new worker to the pool
 *   <li>POST /process?records=N - Process a single batch
 *   <li>POST /process/bulk - Process multiple batches concurrently
 * </ul>
 */
@RestController
@RequestMapping("/")
public class WorkerPoolController {

    private final WorkerPoolService workerPoolService;

    public WorkerPoolController(WorkerPoolService workerPoolService) {
        this.workerPoolService = workerPoolService;
    }

    /**
     * Get current worker pool status.
     */
    @GetMapping("/pool/status")
    public Mono<PoolStatus> getPoolStatus() {
        return Mono.fromCompletionStage(workerPoolService.getPoolStatus());
    }

    /**
     * Add a new worker to the pool.
     */
    @PostMapping("/pool/workers")
    public Mono<WorkerAddedResponse> addWorker() {
        return Mono.fromCompletionStage(workerPoolService.addWorker().thenApply(WorkerAddedResponse::new));
    }

    /**
     * Process a single batch of records.
     */
    @PostMapping("/process")
    public Mono<ProcessingResult> processBatch(@RequestParam(defaultValue = "10") int records) {
        return Mono.fromCompletionStage(workerPoolService.processBatch(records));
    }

    /**
     * Process multiple batches concurrently.
     * Example: POST /process/bulk with body: [10, 20, 15, 30, 25]
     */
    @PostMapping("/process/bulk")
    public Mono<List<ProcessingResult>> processBulk(@RequestBody(required = false) List<Integer> batchSizes) {
        // Default to 5 batches of varying sizes if not specified
        if (batchSizes == null || batchSizes.isEmpty()) {
            batchSizes = Arrays.asList(10, 15, 20, 12, 18);
        }
        return Mono.fromCompletionStage(workerPoolService.processBatches(batchSizes));
    }

    /**
     * Home endpoint with usage instructions.
     */
    @GetMapping("/")
    public Mono<String> home() {
        return Mono.just(
                """
                Receptionist Example - Dynamic Worker Pool
                ===========================================

                This example demonstrates Pekko's Receptionist feature for dynamic actor discovery
                using a data processing worker pool pattern.

                Available Endpoints:
                -------------------

                GET  /pool/status           - View current worker pool status
                POST /pool/workers          - Add a new worker to the pool
                POST /process?records=N     - Process a batch with N records
                POST /process/bulk          - Process multiple batches concurrently
                                              (Body: array of record counts, e.g., [10, 20, 15])

                Example Usage:
                -------------

                # Check initial pool status
                curl http://localhost:8080/pool/status

                # Process a batch (round-robin load balancing)
                curl -X POST http://localhost:8080/process?records=20

                # Process multiple batches concurrently
                curl -X POST http://localhost:8080/process/bulk \\
                  -H "Content-Type: application/json" \\
                  -d "[10, 15, 20, 12, 18]"

                # Add more workers for increased throughput
                curl -X POST http://localhost:8080/pool/workers

                # Check updated pool status
                curl http://localhost:8080/pool/status

                Features Demonstrated:
                ---------------------
                - Dynamic service registration with Receptionist
                - Actor discovery via service keys
                - Round-robin load balancing
                - Subscription to pool availability changes
                - Concurrent batch processing
                """);
    }

    public static class WorkerAddedResponse {
        public final String workerId;
        public final String message;

        public WorkerAddedResponse(String workerId) {
            this.workerId = workerId;
            this.message = "Worker " + workerId + " added to pool";
        }
    }
}
