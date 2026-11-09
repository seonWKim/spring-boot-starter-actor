package io.github.seonwkim.example;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import org.springframework.stereotype.Component;

/**
 * Actor that handles hello messages in a cluster environment. Each entity is a separate instance
 * identified by an entity ID. The actor responds with information about the node it's running on.
 */
@Component
public class HelloActor implements SpringShardedActor<HelloActor.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "HelloActor");

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command extends JsonSerializable {}

    /** Command to say hello and get a response. */
    public static class SayHello extends AskCommand<String> implements Command {
        public final String message;

        public SayHello(String message) {
            this.message = message;
        }
    }

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param ctx The sharded actor context containing information about this entity
     * @return The behavior for the actor
     */
    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
                .onMessage(SayHello.class, (state, msg) -> state.onSayHello(msg))
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
        private SpringActorBehavior<Command> onSayHello(SayHello msg) {
            // Get information about the current node and entity
            final String nodeAddress = ctx.nodeAddress();

            // Create a response message with node and entity information
            final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

            // Send the response back to the caller
            msg.reply(message);

            // Log the message for debugging
            ctx.getLog().info(message);

            return SpringActorBehavior.same();
        }
    }
}
