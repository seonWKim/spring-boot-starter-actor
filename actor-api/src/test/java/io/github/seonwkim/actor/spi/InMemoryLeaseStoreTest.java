package io.github.seonwkim.actor.spi;

/** The in-memory fake passes the same contract as the real stores. */
class InMemoryLeaseStoreTest extends LeaseStoreContract {

    private final InMemoryLeaseStore store = new InMemoryLeaseStore();

    @Override
    protected LeaseStore store() {
        return store;
    }
}
