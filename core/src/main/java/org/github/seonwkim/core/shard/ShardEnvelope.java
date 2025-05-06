package org.github.seonwkim.core.shard;

public class ShardEnvelope<T> {
    private final String entityId;
    private final T payload;

    public ShardEnvelope(String entityId, T payload) {
        this.entityId = entityId;
        this.payload = payload;
    }

    public String getEntityId() {
        return entityId;
    }

    public T getPayload() {
        return payload;
    }
}
