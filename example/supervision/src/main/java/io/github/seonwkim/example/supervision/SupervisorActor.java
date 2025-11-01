package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Supervisor actor that spawns and manages worker actors with different supervision strategies.
 * Demonstrates hierarchical supervision and various failure handling strategies.
 */
@Component
public class SupervisorActor
        implements SpringActorWithContext<SupervisorActor, SupervisorActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public SupervisorActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    public interface Command {}

    public static class SpawnWorker implements Command {
        public final String workerId;
        public final String strategy; // "restart", "stop", "resume", "restart-limited"
        public final ActorRef<SpawnResult> replyTo;

        public SpawnWorker(String workerId, String strategy, ActorRef<SpawnResult> replyTo) {
            this.workerId = workerId;
            this.strategy = strategy;
            this.replyTo = replyTo;
        }
    }

    public static class RouteWork implements Command {
        public final String workerId;
        public final String taskName;
        public final ActorRef<WorkerActor.WorkResult> replyTo;

        public RouteWork(String workerId, String taskName, ActorRef<WorkerActor.WorkResult> replyTo) {
            this.workerId = workerId;
            this.taskName = taskName;
            this.replyTo = replyTo;
        }
    }

    public static class TriggerWorkerFailure implements Command {
        public final String workerId;
        public final ActorRef<String> replyTo;

        public TriggerWorkerFailure(String workerId, ActorRef<String> replyTo) {
            this.workerId = workerId;
            this.replyTo = replyTo;
        }
    }

    public static class StopWorker implements Command {
        public final String workerId;
        public final ActorRef<String> replyTo;

        public StopWorker(String workerId, ActorRef<String> replyTo) {
            this.workerId = workerId;
            this.replyTo = replyTo;
        }
    }

    public static class GetHierarchy implements Command {
        public final ActorRef<HierarchyInfo> replyTo;

        public GetHierarchy(ActorRef<HierarchyInfo> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class SpawnResult {
        public final String workerId;
        public final boolean success;
        public final String message;

        public SpawnResult(String workerId, boolean success, String message) {
            this.workerId = workerId;
            this.success = success;
            this.message = message;
        }
    }

    public static class HierarchyInfo {
        public final String supervisorId;
        public final List<WorkerInfo> workers;

        public HierarchyInfo(String supervisorId, List<WorkerInfo> workers) {
            this.supervisorId = supervisorId;
            this.workers = workers;
        }
    }

    public static class WorkerInfo {
        public final String workerId;
        public final String strategy;
        public final String path;

        public WorkerInfo(String workerId, String strategy, String path) {
            this.workerId = workerId;
            this.strategy = strategy;
            this.path = path;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new SupervisorBehavior(ctx, actorContext, logPublisher).create());
    }

    private static class SupervisorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final LogPublisher logPublisher;
        // Track workers and their strategies for hierarchy info
        private final List<WorkerInfo> workerInfos = new ArrayList<>();

        SupervisorBehavior(
                ActorContext<Command> ctx, SpringActorContext actorContext, LogPublisher logPublisher) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.logPublisher = logPublisher;
        }

        public Behavior<Command> create() {
            String supervisorId = actorContext.actorId();
            ctx.getLog().info("Supervisor {} started", supervisorId);
            logPublisher.publish(
                    String.format(
                            "[%s] Supervisor started (path: %s)", supervisorId, ctx.getSelf().path()));

            return Behaviors.receive(Command.class)
                    .onMessage(SpawnWorker.class, this::onSpawnWorker)
                    .onMessage(RouteWork.class, this::onRouteWork)
                    .onMessage(TriggerWorkerFailure.class, this::onTriggerWorkerFailure)
                    .onMessage(StopWorker.class, this::onStopWorker)
                    .onMessage(GetHierarchy.class, this::onGetHierarchy)
                    .build();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onSpawnWorker(SpawnWorker msg) {
            String supervisorId = actorContext.actorId();

            // Check if worker already exists
            Optional<ActorRef<Void>> existing = ctx.getChild(msg.workerId);
            if (existing.isPresent()) {
                ctx.getLog().warn("Worker {} already exists", msg.workerId);
                logPublisher.publish(
                        String.format("[%s] ⚠️ Worker '%s' already exists", supervisorId, msg.workerId));
                msg.replyTo.tell(new SpawnResult(msg.workerId, false, "Worker already exists"));
                return Behaviors.same();
            }

            // Parse supervision strategy
            SupervisorStrategy strategy;
            String strategyDescription;
            switch (msg.strategy) {
                case "restart-limited":
                    strategy = SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1));
                    strategyDescription = "Restart (max 3 times in 1 min)";
                    break;
                case "stop":
                    strategy = SupervisorStrategy.stop();
                    strategyDescription = "Stop on failure";
                    break;
                case "resume":
                    strategy = SupervisorStrategy.resume();
                    strategyDescription = "Resume (ignore failure)";
                    break;
                case "restart":
                default:
                    strategy = SupervisorStrategy.restart();
                    strategyDescription = "Restart on failure";
                    break;
            }

            // Spawn the worker
            ctx.getLog().info("Spawning worker {} with strategy: {}", msg.workerId, strategyDescription);
            logPublisher.publish(
                    String.format(
                            "[%s] 🚀 Spawning worker '%s' with strategy: %s",
                            supervisorId, msg.workerId, strategyDescription));

            ActorRef<WorkerActor.Command> worker =
                    actorContext.spawnChild(ctx, WorkerActor.class, msg.workerId, strategy);

            // Track the worker info
            workerInfos.add(new WorkerInfo(msg.workerId, strategyDescription, worker.path().toString()));

            msg.replyTo.tell(new SpawnResult(msg.workerId, true, "Worker spawned successfully"));
            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onRouteWork(RouteWork msg) {
            String supervisorId = actorContext.actorId();

            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isEmpty()) {
                ctx.getLog().warn("Worker {} not found", msg.workerId);
                logPublisher.publish(
                        String.format("[%s] ⚠️ Worker '%s' not found", supervisorId, msg.workerId));
                return Behaviors.same();
            }

            ActorRef<WorkerActor.Command> worker =
                    (ActorRef<WorkerActor.Command>) (ActorRef<?>) childOpt.get();
            ctx.getLog().info("Routing work '{}' to worker {}", msg.taskName, msg.workerId);
            logPublisher.publish(
                    String.format(
                            "[%s] 📬 Routing task '%s' to worker '%s'",
                            supervisorId, msg.taskName, msg.workerId));

            worker.tell(new WorkerActor.ProcessWork(msg.taskName, msg.replyTo));
            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onTriggerWorkerFailure(TriggerWorkerFailure msg) {
            String supervisorId = actorContext.actorId();

            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isEmpty()) {
                ctx.getLog().warn("Worker {} not found", msg.workerId);
                logPublisher.publish(
                        String.format("[%s] ⚠️ Worker '%s' not found", supervisorId, msg.workerId));
                msg.replyTo.tell("Worker not found");
                return Behaviors.same();
            }

            ActorRef<WorkerActor.Command> worker =
                    (ActorRef<WorkerActor.Command>) (ActorRef<?>) childOpt.get();
            ctx.getLog().info("Triggering failure in worker {}", msg.workerId);
            logPublisher.publish(
                    String.format(
                            "[%s] 💥 Triggering failure in worker '%s'", supervisorId, msg.workerId));

            worker.tell(new WorkerActor.TriggerFailure(msg.replyTo));
            return Behaviors.same();
        }

        private Behavior<Command> onStopWorker(StopWorker msg) {
            String supervisorId = actorContext.actorId();

            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isEmpty()) {
                ctx.getLog().warn("Worker {} not found", msg.workerId);
                logPublisher.publish(
                        String.format("[%s] ⚠️ Worker '%s' not found", supervisorId, msg.workerId));
                msg.replyTo.tell("Worker not found");
                return Behaviors.same();
            }

            ctx.getLog().info("Stopping worker {}", msg.workerId);
            logPublisher.publish(
                    String.format("[%s] 🛑 Stopping worker '%s'", supervisorId, msg.workerId));

            ctx.stop(childOpt.get());

            // Remove from tracked workers
            workerInfos.removeIf(info -> info.workerId.equals(msg.workerId));

            msg.replyTo.tell("Worker stopped");
            return Behaviors.same();
        }

        private Behavior<Command> onGetHierarchy(GetHierarchy msg) {
            String supervisorId = actorContext.actorId();
            msg.replyTo.tell(new HierarchyInfo(supervisorId, new ArrayList<>(workerInfos)));
            return Behaviors.same();
        }
    }
}
