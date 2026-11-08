package io.github.seonwkim.core;

/**
 * Marker interface for actor commands that can receive framework-level support.
 *
 * <p>When your actor's command interface extends {@code FrameworkCommand}, the actor can
 * automatically handle framework-provided commands such as spawning child actors, without
 * needing to explicitly define message handlers for them.
 *
 * <p>This is an opt-in mechanism. Actors that don't need framework commands can simply
 * use plain command interfaces without extending {@code FrameworkCommand}.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @Component
 * public class ParentActor implements SpringActor<ParentActor, ParentActor.Command> {
 *
 *     // Opt-in to framework commands by extending FrameworkCommand
 *     // Framework command handling is automatically enabled when Command extends FrameworkCommand
 *     public interface Command extends FrameworkCommand {}
 *
 *     public static class DoWork implements Command {
 *         // Your custom commands
 *     }
 *
 *     @Override
 *     public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
 *         return SpringActorBehavior.builder(Command.class, actorContext)
 *             .onMessage(DoWork.class, this::handleDoWork)
 *             .build();
 *     }
 * }
 * }
 * </pre>
 *
 * <p><b>Framework-provided commands include:</b>
 * <ul>
 *   <li>{@link FrameworkCommands.SpawnChild} - Spawn a child actor with Spring DI support</li>
 * </ul>
 *
 * @see SpringActorBehavior
 * @see FrameworkCommands
 */
public interface FrameworkCommand {}
