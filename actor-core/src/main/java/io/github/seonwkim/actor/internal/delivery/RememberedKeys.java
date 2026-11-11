package io.github.seonwkim.actor.internal.delivery;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

/**
 * The idempotency keys a consumer handled, each with its payload's fingerprint, for {@code ttl}, at
 * most {@code maxKeys} of them. Keys are kept in the order they were handled, which is also their
 * expiry order, so expired keys and, past the limit, the oldest keys are dropped from the front. Only
 * the actor touches it.
 */
final class RememberedKeys {

    /** What a key and fingerprint are to this consumer. */
    enum Seen {
        /** The key is not remembered. */
        NEW,
        /** The key is remembered with the same fingerprint, or one of the two has none. */
        DUPLICATE,
        /** The key is remembered with a different fingerprint. */
        CONFLICT
    }

    private record Entry(long expiry, @Nullable Integer fingerprint) {}

    private final long ttlNanos;
    private final int maxKeys;
    private final LongSupplier nanoClock;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    RememberedKeys(Duration ttl, int maxKeys, LongSupplier nanoClock) {
        if (maxKeys < 1) {
            throw new IllegalArgumentException("spring.actor.delivery.max-remembered-keys must be at least 1");
        }
        this.ttlNanos = ttl.toNanos();
        this.maxKeys = maxKeys;
        this.nanoClock = nanoClock;
    }

    Seen check(String key, @Nullable Integer fingerprint) {
        dropExpired();
        Entry entry = entries.get(key);
        if (entry == null) {
            return Seen.NEW;
        }
        return entry.fingerprint() == null || fingerprint == null || Objects.equals(entry.fingerprint(), fingerprint)
                ? Seen.DUPLICATE
                : Seen.CONFLICT;
    }

    void remember(String key, @Nullable Integer fingerprint) {
        entries.remove(key);
        entries.put(key, new Entry(nanoClock.getAsLong() + ttlNanos, fingerprint));
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() > maxKeys) {
            oldest.next();
            oldest.remove();
        }
    }

    private void dropExpired() {
        long now = nanoClock.getAsLong();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext() && it.next().getValue().expiry() - now <= 0) {
            it.remove();
        }
    }
}
