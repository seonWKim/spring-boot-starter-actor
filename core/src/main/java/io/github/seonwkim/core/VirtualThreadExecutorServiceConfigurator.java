package io.github.seonwkim.core;

import com.typesafe.config.Config;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import org.apache.pekko.dispatch.DispatcherPrerequisites;
import org.apache.pekko.dispatch.ExecutorServiceConfigurator;
import org.apache.pekko.dispatch.ExecutorServiceFactory;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Executor service configurator that creates an executor backed by Java 21+ virtual threads.
 * This configurator is used by the virtual thread dispatcher to provide lightweight,
 * high-concurrency thread execution for actors performing blocking I/O operations.
 *
 * <p>Virtual threads are only available in Java 21+. This configurator uses reflection
 * to access virtual thread APIs to maintain compatibility with Java 11+.
 */
public class VirtualThreadExecutorServiceConfigurator extends ExecutorServiceConfigurator {

    private final Config config;
    private final DispatcherPrerequisites prerequisites;

    /**
     * Creates a new VirtualThreadExecutorServiceConfigurator.
     *
     * @param config The dispatcher configuration
     * @param prerequisites The dispatcher prerequisites
     */
    public VirtualThreadExecutorServiceConfigurator(Config config, DispatcherPrerequisites prerequisites) {
        super(config, prerequisites);
        this.config = config;
        this.prerequisites = prerequisites;
    }

    /**
     * Creates an ExecutorServiceFactory that produces ExecutorServices backed by virtual threads.
     *
     * @param id The dispatcher id
     * @param threadFactory The thread factory (not used for virtual threads)
     * @return An ExecutorServiceFactory for virtual threads
     */
    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(String id, ThreadFactory threadFactory) {
        return new ExecutorServiceFactory() {
            @Override
            public ExecutorService createExecutorService() {
                try {
                    // Use reflection to call Executors.newVirtualThreadPerTaskExecutor()
                    Class<?> executorsClass = Class.forName("java.util.concurrent.Executors");
                    Method method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
                    return (ExecutorService) method.invoke(null);
                } catch (Exception e) {
                    throw new UnsupportedOperationException(
                            "Failed to create virtual thread executor. "
                                    + "Virtual threads require Java 21 or later. "
                                    + "Current Java version: "
                                    + System.getProperty("java.version"),
                            e);
                }
            }
        };
    }

    /**
     * Returns the shutdown timeout for the executor service.
     *
     * @return The shutdown timeout duration
     */
    public FiniteDuration shutdownTimeout() {
        Duration duration = Duration.create(
                config.getDuration("shutdown-timeout", java.util.concurrent.TimeUnit.MILLISECONDS),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        return FiniteDuration.create(duration.length(), duration.unit());
    }
}
