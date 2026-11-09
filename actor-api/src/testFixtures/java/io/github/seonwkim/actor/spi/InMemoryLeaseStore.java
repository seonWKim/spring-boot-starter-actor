package io.github.seonwkim.actor.spi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link LeaseStore} in this JVM's memory, for tests: nodes in one JVM can share it. */
public final class InMemoryLeaseStore implements LeaseStore {

    private record Held(String owner, Instant expires) {}

    private final Map<String, Held> leases = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLeaseStore() {
        this(Clock.systemUTC());
    }

    public InMemoryLeaseStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String lease, String owner, Duration ttl) {
        Instant now = clock.instant();
        Held after = leases.compute(
                lease,
                (name, held) -> held == null
                                || held.owner().equals(owner)
                                || held.expires().isBefore(now)
                        ? new Held(owner, now.plus(ttl))
                        : held);
        return after.owner().equals(owner);
    }

    @Override
    public boolean renew(String lease, String owner, Duration ttl) {
        Instant now = clock.instant();
        Held after = leases.computeIfPresent(
                lease,
                (name, held) -> held.owner().equals(owner) && !held.expires().isBefore(now)
                        ? new Held(owner, now.plus(ttl))
                        : held);
        return after != null && after.owner().equals(owner) && after.expires().isAfter(now);
    }

    @Override
    public void release(String lease, String owner) {
        leases.computeIfPresent(lease, (name, held) -> held.owner().equals(owner) ? null : held);
    }

    @Override
    public Optional<String> holder(String lease) {
        Held held = leases.get(lease);
        return held == null || held.expires().isBefore(clock.instant()) ? Optional.empty() : Optional.of(held.owner());
    }

    /** Every lease held now, by name: tests read which singleton or shard leases were taken. */
    public Map<String, String> held() {
        Instant now = clock.instant();
        Map<String, String> held = new java.util.TreeMap<>();
        leases.forEach((name, lease) -> {
            if (!lease.expires().isBefore(now)) {
                held.put(name, lease.owner());
            }
        });
        return held;
    }
}
