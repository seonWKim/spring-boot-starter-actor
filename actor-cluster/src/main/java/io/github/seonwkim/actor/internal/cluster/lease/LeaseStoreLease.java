package io.github.seonwkim.actor.internal.cluster.lease;

import io.github.seonwkim.actor.internal.SpringBeansSetup;
import io.github.seonwkim.actor.spi.LeaseStore;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.coordination.lease.LeaseSettings;
import org.apache.pekko.coordination.lease.javadsl.Lease;
import org.apache.pekko.dispatch.Dispatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContextExecutor;

/**
 * A Pekko lease on the application's {@link LeaseStore} bean, used by the split brain resolver
 * ({@code lease-majority}), cluster singletons and shards. Pekko creates one per lease name and
 * owner, by reflection, from the config path {@value #CONFIG_PATH}.
 *
 * <p>Once acquired, the lease is renewed every {@code heartbeat-interval} for
 * {@code heartbeat-timeout} (the ttl). It counts as lost at the start of the last successful renewal
 * plus the ttl minus {@code expiry-margin}: before the store can give it to another owner, even if
 * the store is unreachable and renewals fail. Then the lost callback runs and
 * {@link #checkLease()} returns false.
 */
public final class LeaseStoreLease extends Lease {

    /** The lease config path that pekko.cluster.*.use-lease and lease-majority point to. */
    public static final String CONFIG_PATH = "spring-actor.lease";

    private static final Logger log = LoggerFactory.getLogger(LeaseStoreLease.class);

    /** The lease while held: when it counts as lost, the renewal timer and the lost callback. */
    private record Held(long lostAtNanos, Cancellable renewal, Consumer<Optional<Throwable>> onLost) {}

    private final LeaseSettings settings;
    private final LeaseStore store;
    private final ActorSystem system;
    private final ExecutionContextExecutor blocking;
    private final Duration ttl;
    private final Duration renewInterval;
    private final Duration margin;
    private final Duration operationTimeout;
    private final AtomicReference<Held> held = new AtomicReference<>();

    public LeaseStoreLease(LeaseSettings settings, ExtendedActorSystem system) {
        this.settings = settings;
        this.system = system;
        this.store = system.settings()
                .setup()
                .get(SpringBeansSetup.class)
                .map(beans ->
                        beans.beanFactory().getBeanProvider(LeaseStore.class).getIfUnique())
                .orElseThrow(() -> new IllegalStateException(
                        "Leases need one LeaseStore bean: set spring.actor.cluster.lease.store to jdbc (with actor-jdbc)"
                                + " or redis (with actor-redis), or define your own LeaseStore bean"));
        this.ttl = settings.timeoutSettings().getHeartbeatTimeout();
        this.renewInterval = settings.timeoutSettings().getHeartbeatInterval();
        this.operationTimeout = settings.timeoutSettings().getOperationTimeout();
        this.margin = settings.leaseConfig().getDuration("expiry-margin");
        if (margin.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("spring.actor.cluster.lease.expiry-margin (" + margin
                    + ") must be less than the ttl (" + ttl + ")");
        }
        this.blocking = system.dispatchers().lookup(Dispatchers.DefaultBlockingDispatcherId());
    }

    @Override
    public LeaseSettings getSettings() {
        return settings;
    }

    @Override
    public CompletionStage<Boolean> acquire() {
        return acquire(lost -> {});
    }

    @Override
    public CompletionStage<Boolean> acquire(Consumer<Optional<Throwable>> leaseLostCallback) {
        return call(() -> {
            long started = System.nanoTime();
            if (!store.tryAcquire(settings.leaseName(), settings.ownerName(), ttl)) {
                return false;
            }
            Cancellable renewal =
                    system.scheduler().scheduleWithFixedDelay(renewInterval, renewInterval, this::renew, blocking);
            Held previous = held.getAndSet(new Held(lostAt(started), renewal, leaseLostCallback));
            if (previous != null) {
                previous.renewal().cancel();
            }
            checkAtDeadline(started);
            return true;
        });
    }

    private long lostAt(long renewalStartedNanos) {
        return renewalStartedNanos + ttl.minus(margin).toNanos();
    }

    /**
     * Checks the lease when it would count as lost, so the lost callback runs on time even when
     * renewals hang. A later renewal moves the deadline, and this check then finds the lease held.
     */
    private void checkAtDeadline(long renewalStartedNanos) {
        Duration untilLost = Duration.ofNanos(lostAt(renewalStartedNanos) - System.nanoTime());
        system.scheduler().scheduleOnce(untilLost.isNegative() ? Duration.ZERO : untilLost, this::checkLease, blocking);
    }

    private void renew() {
        Held current = held.get();
        if (current == null) {
            return;
        }
        long started = System.nanoTime();
        boolean renewed;
        Throwable failure = null;
        try {
            renewed = store.renew(settings.leaseName(), settings.ownerName(), ttl);
        } catch (RuntimeException e) {
            renewed = false;
            failure = e;
            log.warn("Renewing lease {} for {} failed", settings.leaseName(), settings.ownerName(), e);
        }
        if (renewed) {
            if (held.compareAndSet(current, new Held(lostAt(started), current.renewal(), current.onLost()))) {
                checkAtDeadline(started);
            }
        } else if (failure == null || System.nanoTime() - current.lostAtNanos() >= 0) {
            // Another owner has it, or the store stayed unreachable until the lease may have expired.
            lose(current, Optional.ofNullable(failure));
        }
    }

    private void lose(Held current, Optional<Throwable> cause) {
        if (held.compareAndSet(current, null)) {
            current.renewal().cancel();
            log.warn("Lost lease {} for {}", settings.leaseName(), settings.ownerName());
            current.onLost().accept(cause);
        }
    }

    @Override
    public CompletionStage<Boolean> release() {
        Held current = held.getAndSet(null);
        if (current != null) {
            current.renewal().cancel();
        }
        return call(() -> {
            store.release(settings.leaseName(), settings.ownerName());
            return true;
        });
    }

    @Override
    public boolean checkLease() {
        Held current = held.get();
        if (current == null) {
            return false;
        }
        if (System.nanoTime() - current.lostAtNanos() >= 0) {
            lose(current, Optional.empty());
            return false;
        }
        return true;
    }

    private CompletionStage<Boolean> call(java.util.function.Supplier<Boolean> operation) {
        return CompletableFuture.supplyAsync(operation, blocking)
                .orTimeout(operationTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
