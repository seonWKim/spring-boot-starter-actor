package io.github.seonwkim.actor;

/** Sent to an actor just before its supervisor restarts it, so it can release resources. */
public enum PreRestart implements ActorSignal {
    INSTANCE
}
