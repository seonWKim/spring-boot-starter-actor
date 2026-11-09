package io.github.seonwkim.actor.metrics.cluster;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cluster membership meters, as this node sees the cluster.
 *
 * <ul>
 *   <li>{@code actor.cluster.members}: members this node sees
 *   <li>{@code actor.cluster.unreachable}: of them, how many are unreachable
 *   <li>{@code actor.cluster.leader}: 1 while this node is the leader, else 0
 *   <li>{@code actor.cluster.events} tagged {@code event}: cluster events this node received, one series
 *       per {@link ClusterEvent} type from the start, so dashboards see zeros instead of gaps
 * </ul>
 */
public final class ClusterMeters implements MeterBinder {

    private final IntSupplier members;
    private final IntSupplier unreachable;
    private final BooleanSupplier leader;
    // Counted here, not in a registry counter: the first events (this node's MemberUp) arrive before
    // Spring Boot binds the meters.
    private final Map<Class<?>, LongAdder> events = Stream.of(ClusterEvent.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(type -> type, type -> new LongAdder()));

    public ClusterMeters(IntSupplier members, IntSupplier unreachable, BooleanSupplier leader) {
        this.members = members;
        this.unreachable = unreachable;
        this.leader = leader;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("actor.cluster.members", members, IntSupplier::getAsInt)
                .description("Cluster members this node sees")
                .register(registry);
        Gauge.builder("actor.cluster.unreachable", unreachable, IntSupplier::getAsInt)
                .description("Cluster members this node sees as unreachable")
                .register(registry);
        Gauge.builder("actor.cluster.leader", leader, l -> l.getAsBoolean() ? 1 : 0)
                .description("1 while this node is the cluster leader")
                .register(registry);
        events.forEach((type, count) -> FunctionCounter.builder("actor.cluster.events", count, LongAdder::sum)
                .description("Cluster events this node received")
                .tag("event", type.getSimpleName())
                .register(registry));
    }

    /** Counts {@code event}, also before {@link #bindTo}. */
    public void record(ClusterEvent event) {
        LongAdder count = events.get(event.getClass());
        if (count != null) { // every ClusterEvent type has one; null only for a type added without a rebuild
            count.increment();
        }
    }
}
