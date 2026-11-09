package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * The sharded actor beans of one Spring context, by entity type and by class. Each context has its
 * own (a bean filled by {@code ClusterConfiguration}'s post-processor), so two contexts in one JVM
 * never see each other's sharded actors.
 */
public final class ShardedActorRegistry {

    private final ConcurrentMap<ShardedEntityType<?>, SpringShardedActor<?>> registry = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, SpringShardedActor<?>> classIndex = new ConcurrentHashMap<>();

    public <T> void register(SpringShardedActor<T> actor) {
        registry.put(actor.entityType(), actor);
        classIndex.put(actor.getClass(), actor);
    }

    @Nullable @SuppressWarnings("unchecked")
    public <T> SpringShardedActor<T> get(ShardedEntityType<T> typeKey) {
        // Safe cast: registry maintains T type consistency between key and value
        return (SpringShardedActor<T>) registry.get(typeKey);
    }

    @Nullable @SuppressWarnings("unchecked")
    public <T> SpringShardedActor<T> getByClass(Class<? extends SpringShardedActor<T>> actorClass) {
        return (SpringShardedActor<T>) classIndex.get(actorClass);
    }

    public Collection<SpringShardedActor<?>> getAll() {
        return registry.values();
    }
}
