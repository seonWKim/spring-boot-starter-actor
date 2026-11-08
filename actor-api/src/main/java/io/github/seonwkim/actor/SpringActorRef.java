package io.github.seonwkim.actor;

/**
 * A reference to an actor: send it messages, read its path. Command fields that carry a reply
 * address use this type, e.g. {@code SpringActorRef<Result> replyTo}; it can be sent to other
 * nodes.
 *
 * <p>Two references are equal when they point to the same actor.
 *
 * @param <T> the message type the actor accepts
 */
public interface SpringActorRef<T> {

    /** Sends a message without waiting for a reply. */
    void tell(T message);

    /** The actor's path, e.g. {@code pekko://system/user/order-1}. */
    String path();

    /**
     * Escape hatch: the underlying engine's reference, e.g. Pekko's
     * {@code org.apache.pekko.actor.typed.ActorRef}. Code that uses it depends on the engine and
     * gets no compatibility promise from the starter.
     *
     * @throws IllegalArgumentException when the reference is not of that type
     */
    <X> X unwrap(Class<X> type);
}
