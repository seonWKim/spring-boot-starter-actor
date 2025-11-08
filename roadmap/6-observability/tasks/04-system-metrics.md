# Task 2.1: System-Level Metrics

**Priority:** HIGH
**Estimated Effort:** 3-4 days
**Dependencies:** Phase 1 complete
**Assignee:** AI Agent

---

## Objective

Implement system-wide metrics to monitor overall actor system health and behavior.

---

## Metrics to Implement

### 1. `system.active-actors` (Gauge)
**Description:** Number of active actors
**Use Case:** Monitor system scale and capacity

**Test Strategy:**
```java
@Test
public void testActiveActorsGauge() {
    // Create 3 actors -> Verify gauge shows 3
    // Stop 1 actor -> Verify gauge shows 2
}
```

### 2. `system.processed-messages` (Counter)
**Description:** Total messages processed system-wide
**Use Case:** Monitor overall throughput

**Test Strategy:**
```java
@Test
public void testSystemProcessedMessages() {
    // Create 2 actors -> Send 3 messages to each
    // Verify system-wide counter shows 6
}
```

### 3. `system.dead-letters` (Counter)
**Description:** Dead letter count
**Use Case:** Alert on message delivery failures

**Test Strategy:**
```java
@Test
public void testDeadLetterCounter() {
    // Send message to non-existent actor path
    // Verify dead letter counter increments
}
```

### 4. `system.unhandled-messages` (Counter)
**Description:** Unhandled message count
**Use Case:** Identify missing message handlers

**Test Strategy:**
```java
@Test
public void testUnhandledMessageCounter() {
    // Create actor without handler for specific message type
    // Send that message -> Verify unhandled counter increments
}
```

### 5. `system.created-actors.total` (Counter)
**Description:** Total actors created
**Use Case:** Track actor creation rate

### 6. `system.terminated-actors.total` (Counter)
**Description:** Total actors terminated
**Use Case:** Track actor termination rate

---

## Implementation Requirements

### Files to Create/Modify

1. **`metrics/src/main/java/io/github/seonwkim/metrics/SystemMetrics.java`**
   - Aggregate metrics across all actors
   - Integrate with dead letter monitoring
   - Integrate with unhandled message detection

2. **Instrumentation for DeadLetters and Unhandled Messages**
   - Intercept dead letter events
   - Intercept unhandled message events

3. **Tests**
   - `SystemMetricsTest.java` with comprehensive tests

---

## Configuration

```yaml
spring:
  actor:
    metrics:
      system:
        enabled: true
        track-dead-letters: true
        track-unhandled-messages: true
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `actor-path`: Actor path (for dead letters)
- `message-type`: Message class (for unhandled messages)

---

## Acceptance Criteria

- [ ] All 6 system metrics implemented
- [ ] Dead letter monitoring integrated
- [ ] Unhandled message detection working
- [ ] Metrics properly tagged
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation updated
- [ ] Performance benchmarks show < 2% overhead

---

## Notes

- System metrics should have minimal performance impact
- Consider using efficient concurrent data structures
- Aggregate metrics across multiple actor systems if needed
