package io.github.seonwkim.actor.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.spi.LeaseStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The behavior every {@link LeaseStore} must have. A store's test extends this class and returns its
 * store; each test uses its own lease name, so the store may be shared between tests.
 */
public abstract class LeaseStoreContract {

    private static final Duration TTL = Duration.ofMinutes(1);

    /** The store under test. Called once per test. */
    protected abstract LeaseStore store();

    private final String lease = "lease-" + UUID.randomUUID();

    @Test
    void ofTwentyConcurrentAcquirersExactlyOneWins() throws Exception {
        LeaseStore store = store();
        int acquirers = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(acquirers);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < acquirers; i++) {
                String owner = "owner-" + i;
                Callable<Boolean> acquire = () -> {
                    start.await();
                    return store.tryAcquire(lease, owner, TTL);
                };
                results.add(pool.submit(acquire));
            }
            start.countDown();
            List<String> winners = new ArrayList<>();
            for (int i = 0; i < acquirers; i++) {
                if (results.get(i).get()) {
                    winners.add("owner-" + i);
                }
            }

            assertThat(winners).hasSize(1);
            assertThat(store.holder(lease)).contains(winners.get(0));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aHeldLeaseIsNotGivenToAnotherOwner() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", TTL);

        assertThat(store.tryAcquire(lease, "b", TTL)).isFalse();
        assertThat(store.holder(lease)).contains("a");
    }

    @Test
    void theHolderMayAcquireAgain() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", TTL);

        assertThat(store.tryAcquire(lease, "a", TTL)).isTrue();
    }

    @Test
    void onlyTheHolderRenews() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", TTL);

        assertThat(store.renew(lease, "a", TTL)).isTrue();
        assertThat(store.renew(lease, "b", TTL)).isFalse();
        assertThat(store.renew("never-acquired-" + lease, "a", TTL)).isFalse();
    }

    @Test
    void aReleasedLeaseIsFreeForAnotherOwner() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", TTL);

        store.release(lease, "a");

        assertThat(store.holder(lease)).isEmpty();
        assertThat(store.tryAcquire(lease, "b", TTL)).isTrue();
    }

    @Test
    void onlyTheHolderReleases() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", TTL);

        store.release(lease, "b");

        assertThat(store.holder(lease)).contains("a");
    }

    @Test
    void anExpiredLeaseCanBeTakenAndCannotBeRenewed() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", Duration.ofSeconds(1));

        // Stores may keep time in whole seconds: wait until the lease is free.
        await().atMost(Duration.ofSeconds(10)).until(() -> store.holder(lease).isEmpty());
        assertThat(store.renew(lease, "a", TTL)).isFalse();
        assertThat(store.tryAcquire(lease, "b", TTL)).isTrue();
        assertThat(store.holder(lease)).contains("b");
    }

    @Test
    void renewingKeepsTheLeaseBeyondItsFirstTtl() {
        LeaseStore store = store();
        store.tryAcquire(lease, "a", Duration.ofSeconds(2));

        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(6)).until(() -> {
            store.renew(lease, "a", Duration.ofSeconds(2));
            return store.holder(lease).filter("a"::equals).isPresent();
        });
    }
}
