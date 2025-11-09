package io.github.seonwkim.example.persistence.snapshot;

/**
 * Hybrid snapshot strategy that triggers based on either operation count or time.
 */
public class HybridSnapshotStrategy implements SnapshotStrategy {
    private final long operationInterval;
    private final long timeIntervalMillis;

    public HybridSnapshotStrategy(long operationInterval, long timeIntervalMillis) {
        this.operationInterval = operationInterval;
        this.timeIntervalMillis = timeIntervalMillis;
    }

    @Override
    public boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot) {
        return operationCount >= operationInterval || timeSinceLastSnapshot >= timeIntervalMillis;
    }

    @Override
    public boolean shouldLoadSnapshot() {
        return true;
    }
}
