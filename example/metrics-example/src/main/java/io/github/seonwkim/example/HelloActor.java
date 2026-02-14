package io.github.seonwkim.example;

import io.github.seonwkim.core.*;
import java.time.Duration;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Simple actor demonstrating metrics collection.
 *
 * <p>Metrics are automatically collected when run with the metrics agent.
 * See /actuator/prometheus for actor.lifecycle.*, actor.message.*, actor.mailbox.* metrics.
 */
@Component
public class HelloActor implements SpringActor<HelloActor.Command> {

    public interface Command {}

    public static class SayHello extends AskCommand<String> implements Command {
        public SayHello() {}
    }

    public static class TriggerFailure extends AskCommand<String> implements Command {
        public TriggerFailure() {}
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    ctx.getLog().info("PreStart hook for id={}", actorContext.actorId());
                    return new HelloActorBehavior(ctx, actorContext);
                })
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .onMessage(TriggerFailure.class, HelloActorBehavior::onTriggerFailure)
                .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
                .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
                .withSupervisionStrategy(SupervisorStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .build();
    }

    private static class HelloActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringActorContext actorContext;

        HelloActorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorContext.actorId());
            msg.reply("Hello from actor " + actorContext.actorId());
            return Behaviors.same();
        }

        private Behavior<Command> onTriggerFailure(TriggerFailure msg) {
            ctx.getLog().warn("Triggering failure for actor {}", actorContext.actorId());
            msg.reply("Triggering failure - actor will restart");
            throw new RuntimeException("Intentional failure to demonstrate PreRestart signal");
        }

        private Behavior<Command> onPreRestart(PreRestart signal) {
            ctx.getLog().warn("Actor {} is being restarted due to failure", actorContext.actorId());
            return Behaviors.same();
        }

        private Behavior<Command> onPostStop(PostStop signal) {
            ctx.getLog().info("Actor {} is stopping. Performing cleanup...", actorContext.actorId());
            return Behaviors.same();
        }
    }
}
