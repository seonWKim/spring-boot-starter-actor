package io.github.seonwkim.actor.metrics.internal.hooks;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nullable;
import org.apache.pekko.dispatch.DefaultExecutorServiceConfigurator;
import org.apache.pekko.dispatch.DispatcherPrerequisites;
import org.apache.pekko.dispatch.ExecutorServiceConfigurator;
import org.apache.pekko.dispatch.ExecutorServiceFactory;
import org.apache.pekko.dispatch.ForkJoinExecutorConfigurator;
import org.apache.pekko.dispatch.ThreadPoolExecutorConfigurator;

/**
 * Runs a dispatcher on Pekko's own executor (named by {@value #DELEGATE_KEY}) and hands each
 * executor it creates to {@link ActorMeters}, which reads the pool's threads and queue. The executor
 * itself is not wrapped, so the dispatcher runs exactly as without metrics.
 */
public final class InstrumentedExecutor extends ExecutorServiceConfigurator {

    /** The dispatcher key that keeps the executor this one delegates to. */
    static final String DELEGATE_KEY = "instrumented-executor";

    private static final Set<String> SUPPORTED =
            Set.of("default-executor", "fork-join-executor", "thread-pool-executor");

    private final ExecutorServiceConfigurator delegate;
    private final String system;

    @Nullable private final ActorMeters meters;

    public InstrumentedExecutor(Config config, DispatcherPrerequisites prerequisites) {
        super(config, prerequisites);
        this.delegate = configurator(config.getString(DELEGATE_KEY), config, prerequisites);
        this.system = prerequisites.settings().name();
        this.meters = prerequisites.settings().setup().get(ActorMeters.class).orElse(null);
    }

    /**
     * {@code config} with the dispatcher at {@code path} running on this configurator, when it runs
     * on one of Pekko's own executors. Dispatchers on Spring executors or virtual threads stay as
     * they are.
     */
    static Config wrap(Config config, String path) {
        String executorKey = path + ".executor";
        if (!config.hasPath(executorKey) || !SUPPORTED.contains(config.getString(executorKey))) {
            return config;
        }
        return config.withValue(path + "." + DELEGATE_KEY, config.getValue(executorKey))
                .withValue(executorKey, ConfigValueFactory.fromAnyRef(InstrumentedExecutor.class.getName()));
    }

    private static ExecutorServiceConfigurator configurator(
            String name, Config config, DispatcherPrerequisites prerequisites) {
        return switch (name) {
            case "fork-join-executor" -> new ForkJoinExecutorConfigurator(
                    config.getConfig("fork-join-executor"), prerequisites);
            case "thread-pool-executor" -> new ThreadPoolExecutorConfigurator(
                    config.getConfig("thread-pool-executor"), prerequisites);
            case "default-executor" -> new DefaultExecutorServiceConfigurator(
                    config.getConfig("default-executor"),
                    prerequisites,
                    configurator(config.getString("default-executor.fallback"), config, prerequisites));
            default -> throw new IllegalArgumentException(
                    DELEGATE_KEY + " must be one of " + SUPPORTED + ", got " + name);
        };
    }

    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(String id, ThreadFactory threadFactory) {
        ExecutorServiceFactory factory = delegate.createExecutorServiceFactory(id, threadFactory);
        if (meters == null) {
            return factory;
        }
        return () -> {
            ExecutorService executor = factory.createExecutorService();
            meters.executorStarted(system, id, executor);
            return executor;
        };
    }
}
