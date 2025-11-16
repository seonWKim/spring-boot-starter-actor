package io.github.seonwkim.example.virtualthreads;

import io.github.seonwkim.core.FrameworkCommand;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import io.github.seonwkim.core.SpringBehaviorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

@Component
public class VirtualThreadTestActor
        implements SpringActorWithContext<VirtualThreadTestActor.Command, SpringActorContext> {

    // Commands
    public interface Command extends FrameworkCommand {
    }

    public static class CheckThread implements Command {
        public final String taskName;

        public CheckThread(String taskName) {
            this.taskName = taskName;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(CheckThread.class, this::onCheckThread)
                .build();
    }

    private Behavior<Command> onCheckThread(
            SpringBehaviorContext<Command> context, CheckThread command) {
        Thread currentThread = Thread.currentThread();
        boolean isVirtual = currentThread.isVirtual();

        context.getLog()
                .info(
                        "Task '{}' - Thread: {}, IsVirtual: {}, ThreadId: {}",
                        command.taskName,
                        currentThread.getName(),
                        isVirtual,
                        currentThread.threadId());

        return Behaviors.same();
    }
}
