package io.github.seonwkim.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for dedicated executor pools to handle blocking operations.
 * This prevents blocking operations from blocking the actor system threads.
 */
@Configuration
public class ExecutorConfiguration {

    /**
     * Dedicated executor for blocking I/O operations (WebSocket, file I/O, etc.)
     * Uses virtual threads (Java 21+) or regular thread pool (Java 11-17) for efficiency.
     */
    @Bean(name = "blockingIoExecutor")
    public Executor blockingIoExecutor() {
        try {
            // Try to use virtual threads if available (Java 21+)
            var executorsClass = Executors.class;
            var method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
            return (Executor) method.invoke(null);
        } catch (Exception e) {
            // Fall back to regular thread pool for Java 11-17
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setName("blocking-io-" + t.getId());
                t.setDaemon(true);
                return t;
            });
        }
    }
}
