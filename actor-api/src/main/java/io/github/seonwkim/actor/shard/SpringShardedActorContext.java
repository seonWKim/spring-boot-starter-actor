package io.github.seonwkim.actor.shard;

import java.util.Objects;

/**
 * What a sharded entity is created with: its entity id and type. It is a {@link
 * io.github.seonwkim.actor.SpringActorContext} whose actor id is the entity id, so the same behavior
 * builder works for local and sharded actors.
 *
 * @param <T> the entity's message type
 */
public final class SpringShardedActorContext<T> extends io.github.seonwkim.actor.SpringActorContext {

    private final ShardedEntityType<T> entityType;
    private final String entityId;

    public SpringShardedActorContext(ShardedEntityType<T> entityType, String entityId) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
    }

    public ShardedEntityType<T> entityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    @Override
    public String actorId() {
        return entityId;
    }
}
