package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.SupervisorStrategy;

/**
 * Framework-provided commands that actors can use when their command interface
 * extends {@link FrameworkCommand}.
 *
 * <p>These commands are automatically handled by {@link SpringActorBehavior} when
 * framework command handling is enabled via the builder.
 *
 * @see FrameworkCommand
 * @see SpringActorBehavior
 */
public final class FrameworkCommands {

    private FrameworkCommands() {
        // Utility class
    }

    /**
     * Framework command to spawn a child actor with Spring dependency injection support.
     *
     * <p>This command allows actors to spawn children dynamically with full Spring DI,
     * supervision strategies, and proper hierarchy management.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * // From parent actor
     * parentRef.tell(new FrameworkCommands.SpawnChild<>(
     *     ChildActor.class,
     *     "child-1",
     *     SupervisorStrategy.restart(),
     *     replyTo
     * ));
     *
     * // Or using ask pattern
     * CompletionStage<SpawnChildResponse> response = parentRef.ask(
     *     replyTo -> new FrameworkCommands.SpawnChild<>(
     *         ChildActor.class,
     *         "child-1",
     *         SupervisorStrategy.restart(),
     *         replyTo
     *     )
     * );
     * }
     * </pre>
     *
     * @param <C> The command type of the child actor to spawn
     */
    public static final class SpawnChild<C> implements FrameworkCommand {
        public final Class<? extends SpringActorWithContext<?, C, ?>> actorClass;
        public final String childId;
        public final SupervisorStrategy strategy;
        public final ActorRef<SpawnChildResponse<C>> replyTo;

        public SpawnChild(
                Class<? extends SpringActorWithContext<?, C, ?>> actorClass,
                String childId,
                SupervisorStrategy strategy,
                ActorRef<SpawnChildResponse<C>> replyTo) {
            this.actorClass = actorClass;
            this.childId = childId;
            this.strategy = strategy;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response to a {@link SpawnChild} command.
     *
     * <p>Contains the {@link ActorRef} of the spawned child if successful, or an error message
     * if the spawn failed.
     *
     * @param <C> The command type of the spawned child actor
     */
    public static final class SpawnChildResponse<C> {
        public final ActorRef<C> childRef;
        public final boolean success;
        public final String message;

        private SpawnChildResponse(ActorRef<C> childRef, boolean success, String message) {
            this.childRef = childRef;
            this.success = success;
            this.message = message;
        }

        public static <C> SpawnChildResponse<C> success(ActorRef<C> childRef) {
            return new SpawnChildResponse<>(childRef, true, "Child spawned successfully");
        }

        public static <C> SpawnChildResponse<C> failure(String message) {
            return new SpawnChildResponse<>(null, false, message);
        }

        public static <C> SpawnChildResponse<C> alreadyExists(ActorRef<C> existingRef) {
            return new SpawnChildResponse<>(existingRef, false, "Child already exists");
        }
    }
}
