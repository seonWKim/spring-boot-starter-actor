package io.github.seonwkim.actor.test;

import io.github.seonwkim.actor.spi.LeaseStore;

/** The in-memory fake passes the same contract as the real stores. */
class InMemoryLeaseStoreTest extends LeaseStoreContract {

    private final InMemoryLeaseStore store = new InMemoryLeaseStore();

    @Override
    protected LeaseStore store() {
        return store;
    }
}
