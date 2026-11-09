package io.github.seonwkim.actor.ddata;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * A set replicated to every node of the cluster. When one node adds an element while another removes
 * it, the add wins. Elements cross nodes, so they must be serializable: {@code String}, {@code
 * Integer}, {@code Long}, or a type that implements {@code JsonSerializable} or {@code
 * CborSerializable}.
 *
 * <p>Methods without a {@link Consistency} use {@link Consistency#local()}.
 *
 * @param <E> element type
 */
public interface ReplicatedSet<E> {

    /** The name the set is replicated under. */
    String name();

    /** Every element, or an empty set if nothing was written yet. */
    default CompletionStage<Set<E>> elements() {
        return elements(Consistency.local());
    }

    /** Every element, read with {@code read} consistency. */
    CompletionStage<Set<E>> elements(Consistency read);

    /** Adds {@code element}. */
    default CompletionStage<Void> add(E element) {
        return add(element, Consistency.local());
    }

    /**
     * Adds {@code element}, waiting for {@code write} consistency. Completes with {@link
     * ReplicationTimeoutException} if the nodes don't confirm in time.
     */
    CompletionStage<Void> add(E element, Consistency write);

    /** Removes {@code element}. */
    default CompletionStage<Void> remove(E element) {
        return remove(element, Consistency.local());
    }

    /** Removes {@code element}, waiting for {@code write} consistency. */
    CompletionStage<Void> remove(E element, Consistency write);
}
