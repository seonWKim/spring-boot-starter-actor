package org.github.seonwkim.core.shard;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public interface ShardedActor<T> {
    // EntityTypeKey.create(actor.commandClass(), actor.commandClass().getSimpleName());
    EntityTypeKey<T> typeKey();

    Behavior<T> create(EntityContext<T> ctx);

    ShardingMessageExtractor<ShardEnvelope<T>, T> extractor();
}
