package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Worker actor that can process tasks and fail on command.
 * Demonstrates supervision strategies (restart, stop, resume).
 */
@Component
public class WorkerActor
        implements SpringActorWithContext<WorkerActor, WorkerActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public WorkerActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    public interface Command {}

    public static class ProcessWork implements Command {
        public final String taskName;
        public final ActorRef<WorkResult> replyTo;

        public ProcessWork(String taskName, ActorRef<WorkResult> replyTo) {
            this.taskName = taskName;
            this.replyTo = replyTo;
        }
    }

    public static class TriggerFailure implements Command {
        public final ActorRef<String> replyTo;

        public TriggerFailure(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class GetStatus implements Command {
        public final ActorRef<WorkerStatus> replyTo;

        public GetStatus(ActorRef<WorkerStatus> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class WorkResult {
        public final String workerId;
        public final String taskName;
        public final int tasksCompleted;

        public WorkResult(String workerId, String taskName, int tasksCompleted) {
            this.workerId = workerId;
            this.taskName = taskName;
            this.tasksCompleted = tasksCompleted;
        }
    }

    public static class WorkerStatus {
        public final String workerId;
        public final int tasksCompleted;
        public final String state;

        public WorkerStatus(String workerId, int tasksCompleted, String state) {
            this.workerId = workerId;
            this.tasksCompleted = tasksCompleted;
            this.state = state;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new WorkerBehavior(ctx, actorContext, logPublisher).create());
    }

    private static class WorkerBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final LogPublisher logPublisher;
        private int tasksCompleted = 0;

        WorkerBehavior(
                ActorContext<Command> ctx, SpringActorContext actorContext, LogPublisher logPublisher) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.logPublisher = logPublisher;
        }

        public Behavior<Command> create() {
            String workerId = actorContext.actorId();
            ctx.getLog().info("Worker {} started", workerId);
            logPublisher.publish(
                    String.format("[%s] Worker started (path: %s)", workerId, ctx.getSelf().path()));

            return Behaviors.receive(Command.class)
                    .onMessage(ProcessWork.class, this::onProcessWork)
                    .onMessage(TriggerFailure.class, this::onTriggerFailure)
                    .onMessage(GetStatus.class, this::onGetStatus)
                    .onSignal(PreRestart.class, this::onPreRestart)
                    .onSignal(PostStop.class, this::onPostStop)
                    .build();
        }

        private Behavior<Command> onProcessWork(ProcessWork msg) {
            String workerId = actorContext.actorId();
            tasksCompleted++;

            ctx.getLog().info("Worker {} processing task: {} (total: {})", workerId, msg.taskName, tasksCompleted);
            logPublisher.publish(
                    String.format(
                            "[%s] Processing task '%s' (total completed: %d)",
                            workerId, msg.taskName, tasksCompleted));

            msg.replyTo.tell(new WorkResult(workerId, msg.taskName, tasksCompleted));
            return Behaviors.same();
        }

        private Behavior<Command> onTriggerFailure(TriggerFailure msg) {
            String workerId = actorContext.actorId();
            ctx.getLog().warn("Worker {} failing intentionally", workerId);
            logPublisher.publish(String.format("[%s] ⚠️ Failing intentionally!", workerId));

            msg.replyTo.tell("Failing now");
            throw new RuntimeException("Intentional failure for supervision demonstration");
        }

        private Behavior<Command> onGetStatus(GetStatus msg) {
            String workerId = actorContext.actorId();
            msg.replyTo.tell(new WorkerStatus(workerId, tasksCompleted, "active"));
            return Behaviors.same();
        }

        private Behavior<Command> onPreRestart(PreRestart signal) {
            String workerId = actorContext.actorId();
            ctx.getLog()
                    .warn(
                            "Worker {} restarting (tasks completed: {})",
                            workerId,
                            tasksCompleted);
            logPublisher.publish(
                    String.format(
                            "[%s] 🔄 Restarting (state lost: %d tasks completed)",
                            workerId, tasksCompleted));
            return Behaviors.same();
        }

        private Behavior<Command> onPostStop(PostStop signal) {
            String workerId = actorContext.actorId();
            ctx.getLog().info("Worker {} stopped (tasks completed: {})", workerId, tasksCompleted);
            logPublisher.publish(
                    String.format("[%s] 🛑 Stopped (final count: %d tasks)", workerId, tasksCompleted));
            return Behaviors.same();
        }
    }
}
