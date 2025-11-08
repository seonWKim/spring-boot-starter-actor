# 7. Performance and Resilience

Essential resilience patterns and performance features for production systems, leveraging existing infrastructure where available.

---

## 7.1 Circuit Breaker Pattern

**Priority:** HIGH  
**Decision:** Library-native vs Pekko's provided lib - recommend library-native for better Spring Boot integration

### Recommendation: Library-Native Implementation

**Why library-native:**
- ✅ Better Spring Boot integration (YAML config, DI, actuator endpoints)
- ✅ Consistent with existing library patterns
- ✅ Built-in metrics and health checks
- ✅ Easier to customize for Spring Boot users
- ✅ Can leverage Spring's existing circuit breaker libraries (Resilience4j)

### Implementation

```java
@Component
public class ResilientOrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withCircuitBreaker(CircuitBreakerConfig.builder()
                .name("order-processing")
                .maxFailures(5)
                .callTimeout(Duration.ofSeconds(10))
                .resetTimeout(Duration.ofSeconds(30))
                .halfOpenRequests(3)
                .failureRateThreshold(0.5)
                .build())
            .onMessage(ProcessOrder.class, this::handleProcess)
            .build();
    }
}
```

### Spring Boot Configuration

```yaml
spring:
  actor:
    circuit-breaker:
      defaults:
        max-failures: 5
        call-timeout: 10s
        reset-timeout: 30s
        failure-rate-threshold: 0.5
      instances:
        order-processing:
          max-failures: 10
          call-timeout: 15s
        payment-processing:
          max-failures: 3
          reset-timeout: 60s
```

### Integration with Resilience4j

```java
// Leverage existing Spring Circuit Breaker
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public OrderActor(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("order-service");
        
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(ProcessOrder.class, (context, msg) -> {
                Try<Result> result = Try.ofSupplier(
                    CircuitBreaker.decorateSupplier(cb, () -> processOrder(msg))
                );
                
                if (result.isSuccess()) {
                    msg.reply(result.get());
                } else {
                    msg.replyError(result.getCause());
                }
                
                return Behaviors.same();
            })
            .build();
    }
}
```

---

## 7.2 Bulkhead Pattern (Dispatcher Configuration)

**Priority:** MEDIUM  
**Status:** ✅ Dispatcher configuration already supported - document and enhance

### Note

The library already has dispatcher configuration support! Focus on:
1. Better documentation of existing features
2. Examples for common bulkhead patterns
3. Spring Boot YAML configuration examples

### Usage Examples

```yaml
# Existing dispatcher configuration
spring:
  actor:
    dispatchers:
      # Isolate external service calls
      external-service-dispatcher:
        type: fixed-pool
        core-pool-size: 10
        max-pool-size: 10
        queue-size: 100
        rejection-policy: caller-runs
        
      # High-throughput processor
      bulk-processor-dispatcher:
        type: fork-join
        parallelism: 8
        throughput: 100
        
      # Blocking IO operations
      blocking-io-dispatcher:
        type: fixed-pool
        core-pool-size: 20
        max-pool-size: 50
```

### Enhanced Documentation Example

```java
@Component
public class ExternalServiceActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            // Use dedicated dispatcher for isolation
            .withDispatcher("external-service-dispatcher")
            .onMessage(CallExternalAPI.class, this::handleExternalCall)
            .build();
    }
}
```

---

## 7.3 Message Deduplication

**Priority:** HIGH (Absolutely need this!)  
**Complexity:** Medium

### Overview

Critical feature for idempotent message processing in distributed systems.

### Implementation

```java
@Component
public class DeduplicatingActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDeduplication(DeduplicationConfig.builder()
                .enabled(true)
                .cacheSize(10000)
                .ttl(Duration.ofMinutes(5))
                .idExtractor(msg -> ((IdentifiableMessage) msg).messageId())
                .onDuplicate(DuplicateAction.IGNORE)  // or LOG, REJECT
                .build())
            .onMessage(ProcessMessage.class, this::handleMessage)
            .build();
    }
}
```

### Spring Boot Configuration

```yaml
spring:
  actor:
    deduplication:
      enabled: true
      default-cache-size: 10000
      default-ttl: 5m
      backend: caffeine  # or redis for distributed
      redis:
        enabled: false
        host: localhost
        port: 6379
```

### Distributed Deduplication with Redis

```java
@Configuration
public class DeduplicationConfiguration {
    
    @Bean
    public DistributedDeduplicationCache redisDeduplicationCache(
            ReactiveRedisTemplate<String, String> redisTemplate) {
        
        return new RedisDeduplicationCache(redisTemplate);
    }
}
```

---

## 7.4 Retry Mechanisms with Backoff

**Priority:** HIGH  
**Complexity:** Low

### Implementation

```java
// Built into actor behavior
@Component
public class ResilientActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withRetry(RetryConfig.builder()
                .maxAttempts(3)
                .backoff(Backoff.exponential(
                    Duration.ofMillis(100),
                    Duration.ofSeconds(10),
                    2.0  // multiplier
                ))
                .jitter(0.2)  // 20% randomization
                .retryOn(TimeoutException.class, TransientFailure.class)
                .onRetryExhausted(error -> log.error("Retry exhausted", error))
                .build())
            .onMessage(ProcessWithRetry.class, this::handleProcess)
            .build();
    }
}

// Also available in ask operations
actor.ask(new ProcessRequest(data))
    .withRetry(RetryConfig.builder()
        .maxAttempts(3)
        .backoff(Backoff.exponential(100ms, 10s))
        .build())
    .execute();
```

---

## 7.5 Adaptive Concurrency Control

**Priority:** ~~LOW~~ **DEFER (may not be needed)**
**Testing:** ~~Need thorough testing for this~~ **Would require extensive testing if implemented**
**Recommendation:** ⚠️ **DEFER UNTIL PROVEN NEED**

### Overview

~~Dynamic concurrency adjustment based on system metrics.~~

**WHY DEFER THIS FEATURE:**
1. **Very High Complexity**: Adaptive algorithms are notoriously difficult to get right
2. **Risk of Instability**: Can cause thrashing if not carefully tuned
3. **Extensive Testing Required**: Needs testing under varied load patterns
4. **Unclear Value**: Most users benefit more from fixed, well-tuned concurrency
5. **Alternative**: Users can manually adjust based on metrics (simpler, safer)

**Recommendation:** Defer until users explicitly request this and provide use cases where manual tuning isn't sufficient.

### Implementation

```java
@Component
public class AdaptiveConcurrencyActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withAdaptiveConcurrency(AdaptiveConcurrencyConfig.builder()
                .minConcurrency(1)
                .maxConcurrency(100)
                .targetLatency(Duration.ofMillis(100))
                .smoothingFactor(0.3)
                .samplingWindow(Duration.ofSeconds(10))
                .adjustmentInterval(Duration.ofSeconds(30))
                .build())
            .onMessage(ProcessData.class, this::handleData)
            .build();
    }
}
```

### Testing Requirements

```java
@Test
public void testAdaptiveConcurrencyUnderLoad() {
    // Test concurrency increases with load
    // Test concurrency decreases when idle
    // Test concurrency stays within bounds
    // Test latency targets are met
    // Test stability (no thrashing)
}

@Test
public void testAdaptiveConcurrencyWithVaryingLatency() {
    // Test response to slow operations
    // Test response to fast operations
    // Test smoothing factor effectiveness
}
```

---

## 7.6 Backpressure Handling

**Priority:** HIGH  
**Complexity:** Medium

### Implementation

```java
@Component
public class BackpressureActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withMailbox(MailboxConfig.builder()
                .capacity(1000)
                .onOverflow(OverflowStrategy.BACKPRESSURE)
                .backpressureSignal(BackpressureSignal.SLOW_DOWN)
                .build())
            .onMessage(ProcessData.class, this::handleData)
            .build();
    }
}
```

---

## Summary

**Key Priorities:**

1. **Circuit Breaker**: Library-native implementation with Resilience4j integration
2. **Message Deduplication**: Absolutely need this - critical for idempotence
3. **Bulkhead Pattern**: Document existing dispatcher configuration
4. **Retry Mechanisms**: Built-in with exponential backoff
5. **Adaptive Concurrency**: Needs thorough testing before production use
6. **Backpressure**: Essential for high-throughput systems

All features designed for **production resilience** with **Spring Boot** patterns.
