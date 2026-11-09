package io.github.seonwkim.actor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A reference to a child actor that provides operations for querying existing child actors.
 * This class is for pure referencing operations only - to spawn new child actors, use
 * {@link SpringActorHandle#child(Class)} which returns a {@link SpringChildActorBuilder}.
 *
 * <p>This class eliminates redundant class and ID parameters by capturing them once and providing
 * simple methods for checking actor existence and getting references.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * // Get existing child
 * Optional<SpringActorHandle<ChildCommand>> child = parentRef
 *     .child(ChildActor.class, "child-1")
 *     .get()
 *     .toCompletableFuture()
 *     .get();
 *
 * // Check if child exists
 * boolean exists = parentRef
 *     .child(ChildActor.class, "child-1")
 *     .exists()
 *     .toCompletableFuture()
 *     .get();
 * }
 * </pre>
 *
 * <p><b>Important:</b> The parent actor must have framework commands enabled
 * (automatically enabled if Command extends FrameworkCommand) for child operations to work.
 *
 * @param <P> The parent actor's command type
 * @param <C> The child actor's command type
 * @see SpringChildActorBuilder for spawning new child actors
 */
public interface SpringChildActorReference<P, C> {

    /**
     * Sets the timeout for subsequent operations.
     * This affects get() and exists() calls made after this method.
     *
     * @param timeout The timeout duration
     * @return This reference for method chaining
     */
    SpringChildActorReference<P, C> withTimeout(Duration timeout);

    /**
     * Gets a reference to the child actor if it exists.
     * Returns an empty Optional if the child doesn't exist.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the result of the operation.
     *
     * @return A CompletionStage that will be completed with an Optional containing the child
     *         actor reference if found, or an empty Optional if not found
     */
    CompletionStage<Optional<SpringActorHandle<C>>> get();

    /**
     * Checks if the child actor exists.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the result of the operation.
     *
     * @return A CompletionStage that will be completed with true if the child exists,
     *         false otherwise
     */
    CompletionStage<Boolean> exists();
}
