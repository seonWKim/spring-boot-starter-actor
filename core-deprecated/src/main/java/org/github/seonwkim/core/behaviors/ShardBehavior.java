package org.github.seonwkim.core.behaviors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public interface ShardBehavior<T> {
    EntityTypeKey<T> getEntityTypeKey();

    Behavior<T> create(EntityContext<T> entityContext);

    ShardingMessageExtractor<T, T> extractor();
}
