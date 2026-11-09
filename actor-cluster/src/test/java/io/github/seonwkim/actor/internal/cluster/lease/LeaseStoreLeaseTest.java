package io.github.seonwkim.actor.internal.cluster.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.typesafe.config.ConfigFactory;
import io.github.seonwkim.actor.internal.SpringBeansSetup;
import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.test.InMemoryLeaseStore;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.BootstrapSetup;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.coordination.lease.javadsl.Lease;
import org.apache.pekko.coordination.lease.javadsl.LeaseProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** The Pekko lease on a LeaseStore: renewal, and when it counts the lease as lost. */
class LeaseStoreLeaseTest {

    private static final Duration TTL = Duration.ofSeconds(3);
    private static final Duration MARGIN = Duration.ofSeconds(1);

    private ActorSystem system;

    @AfterEach
    void stop() {
        if (system != null) {
            system.terminate();
        }
    }

    private Lease lease(LeaseStore store) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("leaseStore", store);
        system = ActorSystem.create(
                "lease-test",
                ActorSystemSetup.create(BootstrapSetup.create(ConfigFactory.parseString(
                                        """
                                        spring-actor.lease {
                                          heartbeat-timeout = 3s
                                          heartbeat-interval = 300ms
                                          expiry-margin = 1s
                                        }
                                        """)
                                .withFallback(ConfigFactory.load())))
                        .withSetup(new SpringBeansSetup(beans)));
        return LeaseProvider.get(system).getLease("orders-singleton", LeaseStoreLease.CONFIG_PATH, "node-1");
    }

    @Test
    void renewalsKeepTheLeaseBeyondItsTtl() throws Exception {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        Lease lease = lease(store);

        assertThat(lease.acquire().toCompletableFuture().get()).isTrue();

        await().during(TTL.multipliedBy(2))
                .atMost(TTL.multipliedBy(3))
                .until(() ->
                        lease.checkLease() && store.holder("orders-singleton").equals(Optional.of("node-1")));
    }

    @Test
    void theLeaseIsLostWhenAnotherOwnerHasTakenIt() throws Exception {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        Lease lease = lease(store);
        AtomicBoolean lost = new AtomicBoolean();
        lease.acquire(cause -> lost.set(true)).toCompletableFuture().get();

        store.release("orders-singleton", "node-1");
        store.tryAcquire("orders-singleton", "node-2", TTL);

        await().atMost(Duration.ofSeconds(2)).untilTrue(lost);
        assertThat(lease.checkLease()).isFalse();
    }

    @Test
    void theLeaseIsLostBeforeItsTtlWhenTheStoreStopsAnswering() throws Exception {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        AtomicBoolean unreachable = new AtomicBoolean();
        LeaseStore flaky = new LeaseStore() {
            @Override
            public boolean tryAcquire(String lease, String owner, Duration ttl) {
                return store.tryAcquire(lease, owner, ttl);
            }

            @Override
            public boolean renew(String lease, String owner, Duration ttl) {
                if (unreachable.get()) {
                    throw new IllegalStateException("store unreachable");
                }
                return store.renew(lease, owner, ttl);
            }

            @Override
            public void release(String lease, String owner) {
                store.release(lease, owner);
            }

            @Override
            public Optional<String> holder(String lease) {
                return store.holder(lease);
            }
        };
        Lease lease = lease(flaky);
        AtomicLong lostAt = new AtomicLong();
        lease.acquire(cause -> lostAt.set(System.nanoTime()))
                .toCompletableFuture()
                .get();
        long lastRenewal = System.nanoTime();

        unreachable.set(true);

        await().atMost(TTL).until(() -> lostAt.get() != 0);
        // Counted lost by last renewal + ttl - margin, so before the store would hand it on.
        assertThat(Duration.ofNanos(lostAt.get() - lastRenewal))
                .isLessThan(TTL.minus(MARGIN).plusMillis(300));
        assertThat(lease.checkLease()).isFalse();
    }
}
