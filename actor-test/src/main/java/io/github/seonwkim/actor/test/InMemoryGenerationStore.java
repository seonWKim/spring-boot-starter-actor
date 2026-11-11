package io.github.seonwkim.actor.test;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A {@link GenerationStore} in memory, for tests: one JVM only. It passes {@link GenerationStoreContract}. */
public final class InMemoryGenerationStore implements GenerationStore {

    private final Clock clock;
    private final Map<String, ActiveGeneration> active = new HashMap<>();
    /** deployment + operation ID -> the generation it activated. */
    private final Map<String, ActiveGeneration> operations = new HashMap<>();

    public InMemoryGenerationStore() {
        this(Clock.systemUTC());
    }

    public InMemoryGenerationStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Optional<ActiveGeneration> active(String deployment) {
        return Optional.ofNullable(active.get(deployment));
    }

    @Override
    public synchronized ActivationResult activate(
            String deployment, String color, long expectedEpoch, String operationId) {
        ActiveGeneration done = operations.get(deployment + '\n' + operationId);
        if (done != null) {
            boolean same = done.color().equals(color) && done.epoch() == expectedEpoch + 1;
            return new ActivationResult(same ? Outcome.ALREADY_ACTIVATED : Outcome.OPERATION_CONFLICT, done);
        }
        ActiveGeneration current = active.get(deployment);
        long epoch = current == null ? 0 : current.epoch();
        if (epoch != expectedEpoch) {
            return new ActivationResult(Outcome.EPOCH_MISMATCH, current);
        }
        ActiveGeneration next = new ActiveGeneration(color, epoch + 1, operationId, clock.instant());
        active.put(deployment, next);
        operations.put(deployment + '\n' + operationId, next);
        return new ActivationResult(Outcome.ACTIVATED, next);
    }
}
