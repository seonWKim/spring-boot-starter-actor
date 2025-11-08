package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating data transformation pipelines using Pekko Streams with actors.
 *
 * <p>This example shows how to build complex data processing pipelines that:
 * <ul>
 *   <li>Transform data through multiple stages</li>
 *   <li>Process data through actors at various stages</li>
 *   <li>Filter and validate data</li>
 *   <li>Handle errors and timeouts</li>
 *   <li>Collect results efficiently</li>
 * </ul>
 */
@Service
public class DataPipelineExample {

    private final SpringActorSystem actorSystem;

    public DataPipelineExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Simple data transformation pipeline.
     *
     * <p>Pipeline stages:
     * <ol>
     *   <li>Source: List of input strings</li>
     *   <li>Filter: Remove empty strings</li>
     *   <li>Transform: Convert to lowercase</li>
     *   <li>Process: Send to actor for processing</li>
     *   <li>Sink: Collect results</li>
     * </ol>
     *
     * @param data Input data
     * @return CompletionStage with list of processed results
     */
    public CompletionStage<List<DataProcessorActor.ProcessedResult>> simpleTransformPipeline(List<String> data) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "pipeline-processor")
                .thenCompose(processor -> Source.from(data)
                        // Filter stage
                        .filter(item -> item != null && !item.trim().isEmpty())
                        // Transform stage
                        .map(String::toLowerCase)
                        // Actor processing stage
                        .mapAsync(5, item -> processor
                                .ask(new DataProcessorActor.ProcessData(item))
                                .withTimeout(Duration.ofSeconds(5))
                                .execute())
                        // Collect results
                        .runWith(Sink.seq(), actorSystem.getRaw()));
    }

    /**
     * Advanced pipeline with multiple processing stages.
     *
     * <p>This demonstrates a more complex pipeline with:
     * <ul>
     *   <li>Multiple transformation stages</li>
     *   <li>Validation at each stage</li>
     *   <li>Error recovery</li>
     *   <li>Batching for efficiency</li>
     * </ul>
     *
     * @param data Input data
     * @return CompletionStage with processed results
     */
    public CompletionStage<List<String>> advancedPipeline(List<String> data) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "advanced-processor")
                .thenCompose(processor -> {
                    // Define a reusable flow for actor processing
                    Flow<String, DataProcessorActor.ProcessedResult, NotUsed> actorFlow = Flow.<String>create()
                            .mapAsync(10, item -> processor
                                    .ask(new DataProcessorActor.ProcessData(item))
                                    .withTimeout(Duration.ofSeconds(5))
                                    .execute()
                                    .exceptionally(ex -> {
                                        // Handle errors by returning a failure result
                                        return new DataProcessorActor.ProcessedResult(
                                                item, "FAILED", System.currentTimeMillis());
                                    }));

                    return Source.from(data)
                            // Stage 1: Initial validation and transformation
                            .filter(item -> item != null)
                            .map(String::trim)
                            .filter(item -> !item.isEmpty())
                            // Stage 2: Process through actor
                            .via(actorFlow)
                            // Stage 3: Filter successful results
                            .filter(result -> !result.getProcessed().equals("FAILED"))
                            // Stage 4: Extract processed value
                            .map(DataProcessorActor.ProcessedResult::getProcessed)
                            // Stage 5: Group into batches of 10
                            .grouped(10)
                            // Stage 6: Process batches (flatten back to stream)
                            .mapConcat(batch -> batch)
                            // Collect all results
                            .runWith(Sink.seq(), actorSystem.getRaw());
                });
    }

    /**
     * Pipeline with throughput monitoring.
     *
     * <p>This example demonstrates how to monitor pipeline throughput:
     * <ul>
     *   <li>Count elements processed</li>
     *   <li>Measure processing time</li>
     *   <li>Log throughput metrics</li>
     * </ul>
     *
     * @param data Input data
     * @return CompletionStage with count of processed items
     */
    public CompletionStage<Long> monitoredPipeline(List<String> data) {
        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "monitored-processor")
                .thenCompose(processor -> Source.from(data)
                        .filter(item -> item != null && !item.trim().isEmpty())
                        .mapAsync(5, item -> processor
                                .ask(new DataProcessorActor.ProcessData(item))
                                .withTimeout(Duration.ofSeconds(5))
                                .execute())
                        .map(result -> {
                            // Log each processed item
                            System.out.println("Processed: " + result.getOriginal() + " -> " + result.getProcessed());
                            return result;
                        })
                        .toMat(Sink.fold(0L, (count, result) -> count + 1), Keep.right())
                        .run(actorSystem.getRaw())
                        .thenApply(count -> {
                            long duration = System.currentTimeMillis() - startTime;
                            double throughput = (count * 1000.0) / duration;
                            System.out.println("Pipeline completed: " + count + " items in " + duration + "ms ("
                                    + String.format("%.2f", throughput) + " items/sec)");
                            return count;
                        }));
    }

    /**
     * Parallel processing pipeline.
     *
     * <p>This example shows how to process data in parallel using multiple actors:
     * <ul>
     *   <li>Multiple actor instances</li>
     *   <li>Parallel processing with controlled parallelism</li>
     *   <li>Automatic load balancing</li>
     * </ul>
     *
     * @param data Input data
     * @param parallelism Level of parallelism
     * @return CompletionStage with processed results
     */
    public CompletionStage<List<DataProcessorActor.ProcessedResult>> parallelPipeline(
            List<String> data, int parallelism) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "parallel-processor")
                .thenCompose(processor -> Source.from(data)
                        .filter(item -> item != null && !item.trim().isEmpty())
                        // Process with high parallelism
                        .mapAsync(parallelism, item -> processor
                                .ask(new DataProcessorActor.ProcessData(item))
                                .withTimeout(Duration.ofSeconds(5))
                                .execute())
                        .runWith(Sink.seq(), actorSystem.getRaw()));
    }
}
