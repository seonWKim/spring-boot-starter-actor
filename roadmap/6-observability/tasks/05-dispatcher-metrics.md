# Task 3.1: Dispatcher/Thread Pool Metrics

**Priority:** HIGH
**Estimated Effort:** 4-5 days
**Dependencies:** Phase 2 complete
**Assignee:** AI Agent

---

## Objective

Implement dispatcher and thread pool metrics to monitor execution resources and performance.

---

## Metrics to Implement

### 1. `dispatcher.threads.active` (Gauge)
**Description:** Active thread count
**Use Case:** Monitor thread utilization

**Test Strategy:**
```java
@Test
public void testActiveThreadCount() {
    // Create actors with blocking tasks
    // Submit concurrent messages
    // Verify active thread count increases
}
```

### 2. `dispatcher.threads.total` (Gauge)
**Description:** Total thread count
**Use Case:** Monitor thread pool size

### 3. `dispatcher.queue.size` (Gauge)
**Description:** Task queue size
**Use Case:** Detect backpressure

**Test Strategy:**
```java
@Test
public void testQueueSize() {
    // Submit 100 tasks to limited thread pool
    // Verify queue size gauge > 0 during processing
}
```

### 4. `dispatcher.tasks.completed` (Counter)
**Description:** Completed tasks
**Use Case:** Monitor throughput

### 5. `dispatcher.tasks.submitted` (Counter)
**Description:** Submitted tasks
**Use Case:** Monitor load

### 6. `dispatcher.tasks.rejected` (Counter)
**Description:** Rejected tasks
**Use Case:** Alert on capacity issues

### 7. `dispatcher.parallelism` (Gauge)
**Description:** Parallelism level
**Use Case:** Configuration validation

### 8. `dispatcher.utilization` (Gauge)
**Description:** Thread pool utilization %
**Use Case:** Capacity planning

---

## Implementation Requirements

### Files to Create/Modify

1. **`metrics/src/main/java/io/github/seonwkim/metrics/DispatcherMetrics.java`**
   - Monitor Pekko dispatcher thread pools
   - Track executor service metrics
   - Calculate utilization percentages

2. **Instrumentation**
   - Instrument ExecutorService implementations
   - Access Pekko dispatcher internals

3. **Tests**
   - `DispatcherMetricsTest.java` with comprehensive tests

---

## Configuration

```yaml
spring:
  actor:
    metrics:
      dispatcher:
        enabled: true
        track-all-dispatchers: true
        track-utilization: true
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `dispatcher-name`: Dispatcher identifier
- `dispatcher-type`: Type (default, fork-join, etc.)

---

## Acceptance Criteria

- [ ] All 8 dispatcher metrics implemented
- [ ] Metrics per dispatcher instance
- [ ] Thread pool utilization calculated correctly
- [ ] Metrics properly tagged
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation updated
- [ ] Performance overhead < 1%

---

## Notes

- May need to access Pekko internal APIs
- Consider reflection if APIs not exposed
- Test with different dispatcher configurations
