package io.github.seonwkim.actor.autoconfigure.health;

import io.github.seonwkim.actor.SpringActorSystem;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/** {@code UP} while the actor system runs, {@code DOWN} once it has terminated. */
public class ActorSystemHealthIndicator extends AbstractHealthIndicator {

    private final SpringActorSystem actorSystem;
    private volatile boolean terminated;

    public ActorSystemHealthIndicator(SpringActorSystem actorSystem) {
        super("Actor system health check failed");
        this.actorSystem = actorSystem;
        // Each whenTerminated() call is a new stage that completes asynchronously: subscribe once.
        actorSystem.whenTerminated().whenComplete((done, error) -> terminated = true);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        (terminated ? builder.down() : builder.up()).withDetail("name", actorSystem.getName());
    }
}
