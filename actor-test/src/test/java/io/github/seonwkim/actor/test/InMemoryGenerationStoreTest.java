package io.github.seonwkim.actor.test;

import io.github.seonwkim.actor.spi.GenerationStore;

/** The in-memory fake passes the same contract as the real stores. */
class InMemoryGenerationStoreTest extends GenerationStoreContract {

    private final InMemoryGenerationStore store = new InMemoryGenerationStore();

    @Override
    protected GenerationStore store() {
        return store;
    }
}
