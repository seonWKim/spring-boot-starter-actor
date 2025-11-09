package io.github.seonwkim.example.persistence.snapshot;

/**
 * Strategy interface for determining when to create snapshots.
 */
public interface SnapshotStrategy {
    boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot);
    boolean shouldLoadSnapshot();
}
