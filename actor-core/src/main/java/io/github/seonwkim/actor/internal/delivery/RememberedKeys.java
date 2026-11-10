package io.github.seonwkim.actor.internal.delivery;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The idempotency keys a consumer handled, each for {@code ttl}, at most {@code maxKeys} of them.
 * Keys are kept in the order they were handled, which is also their expiry order, so expired keys
 * and, past the limit, the oldest keys are dropped from the front. Only the actor touches it.
 */
final class RememberedKeys {

    private final long ttlNanos;
    private final int maxKeys;
    private final LongSupplier nanoClock;
    private final LinkedHashMap<String, Long> expiries = new LinkedHashMap<>();

    RememberedKeys(Duration ttl, int maxKeys, LongSupplier nanoClock) {
        if (maxKeys < 1) {
            throw new IllegalArgumentException("spring.actor.delivery.max-remembered-keys must be at least 1");
        }
        this.ttlNanos = ttl.toNanos();
        this.maxKeys = maxKeys;
        this.nanoClock = nanoClock;
    }

    boolean contains(String key) {
        dropExpired();
        return expiries.containsKey(key);
    }

    void remember(String key) {
        expiries.remove(key);
        expiries.put(key, nanoClock.getAsLong() + ttlNanos);
        Iterator<String> oldest = expiries.keySet().iterator();
        while (expiries.size() > maxKeys) {
            oldest.next();
            oldest.remove();
        }
    }

    private void dropExpired() {
        long now = nanoClock.getAsLong();
        Iterator<Map.Entry<String, Long>> it = expiries.entrySet().iterator();
        while (it.hasNext() && it.next().getValue() - now <= 0) {
            it.remove();
        }
    }
}
