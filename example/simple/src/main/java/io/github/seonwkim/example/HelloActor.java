package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Actor that handles hello messages in a simple (non-clustered) environment. It responds with a
 * greeting message that includes its actor ID.
 */
@Component
public class HelloActor implements SpringActor<HelloActor, HelloActor.Command> {

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command {}

    /** Command to say hello and get a response. */
    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /** Command to trigger an exception and cause the actor to restart (tests PreRestart signal). */
    public static class TriggerFailure implements Command {
        public final ActorRef<String> replyTo;

        public TriggerFailure(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param actorContext The context of the actor
     * @return The behavior for the actor
     */
    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, actorContext).create());
    }

    /**
     * Inner class to isolate stateful behavior logic. This separates the actor's state and behavior
     * from its interface.
     */
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        /**
         * Creates a new behavior with the given context and actor ID.
         *
         * @param ctx The actor context
         * @param actorContext The context of the actor
         */
        HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        /**
         * Creates the initial behavior for the actor with lifecycle hooks.
         * Uses onSignal to handle PreRestart and PostStop signals.
         *
         * @return The behavior for handling messages and signals
         */
        public Behavior<Command> create() {
            onPrestart();
            return Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, this::onSayHello)
                    .onMessage(TriggerFailure.class, this::onTriggerFailure)
                    .onSignal(PreRestart.class, this::onPreRestart)
                    .onSignal(PostStop.class, this::onPostStop)
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
            ctx.getLog().info("Received SayHello for id={}", actorContext.actorId());

            // Send a response back to the caller
            msg.replyTo.tell("Hello from actor " + actorContext.actorId());

            return Behaviors.same();
        }

        /**
         * Handles TriggerFailure commands by throwing an exception.
         * This will trigger the PreRestart signal handler.
         *
         * @param msg The TriggerFailure message
         * @return Never returns (throws exception)
         */
        private Behavior<Command> onTriggerFailure(TriggerFailure msg) {
            ctx.getLog().warn("Triggering failure for actor {}", actorContext.actorId());
            // Reply before throwing to confirm the command was received
            msg.replyTo.tell("Triggering failure - actor will restart");
            // Throw exception to trigger restart
            throw new RuntimeException("Intentional failure to demonstrate PreRestart signal");
        }

        private void onPrestart() {
            ctx.getLog().info("PreStart hook for id={}", actorContext.actorId());
        }

        /**
         * Called before the actor is restarted due to a failure.
         * This is a good place to clean up resources or log state before restart.
         *
         * @param signal The PreRestart signal
         * @return The same behavior (actor will be restarted)
         */
        private Behavior<Command> onPreRestart(PreRestart signal) {
            ctx.getLog().warn(
                "Actor {} is being restarted due to failure",
                actorContext.actorId()
            );

            // You can perform cleanup here before the actor restarts
            // For example: closing connections, releasing resources, etc.
            // Note: State will be lost unless you implement state persistence

            return Behaviors.same();
        }

        /**
         * Called when the actor is stopped (either gracefully or due to failure).
         * This is a good place to release resources and perform final cleanup.
         *
         * @param signal The PostStop signal
         * @return The same behavior
         */
        private Behavior<Command> onPostStop(PostStop signal) {
            ctx.getLog().info(
                "Actor {} is stopping. Performing cleanup...",
                actorContext.actorId()
            );

            // Perform cleanup here
            // For example: close database connections, release file handles,
            // flush buffers, notify other systems, etc.

            return Behaviors.same();
        }
    }
}
