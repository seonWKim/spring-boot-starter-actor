package io.github.seonwkim.core;

import java.time.Duration;
import javax.annotation.Nullable;
import org.springframework.context.SmartLifecycle;

/**
 * Ties Pekko coordinated shutdown to the Spring application context close.
 *
 * <p>Spring stops lifecycle beans from the highest phase to the lowest before it destroys any
 * singleton. This bean runs at {@link #PHASE}, below Spring Boot's web server lifecycles (graceful
 * shutdown at {@code Integer.MAX_VALUE} and stop at {@code Integer.MAX_VALUE - 1} in Boot 2.7;
 * {@code Integer.MAX_VALUE - 1024} and {@code Integer.MAX_VALUE - 2048} since Boot 3.2), so:
 *
 * <ol>
 *   <li>with {@code server.shutdown=graceful} the web server stops taking requests and drains first,
 *   <li>then the actor system runs coordinated shutdown (leaving the cluster gracefully) and this
 *       bean waits at most the configured timeout ({@code spring.actor.shutdown.timeout}),
 *   <li>then Spring destroys the beans the actors use.
 * </ol>
 */
public class ActorSystemShutdownLifecycle implements SmartLifecycle {

    /** Phase of this lifecycle: stops after the web server, before lower-phase lifecycles. */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

    private final SpringActorSystem actorSystem;
    private final Duration timeout;

    private volatile boolean running;

    @Nullable private volatile SpringActorSystem.ShutdownOutcome lastOutcome;

    /**
     * Creates the lifecycle.
     *
     * @param actorSystem The actor system to shut down when the context closes
     * @param timeout The maximum time to wait for the actor system to terminate
     */
    public ActorSystemShutdownLifecycle(SpringActorSystem actorSystem, Duration timeout) {
        this.actorSystem = actorSystem;
        this.timeout = timeout;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        try {
            lastOutcome = actorSystem.shutdown(timeout);
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    /**
     * Returns the timeout this lifecycle waits for the actor system to terminate.
     *
     * @return The shutdown timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns what the last stop did, or null if it has not stopped yet.
     *
     * @return The last shutdown outcome
     */
    @Nullable public SpringActorSystem.ShutdownOutcome getLastOutcome() {
        return lastOutcome;
    }
}
