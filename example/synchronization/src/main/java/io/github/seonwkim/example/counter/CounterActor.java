package io.github.seonwkim.example.counter;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sharded actor that handles counter operations.
 *
 * <p>Each counter is represented by a separate actor instance, identified by its counterId.
 * The actor model provides natural synchronization:
 * <ul>
 *   <li>Single-threaded message processing per actor instance
 *   <li>No race conditions - messages are processed sequentially
 *   <li>Automatic distribution across cluster nodes
 *   <li>On-demand entity creation and passivation
 * </ul>
 *
 * <p>This demonstrates a key advantage of the actor model: you get thread-safety
 * without explicit locks, mutexes, or synchronized blocks.
 */
@Component
public class CounterActor implements SpringShardedActor<CounterActor.Command> {

    private static final Logger logger = LoggerFactory.getLogger(CounterActor.class);

    // Define a type key for this actor type
    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "CounterActor");

    /** Base interface for all commands that can be sent to the counter actor. */
    public interface Command extends JsonSerializable {}

    /** Command to increment the counter and get the new value. */
    public static class Increment implements Command {
        public Increment() {}
    }

    /** Command to get the current value of the counter. */
    public static class GetValue extends AskCommand<Long> implements Command {
        public GetValue() {}
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(context -> new CounterActorBehavior(ctx.getEntityId()))
                .onMessage(Increment.class, CounterActorBehavior::onIncrement)
                .onMessage(GetValue.class, CounterActorBehavior::onGetValue)
                .build();
    }

    /**
     * Inner class to isolate stateful behavior logic. This separates the actor's state and behavior
     * from its interface.
     */
    private static class CounterActorBehavior {
        private final String counterId;
        private long value = 0;

        /**
         * Creates a new behavior with the given context and counter ID.
         */
        CounterActorBehavior(String counterId) {
            this.counterId = counterId;
            logger.debug("Created counter actor for ID: {}", counterId);
        }

        /**
         * Handles Increment commands by incrementing the counter and responding with the new value.
         *
         * @param msg The Increment message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onIncrement(Increment msg) {
            logger.debug("Incrementing counter with ID: {}", counterId);
            value++;
            logger.debug("Counter with ID: {} incremented to: {}", counterId, value);
            return Behaviors.same();
        }

        /**
         * Handles GetValue commands by responding with the current counter value.
         *
         * @param msg The GetValue message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onGetValue(GetValue msg) {
            logger.debug("Getting value for counter with ID: {}", counterId);
            msg.reply(value);
            return Behaviors.same();
        }
    }
}
