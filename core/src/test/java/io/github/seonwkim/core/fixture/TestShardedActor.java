package io.github.seonwkim.core.fixture;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;

public class TestShardedActor implements ShardedActor<TestShardedActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class,
                                                                               "TestShardedActor");

    public interface Command extends Serializable {}

    public static class Ping implements Command {
        public final String message;

        public Ping(String message) {
            this.message = message;
        }
    }

    public static class GetState implements Command {
        private final ActorRef<State> replyTo;

        public GetState(ActorRef<State> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class State implements Serializable {
        private final int messageCount;

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
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(context -> {
            AtomicInteger counter = new AtomicInteger();
            return Behaviors.receive(Command.class)
                            .onMessage(Ping.class, msg -> {
                                counter.incrementAndGet();
                                String entityId = ctx.getEntityId();
                                context.getLog().info("entityId: " + entityId + " received message: " + msg.message);
                                return Behaviors.same();
                            })
                            .onMessage(GetState.class, cmd -> {
                                cmd.replyTo.tell(new State(counter.get()));
                                return Behaviors.same();
                            })
                            .build();
        });
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(5);
    }
}
