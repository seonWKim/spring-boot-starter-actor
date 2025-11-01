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
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
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

    // Hierarchical commands
    public static class SpawnChild implements Command {
        public final String childId;
        public final String strategy;
        public final ActorRef<ActorHierarchy.SpawnResult> replyTo;

        public SpawnChild(String childId, String strategy, ActorRef<ActorHierarchy.SpawnResult> replyTo) {
            this.childId = childId;
            this.strategy = strategy;
            this.replyTo = replyTo;
        }
    }

    public static class RouteToChild implements Command {
        public final String childId;
        public final String taskName;
        public final ActorRef<WorkResult> replyTo;

        public RouteToChild(String childId, String taskName, ActorRef<WorkResult> replyTo) {
            this.childId = childId;
            this.taskName = taskName;
            this.replyTo = replyTo;
        }
    }

    public static class TriggerChildFailure implements Command {
        public final String childId;
        public final ActorRef<String> replyTo;

        public TriggerChildFailure(String childId, ActorRef<String> replyTo) {
            this.childId = childId;
            this.replyTo = replyTo;
        }
    }

    public static class StopChild implements Command {
        public final String childId;
        public final ActorRef<String> replyTo;

        public StopChild(String childId, ActorRef<String> replyTo) {
            this.childId = childId;
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
        // Track children and their strategies
        private final Map<String, String> childStrategies = new HashMap<>();

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
                    .onMessage(SpawnChild.class, this::onSpawnChild)
                    .onMessage(RouteToChild.class, this::onRouteToChild)
                    .onMessage(TriggerChildFailure.class, this::onTriggerChildFailure)
                    .onMessage(StopChild.class, this::onStopChild)
                    .onMessage(RouteSpawnChild.class, this::onRouteSpawnChild)
                    .onMessage(GetHierarchy.class, this::onGetHierarchy)
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

        @SuppressWarnings("unchecked")
        private Behavior<Command> onSpawnChild(SpawnChild msg) {
            String workerId = actorContext.actorId();

            // Check if child already exists
            Optional<ActorRef<Void>> existing = ctx.getChild(msg.childId);
            if (existing.isPresent()) {
                ctx.getLog().warn("Child {} already exists under worker {}", msg.childId, workerId);
                logPublisher.publish(
                        String.format("[%s] ⚠️ Child '%s' already exists", workerId, msg.childId));
                msg.replyTo.tell(new ActorHierarchy.SpawnResult(msg.childId, false, "Child already exists"));
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

            // Spawn the child worker
            ctx.getLog().info("Worker {} spawning child {} with strategy: {}", workerId, msg.childId, strategyDescription);
            logPublisher.publish(
                    String.format(
                            "[%s] 🚀 Spawning child '%s' with strategy: %s",
                            workerId, msg.childId, strategyDescription));

            ActorRef<Command> child =
                    actorContext.spawnChild(ctx, WorkerActor.class, msg.childId, strategy);

            // Track the child strategy
            childStrategies.put(msg.childId, strategyDescription);

            msg.replyTo.tell(new ActorHierarchy.SpawnResult(msg.childId, true, "Child spawned successfully"));
            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onRouteToChild(RouteToChild msg) {
            String workerId = actorContext.actorId();

            // Check if this is me
            if (workerId.equals(msg.childId)) {
                ctx.getLog().info("Worker {} processing work '{}'", workerId, msg.taskName);
                return onProcessWork(new ProcessWork(msg.taskName, msg.replyTo));
            }

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
            if (childOpt.isPresent()) {
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childOpt.get();
                ctx.getLog().info("Worker {} routing work '{}' to child {}", workerId, msg.taskName, msg.childId);
                logPublisher.publish(
                        String.format(
                                "[%s] 📬 Routing task '%s' to child '%s'",
                                workerId, msg.taskName, msg.childId));

                child.tell(new ProcessWork(msg.taskName, msg.replyTo));
                return Behaviors.same();
            }

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} forwarding work to children to find {}", workerId, msg.childId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childRef;
                child.tell(new RouteToChild(msg.childId, msg.taskName, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onTriggerChildFailure(TriggerChildFailure msg) {
            String workerId = actorContext.actorId();

            // Check if this is me
            if (workerId.equals(msg.childId)) {
                ctx.getLog().info("Worker {} triggering failure on self", workerId);
                return onTriggerFailure(new TriggerFailure(msg.replyTo));
            }

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
            if (childOpt.isPresent()) {
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childOpt.get();
                ctx.getLog().info("Worker {} triggering failure in child {}", workerId, msg.childId);
                logPublisher.publish(
                        String.format(
                                "[%s] 💥 Triggering failure in child '%s'", workerId, msg.childId));

                child.tell(new TriggerFailure(msg.replyTo));
                return Behaviors.same();
            }

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} forwarding failure trigger to children to find {}", workerId, msg.childId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childRef;
                child.tell(new TriggerChildFailure(msg.childId, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onStopChild(StopChild msg) {
            String workerId = actorContext.actorId();

            // Check if it's a direct child
            Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
            if (childOpt.isPresent()) {
                ctx.getLog().info("Worker {} stopping child {}", workerId, msg.childId);
                logPublisher.publish(
                        String.format("[%s] 🛑 Stopping child '%s'", workerId, msg.childId));

                ctx.stop(childOpt.get());
                childStrategies.remove(msg.childId);

                msg.replyTo.tell("Child stopped");
                return Behaviors.same();
            }

            // Not a direct child - recursively forward to all children
            ctx.getLog().info("Worker {} forwarding stop request to children to find {}", workerId, msg.childId);

            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childRef;
                child.tell(new StopChild(msg.childId, msg.replyTo));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onRouteSpawnChild(RouteSpawnChild msg) {
            String workerId = actorContext.actorId();

            // Check if this worker is the parent
            if (workerId.equals(msg.parentId)) {
                // Spawn directly
                ctx.getLog().info("Worker {} is the parent, spawning child {}", workerId, msg.childId);
                return onSpawnChild(new SpawnChild(msg.childId, msg.strategy, msg.replyTo));
            }

            // Check if the parent is a direct child
            Optional<ActorRef<Void>> directChildOpt = ctx.getChild(msg.parentId);
            if (directChildOpt.isPresent()) {
                // Found the parent as a direct child, send spawn command to it
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) directChildOpt.get();
                child.tell(new SpawnChild(msg.childId, msg.strategy, msg.replyTo));

                ctx.getLog().info("Worker {} routing spawn of {} to direct child {}", workerId, msg.childId, msg.parentId);
                logPublisher.publish(
                    String.format("[%s] Routing spawn of '%s' to child '%s'", workerId, msg.childId, msg.parentId));

                return Behaviors.same();
            }

            // Parent not a direct child - recursively forward to ALL children
            // Each child will check if it's the parent or route further down
            ctx.getLog().info("Worker {} forwarding spawn request to all children to find parent {}", workerId, msg.parentId);

            boolean hasChildren = false;
            for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
                hasChildren = true;
                ActorRef<Command> child = (ActorRef<Command>) (ActorRef<?>) childRef;

                // Forward the RouteSpawnChild message to this child
                // The child will either handle it or forward it further down
                child.tell(new RouteSpawnChild(msg.parentId, msg.childId, msg.strategy, msg.replyTo));
            }

            // If we have no children and couldn't find the parent, it doesn't exist under this branch
            // Send error response - if another branch finds it, their success will arrive first
            if (!hasChildren) {
                ctx.getLog().warn("Worker {} (leaf node) could not find parent {}", workerId, msg.parentId);
                msg.replyTo.tell(new ActorHierarchy.SpawnResult(msg.childId, false,
                    "Parent '" + msg.parentId + "' not found"));
            }

            return Behaviors.same();
        }

        @SuppressWarnings("unchecked")
        private Behavior<Command> onGetHierarchy(GetHierarchy msg) {
            String workerId = actorContext.actorId();

            // Query all children using ctx.getChildren()
            List<CompletableFuture<ActorHierarchy.ActorNode>> childFutures = new ArrayList<>();

            ctx.getChildren().forEach(childRef -> {
                String childName = childRef.path().name();
                String strategy = childStrategies.getOrDefault(childName, "Unknown");

                // Ask each child for its hierarchy (recursive)
                ActorRef<Command> typedChild = (ActorRef<Command>) (ActorRef<?>) childRef;
                CompletableFuture<ActorHierarchy.ActorNode> future =
                        AskPattern.<Command, ActorHierarchy.ActorNode>ask(
                                typedChild,
                                replyTo -> new GetHierarchy(replyTo),
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
                                workerId,
                                "worker",
                                null, // Workers don't have a strategy themselves, their parent supervises them
                                ctx.getSelf().path().toString(),
                                children
                        );
                        msg.replyTo.tell(node);
                    })
                    .exceptionally(ex -> {
                        ctx.getLog().error("Failed to get hierarchy for worker {}", workerId, ex);
                        // Return node with no children on error
                        ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                                workerId,
                                "worker",
                                null,
                                ctx.getSelf().path().toString(),
                                List.of()
                        );
                        msg.replyTo.tell(node);
                        return null;
                    });

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
