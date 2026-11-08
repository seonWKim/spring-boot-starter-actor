package io.github.seonwkim.actor;

import java.util.Objects;

/**
 * The mailbox an actor's messages wait in.
 *
 * <pre>{@code
 * actorSystem.actor(MyActor.class).withId("a").withMailbox(MailboxConfig.bounded(100)).spawn();
 *
 * // A mailbox defined under spring.actor.<name>, e.g.
 * // spring.actor.my-priority-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedPriorityMailbox
 * actorSystem.actor(MyActor.class).withId("b").withMailbox(MailboxConfig.fromConfig("my-priority-mailbox")).spawn();
 * }</pre>
 *
 * @see DispatcherConfig
 */
public sealed interface MailboxConfig permits MailboxConfig.Default, MailboxConfig.Bounded, MailboxConfig.FromConfig {

    /** The default unbounded mailbox. */
    static MailboxConfig defaultMailbox() {
        return Default.INSTANCE;
    }

    /** A mailbox that holds at most {@code capacity} messages; further messages go to dead letters. */
    static MailboxConfig bounded(int capacity) {
        return new Bounded(capacity);
    }

    /** A mailbox defined in configuration under {@code spring.actor.<path>}. */
    static MailboxConfig fromConfig(String path) {
        return new FromConfig(path);
    }

    /** See {@link #defaultMailbox()}. */
    enum Default implements MailboxConfig {
        INSTANCE
    }

    /** See {@link #bounded(int)}. */
    record Bounded(int capacity) implements MailboxConfig {
        public Bounded {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Mailbox capacity must be positive, got: " + capacity
                        + ". Use MailboxConfig.defaultMailbox() for unbounded capacity.");
            }
        }
    }

    /** See {@link #fromConfig(String)}. */
    record FromConfig(String path) implements MailboxConfig {
        public FromConfig {
            Objects.requireNonNull(path, "Mailbox path cannot be null");
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Mailbox path cannot be empty");
            }
        }
    }
}
