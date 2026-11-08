package io.github.seonwkim.actor;

import java.util.Objects;

/**
 * The thread pool an actor runs on.
 *
 * @see MailboxConfig
 */
public sealed interface DispatcherConfig
        permits DispatcherConfig.Default,
                DispatcherConfig.Blocking,
                DispatcherConfig.SameAsParent,
                DispatcherConfig.FromConfig {

    /** The default dispatcher, for non-blocking work. */
    static DispatcherConfig defaultDispatcher() {
        return Default.INSTANCE;
    }

    /** The dispatcher for blocking work (JDBC, file I/O, blocking HTTP clients). */
    static DispatcherConfig blocking() {
        return Blocking.INSTANCE;
    }

    /** A dispatcher defined in configuration at {@code path}. */
    static DispatcherConfig fromConfig(String path) {
        return new FromConfig(path);
    }

    /** The parent actor's dispatcher. */
    static DispatcherConfig sameAsParent() {
        return SameAsParent.INSTANCE;
    }

    /** See {@link #defaultDispatcher()}. */
    enum Default implements DispatcherConfig {
        INSTANCE
    }

    /** See {@link #blocking()}. */
    enum Blocking implements DispatcherConfig {
        INSTANCE
    }

    /** See {@link #sameAsParent()}. */
    enum SameAsParent implements DispatcherConfig {
        INSTANCE
    }

    /** See {@link #fromConfig(String)}. */
    record FromConfig(String path) implements DispatcherConfig {
        public FromConfig {
            Objects.requireNonNull(path, "Dispatcher path cannot be null");
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Dispatcher path cannot be empty");
            }
        }
    }
}
