package io.github.seonwkim.example.supervision;

import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Shared data structures for actor hierarchy management.
 */
public class ActorHierarchy {

    /**
     * Command to spawn a child worker with a supervision strategy.
     */
    public static class SpawnChild {
        public final String childId;
        public final String strategy;
        public final ActorRef<SpawnResult> replyTo;

        public SpawnChild(String childId, String strategy, ActorRef<SpawnResult> replyTo) {
            this.childId = childId;
            this.strategy = strategy;
            this.replyTo = replyTo;
        }
    }

    /**
     * Result of spawning a child.
     */
    public static class SpawnResult {
        public final String childId;
        public final boolean success;
        public final String message;

        public SpawnResult(String childId, boolean success, String message) {
            this.childId = childId;
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Command to get the hierarchy information for this actor and its children.
     */
    public static class GetHierarchy {
        public final ActorRef<ActorNode> replyTo;

        public GetHierarchy(ActorRef<ActorNode> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * Represents a node in the actor hierarchy tree.
     * Contains information about the actor and its children (recursive).
     */
    public static class ActorNode {
        public final String actorId;
        public final String actorType; // "supervisor" or "worker"
        public final String strategy;
        public final String path;
        public final List<ActorNode> children; // Recursive children

        public ActorNode(
                String actorId,
                String actorType,
                String strategy,
                String path,
                List<ActorNode> children) {
            this.actorId = actorId;
            this.actorType = actorType;
            this.strategy = strategy;
            this.path = path;
            this.children = children;
        }
    }

    /**
     * Helper to parse supervision strategy string into description.
     */
    public static String getStrategyDescription(String strategy) {
        switch (strategy) {
            case "restart-limited":
                return "Restart (max 3 times in 1 min)";
            case "stop":
                return "Stop on failure";
            case "resume":
                return "Resume (ignore failure)";
            case "restart":
            default:
                return "Restart on failure";
        }
    }
}
