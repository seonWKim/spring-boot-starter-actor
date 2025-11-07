package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import javax.annotation.Nullable;

/**
 * Framework-provided commands that actors can use when their Command interface
 * extends {@link FrameworkCommand}.
 *
 * <p>These commands are automatically handled by {@link SpringActorBehavior} when
 * the actor's Command interface extends {@link FrameworkCommand}. No explicit
 * configuration is required.
 *
 * <p><b>Automatic enablement:</b> Simply extend FrameworkCommand in your Command interface:
 * <pre>
 * {@code
 * public interface Command extends FrameworkCommand {}
 *
 * SpringActorBehavior.builder(Command.class, actorContext)
 *     .onMessage(MyMessage.class, this::handleMessage)
 *     .build();  // Framework commands are automatically enabled
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
    public static final class SpawnChild<C> implements FrameworkCommand {
        public final Class<? extends SpringActorWithContext<C, ?>> actorClass;
        public final SpringActorContext childContext;
        @Nullable public final SupervisorStrategy strategy;
        public final MailboxConfig mailboxConfig;
        public final DispatcherConfig dispatcherConfig;
        public final TagsConfig tagsConfig;
        public final ActorRef<SpawnChildResponse<C>> replyTo;

        public SpawnChild(
                Class<? extends SpringActorWithContext<C, ?>> actorClass,
                SpringActorContext childContext,
                @Nullable SupervisorStrategy strategy,
                @Nullable MailboxConfig mailboxConfig,
                @Nullable DispatcherConfig dispatcherConfig,
                @Nullable TagsConfig tagsConfig,
                ActorRef<SpawnChildResponse<C>> replyTo) {
            this.actorClass = actorClass;
            this.childContext = childContext;
            this.strategy = strategy;
            this.mailboxConfig = mailboxConfig != null ? mailboxConfig : MailboxConfig.defaultMailbox();
            this.dispatcherConfig = dispatcherConfig != null ? dispatcherConfig : DispatcherConfig.defaultDispatcher();
            this.tagsConfig = tagsConfig != null ? tagsConfig : TagsConfig.empty();
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
        @Nullable public final ActorRef<C> childRef;
        public final boolean success;
        public final String message;

        private SpawnChildResponse(@Nullable ActorRef<C> childRef, boolean success, String message) {
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

    /**
     * Framework command to get a reference to an existing child actor.
     *
     * <p>This command allows actors to retrieve a reference to a child that has already been spawned.
     *
     * @param <C> The command type of the child actor
     */
    public static final class GetChild<C> implements FrameworkCommand {
        public final Class<? extends SpringActorWithContext<C, ?>> actorClass;
        public final String childId;
        public final ActorRef<GetChildResponse<C>> replyTo;

        public GetChild(
                Class<? extends SpringActorWithContext<C, ?>> actorClass,
                String childId,
                ActorRef<GetChildResponse<C>> replyTo) {
            this.actorClass = actorClass;
            this.childId = childId;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response to a {@link GetChild} command.
     *
     * @param <C> The command type of the child actor
     */
    public static final class GetChildResponse<C> {
        @Nullable public final ActorRef<C> childRef;
        public final boolean found;

        private GetChildResponse(@Nullable ActorRef<C> childRef, boolean found) {
            this.childRef = childRef;
            this.found = found;
        }

        public static <C> GetChildResponse<C> found(ActorRef<C> childRef) {
            return new GetChildResponse<>(childRef, true);
        }

        public static <C> GetChildResponse<C> notFound() {
            return new GetChildResponse<>(null, false);
        }
    }

    /**
     * Framework command to check if a child actor exists.
     *
     * @param <C> The command type of the child actor
     */
    public static final class ExistsChild<C> implements FrameworkCommand {
        public final Class<? extends SpringActorWithContext<C, ?>> actorClass;
        public final String childId;
        public final ActorRef<ExistsChildResponse> replyTo;

        public ExistsChild(
                Class<? extends SpringActorWithContext<C, ?>> actorClass,
                String childId,
                ActorRef<ExistsChildResponse> replyTo) {
            this.actorClass = actorClass;
            this.childId = childId;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response to an {@link ExistsChild} command.
     */
    public static final class ExistsChildResponse {
        public final boolean exists;

        private ExistsChildResponse(boolean exists) {
            this.exists = exists;
        }

        public static ExistsChildResponse exists() {
            return new ExistsChildResponse(true);
        }

        public static ExistsChildResponse notExists() {
            return new ExistsChildResponse(false);
        }
    }
}
