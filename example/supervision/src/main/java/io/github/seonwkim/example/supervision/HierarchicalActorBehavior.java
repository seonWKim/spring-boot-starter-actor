package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
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

/**
 * Shared behavior implementation for hierarchical actors (supervisors and workers).
 * Contains all common logic for child spawning, routing, and hierarchy management.
 */
public class HierarchicalActorBehavior<C> {
    protected final ActorContext<C> ctx;
    protected final SpringActorContext actorContext;
    protected final LogPublisher logPublisher;
    protected final boolean canProcessWork;
    protected final String actorTypeName;
    protected final Class<?> childActorClass;
    protected int tasksCompleted = 0;
    protected int failureCount = 0;
    protected final Map<String, String> childStrategies = new HashMap<>();

    public HierarchicalActorBehavior(
            ActorContext<C> ctx,
            SpringActorContext actorContext,
            LogPublisher logPublisher,
            boolean canProcessWork,
            String actorTypeName,
            Class<?> childActorClass) {
        this.ctx = ctx;
        this.actorContext = actorContext;
        this.logPublisher = logPublisher;
        this.canProcessWork = canProcessWork;
        this.actorTypeName = actorTypeName;
        this.childActorClass = childActorClass;
    }

    // Work processing (workers only)
    protected Behavior<C> onProcessWork(HierarchicalActor.ProcessWork msg) {
        String actorId = actorContext.actorId();
        tasksCompleted++;

        ctx.getLog().info("Worker {} processing task: {} (total: {})", actorId, msg.taskName, tasksCompleted);
        logPublisher.publish(String.format(
                "[%s] Processing task '%s' (total completed: %d)", actorId, msg.taskName, tasksCompleted));

        msg.replyTo.tell(new HierarchicalActor.WorkResult(actorId, msg.taskName, tasksCompleted));
        return Behaviors.same();
    }

    protected Behavior<C> onTriggerFailure(HierarchicalActor.TriggerFailure msg) {
        String actorId = actorContext.actorId();
        failureCount++;
        ctx.getLog().warn("Worker {} failing intentionally (failure #{})", actorId, failureCount);
        logPublisher.publish(String.format("[%s] ⚠️ Failing intentionally! (failure #%d)", actorId, failureCount));

        msg.replyTo.tell("Failing now");
        throw new RuntimeException("Intentional failure for supervision demonstration");
    }

    // Child spawning
    @SuppressWarnings("unchecked")
    protected Behavior<C> onSpawnChild(HierarchicalActor.SpawnChild msg) {
        String actorId = actorContext.actorId();

        // Check if child already exists
        Optional<ActorRef<Void>> existing = ctx.getChild(msg.childId);
        if (existing.isPresent()) {
            ctx.getLog().warn("Child {} already exists under {}", msg.childId, actorId);
            logPublisher.publish(String.format("[%s] ⚠️ Child '%s' already exists", actorId, msg.childId));
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
        ctx.getLog()
                .info(
                        "{} {} spawning child {} with strategy: {}",
                        actorTypeName,
                        actorId,
                        msg.childId,
                        strategyDescription);
        logPublisher.publish(String.format(
                "[%s] 🚀 Spawning child '%s' with strategy: %s", actorId, msg.childId, strategyDescription));

        actorContext.spawnChild(ctx, (Class) childActorClass, msg.childId, strategy);

        // Track the child strategy
        childStrategies.put(msg.childId, strategyDescription);

        msg.replyTo.tell(new ActorHierarchy.SpawnResult(msg.childId, true, "Child spawned successfully"));
        return Behaviors.same();
    }

    // Recursive routing
    @SuppressWarnings("unchecked")
    protected Behavior<C> onRouteToChild(HierarchicalActor.RouteToChild msg) {
        String actorId = actorContext.actorId();

        // Check if this is me
        if (actorId.equals(msg.childId)) {
            ctx.getLog().info("{} {} processing work '{}'", actorTypeName, actorId, msg.taskName);
            return onProcessWork(new HierarchicalActor.ProcessWork(msg.taskName, msg.replyTo));
        }

        // Check if it's a direct child
        Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
        if (childOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childOpt.get();
            ctx.getLog().info("{} {} routing work '{}' to child {}", actorTypeName, actorId, msg.taskName, msg.childId);
            logPublisher.publish(
                    String.format("[%s] 📬 Routing task '%s' to child '%s'", actorId, msg.taskName, msg.childId));

            child.tell((C) new HierarchicalActor.ProcessWork(msg.taskName, msg.replyTo));
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog().info("{} {} forwarding work to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childRef;
            child.tell((C) new HierarchicalActor.RouteToChild(msg.childId, msg.taskName, msg.replyTo));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onTriggerChildFailure(HierarchicalActor.TriggerChildFailure msg) {
        String actorId = actorContext.actorId();

        // Check if this is me
        if (actorId.equals(msg.childId)) {
            ctx.getLog().info("{} {} triggering failure on self", actorTypeName, actorId);
            return onTriggerFailure(new HierarchicalActor.TriggerFailure(msg.replyTo));
        }

        // Check if it's a direct child
        Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
        if (childOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childOpt.get();
            ctx.getLog().info("{} {} triggering failure in child {}", actorTypeName, actorId, msg.childId);
            logPublisher.publish(String.format("[%s] 💥 Triggering failure in child '%s'", actorId, msg.childId));

            child.tell((C) new HierarchicalActor.TriggerFailure(msg.replyTo));
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog()
                .info("{} {} forwarding failure trigger to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childRef;
            child.tell((C) new HierarchicalActor.TriggerChildFailure(msg.childId, msg.replyTo));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onStopChild(HierarchicalActor.StopChild msg) {
        String actorId = actorContext.actorId();

        // Check if it's a direct child
        Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
        if (childOpt.isPresent()) {
            ctx.getLog().info("{} {} stopping child {}", actorTypeName, actorId, msg.childId);
            logPublisher.publish(String.format("[%s] 🛑 Stopping child '%s'", actorId, msg.childId));

            ctx.stop(childOpt.get());
            childStrategies.remove(msg.childId);

            msg.replyTo.tell("Child stopped");
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog().info("{} {} forwarding stop request to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childRef;
            child.tell((C) new HierarchicalActor.StopChild(msg.childId, msg.replyTo));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onRouteSpawnChild(HierarchicalActor.RouteSpawnChild msg) {
        String actorId = actorContext.actorId();

        // Check if this actor is the parent
        if (actorId.equals(msg.parentId)) {
            ctx.getLog().info("{} {} is the parent, spawning child {}", actorTypeName, actorId, msg.childId);
            return onSpawnChild(new HierarchicalActor.SpawnChild(msg.childId, msg.strategy, msg.replyTo));
        }

        // Check if the parent is a direct child
        Optional<ActorRef<Void>> directChildOpt = ctx.getChild(msg.parentId);
        if (directChildOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) directChildOpt.get();
            child.tell((C) new HierarchicalActor.SpawnChild(msg.childId, msg.strategy, msg.replyTo));

            ctx.getLog()
                    .info(
                            "{} {} routing spawn of {} to direct child {}",
                            actorTypeName,
                            actorId,
                            msg.childId,
                            msg.parentId);
            logPublisher.publish(
                    String.format("[%s] Routing spawn of '%s' to child '%s'", actorId, msg.childId, msg.parentId));

            return Behaviors.same();
        }

        // Parent not a direct child - recursively forward to ALL children
        ctx.getLog()
                .info(
                        "{} {} forwarding spawn request to all children to find parent {}",
                        actorTypeName,
                        actorId,
                        msg.parentId);

        boolean hasChildren = false;
        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            hasChildren = true;
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childRef;
            child.tell((C) new HierarchicalActor.RouteSpawnChild(msg.parentId, msg.childId, msg.strategy, msg.replyTo));
        }

        // If leaf node, send error
        if (!hasChildren) {
            ctx.getLog().warn("{} {} (leaf node) could not find parent {}", actorTypeName, actorId, msg.parentId);
            msg.replyTo.tell(
                    new ActorHierarchy.SpawnResult(msg.childId, false, "Parent '" + msg.parentId + "' not found"));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onGetHierarchy(HierarchicalActor.GetHierarchy msg) {
        String actorId = actorContext.actorId();

        // Query all children using ctx.getChildren()
        List<CompletableFuture<ActorHierarchy.ActorNode>> childFutures = new ArrayList<>();

        ctx.getChildren().forEach(childRef -> {
            String childName = childRef.path().name();

            // Ask each child for its hierarchy (recursive)
            ActorRef<C> typedChild = (ActorRef<C>) (ActorRef<?>) childRef;
            CompletableFuture<ActorHierarchy.ActorNode> future = AskPattern.<C, ActorHierarchy.ActorNode>ask(
                            typedChild,
                            replyTo -> (C) new HierarchicalActor.GetHierarchy(replyTo),
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
                        ActorHierarchy.ActorNode childNode = future.join();
                        // Enrich child with strategy from parent's childStrategies map
                        String childStrategy = childStrategies.get(childNode.actorId);
                        if (childStrategy != null) {
                            childNode = new ActorHierarchy.ActorNode(
                                    childNode.actorId,
                                    childNode.actorType,
                                    childStrategy, // Pass parent's strategy for this child
                                    childNode.path,
                                    childNode.failureCount,
                                    childNode.children);
                        }
                        children.add(childNode);
                    }
                    return children;
                })
                .thenAccept(children -> {
                    ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                            actorId,
                            canProcessWork ? "worker" : "supervisor",
                            canProcessWork ? null : "Supervisor",
                            ctx.getSelf().path().toString(),
                            failureCount,
                            children);
                    msg.replyTo.tell(node);
                })
                .exceptionally(ex -> {
                    ctx.getLog().error("Failed to get hierarchy for {} {}", actorTypeName, actorId, ex);
                    ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                            actorId,
                            canProcessWork ? "worker" : "supervisor",
                            canProcessWork ? null : "Supervisor",
                            ctx.getSelf().path().toString(),
                            failureCount,
                            List.of());
                    msg.replyTo.tell(node);
                    return null;
                });

        return Behaviors.same();
    }

    // Lifecycle signals (workers only)
    protected Behavior<C> onPreRestart(PreRestart signal) {
        String actorId = actorContext.actorId();
        ctx.getLog()
                .warn(
                        "Worker {} restarting (failures: {}, tasks completed: {})",
                        actorId,
                        failureCount,
                        tasksCompleted);
        logPublisher.publish(String.format(
                "[%s] 🔄 Restarting (failures: %d, state lost: %d tasks completed)",
                actorId, failureCount, tasksCompleted));
        return Behaviors.same();
    }

    protected Behavior<C> onPostStop(PostStop signal) {
        String actorId = actorContext.actorId();
        ctx.getLog().info("Worker {} stopped (tasks completed: {})", actorId, tasksCompleted);
        logPublisher.publish(String.format("[%s] 🛑 Stopped (final count: %d tasks)", actorId, tasksCompleted));
        return Behaviors.same();
    }
}
