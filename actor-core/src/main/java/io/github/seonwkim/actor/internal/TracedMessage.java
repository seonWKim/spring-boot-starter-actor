package io.github.seonwkim.actor.internal;

import java.util.Map;

/**
 * A message sent with the sender's trace context: W3C {@code traceparent} and related fields, as
 * written by the tracer. Exists only while tracing is on; the receiving actor gets {@link #message()}.
 */
public final class TracedMessage {

    private final Object message;
    private final Map<String, String> headers;

    public TracedMessage(Object message, Map<String, String> headers) {
        this.message = message;
        this.headers = Map.copyOf(headers);
    }

    public Object message() {
        return message;
    }

    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public String toString() {
        return "TracedMessage(" + message + ")";
    }
}
