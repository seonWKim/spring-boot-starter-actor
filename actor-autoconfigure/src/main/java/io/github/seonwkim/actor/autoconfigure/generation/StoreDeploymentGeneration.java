package io.github.seonwkim.actor.autoconfigure.generation;

import io.github.seonwkim.actor.delivery.StaleGenerationException;
import io.github.seonwkim.actor.generation.ActivationEvent;
import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import io.github.seonwkim.actor.generation.DeploymentGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link DeploymentGeneration} on a {@link GenerationStore}. Reads of the active generation are reused
 * for {@code refresh}; an activation through this node replaces the reused read at once.
 */
public class StoreDeploymentGeneration implements DeploymentGeneration {

    private static final Logger log = LoggerFactory.getLogger(StoreDeploymentGeneration.class);

    private final GenerationStore store;
    private final String deployment;
    private final String color;
    private final Duration refresh;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    private record Read(Optional<ActiveGeneration> active, Instant at) {}

    @Nullable private volatile Read last;

    public StoreDeploymentGeneration(
            GenerationStore store,
            String deployment,
            String color,
            Duration refresh,
            Clock clock,
            ApplicationEventPublisher events) {
        this.store = store;
        this.deployment = deployment;
        this.color = color;
        this.refresh = refresh;
        this.clock = clock;
        this.events = events;
    }

    /** The deployment name ({@code spring.actor.generation.name}). */
    public String deployment() {
        return deployment;
    }

    @Override
    public String color() {
        return color;
    }

    @Override
    public Optional<ActiveGeneration> active() {
        Read read = last;
        return read != null && clock.instant().isBefore(read.at().plus(refresh)) ? read.active() : activeNow();
    }

    /** Reads the store now, and reuses the read from here on. */
    public Optional<ActiveGeneration> activeNow() {
        Read read = new Read(store.active(deployment), clock.instant());
        last = read;
        return read.active();
    }

    @Override
    public long stamp() {
        ActiveGeneration active = active().orElse(null);
        if (active == null || !active.color().equals(color)) {
            throw new StaleGenerationException(color + " is not the active generation of " + deployment + " ("
                    + (active == null ? "none is active" : active.color() + " is, at epoch " + active.epoch())
                    + ")");
        }
        return active.epoch();
    }

    @Override
    public void check(long epoch) {
        long active = active().map(ActiveGeneration::epoch).orElse(0L);
        if (epoch != active) {
            throw new StaleGenerationException(
                    "work of epoch " + epoch + " is stale: the active epoch of " + deployment + " is " + active);
        }
    }

    @Override
    public ActivationResult activate(String color, long expectedEpoch, String operationId) {
        return activate(color, expectedEpoch, operationId, null);
    }

    /** An activation by {@code principal} (null from application code), audited. */
    public ActivationResult activate(String color, long expectedEpoch, String operationId, @Nullable String principal) {
        ActivationResult result = store.activate(deployment, color, expectedEpoch, operationId);
        if (result.outcome() == ActivationResult.Outcome.ACTIVATED) {
            last = new Read(Optional.ofNullable(result.generation()), clock.instant());
        }
        audit(color, expectedEpoch, operationId, principal, result);
        return result;
    }

    /** Logs an attempt and publishes its {@link ActivationEvent}; also for attempts rejected before the store. */
    public void audit(
            String color, long expectedEpoch, String operationId, @Nullable String principal, ActivationResult result) {
        String line = "Activation of {} for {} (expected epoch {}, operation {}, by {}): {}, active {}";
        Object[] args = {color, deployment, expectedEpoch, operationId, principal, result.outcome(), result.generation()
        };
        if (result.accepted()) {
            log.info(line, args);
        } else {
            log.warn(line, args);
        }
        events.publishEvent(
                new ActivationEvent(this, deployment, color, expectedEpoch, operationId, principal, result));
    }
}
