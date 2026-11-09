package io.github.seonwkim.actor.spi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link PeerRegistry} in this JVM's memory, for tests: nodes in one JVM can share it. */
public final class InMemoryPeerRegistry implements PeerRegistry {

    private record Key(String service, String id) {}

    private record Entry(Peer peer, Instant seen) {}

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryPeerRegistry() {
        this(Clock.systemUTC());
    }

    public InMemoryPeerRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void register(Peer peer) {
        entries.put(new Key(peer.service(), peer.id()), new Entry(peer, clock.instant()));
    }

    @Override
    public void deregister(Peer peer) {
        entries.remove(new Key(peer.service(), peer.id()));
    }

    @Override
    public List<Peer> peers(String service, Duration ttl) {
        Instant oldest = clock.instant().minus(ttl);
        return entries.values().stream()
                .filter(entry -> entry.peer().service().equals(service))
                .filter(entry -> !entry.seen().isBefore(oldest))
                .map(Entry::peer)
                .toList();
    }
}
