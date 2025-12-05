package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.FrameworkCommand;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Command definitions and result types shared by hierarchical actors (supervisors and workers).
 */
public class HierarchicalActor {

    public interface Command extends FrameworkCommand {}

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
    public static class SpawnChild extends AskCommand<ActorHierarchy.SpawnResult> implements Command {
        public final String childId;
        public final String strategy;

        public SpawnChild(String childId, String strategy) {
            this.childId = childId;
            this.strategy = strategy;
        }
    }

    // Routing commands
    public static class RouteToChild extends AskCommand<WorkResult> implements Command {
        public final String childId;
        public final String taskName;

        public RouteToChild(String childId, String taskName) {
            this.childId = childId;
            this.taskName = taskName;
        }
    }

    public static class TriggerChildFailure extends AskCommand<String> implements Command {
        public final String childId;

        public TriggerChildFailure(String childId) {
            this.childId = childId;
        }
    }

    public static class StopChild extends AskCommand<String> implements Command {
        public final String childId;

        public StopChild(String childId) {
            this.childId = childId;
        }
    }

    public static class RouteSpawnChild extends AskCommand<ActorHierarchy.SpawnResult> implements Command {
        public final String parentId;
        public final String childId;
        public final String strategy;

        public RouteSpawnChild(String parentId, String childId, String strategy) {
            this.parentId = parentId;
            this.childId = childId;
            this.strategy = strategy;
        }
    }

    public static class GetHierarchy extends AskCommand<ActorHierarchy.ActorNode> implements Command {
        public GetHierarchy() {}
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
