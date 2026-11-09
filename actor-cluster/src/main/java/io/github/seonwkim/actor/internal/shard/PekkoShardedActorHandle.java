package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.PekkoAskBuilder;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActorHandle;
import java.time.Duration;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

/** {@link SpringShardedActorHandle} on a Pekko {@link EntityRef}. */
public final class PekkoShardedActorHandle<T> implements SpringShardedActorHandle<T> {

    private final ShardedEntityType<T> entityType;
    private final EntityRef<T> entityRef;
    private final Scheduler scheduler;
    private final Duration defaultTimeout;

    public PekkoShardedActorHandle(
            ShardedEntityType<T> entityType, EntityRef<T> entityRef, Scheduler scheduler, Duration defaultTimeout) {
        this.entityType = entityType;
        this.entityRef = entityRef;
        this.scheduler = scheduler;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public void tell(T message) {
        entityRef.tell(message);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <RES> SpringActorHandle.AskBuilder<RES> ask(AskCommand<RES> command) {
        return new PekkoAskBuilder<T, RES>(
                entityRef, scheduler, defaultTimeout, replyTo -> (T) command.withReplyTo(new PekkoActorRef<>(replyTo)));
    }

    @Override
    public String entityId() {
        return entityRef.getEntityId();
    }

    @Override
    public ShardedEntityType<T> entityType() {
        return entityType;
    }

    @Override
    public <X> X unwrap(Class<X> type) {
        if (type.isInstance(entityRef)) {
            return type.cast(entityRef);
        }
        throw new IllegalArgumentException(
                "A sharded handle unwraps to " + EntityRef.class.getName() + ", not " + type.getName());
    }
}
