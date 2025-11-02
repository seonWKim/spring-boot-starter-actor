package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.SupervisorStrategy;

/**
 * Framework-provided commands that actors can use when framework command handling
 * is enabled via {@link SpringActorBehavior.Builder#withFrameworkCommands()}.
 *
 * <p>These commands are automatically handled by {@link SpringActorBehavior} when
 * framework command handling is enabled via the builder.
 *
 * <p><b>No marker interface required:</b> Simply enable framework commands in the builder:
 * <pre>
 * {@code
 * SpringActorBehavior.builder(actorContext)
 *     .withFrameworkCommands()  // Enables framework command handling
 *     .setup(ctx -> ...)
 *     .build();
 * }
 * </pre>
 *
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
     * // Create child context
     * SpringActorContext childContext = new SpringActorContext() {
     *     @Override
     *     public String actorId() {
     *         return "child-1";
     *     }
     * };
     *
     * // From parent actor (using tell)
     * getContext().getSelf().tell(new FrameworkCommands.SpawnChild<>(
     *     ChildActor.class,
     *     childContext,
     *     SupervisorStrategy.restart(),
     *     replyTo
     * ));
     *
     * // Or using ask pattern
     * CompletionStage<SpawnChildResponse<ChildCommand>> response =
     *     AskPattern.ask(
     *         getContext().getSelf(),
     *         replyTo -> new FrameworkCommands.SpawnChild<>(
     *             ChildActor.class,
     *             childContext,
     *             SupervisorStrategy.restart(),
     *             replyTo
     *         ),
     *         Duration.ofSeconds(3),
     *         getContext().getSystem().scheduler()
     *     );
     * }
     * </pre>
     *
     * @param <C> The command type of the child actor to spawn
     */
    public static final class SpawnChild<C> {
        public final Class<? extends SpringActorWithContext<C, ?>> actorClass;
        public final SpringActorContext childContext;
        public final SupervisorStrategy strategy;
        public final ActorRef<SpawnChildResponse<C>> replyTo;

        public SpawnChild(
                Class<? extends SpringActorWithContext<C, ?>> actorClass,
                SpringActorContext childContext,
                SupervisorStrategy strategy,
                ActorRef<SpawnChildResponse<C>> replyTo) {
            this.actorClass = actorClass;
            this.childContext = childContext;
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
