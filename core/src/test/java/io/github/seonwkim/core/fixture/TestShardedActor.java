package io.github.seonwkim.core.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class TestShardedActor implements SpringShardedActor<TestShardedActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "TestShardedActor");

    public interface Command extends JsonSerializable {}

    public static class Ping implements Command {
        public final String message;

        @JsonCreator
        public Ping(String message) {
            this.message = message;
        }
    }

    public static class GetState extends AskCommand<State> implements Command {
        public GetState() {}
    }

    public static class State implements JsonSerializable {
        private final int messageCount;

        @JsonCreator
        public State(int messageCount) {
            this.messageCount = messageCount;
        }

        public int getMessageCount() {
            return messageCount;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(context -> new ActorState(context, ctx.getEntityId()))
                .onMessage(Ping.class, (state, msg) -> {
                    state.counter.incrementAndGet();
                    state.context.getLog().info("entityId: {} received message: {}", state.entityId, msg.message);
                    return Behaviors.same();
                })
                .onMessage(GetState.class, (state, cmd) -> {
                    cmd.reply(new State(state.counter.get()));
                    return Behaviors.same();
                })
                .build();
    }

    private static class ActorState {
        private final SpringBehaviorContext<Command> context;
        private final String entityId;
        private final AtomicInteger counter;

        ActorState(SpringBehaviorContext<Command> context, String entityId) {
            this.context = context;
            this.entityId = entityId;
            this.counter = new AtomicInteger();
        }
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(5);
    }
}
