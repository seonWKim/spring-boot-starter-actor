package io.github.seonwkim.example;

import java.io.Serializable;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;
import org.springframework.stereotype.Component;

@Component
public class HelloActor implements ShardedActor<HelloActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "HelloActor");

    public interface Command extends Serializable {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;
        public final String message;

        public SayHello(ActorRef<String> replyTo, String message) {
            this.replyTo = replyTo;
            this.message = message;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(
                context ->
                        Behaviors.receive(Command.class)
                                 .onMessage(SayHello.class, msg -> {
                                     final String nodeAddress = context.getSystem().address().toString();
                                     final String entityId = ctx.getEntityId();
                                     final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";
                                     msg.replyTo.tell(message);
                                     context.getLog().info(message);
                                     return Behaviors.same();
                                 })
                                 .build()
        );
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
