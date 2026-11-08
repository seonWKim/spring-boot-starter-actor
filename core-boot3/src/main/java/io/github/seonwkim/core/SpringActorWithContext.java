package io.github.seonwkim.core;

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
 * public class UserActor implements SpringActorWithContext&lt;Command, UserActorContext&gt; {
 *
 *     // Framework commands are automatically enabled when Command extends FrameworkCommand
 *     public interface Command extends FrameworkCommand {}
 *
 *     public static class MyCommand implements Command {}
 *
 *     &#64;Override
 *     public SpringActorBehavior&lt;Command&gt; create(UserActorContext context) {
 *         // Type-safe access to custom context - no casting needed!
 *         return SpringActorBehavior.builder(Command.class, context)
 *             .onMessage(MyCommand.class, (ctx, msg) -&gt; {
 *                 ctx.getLog().info("Handling command");
 *                 return Behaviors.same();
 *             })
 *             .build();
 *     }
 *
 *     public static class UserActorContext implements SpringActorContext {
 *         private final String userId;
 *         // ... custom fields and methods
 *
 *         &#64;Override
 *         public String actorId() {
 *             return userId;
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <C> The command type that this actor handles
 * @param <CTX> The context type that this actor requires (must extend SpringActorContext)
 * @see SpringActor
 * @see SpringActorBehavior
 */
public interface SpringActorWithContext<C, CTX extends SpringActorContext> {
    /**
     * Creates a behavior for this actor. This method is called by the actor system when a new actor
     * is created.
     *
     * @param actorContext The context of the actor (type-safe, no casting needed)
     * @return A SpringActorBehavior for the actor
     */
    SpringActorBehavior<C> create(CTX actorContext);
}
