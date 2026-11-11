package io.github.seonwkim.actor.spi;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import java.util.Optional;

/**
 * Where the active generation of each deployment is kept for blue-green activation
 * ({@code spring.actor.generation.*}): one record per deployment, changed only by a compare-and-set on
 * its epoch. Built-in implementations are {@code JdbcGenerationStore} ({@code actor-jdbc}) and
 * {@code RedisGenerationStore} ({@code actor-redis}); an application bean of this type replaces them.
 * Every implementation must pass the {@code GenerationStoreContract} test suite. Methods may block.
 */
public interface GenerationStore {

    /** The active generation of {@code deployment}, or empty before its first activation. */
    Optional<ActiveGeneration> active(String deployment);

    /**
     * Atomically, as one step against every other caller:
     *
     * <ol>
     *   <li>if {@code operationId} already activated a generation of {@code deployment}, returns
     *       {@code ALREADY_ACTIVATED} with it when it has the same color and was activated from
     *       {@code expectedEpoch}, and {@code OPERATION_CONFLICT} with it otherwise;
     *   <li>if the active epoch (0 before the first activation) is not {@code expectedEpoch}, returns
     *       {@code EPOCH_MISMATCH} with the active generation;
     *   <li>otherwise makes {@code color} active at {@code expectedEpoch + 1}, stamped with the store's
     *       clock, remembers {@code operationId}, and returns {@code ACTIVATED} with the new generation.
     * </ol>
     */
    ActivationResult activate(String deployment, String color, long expectedEpoch, String operationId);
}
