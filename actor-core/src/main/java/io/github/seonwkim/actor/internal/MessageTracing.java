package io.github.seonwkim.actor.internal;

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Carries the current trace across {@code tell} and {@code ask}. The actor system has one only when
 * tracing is on ({@link MessageTracingSetup}); without it messages are sent as they are.
 */
public interface MessageTracing {

    /**
     * Starts sending {@code message} by {@code operation} ({@code tell} or {@code ask}), or returns
     * null when there is no trace to carry.
     */
    @Nullable Sent send(Object message, String operation);

    /** Runs {@code handler}, the receiving actor's handling of {@code message}, inside its trace. */
    <R> R receive(TracedMessage message, String actorPath, Supplier<R> handler);

    /** The message to tell: {@code message} itself, or a {@link TracedMessage} when there is a trace. */
    default Object tell(Object message) {
        Sent sent = send(message, "tell");
        if (sent == null) {
            return message;
        }
        sent.stop(null);
        return sent.message();
    }

    /** A message being sent with its trace. */
    interface Sent {
        TracedMessage message();

        /** Ends the send: right after a tell, when the reply or a failure arrives for an ask. */
        void stop(@Nullable Throwable error);
    }
}
