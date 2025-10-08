package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

/**
 * Actor that handles hello messages in a cluster environment. Each entity is a separate instance
 * identified by an entity ID. The actor responds with information about the node it's running on.
 */
@Component
public class HelloActor implements ShardedActor<HelloActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "HelloActor");

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command extends JsonSerializable {}

    /** Command to say hello and get a response. */
    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;
        public final String message;

        @JsonCreator
        public SayHello(@JsonProperty("replyTo") ActorRef<String> replyTo, @JsonProperty("message") String message) {
            this.replyTo = replyTo;
            this.message = message;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param ctx The entity context containing information about this entity
     * @return The behavior for the actor
     */
    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(context -> Behaviors.receive(Command.class)
                .onMessage(SayHello.class, msg -> {
                    // Get information about the current node and entity
                    final String nodeAddress = context.getSystem().address().toString();
                    final String entityId = ctx.getEntityId();

                    // Create a response message with node and entity information
                    final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

                    // Send the response back to the caller
                    msg.replyTo.tell(message);

                    // Log the message for debugging
                    context.getLog().info(message);

                    return Behaviors.same();
                })
                .build());
    }

    /**
     * Provides a message extractor for sharding. This determines how messages are routed to the
     * correct entity.
     *
     * @return The sharding message extractor
     */
    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
