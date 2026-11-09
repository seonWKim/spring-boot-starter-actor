package io.github.seonwkim.actor.spi;

import java.time.Duration;
import java.util.List;

/**
 * Where the nodes of a cluster find each other when there are no fixed seed nodes
 * ({@code spring.actor.cluster.join=discovery}). Each node registers itself at startup, renews its
 * entry with {@link #heartbeat} while it runs, and removes it with {@link #deregister} when it
 * leaves gracefully. A node that crashes stops renewing, and {@link #peers} stops listing it once
 * the entry is older than the ttl.
 *
 * <p>An implementation only stores and reads entries: the starter owns the timers and the shutdown
 * hook. Built-in implementations are {@code JdbcPeerRegistry} ({@code actor-jdbc}) and
 * {@code RedisPeerRegistry} ({@code actor-redis}); an application bean of this type replaces them.
 * Every implementation must pass the {@code PeerRegistryContract} test suite. Methods may block and
 * are called from a blocking dispatcher, never from an actor.
 */
public interface PeerRegistry {

    /**
     * Adds the peer, or replaces the entry with the same service and id, and marks it as seen now.
     */
    void register(Peer peer);

    /**
     * Marks the peer as seen now, adding it again if its entry is gone. By default it registers the
     * peer again; a store with a cheaper renewal may override it.
     */
    default void heartbeat(Peer peer) {
        register(peer);
    }

    /** Removes the entry with the peer's service and id. Does nothing if there is none. */
    void deregister(Peer peer);

    /**
     * The peers of {@code service} seen within {@code ttl}, in no particular order. "Now" is the
     * store's clock where it has one (the database's or Redis's), so the nodes' clocks need not agree.
     */
    List<Peer> peers(String service, Duration ttl);
}
