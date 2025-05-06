package org.github.seonwkim.core.shard;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ShardedActorRegistry {
    private final Map<EntityTypeKey<?>, ShardedActor<?>> registry = new HashMap<>();

    public static ShardedActorRegistry INSTANCE = new ShardedActorRegistry();

    public <T> void register(ShardedActor<T> actor) {
        registry.put(actor.typeKey(), actor);
    }

    @SuppressWarnings("unchecked")
    public <T> ShardedActor<T> get(EntityTypeKey<T> typeKey) {
        return (ShardedActor<T>) registry.get(typeKey);
    }

    public Collection<ShardedActor<?>> getAll() {
        return registry.values();
    }
}
