# Task 1.1: Actor Mailbox Metrics

**Priority:** CRITICAL
**Estimated Effort:** 2-3 days
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Implement mailbox-related metrics to monitor actor message queue behavior and identify bottlenecks.

---

## Metrics to Implement

### 1. `actor.time-in-mailbox` (Timer)
**Description:** Time from message enqueue to dequeue
**Use Case:** Identify actors with message processing delays

**Test Strategy:**
```java
@Test
public void testTimeInMailboxMetric() {
    // Create actor with slow message handler
    // Send multiple messages
    // Verify time-in-mailbox metric increases for queued messages
}
```

### 2. `actor.mailbox-size` (Gauge)
**Description:** Current number of messages in mailbox
**Use Case:** Monitor message queue depth

**Test Strategy:**
```java
@Test
public void testMailboxSizeGauge() {
    // Create actor with blocking handler
    // Send 10 messages
    // Verify mailbox size gauge shows 9 pending messages
}
```

### 3. `mailbox.enqueue-time` (Timer)
**Description:** Time to enqueue message
**Use Case:** Detect mailbox contention issues

### 4. `mailbox.dequeue-time` (Timer)
**Description:** Time to dequeue message
**Use Case:** Monitor mailbox performance

### 5. `mailbox.size.max` (Gauge)
**Description:** Maximum mailbox size reached
**Use Case:** Capacity planning

### 6. `mailbox.overflow` (Counter)
**Description:** Count of mailbox overflow events
**Use Case:** Alert when mailbox capacity exceeded

---

## Implementation Requirements

### Files to Create/Modify

1. **`metrics/src/main/java/io/github/seonwkim/metrics/MailboxMetrics.java`**
   ```java
   @Component
   public class MailboxMetrics {
       @Metric(name = "actor.mailbox.time-in-mailbox")
       private Timer timeInMailbox;

       @Metric(name = "actor.mailbox.size")
       private Gauge mailboxSize;

       @Metric(name = "mailbox.enqueue-time")
       private Timer enqueueTime;

       @Metric(name = "mailbox.dequeue-time")
       private Timer dequeueTime;

       @Metric(name = "mailbox.size.max")
       private Gauge maxMailboxSize;

       @Metric(name = "mailbox.overflow")
       private Counter mailboxOverflow;
   }
   ```

2. **Mailbox Interceptor**
   - Intercept message enqueue/dequeue operations
   - Record timestamps and update metrics
   - Handle bounded mailbox overflow events

3. **Tests**
   - `MailboxMetricsTest.java` with all test strategies

---

## Configuration

```yaml
spring:
  actor:
    metrics:
      mailbox:
        enabled: true
        track-time-in-mailbox: true
        track-max-size: true
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `actor-path`: Full actor path
- `actor-class`: Actor class name
- `mailbox-type`: Type of mailbox (unbounded, bounded, etc.)

---

## Acceptance Criteria

- [ ] All 6 mailbox metrics implemented
- [ ] Metrics properly tagged with actor context
- [ ] Comprehensive unit tests (>80% coverage)
- [ ] Integration tests with real actors
- [ ] Documentation updated
- [ ] Metrics visible in Prometheus format at `/actuator/prometheus`
- [ ] No performance degradation (< 1% overhead)

---

## Testing Checklist

- [ ] Message enqueue updates time-in-mailbox
- [ ] Mailbox size gauge reflects current queue depth
- [ ] Max mailbox size tracked correctly
- [ ] Overflow counter increments on bounded mailbox overflow
- [ ] Metrics properly scoped per actor instance
- [ ] No memory leaks from metric collection
- [ ] Metrics reset on actor restart

---

## Notes

- Consider using Micrometer's `@Timed` and `@Counted` annotations
- Mailbox instrumentation should be low-overhead
- Support both bounded and unbounded mailboxes
- Handle actor lifecycle (metrics cleanup on stop)
