package io.github.seonwkim.example;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.DispatcherSelector;
import org.apache.pekko.dispatch.ExecutorServiceFactory;

import io.github.seonwkim.core.SpringActorSystem;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import scala.concurrent.ExecutionContextExecutor;

public class PekkoMetricsBinder implements MeterBinder {
    private final SpringActorSystem actorSystem;

    public PekkoMetricsBinder(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder("pekko.deadletters.count", actorSystem.getRaw().deadLetters(), ref -> 0.0)
             .description("Number of messages sent to dead letters (placeholder)")
             .register(meterRegistry);

        // 2. Dispatcher / ThreadPool Metrics
        String dispatcherId = "pekko.actor.default-dispatcher";
        ExecutionContextExecutor defaultDispatcher = actorSystem.getRaw().dispatchers().lookup(DispatcherSelector.defaultDispatcher());
        ExecutionContextExecutor blockingDispatcher = actorSystem.getRaw().dispatchers().lookup(DispatcherSelector.blocking());
        if (defaultDispatcher.executorServiceFactory() instanceof ExecutorServiceFactory) {
            ExecutorServiceFactory factory = (ExecutorServiceFactory) defaultDispatcher.executorServiceFactory();
            java.util.concurrent.ExecutorService executor = factory.createExecutorService();

            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;

                Gauge.builder("pekko.dispatcher.pool.size", threadPool, ThreadPoolExecutor::getPoolSize)
                     .description("Thread pool size of dispatcher")
                     .register(meterRegistry);

                Gauge.builder("pekko.dispatcher.active.threads", threadPool, ThreadPoolExecutor::getActiveCount)
                     .description("Active threads in dispatcher")
                     .register(meterRegistry);

                Gauge.builder("pekko.dispatcher.queue.size", threadPool, tp -> (double) tp.getQueue().size())
                     .description("Pending task queue size")
                     .register(meterRegistry);
            }
        }
    }
}
