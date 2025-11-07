package io.github.seonwkim.core;

import java.util.Objects;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.Props;

/**
 * Type-safe configuration for actor mailboxes.
 *
 * <p>Mailboxes can be configured in two ways:
 * <ul>
 *   <li><strong>Built-in bounded mailbox:</strong> Use {@link #bounded(int)} for simple capacity limits
 *   <li><strong>Custom mailbox from config:</strong> Use {@link #fromConfig(String)} to reference mailboxes
 *       defined in application.yml under spring.actor.*
 * </ul>
 *
 * <p>When you define a mailbox in application.yml, Pekko automatically registers it:
 * <pre>
 * spring.actor.my-priority-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedPriorityMailbox
 * spring.actor.my-bounded-mailbox.mailbox-type=org.apache.pekko.dispatch.BoundedMailbox
 * spring.actor.my-bounded-mailbox.mailbox-capacity=100
 * </pre>
 *
 * <p>Then reference it using:
 * <pre>{@code
 * // Bounded mailbox (built-in)
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withMailbox(MailboxConfig.bounded(100))
 *     .spawn();
 *
 * // Custom mailbox from configuration
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withMailbox(MailboxConfig.fromConfig("my-priority-mailbox"))
 *     .spawn();
 *
 * // Combined with dispatcher
 * actorSystem.spawn(MyActor.class)
 *     .withId("my-actor")
 *     .withMailbox(MailboxConfig.bounded(100))
 *     .withBlockingDispatcher()
 *     .spawn();
 * }</pre>
 *
 * @see DispatcherConfig for dispatcher configuration
 */
public abstract class MailboxConfig {

    /**
     * Use the default Pekko mailbox (SingleConsumerOnlyUnboundedMailbox).
     * This is the most efficient mailbox for the common case.
     *
     * @return A mailbox configuration for the default mailbox
     */
    public static MailboxConfig defaultMailbox() {
        return DefaultMailbox.INSTANCE;
    }

    /**
     * Use a bounded mailbox with the specified capacity.
     * When the mailbox is full, the sender will be blocked until space is available.
     *
     * <p><strong>Warning:</strong> Blocking behavior can impact sender performance and may cause deadlocks
     * if actors wait for responses. Consider the message flow carefully.
     *
     * @param capacity The maximum number of messages the mailbox can hold
     * @return A mailbox configuration for a bounded mailbox
     * @throws IllegalArgumentException if capacity is not positive
     */
    public static MailboxConfig bounded(int capacity) {
        return new BoundedMailbox(capacity);
    }

    /**
     * Use a custom mailbox defined in the application configuration.
     * The mailbox is automatically registered by Pekko when you define it under "spring.actor".
     *
     * <p>Example configuration:
     * <pre>
     * spring.actor.my-priority-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedPriorityMailbox
     * spring.actor.my-control-aware-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedControlAwareMailbox
     * spring.actor.my-custom-bounded.mailbox-type=org.apache.pekko.dispatch.BoundedMailbox
     * spring.actor.my-custom-bounded.mailbox-capacity=500
     * </pre>
     *
     * @param path The mailbox configuration path (e.g., "my-priority-mailbox")
     * @return A mailbox configuration for the custom mailbox
     * @throws IllegalArgumentException if path is null or empty
     */
    public static MailboxConfig fromConfig(String path) {
        return new FromConfigMailbox(path);
    }

    // Package-private constructor to prevent external subclassing
    MailboxConfig() {}

    /**
     * Converts this mailbox configuration to a Pekko MailboxSelector.
     * Used when dispatcher doesn't require Props.
     *
     * @return MailboxSelector for this configuration
     */
    public abstract MailboxSelector toMailboxSelector();

    /**
     * Applies this mailbox configuration to the given Props.
     * Used when combining mailbox with a dispatcher that uses Props.
     *
     * @param props The Props to apply mailbox configuration to
     * @return Props with mailbox configuration applied
     */
    public abstract Props applyToProps(Props props);

    /**
     * Returns a description of this mailbox configuration for debugging.
     *
     * @return A human-readable description
     */
    public abstract String describe();

    // ========== Inner Classes ==========

    /**
     * Default mailbox configuration - uses Pekko's default mailbox (SingleConsumerOnlyUnboundedMailbox).
     */
    private static final class DefaultMailbox extends MailboxConfig {
        static final DefaultMailbox INSTANCE = new DefaultMailbox();

        private DefaultMailbox() {}

        @Override
        public MailboxSelector toMailboxSelector() {
            return MailboxSelector.defaultMailbox();
        }

        @Override
        public Props applyToProps(Props props) {
            // Default mailbox doesn't need to be set on Props
            return props;
        }

        @Override
        public String describe() {
            return "Default mailbox (SingleConsumerOnlyUnboundedMailbox)";
        }

        @Override
        public String toString() {
            return "MailboxConfig.defaultMailbox()";
        }
    }

    /**
     * Bounded mailbox configuration - blocks sender when capacity is reached.
     * Uses Pekko's built-in BoundedMailbox.
     */
    private static final class BoundedMailbox extends MailboxConfig {
        private final int capacity;

        BoundedMailbox(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Mailbox capacity must be positive, got: " + capacity
                        + ". Use MailboxConfig.defaultMailbox() for unbounded capacity.");
            }
            this.capacity = capacity;
        }

        @Override
        public MailboxSelector toMailboxSelector() {
            return MailboxSelector.bounded(capacity);
        }

        @Override
        public Props applyToProps(Props props) {
            // Bounded mailbox is set via MailboxSelector, which is handled separately
            // when spawning with Props
            return props;
        }

        @Override
        public String describe() {
            return "Bounded mailbox (capacity: " + capacity + ", blocks on overflow)";
        }

        @Override
        public String toString() {
            return "MailboxConfig.bounded(" + capacity + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoundedMailbox that = (BoundedMailbox) o;
            return capacity == that.capacity;
        }

        @Override
        public int hashCode() {
            return Objects.hash(capacity);
        }
    }

    /**
     * Custom mailbox configuration - uses a mailbox defined in application configuration.
     * The mailbox is automatically registered by Pekko from spring.actor.* configuration.
     */
    private static final class FromConfigMailbox extends MailboxConfig {
        private final String path;

        FromConfigMailbox(String path) {
            this.path = Objects.requireNonNull(path, "Mailbox path cannot be null");
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Mailbox path cannot be empty");
            }
        }

        @Override
        public MailboxSelector toMailboxSelector() {
            return MailboxSelector.fromConfig(path);
        }

        @Override
        public Props applyToProps(Props props) {
            return props.withMailboxFromConfig(path);
        }

        @Override
        public String describe() {
            return "Custom mailbox from config: " + path;
        }

        @Override
        public String toString() {
            return "MailboxConfig.fromConfig(\"" + path + "\")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FromConfigMailbox that = (FromConfigMailbox) o;
            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }
}
