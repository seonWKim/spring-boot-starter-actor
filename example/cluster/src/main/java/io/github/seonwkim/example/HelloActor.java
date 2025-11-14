package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

/**
 * Sharded actor that demonstrates cluster distribution and location transparency.
 *
 * <p>This actor shows how sharding works in a cluster:
 * <ul>
 *   <li>Each entity ID maps to a specific actor instance
 *   <li>Entities are automatically distributed across cluster nodes
 *   <li>The cluster sharding system routes messages to the correct node
 *   <li>Entities can be rebalanced when nodes join or leave
 *   <li>Actors are created on-demand and passivated when idle
 * </ul>
 *
 * <p>The response includes node information to demonstrate that entities
 * can run on different nodes in the cluster.
 */
@Component
public class HelloActor implements SpringShardedActor<HelloActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "HelloActor");

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command extends JsonSerializable {}

    /** Command to say hello and get a response. */
    public static class SayHello extends AskCommand<String> implements Command {
        public final String message;

        @JsonCreator
        public SayHello(@JsonProperty("message") String message) {
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
     * @param ctx The sharded actor context containing information about this entity
     * @return The behavior for the actor
     */
    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .build();
    }

    /**
     * Behavior handler for hello actor. Holds the entity ID and handles messages.
     */
    private static class HelloActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String entityId;

        HelloActorBehavior(SpringBehaviorContext<Command> ctx, String entityId) {
            this.ctx = ctx;
            this.entityId = entityId;
        }

        /**
         * Handles SayHello commands by responding with node and entity information.
         *
         * @param msg The SayHello message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onSayHello(SayHello msg) {
            // Get information about the current node and entity
            final String nodeAddress = ctx.getUnderlying().getSystem().address().toString();

            // Create a response message with node and entity information
            final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

            // Send the response back to the caller
            msg.reply(message);

            // Log the message for debugging
            ctx.getLog().info(message);

            return Behaviors.same();
        }
    }
}
