package io.github.seonwkim.actor.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The behavior every {@link PeerRegistry} must have. A store's test extends this class and returns
 * its registry; each test uses its own service name, so the store may be shared between tests.
 */
public abstract class PeerRegistryContract {

    private static final Duration LONG_TTL = Duration.ofMinutes(5);

    /** The registry under test. Called once per test. */
    protected abstract PeerRegistry registry();

    private final String service = "svc-" + UUID.randomUUID();

    private Peer peer(String id, int port) {
        return new Peer(service, id, "10.0.0." + port % 250, port);
    }

    @Test
    void aRegisteredPeerIsListed() {
        PeerRegistry registry = registry();
        Peer a = peer("a", 7626);
        Peer b = peer("b", 7627);

        registry.register(a);
        registry.register(b);

        assertThat(registry.peers(service, LONG_TTL)).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void peersOfAnotherServiceAreNotListed() {
        PeerRegistry registry = registry();
        Peer mine = peer("a", 7626);
        // A service whose name starts with ours and a separator must not leak into our listing either.
        Peer other = new Peer(service + ":other", "a", "10.0.0.9", 7626);

        registry.register(mine);
        registry.register(other);

        assertThat(registry.peers(service, LONG_TTL)).containsExactly(mine);
    }

    @Test
    void registeringTheSameIdAgainReplacesTheEntry() {
        PeerRegistry registry = registry();
        registry.register(peer("a", 7626));
        Peer moved = new Peer(service, "a", "10.0.0.99", 8558);

        registry.register(moved);

        assertThat(registry.peers(service, LONG_TTL)).containsExactly(moved);
    }

    @Test
    void aDeregisteredPeerIsNotListed() {
        PeerRegistry registry = registry();
        Peer leaving = peer("a", 7626);
        Peer staying = peer("b", 7627);
        registry.register(leaving);
        registry.register(staying);

        registry.deregister(leaving);
        registry.deregister(peer("never-registered", 7628));

        assertThat(registry.peers(service, LONG_TTL)).containsExactly(staying);
    }

    @Test
    void aPeerNotSeenWithinTheTtlIsNotListed() {
        PeerRegistry registry = registry();
        Peer silent = peer("a", 7626);
        registry.register(silent);

        // Stores may keep time in whole seconds, so wait for a ttl of one second to pass.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> registry.peers(service, Duration.ofSeconds(1)).isEmpty());
        assertThat(registry.peers(service, LONG_TTL)).containsExactly(silent);
    }

    @Test
    void heartbeatsKeepAPeerListed() {
        PeerRegistry registry = registry();
        Peer alive = peer("a", 7626);
        registry.register(alive);

        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(6)).until(() -> {
            registry.heartbeat(alive);
            return registry.peers(service, Duration.ofSeconds(2)).contains(alive);
        });
    }

    @Test
    void aHeartbeatAddsAPeerWhoseEntryIsGone() {
        PeerRegistry registry = registry();
        Peer peer = peer("a", 7626);
        registry.register(peer);
        registry.deregister(peer);

        registry.heartbeat(peer);

        assertThat(registry.peers(service, LONG_TTL)).containsExactly(peer);
    }
}
