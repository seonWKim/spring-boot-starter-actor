package io.github.seonwkim.actor;

import java.time.Duration;
import java.util.Objects;

/**
 * What happens to an actor when its message handler throws.
 *
 * <pre>{@code
 * actorSystem.actor(OrderActor.class)
 *     .withId("order-1")
 *     .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(3, Duration.ofMinutes(1)))
 *     .spawn();
 * }</pre>
 */
public sealed interface SupervisionStrategy
        permits SupervisionStrategy.Restart, SupervisionStrategy.Resume, SupervisionStrategy.Stop {

    /** Restart the actor with fresh state, with no limit on restarts. */
    static Restart restart() {
        return new Restart(-1, Duration.ZERO);
    }

    /** Keep the actor and its state, and drop the message that failed. */
    static SupervisionStrategy resume() {
        return Resume.INSTANCE;
    }

    /** Stop the actor (the default when no strategy is set). */
    static SupervisionStrategy stop() {
        return Stop.INSTANCE;
    }

    /**
     * Restart the actor with fresh state.
     *
     * @param maxRetries the restarts allowed within {@code within}, or -1 for no limit
     * @param within the window {@code maxRetries} counts in; ignored when there is no limit
     */
    record Restart(int maxRetries, Duration within) implements SupervisionStrategy {
        public Restart {
            Objects.requireNonNull(within, "within");
            if (maxRetries < -1) {
                throw new IllegalArgumentException("maxRetries must be -1 (no limit) or >= 0, got " + maxRetries);
            }
        }

        /** Stop the actor instead once it has restarted {@code maxRetries} times within {@code within}. */
        public Restart withLimit(int maxRetries, Duration within) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0, got " + maxRetries);
            }
            return new Restart(maxRetries, within);
        }
    }

    /** See {@link #resume()}. */
    enum Resume implements SupervisionStrategy {
        INSTANCE
    }

    /** See {@link #stop()}. */
    enum Stop implements SupervisionStrategy {
        INSTANCE
    }
}
