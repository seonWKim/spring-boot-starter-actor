package io.github.seonwkim.actor.spi;

/** The in-memory fake passes the same contract as the real stores. */
class InMemoryPeerRegistryTest extends PeerRegistryContract {

    private final InMemoryPeerRegistry registry = new InMemoryPeerRegistry();

    @Override
    protected PeerRegistry registry() {
        return registry;
    }
}
