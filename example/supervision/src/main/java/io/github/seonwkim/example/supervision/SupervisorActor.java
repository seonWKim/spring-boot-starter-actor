package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
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
        public final ActorRef<ActorHierarchy.ActorNode> replyTo;

        public GetHierarchy(ActorRef<ActorHierarchy.ActorNode> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class RouteSpawnChild implements Command {
        public final String parentId;
        public final String childId;
        public final String strategy;
        public final ActorRef<ActorHierarchy.SpawnResult> replyTo;

        public RouteSpawnChild(String parentId, String childId, String strategy, ActorRef<ActorHierarchy.SpawnResult> replyTo) {
            this.parentId = parentId;
            this.childId = childId;
            this.strategy = strategy;
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

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new SupervisorBehavior(ctx, actorContext, logPublisher).create());
    }

    private static class SupervisorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final LogPublisher logPublisher;
        // Track child strategies (using Map instead of List)
        private final Map<String, String> childStrategies = new HashMap<>();

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
                    .onMessage(RouteSpawnChild.class, this::onRouteSpawnChild)
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

            // Track the worker strategy
            childStrategies.put(msg.workerId, strategyDescription);

            msg.replyTo.tell(new SpawnResult(msg.workerId, true, "Worker spawned successfully"));
            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onRouteWork(RouteWork msg) {
            String supervisorId = actorContext.actorId();

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isPresent()) {
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

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} not a direct child of {}, forwarding to children", msg.workerId, supervisorId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<WorkerActor.Command> worker = (ActorRef<WorkerActor.Command>) (ActorRef<?>) childRef;
                worker.tell(new WorkerActor.RouteToChild(msg.workerId, msg.taskName, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onTriggerWorkerFailure(TriggerWorkerFailure msg) {
            String supervisorId = actorContext.actorId();

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isPresent()) {
                ActorRef<WorkerActor.Command> worker =
                        (ActorRef<WorkerActor.Command>) (ActorRef<?>) childOpt.get();
                ctx.getLog().info("Triggering failure in worker {}", msg.workerId);
                logPublisher.publish(
                        String.format(
                                "[%s] 💥 Triggering failure in worker '%s'", supervisorId, msg.workerId));

                worker.tell(new WorkerActor.TriggerFailure(msg.replyTo));
                return Behaviors.same();
            }

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} not a direct child of {}, forwarding failure trigger to children", msg.workerId, supervisorId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<WorkerActor.Command> worker = (ActorRef<WorkerActor.Command>) (ActorRef<?>) childRef;
                worker.tell(new WorkerActor.TriggerChildFailure(msg.workerId, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onStopWorker(StopWorker msg) {
            String supervisorId = actorContext.actorId();

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.workerId);
            if (childOpt.isPresent()) {
                ctx.getLog().info("Stopping worker {}", msg.workerId);
                logPublisher.publish(
                        String.format("[%s] 🛑 Stopping worker '%s'", supervisorId, msg.workerId));

                ctx.stop(childOpt.get());

                // Remove from tracked strategies
                childStrategies.remove(msg.workerId);

                msg.replyTo.tell("Worker stopped");
                return Behaviors.same();
            }

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} not a direct child of {}, forwarding stop request to children", msg.workerId, supervisorId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<WorkerActor.Command> worker = (ActorRef<WorkerActor.Command>) (ActorRef<?>) childRef;
                worker.tell(new WorkerActor.StopChild(msg.workerId, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onRouteSpawnChild(RouteSpawnChild msg) {
            String supervisorId = actorContext.actorId();

            // Check if this supervisor is the parent
            if (supervisorId.equals(msg.parentId)) {
                // Spawn directly
                return onSpawnWorker(new SpawnWorker(msg.childId, msg.strategy,
                    (ActorRef<SpawnResult>) (ActorRef<?>) msg.replyTo));
            }

            // Check if the parent is a direct child
            Optional<ActorRef<Void>> directChildOpt = ctx.getChild(msg.parentId);
            if (directChildOpt.isPresent()) {
                // Found the parent as a direct child, send spawn command to it
                ActorRef<WorkerActor.Command> worker = (ActorRef<WorkerActor.Command>) (ActorRef<?>) directChildOpt.get();
                worker.tell(new WorkerActor.SpawnChild(msg.childId, msg.strategy, msg.replyTo));

                ctx.getLog().info("Routing spawn of {} to direct child {}", msg.childId, msg.parentId);
                logPublisher.publish(
                    String.format("[%s] Routing spawn of '%s' to child '%s'", supervisorId, msg.childId, msg.parentId));

                return Behaviors.same();
            }

            // Parent not a direct child - recursively forward to ALL children
            // Each child will check if it's the parent or route further down
            ctx.getLog().info("Parent {} not a direct child of {}, forwarding to all children", msg.parentId, supervisorId);

            boolean hasChildren = false;
            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                hasChildren = true;
                ActorRef<WorkerActor.Command> worker = (ActorRef<WorkerActor.Command>) (ActorRef<?>) childRef;

                // Forward the RouteSpawnChild message to this child
                // The child will either handle it or forward it further down
                worker.tell(new WorkerActor.RouteSpawnChild(msg.parentId, msg.childId, msg.strategy, msg.replyTo));
            }

            // If we have no children and couldn't find the parent, it doesn't exist
            if (!hasChildren) {
                ctx.getLog().warn("Parent {} not found in hierarchy under {}", msg.parentId, supervisorId);
                msg.replyTo.tell(new ActorHierarchy.SpawnResult(msg.childId, false, "Parent not found"));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onGetHierarchy(GetHierarchy msg) {
            String supervisorId = actorContext.actorId();

            // Query all children using ctx.getChildren()
            List<CompletableFuture<ActorHierarchy.ActorNode>> childFutures = new ArrayList<>();

            ctx.getChildren().forEach(childRef -> {
                String childName = childRef.path().name();

                // Ask each child for its hierarchy (recursive)
                ActorRef<WorkerActor.Command> typedChild = (ActorRef<WorkerActor.Command>) (ActorRef<?>) childRef;
                CompletableFuture<ActorHierarchy.ActorNode> future =
                        AskPattern.<WorkerActor.Command, ActorHierarchy.ActorNode>ask(
                                typedChild,
                                replyTo -> new WorkerActor.GetHierarchy(replyTo),
                                Duration.ofSeconds(3),
                                ctx.getSystem().scheduler())
                        .toCompletableFuture();

                childFutures.add(future);
            });

            // Wait for all children to respond, then send the complete hierarchy
            CompletableFuture.allOf(childFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<ActorHierarchy.ActorNode> children = new ArrayList<>();
                        for (CompletableFuture<ActorHierarchy.ActorNode> future : childFutures) {
                            children.add(future.join());
                        }
                        return children;
                    })
                    .thenAccept(children -> {
                        ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                                supervisorId,
                                "supervisor",
                                "Supervisor", // Supervisors don't fail, so this is just for display
                                ctx.getSelf().path().toString(),
                                children
                        );
                        msg.replyTo.tell(node);
                    })
                    .exceptionally(ex -> {
                        ctx.getLog().error("Failed to get hierarchy for supervisor {}", supervisorId, ex);
                        // Return node with no children on error
                        ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                                supervisorId,
                                "supervisor",
                                "Supervisor",
                                ctx.getSelf().path().toString(),
                                List.of()
                        );
                        msg.replyTo.tell(node);
                        return null;
                    });

            return Behaviors.same();
        }
    }
}
