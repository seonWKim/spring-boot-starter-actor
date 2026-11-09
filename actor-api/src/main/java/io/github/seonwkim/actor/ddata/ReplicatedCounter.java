package io.github.seonwkim.actor.ddata;

import java.util.concurrent.CompletionStage;

/**
 * A counter replicated to every node of the cluster. Increments and decrements made on different nodes
 * at the same time all count: none of them is lost.
 *
 * <p>Methods without a {@link Consistency} use {@link Consistency#local()}.
 */
public interface ReplicatedCounter {

    /** The name the counter is replicated under. */
    String name();

    /** The current value, zero if nothing was written yet. */
    default CompletionStage<Long> value() {
        return value(Consistency.local());
    }

    /** The current value, read with {@code read} consistency. */
    CompletionStage<Long> value(Consistency read);

    /** Adds {@code delta}. */
    default CompletionStage<Void> increment(long delta) {
        return increment(delta, Consistency.local());
    }

    /**
     * Adds {@code delta}, waiting for {@code write} consistency. Completes with {@link
     * ReplicationTimeoutException} if the nodes don't confirm in time.
     */
    CompletionStage<Void> increment(long delta, Consistency write);

    /** Subtracts {@code delta}. */
    default CompletionStage<Void> decrement(long delta) {
        return decrement(delta, Consistency.local());
    }

    /** Subtracts {@code delta}, waiting for {@code write} consistency. */
    CompletionStage<Void> decrement(long delta, Consistency write);
}
