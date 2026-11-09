package io.github.seonwkim.actor;

/**
 * Sent to an actor when an actor it watches ({@link SpringBehaviorContext#watch}) has stopped.
 *
 * <p>Handle it with {@code onSignal(ActorTerminated.class, ...)}. An actor that watches another and
 * does not handle this signal fails when the watched actor stops.
 *
 * @param ref the actor that stopped
 */
public record ActorTerminated(SpringActorRef<Void> ref) implements ActorSignal {}
