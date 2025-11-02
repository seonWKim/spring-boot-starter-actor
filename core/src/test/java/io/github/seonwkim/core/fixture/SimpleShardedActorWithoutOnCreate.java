package io.github.seonwkim.core.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;
import io.github.seonwkim.core.shard.ShardedActorBehavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Test sharded actor that demonstrates building a sharded actor WITHOUT using onCreate().
 * This uses the default state type (ActorContext) and handles messages directly with closures.
 */
public class SimpleShardedActorWithoutOnCreate implements ShardedActor<SimpleShardedActorWithoutOnCreate.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "SimpleShardedActorWithoutOnCreate");

    public interface Command extends JsonSerializable {}

    public static class Echo implements Command {
        public final String message;
        public final ActorRef<Object> replyTo;

        @JsonCreator
        public Echo(@JsonProperty("message") String message, @JsonProperty("replyTo") ActorRef<Object> replyTo) {
            this.message = message;
            this.replyTo = replyTo;
        }
    }

    public static class GetEntityId implements Command {
        private final ActorRef<Object> replyTo;

        @JsonCreator
        public GetEntityId(@JsonProperty("replyTo") ActorRef<Object> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public ShardedActorBehavior<Command> create(EntityContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return ShardedActorBehavior.builder(Command.class, ctx)
                // No onCreate() - message handlers work directly with ActorContext
                .onMessage(Echo.class, (actorCtx, msg) -> {
                    actorCtx.getLog().info("Entity {} received echo: {}", entityId, msg.message);
                    msg.replyTo.tell("Echo from entity [" + entityId + "]: " + msg.message);
                    return Behaviors.same();
                })
                .onMessage(GetEntityId.class, (actorCtx, msg) -> {
                    msg.replyTo.tell(entityId);
                    return Behaviors.same();
                })
                .build();
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(5);
    }
}
