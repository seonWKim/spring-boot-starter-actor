package io.github.seonwkim.example.supervision;

import org.apache.pekko.actor.typed.ActorRef;

/**
 * Command definitions and result types shared by hierarchical actors (supervisors and workers).
 */
public class HierarchicalActor {

    public interface Command {}

    // Work processing commands (only for workers)
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

    // Child management commands
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

    // Routing commands
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

    public static class GetHierarchy implements Command {
        public final ActorRef<ActorHierarchy.ActorNode> replyTo;

        public GetHierarchy(ActorRef<ActorHierarchy.ActorNode> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Response types
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
}
