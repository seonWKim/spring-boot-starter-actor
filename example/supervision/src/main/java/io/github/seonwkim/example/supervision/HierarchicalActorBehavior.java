package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorHandle;
import io.github.seonwkim.core.SpringBehaviorContext;
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
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Shared behavior implementation for hierarchical actors (supervisors and workers).
 * Contains all common logic for child spawning, routing, and hierarchy management.
 */
public class HierarchicalActorBehavior<C> {
    protected final SpringBehaviorContext<C> ctx;
    protected final SpringActorContext actorContext;
    protected final LogPublisher logPublisher;
    protected final boolean canProcessWork;
    protected final String actorTypeName;
    protected final Class<?> childActorClass;
    protected int tasksCompleted = 0;
    protected int failureCount = 0;
    protected final Map<String, String> childStrategies = new HashMap<>();

    public HierarchicalActorBehavior(
            SpringBehaviorContext<C> ctx,
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

        // Log spawn attempt
        ctx.getLog()
                .info(
                        "{} {} spawning child {} with strategy: {}",
                        actorTypeName,
                        actorId,
                        msg.childId,
                        strategyDescription);
        logPublisher.publish(String.format(
                "[%s] 🚀 Spawning child '%s' with strategy: %s", actorId, msg.childId, strategyDescription));

        // Use spring-boot-starter-actor API to spawn child
        SpringActorHandle<C> self = ctx.getSelf();

        ctx.getUnderlying().pipeToSelf(
                self.child((Class) childActorClass)
                        .withId(msg.childId)
                        .withSupervisionStrategy(strategy)
                        .spawn(),
                (childRef, failure) -> (C) new ChildSpawnResult(
                        msg, strategyDescription, (SpringActorHandle<?>) childRef, failure));

        return Behaviors.same();
    }

    // Internal message to handle child spawn result
    protected Behavior<C> onChildSpawnResult(ChildSpawnResult result) {
        String actorId = actorContext.actorId();

        if (result.failure != null) {
            ctx.getLog().error("Failed to spawn child {}", result.originalMsg.childId, result.failure);
            logPublisher.publish(String.format("[%s] ❌ Failed to spawn child '%s': %s",
                    actorId, result.originalMsg.childId, result.failure.getMessage()));
            result.originalMsg.reply(new ActorHierarchy.SpawnResult(
                    result.originalMsg.childId, false, "Failed to spawn: " + result.failure.getMessage()));
            return Behaviors.same();
        }

        if (result.childRef != null) {
            // Track the child strategy
            childStrategies.put(result.originalMsg.childId, result.strategyDescription);

            ctx.getLog().info("Successfully spawned child {} with strategy {}",
                    result.originalMsg.childId, result.strategyDescription);
            result.originalMsg.reply(new ActorHierarchy.SpawnResult(
                    result.originalMsg.childId, true, "Child spawned successfully"));
        } else {
            ctx.getLog().warn("Child spawn returned null ref for {}", result.originalMsg.childId);
            result.originalMsg.reply(new ActorHierarchy.SpawnResult(
                    result.originalMsg.childId, false, "Child spawn returned null"));
        }

        return Behaviors.same();
    }

    // Internal message class for handling async spawn results
    public static class ChildSpawnResult implements HierarchicalActor.Command {
        final HierarchicalActor.SpawnChild originalMsg;
        final String strategyDescription;
        final SpringActorHandle<?> childRef;
        final Throwable failure;

        ChildSpawnResult(
                HierarchicalActor.SpawnChild originalMsg,
                String strategyDescription,
                SpringActorHandle<?> childRef,
                Throwable failure) {
            this.originalMsg = originalMsg;
            this.strategyDescription = strategyDescription;
            this.childRef = childRef;
            this.failure = failure;
        }
    }

    // Recursive routing
    @SuppressWarnings("unchecked")
    protected Behavior<C> onRouteToChild(HierarchicalActor.RouteToChild msg) {
        String actorId = actorContext.actorId();

        // Check if this is me
        if (actorId.equals(msg.childId)) {
            ctx.getLog().info("{} {} processing work '{}'", actorTypeName, actorId, msg.taskName);
            return onProcessWork(new HierarchicalActor.ProcessWork(msg.taskName, msg.getReplyTo()));
        }

        // Check if it's a direct child
        Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
        if (childOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) childOpt.get();
            ctx.getLog().info("{} {} routing work '{}' to child {}", actorTypeName, actorId, msg.taskName, msg.childId);
            logPublisher.publish(
                    String.format("[%s] 📬 Routing task '%s' to child '%s'", actorId, msg.taskName, msg.childId));

            child.tell((C) new HierarchicalActor.ProcessWork(msg.taskName, msg.getReplyTo()));
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog().info("{} {} forwarding work to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) childRef;
            child.tell((C) new HierarchicalActor.RouteToChild(msg.childId, msg.taskName));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onTriggerChildFailure(HierarchicalActor.TriggerChildFailure msg) {
        String actorId = actorContext.actorId();

        // Check if this is me
        if (actorId.equals(msg.childId)) {
            ctx.getLog().info("{} {} triggering failure on self", actorTypeName, actorId);
            return onTriggerFailure(new HierarchicalActor.TriggerFailure(msg.getReplyTo()));
        }

        // Check if it's a direct child
        Optional<ActorRef<Void>> childOpt = ctx.getChild(msg.childId);
        if (childOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) (ActorRef<?>) childOpt.get();
            ctx.getLog().info("{} {} triggering failure in child {}", actorTypeName, actorId, msg.childId);
            logPublisher.publish(String.format("[%s] 💥 Triggering failure in child '%s'", actorId, msg.childId));

            child.tell((C) new HierarchicalActor.TriggerFailure(msg.getReplyTo()));
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog()
                .info("{} {} forwarding failure trigger to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) childRef;
            child.tell((C) new HierarchicalActor.TriggerChildFailure(msg.childId));
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

            msg.reply("Child stopped");
            return Behaviors.same();
        }

        // Not a direct child - recursively forward to all children
        ctx.getLog().info("{} {} forwarding stop request to children to find {}", actorTypeName, actorId, msg.childId);

        for (ActorRef<Void> childRef : (Iterable<ActorRef<Void>>) ctx.getChildren()::iterator) {
            ActorRef<C> child = (ActorRef<C>) childRef;
            HierarchicalActor.StopChild stopCmd = new HierarchicalActor.StopChild(msg.childId);
            stopCmd.withReplyTo(msg.getReplyTo());
            child.tell((C) stopCmd);
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onRouteSpawnChild(HierarchicalActor.RouteSpawnChild msg) {
        String actorId = actorContext.actorId();

        // Check if this actor is the parent
        if (actorId.equals(msg.parentId)) {
            ctx.getLog().info("{} {} is the parent, spawning child {}", actorTypeName, actorId, msg.childId);
            HierarchicalActor.SpawnChild spawnCmd = new HierarchicalActor.SpawnChild(msg.childId, msg.strategy);
            spawnCmd.withReplyTo(msg.getReplyTo());
            return onSpawnChild(spawnCmd);
        }

        // Check if the parent is a direct child
        Optional<ActorRef<Void>> directChildOpt = ctx.getChild(msg.parentId);
        if (directChildOpt.isPresent()) {
            ActorRef<C> child = (ActorRef<C>) directChildOpt.get();
            HierarchicalActor.SpawnChild spawnCmd = new HierarchicalActor.SpawnChild(msg.childId, msg.strategy);
            spawnCmd.withReplyTo(msg.getReplyTo());
            child.tell((C) spawnCmd);

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
            ActorRef<C> child = (ActorRef<C>) childRef;
            HierarchicalActor.RouteSpawnChild routeCmd =
                    new HierarchicalActor.RouteSpawnChild(msg.parentId, msg.childId, msg.strategy);
            routeCmd.withReplyTo(msg.getReplyTo());
            child.tell((C) routeCmd);
        }

        // If leaf node, send error
        if (!hasChildren) {
            ctx.getLog().warn("{} {} (leaf node) could not find parent {}", actorTypeName, actorId, msg.parentId);
            msg.reply(new ActorHierarchy.SpawnResult(msg.childId, false, "Parent '" + msg.parentId + "' not found"));
        }

        return Behaviors.same();
    }

    @SuppressWarnings("unchecked")
    protected Behavior<C> onGetHierarchy(HierarchicalActor.GetHierarchy msg) {
        String actorId = actorContext.actorId();

        // Query all children using ctx.getChildren()
        List<CompletableFuture<ActorHierarchy.ActorNode>> childFutures = new ArrayList<>();

        ctx.getChildren().forEach(childRef -> {
            // Ask each child for its hierarchy (recursive)
            ActorRef<C> typedChild = (ActorRef<C>) childRef;
            CompletableFuture<ActorHierarchy.ActorNode> future = AskPattern.<C, ActorHierarchy.ActorNode>ask(
                            typedChild,
                            replyTo -> {
                                HierarchicalActor.GetHierarchy cmd = new HierarchicalActor.GetHierarchy();
                                cmd.withReplyTo(replyTo);
                                return (C) cmd;
                            },
                            Duration.ofSeconds(3),
                            ctx.getUnderlying().getSystem().scheduler())
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
                            ctx.path().toString(),
                            failureCount,
                            children);
                    msg.reply(node);
                })
                .exceptionally(ex -> {
                    ctx.getLog().error("Failed to get hierarchy for {} {}", actorTypeName, actorId, ex);
                    ActorHierarchy.ActorNode node = new ActorHierarchy.ActorNode(
                            actorId,
                            canProcessWork ? "worker" : "supervisor",
                            canProcessWork ? null : "Supervisor",
                            ctx.path().toString(),
                            failureCount,
                            List.of());
                    msg.reply(node);
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
