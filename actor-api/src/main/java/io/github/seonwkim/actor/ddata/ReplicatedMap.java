package io.github.seonwkim.actor.ddata;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A map replicated to every node of the cluster. When two nodes write the same key at the same time,
 * the later write wins (by wall clock). Keys and values cross nodes, so they must be serializable:
 * {@code String}, {@code Integer}, {@code Long}, or a type that implements {@code JsonSerializable} or
 * {@code CborSerializable}.
 *
 * <p>Methods without a {@link Consistency} use {@link Consistency#local()}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface ReplicatedMap<K, V> {

    /** The name the map is replicated under. */
    String name();

    /** Every entry, or an empty map if nothing was written yet. */
    default CompletionStage<Map<K, V>> entries() {
        return entries(Consistency.local());
    }

    /** Every entry, read with {@code read} consistency. */
    CompletionStage<Map<K, V>> entries(Consistency read);

    /** The value for {@code key}. */
    default CompletionStage<Optional<V>> get(K key) {
        return get(key, Consistency.local());
    }

    /** The value for {@code key}, read with {@code read} consistency. */
    CompletionStage<Optional<V>> get(K key, Consistency read);

    /** Sets {@code key} to {@code value}. */
    default CompletionStage<Void> put(K key, V value) {
        return put(key, value, Consistency.local());
    }

    /**
     * Sets {@code key} to {@code value}, waiting for {@code write} consistency. Completes with {@link
     * ReplicationTimeoutException} if the nodes don't confirm in time.
     */
    CompletionStage<Void> put(K key, V value, Consistency write);

    /** Removes {@code key}. */
    default CompletionStage<Void> remove(K key) {
        return remove(key, Consistency.local());
    }

    /** Removes {@code key}, waiting for {@code write} consistency. */
    CompletionStage<Void> remove(K key, Consistency write);
}
