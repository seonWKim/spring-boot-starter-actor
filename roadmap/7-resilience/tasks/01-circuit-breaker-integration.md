# Task 1.1: Circuit Breaker Integration with Resilience4j

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Integrate Resilience4j circuit breaker with the actor system to protect against cascading failures when actors call external services or other actors.

---

## Design Decision

**Approach:** Library-native implementation using Resilience4j
**Rationale:**
- Resilience4j is the standard Spring Boot circuit breaker library
- Better integration with Spring ecosystem
- Proven, battle-tested implementation
- Built-in metrics and monitoring
- Easier to customize for Spring Boot users

---

## Requirements

### 1. Core Integration

Actors should support circuit breakers via:

**Option A: Annotation-based (Spring-friendly)**
```java
@Component
public class OrderActor implements SpringActor<Command> {

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withCircuitBreaker("order-processing")  // By name
            .onMessage(ProcessOrder.class, this::handleProcess)
            .build();
    }
}
```

**Option B: Configuration object**
```java
.withCircuitBreaker(CircuitBreakerConfig.builder()
    .name("order-processing")
    .maxFailures(5)
    .callTimeout(Duration.ofSeconds(10))
    .resetTimeout(Duration.ofSeconds(30))
    .failureRateThreshold(0.5)
    .build())
```

### 2. Spring Boot Configuration

```yaml
spring:
  actor:
    circuit-breaker:
      enabled: true
      # Use Resilience4j configuration

resilience4j:
  circuitbreaker:
    instances:
      order-processing:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 10s
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

### 3. Actor Integration Points

Circuit breakers should wrap:
1. **Message processing** (entire message handler)
2. **Ask operations** (request-response calls)
3. **External service calls** (database, HTTP, etc.)

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerConfig.java`**
   - Circuit breaker configuration class
   - Builder pattern support

2. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/CircuitBreakerBehavior.java`**
   - Behavior wrapper that applies circuit breaker
   - Intercepts message processing
   - Records success/failure

3. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/CircuitBreakerRegistry.java`**
   - Manages circuit breaker instances per actor
   - Integration with Resilience4j registry

4. **`core/src/main/java/io/github/seonwkim/core/SpringActorBehavior.java`** (modify)
   - Add `.withCircuitBreaker()` method

### Files to Modify

1. **`core/build.gradle.kts`**
   - Add Resilience4j dependencies:
   ```kotlin
   implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
   implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
   implementation("io.github.resilience4j:resilience4j-micrometer:2.1.0")
   ```

---

## Example Usage

### Basic Circuit Breaker
```java
@Component
public class PaymentActor implements SpringActor<Command> {

    private final PaymentGateway paymentGateway;

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withCircuitBreaker("payment-gateway")
            .onMessage(ProcessPayment.class, (context, msg) -> {
                // This call is protected by circuit breaker
                PaymentResult result = paymentGateway.charge(msg.amount());
                msg.reply(result);
                return Behaviors.same();
            })
            .build();
    }
}
```

### With Fallback
```java
.withCircuitBreaker(CircuitBreakerConfig.builder()
    .name("payment-gateway")
    .fallback((throwable, msg) -> {
        log.warn("Circuit open, using fallback", throwable);
        return new PaymentFailed("Service unavailable");
    })
    .build())
```

### Circuit Breaker Events
```java
@Component
public class CircuitBreakerEventHandler {

    @EventListener
    public void onCircuitBreakerStateChange(CircuitBreakerOnStateTransitionEvent event) {
        log.info("Circuit breaker {} changed from {} to {}",
            event.getCircuitBreakerName(),
            event.getStateTransition().getFromState(),
            event.getStateTransition().getToState());
    }
}
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class CircuitBreakerIntegrationTest {

    @Test
    public void testCircuitOpensAfterFailures() {
        // Configure circuit breaker with max 3 failures
        // Send 3 failing messages
        // Verify circuit opens
        // Send another message
        // Verify it fails fast (CallNotPermittedException)
    }

    @Test
    public void testCircuitClosesAfterRecovery() {
        // Open circuit
        // Wait for reset timeout
        // Send successful message
        // Verify circuit closes
    }

    @Test
    public void testFallbackInvoked() {
        // Configure circuit with fallback
        // Open circuit
        // Send message
        // Verify fallback result returned
    }
}
```

---

## Acceptance Criteria

- [ ] Resilience4j integrated with actor system
- [ ] Circuit breakers configurable per actor via YAML
- [ ] `.withCircuitBreaker()` API available in behavior builder
- [ ] Circuit breaker states tracked (CLOSED, OPEN, HALF_OPEN)
- [ ] Fallback support implemented
- [ ] Events published for state transitions
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation with examples
- [ ] No breaking changes to existing API

---

## Metrics Integration

Circuit breakers should expose metrics:
- `circuit-breaker.state` (Gauge) - Current state
- `circuit-breaker.calls.total` (Counter) - Total calls
- `circuit-breaker.calls.successful` (Counter)
- `circuit-breaker.calls.failed` (Counter)
- `circuit-breaker.calls.not-permitted` (Counter) - Rejected by open circuit

---

## Documentation

Update:
- README.md with circuit breaker example
- mkdocs/docs/ with comprehensive guide
- JavaDoc on all public APIs
