package io.github.seonwkim.actor;

/**
 * A lifecycle event an actor can react to with {@code SpringActorBehavior.Builder.onSignal}.
 *
 * @see PreRestart
 * @see PostStop
 */
public sealed interface ActorSignal permits PreRestart, PostStop {}
