package io.github.seonwkim.core;

import java.util.Objects;
import org.apache.pekko.actor.typed.Props;

/**
 * Configuration for actor dispatchers.
 *
 * <p>Use the factory methods to create dispatcher configurations:
 * <ul>
 *   <li>{@link #defaultDispatcher()} - Use the default Pekko dispatcher
 *   <li>{@link #blocking()} - Use Pekko's blocking I/O dispatcher
 *   <li>{@link #fromConfig(String)} - Use a custom dispatcher from configuration
 *   <li>{@link #sameAsParent()} - Use the same dispatcher as the parent actor
 * </ul>
 */
public abstract class DispatcherConfig {

    /**
     * Use the default Pekko dispatcher. This is the default behavior when no dispatcher
     * is explicitly configured.
     *
     * @return A dispatcher configuration for the default dispatcher
     */
    public static DispatcherConfig defaultDispatcher() {
        return DefaultDispatcher.INSTANCE;
    }

    /**
     * Use Pekko's default blocking I/O dispatcher. Use this for actors that perform
     * blocking operations like database calls or file I/O.
     *
     * @return A dispatcher configuration for blocking operations
     */
    public static DispatcherConfig blocking() {
        return BlockingDispatcher.INSTANCE;
    }

    /**
     * Use a custom dispatcher defined in the application configuration.
     * The dispatcher must be configured under the "spring.actor" prefix in your
     * application.yml or application.properties.
     *
     * @param path The dispatcher name/path (e.g., "my-custom-dispatcher")
     * @return A dispatcher configuration for the custom dispatcher
     */
    public static DispatcherConfig fromConfig(String path) {
        return new FromConfigDispatcher(path);
    }

    /**
     * Use the same dispatcher as the parent actor. This is useful for child actors
     * that should share the same thread pool as their parent.
     *
     * @return A dispatcher configuration that inherits from the parent
     */
    public static DispatcherConfig sameAsParent() {
        return SameAsParentDispatcher.INSTANCE;
    }

    // Package-private constructor to prevent external subclassing
    DispatcherConfig() {}

    /**
     * Returns whether this dispatcher configuration requires using Props.
     * The default dispatcher uses the mailbox selector instead of Props.
     *
     * @return true if Props should be used, false if mailbox selector should be used
     */
    public abstract boolean shouldUseProps();

    /**
     * Converts this dispatcher configuration to Pekko Props.
     * Only called when {@link #shouldUseProps()} returns true.
     *
     * @return Props configured with the appropriate dispatcher
     */
    public abstract Props toProps();

    /**
     * Default dispatcher configuration - uses Pekko's default dispatcher.
     */
    private static final class DefaultDispatcher extends DispatcherConfig {
        static final DefaultDispatcher INSTANCE = new DefaultDispatcher();

        private DefaultDispatcher() {}

        @Override
        public boolean shouldUseProps() {
            return false; // Use mailboxSelector instead
        }

        @Override
        public Props toProps() {
            throw new UnsupportedOperationException("Default dispatcher doesn't use Props");
        }

        @Override
        public String toString() {
            return "DispatcherConfig.default()";
        }
    }

    /**
     * Blocking dispatcher configuration - uses Pekko's default blocking I/O dispatcher.
     */
    private static final class BlockingDispatcher extends DispatcherConfig {
        static final BlockingDispatcher INSTANCE = new BlockingDispatcher();

        private BlockingDispatcher() {}

        @Override
        public boolean shouldUseProps() {
            return true;
        }

        @Override
        public Props toProps() {
            return Props.empty().withDispatcherFromConfig("pekko.actor.default-blocking-io-dispatcher");
        }

        @Override
        public String toString() {
            return "DispatcherConfig.blocking()";
        }
    }

    /**
     * Custom dispatcher configuration - uses a dispatcher defined in application configuration.
     */
    private static final class FromConfigDispatcher extends DispatcherConfig {
        private final String path;

        FromConfigDispatcher(String path) {
            this.path = Objects.requireNonNull(path, "Dispatcher path cannot be null");
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Dispatcher path cannot be empty");
            }
        }

        @Override
        public boolean shouldUseProps() {
            return true;
        }

        @Override
        public Props toProps() {
            return Props.empty().withDispatcherFromConfig(path);
        }

        @Override
        public String toString() {
            return "DispatcherConfig.fromConfig(\"" + path + "\")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FromConfigDispatcher that = (FromConfigDispatcher) o;
            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }

    /**
     * Same-as-parent dispatcher configuration - inherits the parent actor's dispatcher.
     */
    private static final class SameAsParentDispatcher extends DispatcherConfig {
        static final SameAsParentDispatcher INSTANCE = new SameAsParentDispatcher();

        private SameAsParentDispatcher() {}

        @Override
        public boolean shouldUseProps() {
            return true;
        }

        @Override
        public Props toProps() {
            return Props.empty().withDispatcherSameAsParent();
        }

        @Override
        public String toString() {
            return "DispatcherConfig.sameAsParent()";
        }
    }
}
