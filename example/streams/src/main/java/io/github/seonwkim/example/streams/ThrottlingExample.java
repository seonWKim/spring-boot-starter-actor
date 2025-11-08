package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating throttling in Pekko Streams with actors.
 *
 * <p>Pekko Streams provides built-in throttling to control the rate of element processing.
 * This is useful for:
 * <ul>
 *   <li>Rate limiting external API calls</li>
 *   <li>Preventing system overload</li>
 *   <li>Smooth out bursty traffic</li>
 *   <li>Comply with rate limits of downstream systems</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This example uses Pekko's built-in throttling,
 * not a custom implementation. This ensures battle-tested, production-ready behavior.
 */
@Service
public class ThrottlingExample {

    private final SpringActorSystem actorSystem;

    public ThrottlingExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Demonstrates basic throttling with a fixed rate.
     *
     * <p>Limits the stream to process at most N elements per time unit.
     * Additional elements are delayed to meet the rate limit.
     *
     * @param data Input data
     * @param elementsPerSecond Maximum elements per second
     * @return CompletionStage with processing time
     */
    public CompletionStage<Long> fixedRateThrottle(List<String> data, int elementsPerSecond) {
        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "throttled-processor")
                .thenCompose(processor -> Source.from(data)
                        // Throttle to specified rate (shaping mode)
                        .throttle(elementsPerSecond, Duration.ofSeconds(1))
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw())
                        .thenApply(done -> {
                            long duration = System.currentTimeMillis() - startTime;
                            double actualRate = (data.size() * 1000.0) / duration;
                            System.out.println(String.format(
                                    "Processed %d items with %d items/sec throttle in %dms (actual rate: %.2f items/sec)",
                                    data.size(), elementsPerSecond, duration, actualRate));
                            return duration;
                        }));
    }

    /**
     * Demonstrates burst throttling.
     *
     * <p>Allows a burst of elements, then throttles to the specified rate.
     * Useful for handling occasional bursts while maintaining overall rate limit.
     *
     * @param data Input data
     * @param maxRate Maximum sustained rate
     * @param maxBurst Maximum burst size
     * @return CompletionStage with result
     */
    public CompletionStage<Long> burstThrottle(List<String> data, int maxRate, int maxBurst) {
        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "burst-processor")
                .thenCompose(processor -> Source.from(data)
                        // Throttle with burst allowance
                        .throttle(maxRate, Duration.ofSeconds(1), maxBurst, ThrottleMode.shaping())
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw())
                        .thenApply(done -> {
                            long duration = System.currentTimeMillis() - startTime;
                            System.out.println(String.format(
                                    "Processed %d items with burst throttle (rate=%d/sec, burst=%d) in %dms",
                                    data.size(), maxRate, maxBurst, duration));
                            return duration;
                        }));
    }

    /**
     * Demonstrates enforcing throttle mode.
     *
     * <p>In enforcing mode, elements that exceed the rate are failed/dropped.
     * This is stricter than shaping mode which delays elements.
     *
     * @param data Input data
     * @param maxRate Maximum rate
     * @return CompletionStage with result
     */
    public CompletionStage<Long> enforcingThrottle(List<String> data, int maxRate) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "enforcing-processor")
                .thenCompose(processor -> Source.from(data)
                        // Enforcing mode: fail if rate is exceeded
                        .throttle(maxRate, Duration.ofSeconds(1), 0, ThrottleMode.enforcing())
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .exceptionally(ex -> {
                            System.out.println("Stream failed due to rate enforcement: " + ex.getMessage());
                            return 0L;
                        })
                        .thenApply(count -> {
                            System.out.println("Processed " + count
                                    + " items with enforcing throttle (some may have been dropped)");
                            return count;
                        }));
    }

    /**
     * Demonstrates throttling for API rate limits.
     *
     * <p>Real-world example: calling an external API with rate limits.
     * This pattern ensures you don't exceed API quotas.
     *
     * @param data Input data
     * @param maxCallsPerMinute API rate limit
     * @return CompletionStage with result
     */
    public CompletionStage<Long> apiRateLimitExample(List<String> data, int maxCallsPerMinute) {
        // Convert per-minute limit to per-second
        int callsPerSecond = maxCallsPerMinute / 60;
        // Allow small burst for efficiency
        int burst = Math.max(1, callsPerSecond / 2);

        long startTime = System.currentTimeMillis();

        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "api-processor")
                .thenCompose(processor -> Source.from(data)
                        // Throttle to API rate limit
                        .throttle(callsPerSecond, Duration.ofSeconds(1), burst, ThrottleMode.shaping())
                        // Simulate API calls through actor
                        .mapAsync(
                                3,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(10))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw())
                        .thenApply(done -> {
                            long duration = System.currentTimeMillis() - startTime;
                            double actualRate = (data.size() * 60000.0) / duration;
                            System.out.println(String.format(
                                    "API calls completed: %d items in %dms (rate: %.2f calls/min, limit: %d calls/min)",
                                    data.size(), duration, actualRate, maxCallsPerMinute));
                            return duration;
                        }));
    }

    /**
     * Demonstrates time-based throttling with grouping.
     *
     * <p>Process data in time windows, useful for batching and rate limiting together.
     *
     * @param data Input data
     * @param windowSize Time window size
     * @return CompletionStage with result
     */
    public CompletionStage<Long> timeWindowThrottle(List<String> data, Duration windowSize) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "window-processor")
                .thenCompose(processor -> Source.from(data)
                        // Group elements within time windows
                        .groupedWithin(Integer.MAX_VALUE, windowSize)
                        // Process each window (batched)
                        .mapConcat(batch -> {
                            System.out.println("Processing window with " + batch.size() + " elements");
                            return batch;
                        })
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw()));
    }

    /**
     * Demonstrates multi-stage throttling.
     *
     * <p>Different stages of the pipeline can have different rate limits.
     * This is useful when different operations have different cost profiles.
     *
     * @param data Input data
     * @return CompletionStage with result
     */
    public CompletionStage<Long> multiStageThrottle(List<String> data) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "multistage-processor")
                .thenCompose(processor -> Source.from(data)
                        // Stage 1: Fast initial processing
                        .map(String::trim)
                        .throttle(100, Duration.ofSeconds(1)) // 100/sec for preprocessing
                        // Stage 2: Actor processing (more expensive)
                        .mapAsync(
                                5,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .throttle(20, Duration.ofSeconds(1)) // 20/sec for actor processing
                        // Stage 3: Final aggregation
                        .map(result -> 1L)
                        .runWith(Sink.fold(0L, Long::sum), actorSystem.getRaw())
                        .thenApply(count -> {
                            System.out.println("Completed multi-stage throttled pipeline: " + count + " items");
                            return count;
                        }));
    }

    /**
     * Compares processing with and without throttling.
     *
     * @param data Input data
     * @return CompletionStage with comparison results
     */
    public CompletionStage<String> compareThrottling(List<String> data) {
        // Without throttling
        long start1 = System.currentTimeMillis();
        CompletionStage<Long> time1 = actorSystem
                .getOrSpawn(DataProcessorActor.class, "no-throttle")
                .thenCompose(processor -> Source.from(data)
                        .mapAsync(
                                10,
                                item -> processor
                                        .ask(new DataProcessorActor.ProcessData(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw())
                        .thenApply(done -> System.currentTimeMillis() - start1));

        // With throttling
        CompletionStage<Long> time2 = fixedRateThrottle(data, 10);

        return time1.thenCombine(time2, (t1, t2) -> {
            return String.format(
                    "Throttling comparison:\n"
                            + "  Without throttling: %dms (%.2f items/sec)\n"
                            + "  With throttling:    %dms (%.2f items/sec)\n"
                            + "  Throttling adds %dms overhead but provides controlled rate",
                    t1,
                    (data.size() * 1000.0) / t1,
                    t2,
                    (data.size() * 1000.0) / t2,
                    t2 - t1);
        });
    }
}
