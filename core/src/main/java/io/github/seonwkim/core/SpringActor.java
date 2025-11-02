package io.github.seonwkim.core;

/**
 * Interface for Spring-managed actors using the default {@link SpringActorContext}. Classes
 * implementing this interface will be automatically registered with the actor system.
 *
 * <p>This is the preferred interface for most actors. It provides a clean API with only 1 generic
 * parameter. For actors requiring custom context types, use {@link SpringActorWithContext} instead.
 *
 * <p>The SpringActor interface is part of the actor system architecture:
 *
 * <ul>
 *   <li>SpringActor: Interface for actor implementations that can be managed by the Spring actor
 *       system (this interface)
 *   <li>SpringActorSystem: Main entry point for creating and managing actors
 *   <li>RootGuardian: Internal component that manages the lifecycle of actors
 * </ul>
 *
 * <p>When you implement this interface, your actor will be registered with the ActorTypeRegistry
 * and can be spawned using the SpringActorSystem.spawn methods.
 *
 * <p>Example usage:
 * <pre>
 * &#64;Component
 * public class HelloActor implements SpringActor&lt;HelloActor.Command&gt; {
 *     &#64;Override
 *     public SpringActorBehavior&lt;Command&gt; create(SpringActorContext actorContext) {
 *         return SpringActorBehavior.builder(actorContext)
 *             .setup(ctx -&gt; ...)
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <C> The command type that this actor handles
 * @see SpringActorWithContext
 */
public interface SpringActor<C> extends SpringActorWithContext<C, SpringActorContext> {}
