package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.internal.MessageTracingSetup;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import io.github.seonwkim.actor.shard.SpringShardedActorHandle;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;

/** {@link SpringShardedActorBuilder} on Pekko {@link ClusterSharding}. */
public final class PekkoShardedActorBuilder<T> implements SpringShardedActorBuilder<T> {

    private final ShardedEntityType<T> entityType;

    @Nullable private final ClusterSharding sharding;

    private final ActorSystem<?> system;

    @Nullable private String entityId;

    private Duration timeout = ActorConstants.DEFAULT_TIMEOUT;

    public PekkoShardedActorBuilder(
            ShardedEntityType<T> entityType, @Nullable ClusterSharding sharding, ActorSystem<?> system) {
        this.entityType = entityType;
        this.sharding = sharding;
        this.system = system;
    }

    @Override
    public SpringShardedActorBuilder<T> withId(String entityId) {
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        this.entityId = entityId;
        return this;
    }

    @Override
    public SpringShardedActorBuilder<T> withTimeout(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        return this;
    }

    @Override
    public SpringShardedActorHandle<T> get() {
        if (entityId == null) {
            throw new IllegalStateException("Entity ID must be set using withId() before calling get()");
        }
        if (sharding == null) {
            throw new IllegalStateException("Cluster sharding not configured");
        }
        return new PekkoShardedActorHandle<>(
                entityType,
                sharding.entityRefFor(PekkoSharding.typeKey(entityType), entityId),
                system.scheduler(),
                MessageTracingSetup.of(system),
                timeout);
    }
}
