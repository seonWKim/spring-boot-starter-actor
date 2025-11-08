package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating backpressure handling in Pekko Streams with actors.
 *
 * <p>Pekko Streams provides built-in backpressure handling. This example shows:
 * <ul>
 *   <li>How backpressure works automatically</li>
 *   <li>Buffer strategies for handling slow consumers</li>
 *   <li>Overflow strategies when buffers are full</li>
 *   <li>Controlling parallelism to manage load</li>
 * </ul>
 *
 * <p><strong>Key Concept:</strong> Backpressure is automatic in Pekko Streams.
 * Fast producers are slowed down by slow consumers, preventing memory issues.
 */
@Service
public class BackpressureExample {

    private final SpringActorSystem actorSystem;

    public BackpressureExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Demonstrates automatic backpressure with slow actor processing.
     *
     * <p>The stream automatically slows down the source when the actor
     * cannot keep up with processing. No manual backpressure handling needed.
     *
     * @param data Input data
     * @return CompletionStage that completes when processing is done
     */
    public CompletionStage<Long> automaticBackpressure(List<String> data) {
        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "slow-processor")
                .thenCompose(processor -> Source.from(data)
                        // Actor processing with limited parallelism
                        // If actor is slow, source will be slowed down automatically
                        .mapAsync(
                                2, // Only 2 concurrent operations
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(10))
                                        .execute()
                                        .thenApply(result -> {
                                            // Add artificial delay to simulate slow processing
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                            return result;
                                        }))
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            long duration = System.currentTimeMillis() - startTime;
                            System.out.println("Processed " + count
                                    + " items with automatic backpressure in " + duration + "ms");
                            return count;
                        }));
    }

    /**
     * Demonstrates buffering strategy to smooth out bursts.
     *
     * <p>Buffers allow temporary bursts of data without backpressuring the source immediately.
     * Once buffer is full, backpressure kicks in.
     *
     * @param data Input data
     * @param bufferSize Size of the buffer
     * @return CompletionStage with result
     */
    public CompletionStage<Long> withBuffering(List<String> data, int bufferSize) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "buffered-processor")
                .thenCompose(processor -> Source.from(data)
                        // Add a buffer to handle bursts
                        .buffer(bufferSize, OverflowStrategy.backpressure())
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            System.out.println("Processed " + count + " items with " + bufferSize + " buffer");
                            return count;
                        }));
    }

    /**
     * Demonstrates drop-oldest overflow strategy.
     *
     * <p>When buffer is full, oldest elements are dropped to make room for new ones.
     * Useful when recent data is more important than old data.
     *
     * @param data Input data
     * @param bufferSize Size of the buffer
     * @return CompletionStage with result
     */
    public CompletionStage<Long> dropOldestStrategy(List<String> data, int bufferSize) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "drop-oldest-processor")
                .thenCompose(processor -> Source.from(data)
                        // Drop oldest elements when buffer is full
                        .buffer(bufferSize, OverflowStrategy.dropHead())
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            System.out.println("Processed " + count
                                    + " items (may be less than input if buffer overflowed)");
                            return count;
                        }));
    }

    /**
     * Demonstrates drop-newest overflow strategy.
     *
     * <p>When buffer is full, newest elements are dropped.
     * Useful when you want to preserve older data in flight.
     *
     * @param data Input data
     * @param bufferSize Size of the buffer
     * @return CompletionStage with result
     */
    public CompletionStage<Long> dropNewestStrategy(List<String> data, int bufferSize) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "drop-newest-processor")
                .thenCompose(processor -> Source.from(data)
                        // Drop newest elements when buffer is full
                        .buffer(bufferSize, OverflowStrategy.dropTail())
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            System.out.println("Processed " + count
                                    + " items (may be less than input if buffer overflowed)");
                            return count;
                        }));
    }

    /**
     * Demonstrates controlling parallelism to manage backpressure.
     *
     * <p>By adjusting the parallelism parameter in mapAsync, you control
     * how many concurrent operations are allowed. This affects backpressure behavior.
     *
     * @param data Input data
     * @param parallelism Level of parallelism
     * @return CompletionStage with processing time
     */
    public CompletionStage<Long> controlledParallelism(List<String> data, int parallelism) {
        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "parallel-processor-" + parallelism)
                .thenCompose(processor -> Source.from(data)
                        // Parallelism controls how many concurrent actor calls
                        .mapAsync(
                                parallelism,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            long duration = System.currentTimeMillis() - startTime;
                            System.out.println("Processed " + count + " items with parallelism=" + parallelism
                                    + " in " + duration + "ms");
                            return duration;
                        }));
    }

    /**
     * Compares different parallelism levels to show impact on throughput.
     *
     * @param data Input data
     * @return CompletionStage with comparison results
     */
    public CompletionStage<String> compareParallelism(List<String> data) {
        CompletionStage<Long> time1 = controlledParallelism(data, 1);
        CompletionStage<Long> time5 = controlledParallelism(data, 5);
        CompletionStage<Long> time10 = controlledParallelism(data, 10);

        return time1.thenCombine(time5, (t1, t5) -> new long[] {t1, t5})
                .thenCombine(time10, (times, t10) -> {
                    return String.format(
                            "Parallelism comparison:\n"
                                    + "  Parallelism=1:  %dms\n"
                                    + "  Parallelism=5:  %dms (%.1fx faster)\n"
                                    + "  Parallelism=10: %dms (%.1fx faster)",
                            times[0],
                            times[1],
                            (double) times[0] / times[1],
                            t10,
                            (double) times[0] / t10);
                });
    }

    /**
     * Demonstrates async boundaries for better backpressure handling.
     *
     * <p>Async boundaries allow different stages of the stream to run independently,
     * providing better throughput and backpressure handling.
     *
     * @param data Input data
     * @return CompletionStage with result
     */
    public CompletionStage<Long> withAsyncBoundaries(List<String> data) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "async-processor")
                .thenCompose(processor -> Source.from(data)
                        // Stage 1: Filtering (runs on its own thread)
                        .filter(item -> !item.isEmpty())
                        .async() // Async boundary
                        // Stage 2: Actor processing (runs on its own thread)
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .async() // Async boundary
                        // Stage 3: Collection (runs on its own thread)
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            System.out.println("Processed " + count + " items with async boundaries");
                            return count;
                        }));
    }
}
