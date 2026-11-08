package io.github.seonwkim.actor.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.ShardingStrategy;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import java.util.concurrent.atomic.AtomicInteger;

public class TestShardedActor implements SpringShardedActor<TestShardedActor.Command> {
    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "TestShardedActor");

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
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(context -> new ActorState(context, ctx.getEntityId()))
                .onMessage(Ping.class, (state, msg) -> {
                    state.counter.incrementAndGet();
                    state.context.getLog().info("entityId: {} received message: {}", state.entityId, msg.message);
                    return SpringActorBehavior.same();
                })
                .onMessage(GetState.class, (state, cmd) -> {
                    cmd.reply(new State(state.counter.get()));
                    return SpringActorBehavior.same();
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
    public ShardingStrategy sharding() {
        return ShardingStrategy.hashed(5);
    }
}
