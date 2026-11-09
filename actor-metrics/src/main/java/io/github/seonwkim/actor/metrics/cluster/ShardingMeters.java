package io.github.seonwkim.actor.metrics.cluster;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Cluster Sharding meters, one series per sharded entity type, tagged {@code type}.
 *
 * <ul>
 *   <li>{@code actor.sharding.regions}: shard regions that host the type in the cluster
 *   <li>{@code actor.sharding.shards}: the type's shards on this node
 *   <li>{@code actor.sharding.entities}: the type's running entities on this node
 * </ul>
 *
 * <p>The entity types are read when the meters are bound, after every sharded actor bean exists.
 */
public final class ShardingMeters implements MeterBinder {

    private final Supplier<List<String>> types;
    private final ToIntFunction<String> regions;
    private final ToIntFunction<String> shards;
    private final ToIntFunction<String> entities;

    public ShardingMeters(
            Supplier<List<String>> types,
            ToIntFunction<String> regions,
            ToIntFunction<String> shards,
            ToIntFunction<String> entities) {
        this.types = types;
        this.regions = regions;
        this.shards = shards;
        this.entities = entities;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String type : types.get()) {
            gauge(registry, "actor.sharding.regions", "Shard regions that host the entity type", type, regions);
            gauge(registry, "actor.sharding.shards", "Shards of the entity type on this node", type, shards);
            gauge(registry, "actor.sharding.entities", "Running entities of the type on this node", type, entities);
        }
    }

    private static void gauge(
            MeterRegistry registry, String name, String description, String type, ToIntFunction<String> value) {
        Gauge.builder(name, type, value::applyAsInt)
                .description(description)
                .tag("type", type)
                .register(registry);
    }
}
