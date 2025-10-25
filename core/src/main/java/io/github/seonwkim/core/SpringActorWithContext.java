package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

/**
 * Interface for Spring-managed actors that require custom context types. Classes implementing this
 * interface will be automatically registered with the actor system.
 *
 * <p>This interface provides type-safe context handling for actors that need custom context
 * implementations beyond the default {@link SpringActorContext}. For actors using the default
 * context, use {@link SpringActor} instead for a cleaner API.
 *
 * <p>Example usage with custom context:
 * <pre>
 * &#64;Component
 * public class UserActor implements SpringActorWithContext&lt;UserActor, Command, UserActorContext&gt; {
 *     &#64;Override
 *     public Behavior&lt;Command&gt; create(UserActorContext context) {
 *         // Type-safe access to custom context - no casting needed!
 *         return Behaviors.setup(ctx -&gt; ...);
 *     }
 *
 *     public static class UserActorContext implements SpringActorContext {
 *         private final WebSocketSession session;
 *         // ... custom fields and methods
 *     }
 * }
 * </pre>
 *
 * @param <A> The actor implementation type (self-reference)
 * @param <C> The command type that this actor handles
 * @param <CTX> The context type that this actor requires (must extend SpringActorContext)
 * @see SpringActor
 */
public interface SpringActorWithContext<
        A extends SpringActorWithContext<A, C, CTX>, C, CTX extends SpringActorContext> {
    /**
     * Creates a behavior for this actor. This method is called by the actor system when a new actor
     * is created.
     *
     * @param actorContext The context of the actor (type-safe, no casting needed)
     * @return A behavior for the actor
     */
    Behavior<C> create(CTX actorContext);
}
