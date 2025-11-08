# Task 4.1: Spring Boot Actuator Health Indicators

**Priority:** HIGH
**Estimated Effort:** 3-4 days
**Dependencies:** Phase 3 complete
**Assignee:** AI Agent

---

## Objective

Integrate actor system health monitoring with Spring Boot Actuator for production deployments.

---

## Components to Implement

### 1. ActorSystemHealthIndicator

**Description:** Health indicator for actor system status

**Implementation:**
```java
@Component
public class ActorSystemHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Check active actors > 0
        // Check dead letter rate < threshold
        // Check average mailbox size < threshold
        // Return Health.up() or Health.down()
    }
}
```

### 2. ClusterHealthIndicator

**Description:** Health indicator for cluster status (if clustering enabled)

**Implementation:**
```java
@Component
@ConditionalOnProperty("spring.actor.cluster.enabled")
public class ClusterHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Check for unreachable members
        // Check cluster convergence
        // Return health status with member details
    }
}
```

### 3. DispatcherHealthIndicator

**Description:** Health indicator for dispatcher/thread pool status

---

## Implementation Requirements

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/actuator/ActorSystemHealthIndicator.java`**
2. **`core/src/main/java/io/github/seonwkim/core/actuator/ClusterHealthIndicator.java`**
3. **`core/src/main/java/io/github/seonwkim/core/actuator/DispatcherHealthIndicator.java`**
4. **`core/src/main/java/io/github/seonwkim/core/actuator/ActorHealthAutoConfiguration.java`**

### Tests
- `ActorSystemHealthIndicatorTest.java`
- `ClusterHealthIndicatorTest.java`
- Integration tests with Spring Boot Actuator

---

## Configuration

```yaml
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health, metrics, info

spring:
  actor:
    health:
      enabled: true
      thresholds:
        dead-letter-rate: 0.01
        max-mailbox-size: 1000
        min-active-actors: 1
```

---

## Health Response Format

```json
{
  "status": "UP",
  "components": {
    "actorSystem": {
      "status": "UP",
      "details": {
        "name": "application",
        "activeActors": 42,
        "processedMessages": 10523,
        "deadLetterRate": 0.001,
        "avgMailboxSize": 15,
        "uptime": "PT2H15M"
      }
    },
    "cluster": {
      "status": "UP",
      "details": {
        "members": 3,
        "leader": "pekko://app@host1:2551",
        "unreachable": []
      }
    }
  }
}
```

---

## Acceptance Criteria

- [ ] ActorSystemHealthIndicator implemented and tested
- [ ] ClusterHealthIndicator implemented and tested
- [ ] Auto-configuration for health indicators
- [ ] Health details include relevant metrics
- [ ] Health thresholds configurable
- [ ] Integration with Spring Boot Actuator
- [ ] Documentation updated
- [ ] Example application demonstrates health checks

---

## Notes

- Health checks should be fast (< 100ms)
- Cache health status if needed
- Consider graceful degradation
- Integrate with existing metrics infrastructure
