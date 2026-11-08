package io.github.seonwkim.core.health;

import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * Reports the actor system as {@code UP} while it runs and {@code DOWN} once it has terminated.
 *
 * <p>Registered as the {@code actorSystem} health contributor by {@link ActorHealthAutoConfiguration}.
 */
public class ActorSystemHealthIndicator extends AbstractHealthIndicator {

    private final ActorSystem<?> actorSystem;

    public ActorSystemHealthIndicator(ActorSystem<?> actorSystem) {
        super("Actor system health check failed");
        this.actorSystem = actorSystem;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (actorSystem.getWhenTerminated().toCompletableFuture().isDone()) {
            builder.down();
        } else {
            builder.up();
        }
    }
}
