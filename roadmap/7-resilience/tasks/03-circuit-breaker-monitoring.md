# Task 1.3: Circuit Breaker Monitoring and Metrics

**Priority:** HIGH
**Estimated Effort:** 2-3 days
**Dependencies:** Task 1.1, Task 1.2
**Assignee:** AI Agent

---

## Objective

Expose circuit breaker state, metrics, and health information through Spring Boot Actuator endpoints for monitoring and observability.

---

## Requirements

### 1. Actuator Endpoints

Expose circuit breaker information via Spring Boot Actuator:

**GET /actuator/circuitbreakers**
```json
{
  "circuitBreakers": {
    "order-processing": {
      "state": "CLOSED",
      "failureRate": 25.5,
      "slowCallRate": 10.0,
      "bufferedCalls": 10,
      "failedCalls": 3,
      "successfulCalls": 7,
      "notPermittedCalls": 0
    },
    "payment-gateway": {
      "state": "OPEN",
      "failureRate": 75.0,
      "bufferedCalls": 10,
      "failedCalls": 8,
      "successfulCalls": 2,
      "notPermittedCalls": 5
    }
  }
}
```

**GET /actuator/circuitbreakers/{name}**
```json
{
  "name": "order-processing",
  "state": "CLOSED",
  "metrics": {
    "failureRate": 25.5,
    "slowCallRate": 10.0,
    "bufferedCalls": 10,
    "failedCalls": 3,
    "successfulCalls": 7,
    "notPermittedCalls": 0,
    "slowCalls": 1
  },
  "config": {
    "failureRateThreshold": 50.0,
    "slowCallRateThreshold": 100.0,
    "slidingWindowSize": 10,
    "minimumNumberOfCalls": 5,
    "waitDurationInOpenState": "30s"
  }
}
```

### 2. Health Indicators

Integrate with Spring Boot health endpoint:

**GET /actuator/health**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "order-processing": "CLOSED",
        "payment-gateway": "OPEN"
      }
    }
  }
}
```

Health status logic:
- `UP` - All circuit breakers are CLOSED or HALF_OPEN
- `DOWN` - Any circuit breaker is OPEN
- Configurable behavior

### 3. Metrics

Export metrics via Micrometer:

**Metrics:**
- `circuit.breaker.state` (Gauge) - Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `circuit.breaker.calls` (Counter) - Total calls
  - Tags: `name`, `kind` (successful, failed, not_permitted)
- `circuit.breaker.failure.rate` (Gauge) - Current failure rate
- `circuit.breaker.slow.call.rate` (Gauge) - Current slow call rate
- `circuit.breaker.state.transitions` (Counter) - State changes
  - Tags: `name`, `from_state`, `to_state`

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerEndpoint.java`**
   - Actuator endpoint for circuit breaker information
   - List all circuit breakers
   - Get individual circuit breaker details

2. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerHealthIndicator.java`**
   - Health indicator implementation
   - Aggregate circuit breaker states
   - Configurable health status logic

3. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerMetrics.java`**
   - Metrics registration and updates
   - Integration with Micrometer
   - Event listeners for state changes

4. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerMonitoringAutoConfiguration.java`**
   - Auto-configuration for monitoring features
   - Conditional on Actuator presence

---

## Actuator Endpoint Implementation

```java
@Endpoint(id = "circuitbreakers")
public class ActorCircuitBreakerEndpoint {
    
    private final CircuitBreakerRegistry registry;
    
    @ReadOperation
    public Map<String, Object> circuitBreakers() {
        // Return all circuit breakers with their states
    }
    
    @ReadOperation
    public CircuitBreakerDetails circuitBreaker(@Selector String name) {
        // Return details for specific circuit breaker
    }
}
```

---

## Health Indicator Implementation

```java
@Component
public class ActorCircuitBreakerHealthIndicator implements HealthIndicator {
    
    private final CircuitBreakerRegistry registry;
    private final ActorCircuitBreakerProperties properties;
    
    @Override
    public Health health() {
        Map<String, String> details = new HashMap<>();
        boolean anyOpen = false;
        
        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            String state = cb.getState().name();
            details.put(cb.getName(), state);
            
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                anyOpen = true;
            }
        }
        
        Health.Builder builder = anyOpen ? Health.down() : Health.up();
        return builder.withDetails(details).build();
    }
}
```

---

## Metrics Implementation

```java
@Component
public class ActorCircuitBreakerMetrics {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onCircuitBreakerEvent(CircuitBreakerOnStateTransitionEvent event) {
        // Record state transition
        meterRegistry.counter("circuit.breaker.state.transitions",
            "name", event.getCircuitBreakerName(),
            "from_state", event.getStateTransition().getFromState().name(),
            "to_state", event.getStateTransition().getToState().name()
        ).increment();
    }
    
    public void registerMetrics(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();
        
        // Register state gauge
        Gauge.builder("circuit.breaker.state", circuitBreaker,
            cb -> cb.getState().getOrder())
            .tag("name", name)
            .register(meterRegistry);
            
        // Register failure rate gauge
        Gauge.builder("circuit.breaker.failure.rate", circuitBreaker,
            cb -> cb.getMetrics().getFailureRate())
            .tag("name", name)
            .register(meterRegistry);
    }
}
```

---

## Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,circuitbreakers
  health:
    circuitbreakers:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

spring:
  actor:
    circuit-breaker:
      health:
        enabled: true
        open-state-affects-health: true  # DOWN when any circuit is OPEN
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
public class CircuitBreakerMonitoringTest {
    
    @Test
    public void testActuatorEndpoint() {
        // GET /actuator/circuitbreakers
        // Verify response structure
    }
    
    @Test
    public void testHealthIndicator() {
        // Verify health status changes with circuit breaker state
    }
    
    @Test
    public void testMetrics() {
        // Verify metrics are recorded
        // Verify state transitions are counted
    }
    
    @Test
    public void testEventListeners() {
        // Verify events are published on state changes
    }
}
```

---

## Acceptance Criteria

- [ ] Actuator endpoint `/actuator/circuitbreakers` exposes all circuit breakers
- [ ] Individual circuit breaker details available via `/actuator/circuitbreakers/{name}`
- [ ] Health indicator reflects circuit breaker states
- [ ] Metrics exported via Micrometer
- [ ] State transition events published
- [ ] Configuration to enable/disable monitoring features
- [ ] Tests for all monitoring features
- [ ] Documentation with examples

---

## Documentation

Update:
- Monitoring guide in mkdocs
- Actuator endpoint documentation
- Metrics reference
- Health indicator configuration
