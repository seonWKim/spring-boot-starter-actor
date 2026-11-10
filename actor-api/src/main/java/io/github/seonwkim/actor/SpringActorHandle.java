package io.github.seonwkim.actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A handle to an actor: tell it, ask it, stop it, and work with its children. Handles are equal
 * when they point to the same actor.
 *
 * @param <T> The type of messages that the actor can handle
 */
public interface SpringActorHandle<T> extends SpringActorRef<T> {

    int DEFAULT_TIMEOUT_SECONDS = ActorConstants.DEFAULT_TIMEOUT_SECONDS;

    /**
     * Asks the actor a question using an AskCommand and returns a builder for configuring
     * the ask operation. This method automatically injects the reply-to reference into the command.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * // Simple ask
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123")).execute();
     *
     * // With timeout
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .execute();
     *
     * // With timeout handler
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .onTimeout(() -> "default-value")
     *     .execute();
     * }
     * </pre>
     *
     * @param command The command that implements AskCommand (must also be assignable to T)
     * @param <RES> The type of the response message
     * @return An AskBuilder for configuring and executing the ask operation
     */
    <RES> AskBuilder<RES> ask(AskCommand<RES> command);

    /**
     * Stops the actor once it has handled the messages already in its mailbox.
     *
     * @throws UnsupportedOperationException on the handle of a cluster singleton, which is this node's
     *     proxy to it: the singleton runs as long as the cluster does
     */
    void stop();

    /**
     * Creates a unified reference to a specific child actor for performing operations.
     * This is the recommended way to interact with child actors as it provides a concise,
     * single-entry-point API for all child operations.
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
     * // Check existence
     * boolean exists = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .exists()
     *     .toCompletableFuture()
     *     .get();
     *
     * // Spawn new child
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .spawn(SupervisionStrategy.restart())
     *     .toCompletableFuture()
     *     .get();
     *
     * // Get or spawn (convenience)
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .getOrSpawn(SupervisionStrategy.restart())
     *     .toCompletableFuture()
     *     .get();
     * }
     * </pre>
     *
     * <p><b>Important:</b> The parent actor must have framework commands enabled
     * (automatically enabled if Command extends FrameworkCommand) for child operations to work.
     *
     * @param childActorClass The child actor class (must be a Spring component)
     * @param childId The unique ID for the child actor
     * @param <CC> The command type of the child actor
     * @return A unified reference for performing operations on the child actor
     */
    <CC> SpringChildActorReference<T, CC> child(
            Class<? extends SpringActorWithContext<CC, ?>> childActorClass, String childId);

    /**
     * Creates a fluent builder for spawning a child actor with advanced configuration options.
     *
     * <p>This method is useful when you need to configure the child actor with custom context
     * or other advanced options before spawning. For simple use cases, prefer
     * {@link #child(Class, String)} which provides a more concise API.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class)
     *     .withId("child-1")
     *     .withSupervisionStrategy(SupervisionStrategy.restart())
     *     .withTimeout(Duration.ofSeconds(5))
     *     .spawn()
     *     .toCompletableFuture()
     *     .get();
     * }
     * </pre>
     *
     * <p><b>Important:</b> The parent actor must have framework commands enabled
     * (automatically enabled if Command extends FrameworkCommand) for child spawning to work.
     *
     * @param childActorClass The child actor class (must be a Spring component)
     * @param <CC> The command type of the child actor
     * @return A fluent builder for configuring and spawning the child actor
     */
    <CC> SpringChildActorBuilder<T, CC> child(Class<? extends SpringActorWithContext<CC, ?>> childActorClass);

    /**
     * An ask being prepared: set a timeout or a fallback, then {@link #execute()}.
     *
     * @param <RES> the reply type
     */
    interface AskBuilder<RES> {
        /** Replaces the handle's default timeout. */
        AskBuilder<RES> withTimeout(Duration timeout);

        /** Completes with this value instead of failing when the ask times out. */
        AskBuilder<RES> onTimeout(Supplier<RES> defaultValueSupplier);

        /** Sends the command; completes with the reply, or fails with a {@code TimeoutException}. */
        CompletionStage<RES> execute();
    }
}
