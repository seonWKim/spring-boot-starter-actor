package io.github.seonwkim.example.supervision;

import java.util.List;

import org.apache.pekko.actor.typed.ActorRef;

/**
 * Shared data structures for actor hierarchy management.
 */
public class ActorHierarchy {

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
     * Represents a node in the actor hierarchy tree.
     * Contains information about the actor and its children (recursive).
     */
    public static class ActorNode {
        public final String actorId;
        public final String actorType; // "supervisor" or "worker"
        public final String strategy;
        public final String path;
        public final int failureCount; // Number of failures
        public final List<ActorNode> children; // Recursive children

        public ActorNode(
                String actorId,
                String actorType,
                String strategy,
                String path,
                int failureCount,
                List<ActorNode> children) {
            this.actorId = actorId;
            this.actorType = actorType;
            this.strategy = strategy;
            this.path = path;
            this.failureCount = failureCount;
            this.children = children;
        }
    }
}
