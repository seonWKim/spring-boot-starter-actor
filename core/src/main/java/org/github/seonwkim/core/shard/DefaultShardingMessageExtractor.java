package org.github.seonwkim.core.shard;

import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;

public class DefaultShardingMessageExtractor<T> extends ShardingMessageExtractor<ShardEnvelope<T>, T> {

    private final int numberOfShards;

    public DefaultShardingMessageExtractor(int numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    @Override
    public String entityId(ShardEnvelope<T> envelope) {
        return envelope.getEntityId();
    }

    @Override
    public String shardId(String entityId) {
        return String.valueOf(Math.abs(entityId.hashCode()) % numberOfShards);
    }

    @Override
    public T unwrapMessage(ShardEnvelope<T> envelope) {
        return envelope.getPayload();
    }
}
