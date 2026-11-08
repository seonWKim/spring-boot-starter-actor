package io.github.seonwkim.actor.internal;

import com.typesafe.config.Config;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.dispatch.DispatcherPrerequisites;
import org.apache.pekko.dispatch.ExecutorServiceConfigurator;
import org.apache.pekko.dispatch.ExecutorServiceFactory;
import org.springframework.beans.factory.BeanFactory;

/**
 * Runs a dispatcher on a Spring {@link Executor} bean, named by the dispatcher's
 * {@value #BEAN_KEY} key. The bean's owner (the Spring context) shuts it down; stopping the
 * dispatcher only stops handing it work.
 */
public final class SpringExecutorConfigurator extends ExecutorServiceConfigurator {

    /** The dispatcher config key that names the executor bean. */
    public static final String BEAN_KEY = "spring-executor";

    private final String beanName;
    private final BeanFactory beanFactory;

    public SpringExecutorConfigurator(Config config, DispatcherPrerequisites prerequisites) {
        super(config, prerequisites);
        this.beanName = config.getString(BEAN_KEY);
        this.beanFactory = prerequisites
                .settings()
                .setup()
                .get(SpringBeansSetup.class)
                .orElseThrow(() -> new IllegalStateException("Dispatcher with " + BEAN_KEY + " = " + beanName
                        + " needs an actor system built by the starter"))
                .beanFactory();
    }

    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(String id, ThreadFactory threadFactory) {
        return () -> new BeanExecutorService(beanFactory.getBean(beanName, Executor.class));
    }

    /** An {@link ExecutorService} view of a Spring executor that never shuts the executor down. */
    private static final class BeanExecutorService extends AbstractExecutorService {
        private final Executor executor;
        private volatile boolean shutdown;

        BeanExecutorService(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            executor.execute(command);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }
}
