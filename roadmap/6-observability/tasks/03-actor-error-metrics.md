# Task 1.3: Actor Error Metrics

**Priority:** CRITICAL
**Estimated Effort:** 1-2 days
**Dependencies:** Task 1.2
**Assignee:** AI Agent

---

## Objective

Implement error and message processing metrics to monitor actor health and throughput.

---

## Metrics to Implement

### 1. `actor.errors` (Counter)
**Description:** Count of processing errors
**Use Case:** Alert on error spikes, monitor actor health

**Test Strategy:**
```java
@Test
public void testActorErrorCounter() {
    // Create actor
    // Send message that triggers exception
    // Verify error counter increments by 1
}
```

### 2. `actor.messages.processed` (Counter)
**Description:** Total messages processed
**Use Case:** Monitor throughput and load

**Test Strategy:**
```java
@Test
public void testMessagesProcessedCounter() {
    // Create actor
    // Send 5 messages
    // Verify processed counter equals 5 after all messages complete
}
```

### 3. `actor.errors.by-type` (Counter)
**Description:** Errors grouped by exception type
**Use Case:** Identify common failure patterns

---

## Implementation Requirements

### Files to Create/Modify

1. **`metrics/src/main/java/io/github/seonwkim/metrics/ActorErrorMetrics.java`**
   - Track message processing success/failure
   - Capture exception types
   - Integrate with message processing interceptor

2. **Tests**
   - `ActorErrorMetricsTest.java` with all test strategies

---

## Configuration

```yaml
spring:
  actor:
    metrics:
      errors:
        enabled: true
        track-by-type: true
        capture-stack-traces: false  # For security
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `actor-path`: Full actor path
- `actor-class`: Actor class name
- `message-type`: Message class name
- `error-type`: Exception class name (for error metrics)

---

## Acceptance Criteria

- [ ] Error counter increments on message processing exceptions
- [ ] Messages processed counter increments on success
- [ ] Errors grouped by exception type
- [ ] Metrics properly tagged
- [ ] Comprehensive unit tests (>80% coverage)
- [ ] Integration tests with failing actors
- [ ] Documentation updated
- [ ] No performance degradation

---

## Notes

- Only count user exceptions, not framework exceptions
- Don't expose sensitive error details in metrics
- Consider sampling for high-throughput actors
