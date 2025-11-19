# Task 1.2: Actor Lifecycle Metrics

**Priority:** CRITICAL
**Estimated Effort:** 2-3 days
**Dependencies:** Task 1.1
**Assignee:** AI Agent

---

## Objective

Implement lifecycle-related metrics to monitor actor creation, restarts, and termination.

---

## Metrics to Implement

### 1. `actor.lifecycle.restarts` (Counter)
**Description:** Count of actor restarts
**Use Case:** Monitor supervision strategy effectiveness

**Test Strategy:**
```java
@Test
public void testActorRestartCounter() {
    // Create supervised actor with restart strategy
    // Trigger failure
    // Verify restart counter increments
}
```

### 2. `actor.lifecycle.stops` (Counter)
**Description:** Count of actor stops
**Use Case:** Monitor actor lifecycle management

**Test Strategy:**
```java
@Test
public void testActorStopCounter() {
    // Create actor
    // Call stop/terminate
    // Verify stop counter increments by 1
}
```

### 3. `actor.lifecycle.created.total` (Counter)
**Description:** Total actors created
**Use Case:** Track actor creation rate

### 4. `actor.lifecycle.terminated.total` (Counter)
**Description:** Total actors terminated
**Use Case:** Track actor termination rate

---

## Implementation Requirements

### Files to Create/Modify

1. **`metrics/src/main/java/io/github/seonwkim/metrics/ActorLifecycleMetrics.java`**
   - Implement lifecycle metrics collection
   - Track restarts via supervision events
   - Track stops vs restarts separately

2. **Tests**
   - `ActorLifecycleMetricsTest.java` with all test strategies

---

## Configuration

```yaml
spring:
  actor:
    metrics:
      lifecycle:
        enabled: true
        track-restarts: true
        track-stops: true
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `actor-path`: Full actor path
- `actor-class`: Actor class name
- `supervision-strategy`: Strategy used (restart, stop, escalate)

---

## Acceptance Criteria

- [ ] All lifecycle metrics implemented
- [ ] Metrics properly tagged with actor context
- [ ] Comprehensive unit tests (>80% coverage)
- [ ] Integration tests with supervised actors
- [ ] Documentation updated
- [ ] Metrics visible in Prometheus format
- [ ] No performance degradation (< 1% overhead)

---

## Notes

- Integrate with Pekko supervision mechanism
- Distinguish between planned stops and failures
- Track supervision strategy decisions
