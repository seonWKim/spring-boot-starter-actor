package io.github.seonwkim.actor;

/** Sent to an actor after it has stopped, so it can release resources. */
public enum PostStop implements ActorSignal {
    INSTANCE
}
