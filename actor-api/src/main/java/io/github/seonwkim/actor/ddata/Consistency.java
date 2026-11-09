package io.github.seonwkim.actor.ddata;

import java.time.Duration;
import java.util.Objects;

/**
 * How many nodes a read or write of replicated data waits for.
 *
 * <ul>
 *   <li>{@link #local()}: this node only. Fast, and the change spreads to the other nodes in the
 *       background (by default within about two seconds).
 *   <li>{@link #majority(Duration)}: more than half of the nodes. A majority write followed by a
 *       majority read sees the write, on any node.
 *   <li>{@link #all(Duration)}: every node.
 * </ul>
 *
 * @param level how many nodes
 * @param timeout how long to wait for them; zero for {@link Level#LOCAL}
 */
public record Consistency(Level level, Duration timeout) {

    /** How many nodes to wait for. */
    public enum Level {
        LOCAL,
        MAJORITY,
        ALL
    }

    private static final Consistency LOCAL = new Consistency(Level.LOCAL, Duration.ZERO);

    public Consistency {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(timeout, "timeout");
        if (level != Level.LOCAL && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException(level + " consistency needs a positive timeout, got " + timeout);
        }
    }

    /** This node only. The default. */
    public static Consistency local() {
        return LOCAL;
    }

    /** More than half of the nodes, waiting at most {@code timeout}. */
    public static Consistency majority(Duration timeout) {
        return new Consistency(Level.MAJORITY, timeout);
    }

    /** Every node, waiting at most {@code timeout}. */
    public static Consistency all(Duration timeout) {
        return new Consistency(Level.ALL, timeout);
    }
}
