package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.internal.MessageTracingSetup;
import io.github.seonwkim.actor.internal.PekkoBehaviors;
import io.github.seonwkim.actor.shard.ShardEnvelope;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.ShardingStrategy;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/** Maps the starter's sharding types to Pekko Cluster Sharding. */
public final class PekkoSharding {

    private PekkoSharding() {}

    public static <T> EntityTypeKey<T> typeKey(ShardedEntityType<T> type) {
        return EntityTypeKey.create(type.messageType(), type.name());
    }

    public static <T> ShardingMessageExtractor<ShardEnvelope<T>, T> extractor(ShardingStrategy strategy) {
        if (strategy instanceof ShardingStrategy.Hashed hashed) {
            return new DefaultShardingMessageExtractor<>(hashed.numberOfShards());
        }
        throw new IllegalArgumentException("Unknown sharding strategy: " + strategy);
    }

    /** Registers the actor's entity type; on nodes without its role this starts a proxy only. */
    public static <T> void init(ActorSystem<?> system, ClusterSharding sharding, SpringShardedActor<T> actor) {
        ShardedEntityType<T> type = actor.entityType();
        Entity<T, ShardEnvelope<T>> entity = Entity.of(
                        typeKey(type),
                        entityCtx -> MessageTracingSetup.receiving(
                                system,
                                PekkoBehaviors.toPekko(
                                        actor.create(new SpringShardedActorContext<>(type, entityCtx.getEntityId())))))
                .withMessageExtractor(extractor(actor.sharding()));
        if (actor.role().isPresent()) {
            entity = entity.withRole(actor.role().get());
        }
        sharding.init(entity);
    }
}
