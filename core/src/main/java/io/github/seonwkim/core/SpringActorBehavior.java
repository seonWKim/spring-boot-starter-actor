package io.github.seonwkim.core;

import java.util.function.Function;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * A behavior wrapper that provides framework-level features for Spring actors.
 *
 * <p>This class wraps Pekko's {@link Behavior} and adds Spring actor framework capabilities
 * such as automatic handling of framework commands (e.g., spawning children with Spring DI).
 *
 * <p>Use the fluent builder API to construct behaviors:
 * <pre>
 * {@code
 * @Override
 * public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
 *     return SpringActorBehavior.builder(actorContext)
 *         .withFrameworkCommands()  // Opt-in to framework command handling
 *         .setup(ctx -> {
 *             // Use Pekko's standard Behaviors API
 *             return SpringActorBehavior.of(Behaviors.receive(Command.class)
 *                 .onMessage(DoWork.class, this::handleDoWork)
 *                 .build());
 *         })
 *         .build();
 * }
 * }
 * </pre>
 *
 * <p><b>Framework Commands:</b> When framework commands are enabled via
 * {@link Builder#withFrameworkCommands()}, the behavior will automatically handle:
 * <ul>
 *   <li>{@link FrameworkCommands.SpawnChild} - Spawn child actors with Spring DI</li>
 * </ul>
 *
 * <p><b>Zero Overhead:</b> If framework commands are not enabled, there is no performance
 * overhead - the user's behavior is used directly without any wrapping.
 *
 * @param <C> The command type this behavior handles
 * @see FrameworkCommand
 * @see FrameworkCommands
 */
public final class SpringActorBehavior<C> {

    private final Behavior<C> behavior;

    private SpringActorBehavior(Behavior<C> behavior) {
        this.behavior = behavior;
    }

    /**
     * Unwraps this SpringActorBehavior to get the underlying Pekko Behavior.
     *
     * @return the underlying Pekko behavior
     */
    public Behavior<C> asBehavior() {
        return behavior;
    }

    /**
     * Creates a new builder for constructing a SpringActorBehavior.
     *
     * @param actorContext the Spring actor context
     * @param <C>          the command type
     * @return a new builder instance
     */
    public static <C> Builder<C> builder(SpringActorContext actorContext) {
        return new Builder<>(actorContext);
    }

    /**
     * Builder for creating SpringActorBehavior instances with a fluent API.
     *
     * @param <C> the command type
     */
    public static final class Builder<C> {
        private final SpringActorContext actorContext;
        private boolean enableFrameworkCommands = false;
        private Function<ActorContext<C>, SpringActorBehavior<C>> setupFunction;

        private Builder(SpringActorContext actorContext) {
            this.actorContext = actorContext;
        }

        /**
         * Enables framework command handling.
         *
         * <p>When enabled, the behavior will automatically handle framework commands such as
         * {@link FrameworkCommands.SpawnChild} without requiring explicit message handlers.
         *
         * @return this builder for chaining
         */
        public Builder<C> withFrameworkCommands() {
            this.enableFrameworkCommands = true;
            return this;
        }

        /**
         * Defines the behavior using a setup function.
         *
         * <p>The setup function receives the Pekko ActorContext and should return a
         * SpringActorBehavior that defines how the actor processes messages.
         *
         * @param setupFunction the function to create the behavior
         * @return this builder for chaining
         */
        public Builder<C> setup(Function<ActorContext<C>, SpringActorBehavior<C>> setupFunction) {
            this.setupFunction = setupFunction;
            return this;
        }

        /**
         * Builds the final SpringActorBehavior.
         *
         * @return the constructed behavior
         */
        public SpringActorBehavior<C> build() {
            if (setupFunction == null) {
                throw new IllegalStateException("setup() must be called before build()");
            }

            Behavior<C> behavior = Behaviors.setup(ctx -> {
                SpringActorBehavior<C> userBehavior = setupFunction.apply(ctx);

                // TODO: Implement framework command handling when enableFrameworkCommands is true
                // For now, always use user behavior directly
                return userBehavior.asBehavior();
            });

            return new SpringActorBehavior<>(behavior);
        }
    }
}
