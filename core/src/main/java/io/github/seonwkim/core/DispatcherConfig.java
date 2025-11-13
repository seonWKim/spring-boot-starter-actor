package io.github.seonwkim.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.pekko.actor.typed.Props;

/**
 * Configuration for actor dispatchers.
 *
 * <p>Use the factory methods to create dispatcher configurations:
 * <ul>
 *   <li>{@link #defaultDispatcher()} - Use the default Pekko dispatcher
 *   <li>{@link #blocking()} - Use Pekko's blocking I/O dispatcher
 *   <li>{@link #virtualThreads()} - Use virtual threads dispatcher (Java 21+)
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
     * Use a dispatcher backed by Java 21+ virtual threads. This dispatcher is ideal for
     * actors that perform blocking I/O operations, as virtual threads are lightweight
     * and can handle many concurrent operations efficiently.
     *
     * <p>This method checks if virtual threads are available at runtime. If you're running
     * on Java 21 or later, it will configure a virtual thread dispatcher. If virtual threads
     * are not available (Java 11-17), this will throw an {@link UnsupportedOperationException}
     * at configuration time.
     *
     * <p>Example usage:
     * <pre>{@code
     * actorSystem.actor(MyBlockingActor.class)
     *     .withId("blocking-actor")
     *     .withVirtualThreadDispatcher()
     *     .spawn();
     * }</pre>
     *
     * @return A dispatcher configuration for virtual threads
     * @throws UnsupportedOperationException if virtual threads are not available (Java version < 21)
     */
    public static DispatcherConfig virtualThreads() {
        if (!VirtualThreadDispatcher.isVirtualThreadsAvailable()) {
            throw new UnsupportedOperationException(
                    "Virtual threads are not available. "
                            + "Virtual threads require Java 21 or later. "
                            + "Current Java version: "
                            + System.getProperty("java.version"));
        }
        return VirtualThreadDispatcher.INSTANCE;
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
     * Returns the virtual thread dispatcher configuration to be merged into the actor system config.
     * This should be called during actor system initialization to ensure the virtual thread dispatcher
     * is available when actors try to use it.
     *
     * @return A map containing the virtual thread dispatcher configuration, or empty map if not supported
     */
    public static Map<String, Object> getVirtualThreadDispatcherConfig() {
        if (!VirtualThreadDispatcher.isVirtualThreadsAvailable()) {
            return Collections.emptyMap();
        }

        Map<String, Object> dispatcherConfig = new HashMap<>();
        dispatcherConfig.put("type", "Dispatcher");
        // Use our custom executor service configurator for virtual threads
        dispatcherConfig.put(
                "executor",
                "io.github.seonwkim.core.VirtualThreadExecutorServiceConfigurator");
        dispatcherConfig.put("shutdown-timeout", "1s");
        // Virtual threads handle blocking very well, so throughput can be higher
        dispatcherConfig.put("throughput", 100);

        Map<String, Object> dispatchers = new HashMap<>();
        dispatchers.put(VirtualThreadDispatcher.DISPATCHER_NAME, dispatcherConfig);

        Map<String, Object> actor = new HashMap<>();
        actor.put("dispatchers", dispatchers);

        Map<String, Object> pekko = new HashMap<>();
        pekko.put("actor", actor);

        Map<String, Object> result = new HashMap<>();
        result.put("pekko", pekko);

        return result;
    }

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

    /**
     * Virtual thread dispatcher configuration - uses Java 21+ virtual threads for actor execution.
     * This dispatcher is automatically configured when requested and checks for virtual thread
     * availability at construction time.
     */
    private static final class VirtualThreadDispatcher extends DispatcherConfig {
        static final VirtualThreadDispatcher INSTANCE = new VirtualThreadDispatcher();
        static final String DISPATCHER_NAME = "virtual-thread-dispatcher";

        private VirtualThreadDispatcher() {
            // Constructor doesn't check availability - the factory method virtualThreads() does that
        }

        @Override
        public boolean shouldUseProps() {
            return true;
        }

        @Override
        public Props toProps() {
            return Props.empty().withDispatcherFromConfig(DISPATCHER_NAME);
        }

        @Override
        public String toString() {
            return "DispatcherConfig.virtualThreads()";
        }

        /**
         * Checks if virtual threads are available by attempting to access the Thread.ofVirtual() method
         * which was introduced in Java 21.
         *
         * @return true if virtual threads are available, false otherwise
         */
        static boolean isVirtualThreadsAvailable() {
            try {
                // Check if Thread.ofVirtual() method exists (Java 21+)
                Thread.class.getMethod("ofVirtual");
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }
}
