package io.github.seonwkim.example;

import io.github.seonwkim.actor.*;
import io.github.seonwkim.actor.PostStop;
import io.github.seonwkim.actor.PreRestart;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.time.Duration;
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
                .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .build();
    }

    private static class HelloActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringActorContext actorContext;

        HelloActorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        private SpringActorBehavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorContext.actorId());
            msg.reply("Hello from actor " + actorContext.actorId());
            return SpringActorBehavior.same();
        }

        private SpringActorBehavior<Command> onTriggerFailure(TriggerFailure msg) {
            ctx.getLog().warn("Triggering failure for actor {}", actorContext.actorId());
            msg.reply("Triggering failure - actor will restart");
            throw new RuntimeException("Intentional failure to demonstrate PreRestart signal");
        }

        private SpringActorBehavior<Command> onPreRestart(PreRestart signal) {
            ctx.getLog().warn("Actor {} is being restarted due to failure", actorContext.actorId());
            return SpringActorBehavior.same();
        }

        private SpringActorBehavior<Command> onPostStop(PostStop signal) {
            ctx.getLog().info("Actor {} is stopping. Performing cleanup...", actorContext.actorId());
            return SpringActorBehavior.same();
        }
    }
}
