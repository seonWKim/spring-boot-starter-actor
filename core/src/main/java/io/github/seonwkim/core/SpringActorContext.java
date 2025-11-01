package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Represents the context for an actor in the Spring Actor system.
 *
 * <p>This interface provides a way to identify actors uniquely within the actor system through the
 * {@link #actorId()} method. It can be extended to include additional context information specific
 * to different actor types.
 *
 * <p>The default implementation is {@code DefaultSpringActorContext}, which simply stores and
 * returns an actor ID. Custom implementations can add additional context information as needed,
 * such as in the chat example's {@code UserActorContext} which includes a WebSocketSession.
 *
 * <p>This context is used when spawning and stopping actors through the {@code SpringActorSystem},
 * and is passed to actor factory methods during creation.
 *
 * <p><b>Hierarchical Supervision:</b> This interface supports spawning child actors with Spring
 * dependency injection through the {@link #spawnChild} methods. The registry is automatically
 * injected by the framework when actors are created, enabling hierarchical supervision trees.
 */
public interface SpringActorContext {

    /**
     * Returns the unique identifier for this actor.
     *
     * <p>This ID is used to identify the actor within the actor system and should be unique for each
     * actor of the same type. It is used when spawning, addressing, and stopping actors.
     *
     * @return the unique actor identifier
     */
    String actorId();

    /**
     * Returns the actor type registry for creating Spring-managed child actors.
     *
     * <p>This registry is automatically injected by the framework when actors are created.
     * Default implementations return null. The framework ensures a non-null registry is available
     * when actors are spawned, enabling the {@link #spawnChild} methods to work correctly.
     *
     * @return the actor type registry, or null if not yet injected
     */
    default ActorTypeRegistry registry() {
        return null;
    }

    /**
     * Spawns a Spring-managed child actor with default supervision strategy.
     *
     * <p>This is a convenience method that calls {@link #spawnChild(ActorContext, Class, String, SupervisorStrategy)}
     * with a default restart supervision strategy.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * @Override
     * public Behavior<Command> create(SpringActorContext actorContext) {
     *     return Behaviors.setup(ctx -> {
     *         ActorRef<ChildActor.Command> child = actorContext.spawnChild(
     *             ctx,
     *             ChildActor.class,
     *             "child-1"
     *         );
     *         return parentBehavior(child);
     *     });
     * }
     * }
     * </pre>
     *
     * @param pekkoContext the Pekko actor context (from Behaviors.setup)
     * @param actorClass   the class of the child actor to spawn (must be a Spring bean)
     * @param childId      the unique identifier for the child actor
     * @param <A>          the type of the actor being spawned
     * @param <C>          the type of commands the actor handles
     * @return an ActorRef to the spawned child actor
     */
    default <A extends SpringActorWithContext<A, C, ?>, C> ActorRef<C> spawnChild(
            ActorContext<?> pekkoContext,
            Class<A> actorClass,
            String childId
    ) {
        return spawnChild(pekkoContext, actorClass, childId, SupervisorStrategy.restart());
    }

    /**
     * Spawns a Spring-managed child actor with a custom supervision strategy.
     *
     * <p>This method creates a child actor with full Spring dependency injection support.
     * The child actor will be spawned under the current actor in the supervision hierarchy,
     * allowing for proper error escalation and isolation.
     *
     * <p>The supervision strategy determines how the child actor behaves when it fails:
     * <ul>
     *   <li>{@code SupervisorStrategy.restart()} - Restart the actor on failure (default)</li>
     *   <li>{@code SupervisorStrategy.stop()} - Stop the actor on failure</li>
     *   <li>{@code SupervisorStrategy.resume()} - Resume processing, ignoring the failure</li>
     * </ul>
     *
     * <p>Example with custom supervision:
     * <pre>
     * {@code
     * @Override
     * public Behavior<Command> create(SpringActorContext actorContext) {
     *     return Behaviors.setup(ctx -> {
     *         // Restart up to 10 times within 1 minute
     *         ActorRef<ChildActor.Command> child = actorContext.spawnChild(
     *             ctx,
     *             ChildActor.class,
     *             "child-1",
     *             SupervisorStrategy.restart()
     *                 .withLimit(10, Duration.ofMinutes(1))
     *         );
     *         return parentBehavior(child);
     *     });
     * }
     * }
     * </pre>
     *
     * @param pekkoContext the Pekko actor context (from Behaviors.setup)
     * @param actorClass   the class of the child actor to spawn (must be a Spring bean)
     * @param childId      the unique identifier for the child actor
     * @param strategy     the supervision strategy for this child
     * @param <A>          the type of the actor being spawned
     * @param <C>          the type of commands the actor handles
     * @return an ActorRef to the spawned child actor
     */
    default <A extends SpringActorWithContext<A, C, ?>, C> ActorRef<C> spawnChild(
            ActorContext<?> pekkoContext,
            Class<A> actorClass,
            String childId,
            SupervisorStrategy strategy
    ) {
        ActorTypeRegistry reg = registry();
        if (reg == null) {
            throw new IllegalStateException(
                "Registry not available. Ensure the actor was spawned through SpringActorSystem."
            );
        }

        // Create a child context with the same registry
        SpringActorContext childContext = new SpringActorContext() {
            @Override
            public String actorId() {
                return childId;
            }

            @Override
            public ActorTypeRegistry registry() {
                return reg;
            }
        };

        // Use the registry to create a Spring-managed behavior
        Behavior<C> behavior = (Behavior<C>) reg.createBehavior(actorClass, childContext);

        // Wrap with supervision strategy
        Behavior<C> supervised = Behaviors.supervise(behavior).onFailure(strategy);

        // Spawn using Pekko's native spawn
        return pekkoContext.spawn(supervised, childId);
    }
}
