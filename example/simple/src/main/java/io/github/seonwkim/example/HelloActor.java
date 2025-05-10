package io.github.seonwkim.example;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import io.github.seonwkim.core.SpringActor;
import org.springframework.stereotype.Component;

/**
 * Actor that handles hello messages in a simple (non-clustered) environment.
 * It responds with a greeting message that includes its actor ID.
 */
@Component
public class HelloActor implements SpringActor {

    /**
     * Base interface for all commands that can be sent to the hello actor.
     */
    public interface Command {}

    /**
     * Command to say hello and get a response.
     */
    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * Returns the class of commands this actor can handle.
     *
     * @return The command class
     */
    @Override
    public Class<?> commandClass() {
        return Command.class;
    }

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param id The ID of the actor
     * @return The behavior for the actor
     */
    @Override
    public Behavior<Command> create(String id) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, id).create());
    }

    /**
     * Inner class to isolate stateful behavior logic.
     * This separates the actor's state and behavior from its interface.
     */
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final String actorId;

        /**
         * Creates a new behavior with the given context and actor ID.
         *
         * @param ctx The actor context
         * @param actorId The ID of the actor
         */
        HelloActorBehavior(ActorContext<Command> ctx, String actorId) {
            this.ctx = ctx;
            this.actorId = actorId;
        }

        /**
         * Creates the initial behavior for the actor.
         *
         * @return The behavior for handling messages
         */
        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                            .onMessage(SayHello.class, this::onSayHello)
                            .build();
        }

        /**
         * Handles SayHello commands by responding with a greeting.
         *
         * @param msg The SayHello message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onSayHello(SayHello msg) {
            // Log the received message
            ctx.getLog().info("Received SayHello for id={}", actorId);

            // Send a response back to the caller
            msg.replyTo.tell("Hello from actor " + actorId);

            return Behaviors.same();
        }
    }
}
