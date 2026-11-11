package io.github.seonwkim.actor.generation;

import io.github.seonwkim.actor.delivery.StaleGenerationException;
import java.util.Optional;

/**
 * Blue-green activation and fencing for this deployment. Each deployment (color) runs with
 * {@code spring.actor.generation.color} set; the active generation (color and epoch) is one record in
 * the shared store ({@code spring.actor.generation.store}), and the pipeline changes it with
 * {@link #activate}, usually through the {@code generation} Actuator endpoint.
 *
 * <p>Fencing: stamp work with {@link #stamp()} where it starts and {@link #check} it where it runs.
 * Work stamped by a color that was replaced fails with {@link StaleGenerationException}, a retryable
 * outcome, even after that color is activated again: the epoch only grows.
 *
 * <p>Reads of the active generation are reused for {@code spring.actor.generation.refresh} (1 second by
 * default), so a node sees an activation made elsewhere within that time.
 */
public interface DeploymentGeneration {

    /** This node's color ({@code spring.actor.generation.color}). */
    String color();

    /** The active generation, or empty before the first activation. */
    Optional<ActiveGeneration> active();

    /** Whether this node's color is the active one. */
    default boolean isActive() {
        return active().map(active -> active.color().equals(color())).orElse(false);
    }

    /**
     * The epoch to stamp new work with.
     *
     * @throws StaleGenerationException if this node's color is not active
     */
    long stamp();

    /**
     * Checks work stamped with {@code epoch}.
     *
     * @throws StaleGenerationException unless {@code epoch} is the active epoch
     */
    void check(long epoch);

    /**
     * Makes {@code color} active if the active epoch is {@code expectedEpoch} (0 before the first
     * activation): a compare-and-set, so of several deployments that expect the same epoch exactly one
     * wins. Retrying the same {@code operationId} with the same color and epoch returns the generation it
     * activated. Every attempt publishes an {@link ActivationEvent}.
     */
    ActivationResult activate(String color, long expectedEpoch, String operationId);
}
