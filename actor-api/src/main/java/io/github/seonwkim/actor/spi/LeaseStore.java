package io.github.seonwkim.actor.spi;

import java.time.Duration;
import java.util.Optional;

/**
 * Named leases with one holder at a time, for the split brain resolver's {@code lease-majority}
 * strategy and for cluster singletons and shards ({@code spring.actor.cluster.singleton.use-lease},
 * {@code spring.actor.cluster.sharding.use-lease}).
 *
 * <p>A lease is held by one owner until it releases it or until its ttl passes without a renewal.
 * Each method is one atomic step in the store, so of many owners that try to acquire a free lease at
 * once exactly one wins. Expiry uses the store's clock where it has one (the database's, Redis's).
 *
 * <p>An implementation only stores and checks leases: the starter renews them on a timer and counts
 * a lease as lost when it could not renew in time. Built-in implementations are
 * {@code JdbcLeaseStore} ({@code actor-jdbc}) and {@code RedisLeaseStore} ({@code actor-redis}); an
 * application bean of this type replaces them. Every implementation must pass the
 * {@code LeaseStoreContract} test suite. Methods may block and are called from a blocking dispatcher.
 */
public interface LeaseStore {

    /**
     * Takes the lease for {@code owner} for {@code ttl}, if it is free, expired, or already held by
     * {@code owner} (then its ttl starts again).
     *
     * @return whether {@code owner} holds the lease now
     */
    boolean tryAcquire(String lease, String owner, Duration ttl);

    /**
     * Extends the lease by {@code ttl} from now, only if {@code owner} still holds it.
     *
     * @return whether {@code owner} still holds the lease; false once it expired or another owner
     *     took it
     */
    boolean renew(String lease, String owner, Duration ttl);

    /** Frees the lease if {@code owner} holds it. Does nothing otherwise. */
    void release(String lease, String owner);

    /** The owner that holds the lease now, or empty if it is free or expired. */
    Optional<String> holder(String lease);
}
