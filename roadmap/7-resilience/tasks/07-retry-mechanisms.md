# Task 3.1: Retry Mechanisms with Exponential Backoff

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Implement retry mechanisms with exponential backoff and jitter to handle transient failures gracefully in actor message processing.

---

## Requirements

### 1. Retry Configuration

```java
@Component
public class ResilientActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withRetry(RetryConfig.builder()
                .maxAttempts(3)
                .backoff(Backoff.exponential(
                    Duration.ofMillis(100),   // initial delay
                    Duration.ofSeconds(10),   // max delay
                    2.0                       // multiplier
                ))
                .jitter(0.2)  // 20% randomization
                .retryOn(TimeoutException.class, TransientException.class)
                .retryIf(ex -> ex instanceof ServiceException && 
                              ((ServiceException) ex).isRetryable())
                .onRetryExhausted((ctx, msg, error) -> {
                    ctx.getLog().error("Retry exhausted for message: {}", msg, error);
                })
                .build())
            .onMessage(ProcessRequest.class, this::handleRequest)
            .build();
    }
}
```

### 2. Backoff Strategies

Support multiple backoff strategies:

```java
// Exponential backoff
Backoff.exponential(
    Duration.ofMillis(100),  // initial
    Duration.ofSeconds(10),  // max
    2.0                      // multiplier
)

// Fixed delay
Backoff.fixed(Duration.ofSeconds(1))

// Linear backoff
Backoff.linear(
    Duration.ofMillis(100),  // initial
    Duration.ofMillis(500),  // increment
    Duration.ofSeconds(10)   // max
)

// Custom backoff
Backoff.custom((attempt) -> {
    return Duration.ofMillis(100 * attempt);
})
```

### 3. Jitter

Add randomization to prevent thundering herd:

```java
public class JitterCalculator {
    
    public Duration applyJitter(Duration delay, double jitterFactor) {
        if (jitterFactor <= 0) {
            return delay;
        }
        
        long delayMs = delay.toMillis();
        long jitterMs = (long) (delayMs * jitterFactor);
        long randomJitter = ThreadLocalRandom.current().nextLong(-jitterMs, jitterMs + 1);
        
        return Duration.ofMillis(Math.max(0, delayMs + randomJitter));
    }
}
```

### 4. Retry Conditions

Configure which exceptions/conditions should trigger retry:

```java
RetryConfig.builder()
    // Retry on specific exception types
    .retryOn(TimeoutException.class, IOException.class)
    
    // Don't retry on specific exceptions
    .doNotRetryOn(IllegalArgumentException.class, ValidationException.class)
    
    // Custom retry predicate
    .retryIf(ex -> {
        if (ex instanceof HttpException) {
            int status = ((HttpException) ex).getStatusCode();
            return status >= 500 || status == 429;  // Retry 5xx and rate limit
        }
        return false;
    })
    
    // Retry based on result
    .retryOnResult(result -> {
        if (result instanceof ApiResponse) {
            return !((ApiResponse) result).isSuccess();
        }
        return false;
    })
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/retry/RetryConfig.java`**
   - Configuration for retry behavior
   - Builder pattern

2. **`core/src/main/java/io/github/seonwkim/core/retry/Backoff.java`**
   - Backoff strategy interface and implementations
   - Exponential, fixed, linear, custom

3. **`core/src/main/java/io/github/seonwkim/core/retry/RetryBehavior.java`**
   - Behavior wrapper that implements retry logic
   - Schedules retry attempts

4. **`core/src/main/java/io/github/seonwkim/core/retry/RetryContext.java`**
   - Tracks retry attempts and state
   - Passed to handlers and callbacks

5. **`core/src/main/java/io/github/seonwkim/core/retry/RetryMetrics.java`**
   - Metrics for retry operations
   - Integration with Micrometer

6. **`core/src/main/java/io/github/seonwkim/core/SpringActorBehavior.java`** (modify)
   - Add `.withRetry()` method to builder

---

## RetryConfig Structure

```java
public class RetryConfig {
    private final int maxAttempts;
    private final Backoff backoff;
    private final double jitter;
    private final Set<Class<? extends Throwable>> retryExceptions;
    private final Set<Class<? extends Throwable>> doNotRetryExceptions;
    private final Predicate<Throwable> retryPredicate;
    private final Predicate<Object> retryOnResult;
    private final RetryCallback onRetry;
    private final RetryExhaustedCallback onRetryExhausted;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int maxAttempts = 3;
        private Backoff backoff = Backoff.exponential(
            Duration.ofMillis(100),
            Duration.ofSeconds(10),
            2.0
        );
        private double jitter = 0.1;
        
        public Builder maxAttempts(int maxAttempts) { ... }
        public Builder backoff(Backoff backoff) { ... }
        public Builder jitter(double jitter) { ... }
        public Builder retryOn(Class<? extends Throwable>... exceptions) { ... }
        public Builder doNotRetryOn(Class<? extends Throwable>... exceptions) { ... }
        public Builder retryIf(Predicate<Throwable> predicate) { ... }
        public Builder retryOnResult(Predicate<Object> predicate) { ... }
        public Builder onRetry(RetryCallback callback) { ... }
        public Builder onRetryExhausted(RetryExhaustedCallback callback) { ... }
        
        public RetryConfig build() { ... }
    }
}
```

---

## Backoff Implementation

```java
public interface Backoff {
    Duration calculateDelay(int attempt);
    
    static Backoff exponential(Duration initial, Duration max, double multiplier) {
        return new ExponentialBackoff(initial, max, multiplier);
    }
    
    static Backoff fixed(Duration delay) {
        return new FixedBackoff(delay);
    }
    
    static Backoff linear(Duration initial, Duration increment, Duration max) {
        return new LinearBackoff(initial, increment, max);
    }
}

class ExponentialBackoff implements Backoff {
    private final long initialMs;
    private final long maxMs;
    private final double multiplier;
    
    @Override
    public Duration calculateDelay(int attempt) {
        long delayMs = (long) (initialMs * Math.pow(multiplier, attempt - 1));
        return Duration.ofMillis(Math.min(delayMs, maxMs));
    }
}
```

---

## RetryBehavior Implementation

```java
public class RetryBehavior<C> {
    
    private final RetryConfig config;
    private final BiFunction<ActorContext<C>, C, Behavior<C>> handler;
    private final RetryMetrics metrics;
    
    public Behavior<C> onMessage(ActorContext<C> ctx, C msg) {
        return attemptWithRetry(ctx, msg, 1);
    }
    
    private Behavior<C> attemptWithRetry(ActorContext<C> ctx, C msg, int attempt) {
        try {
            // Execute handler
            Behavior<C> result = handler.apply(ctx, msg);
            
            // Check if result indicates retry needed
            if (config.shouldRetryOnResult(result)) {
                return scheduleRetry(ctx, msg, attempt, null);
            }
            
            // Success
            metrics.recordSuccess(attempt);
            return result;
            
        } catch (Throwable ex) {
            // Check if this exception should be retried
            if (shouldRetry(ex, attempt)) {
                return scheduleRetry(ctx, msg, attempt, ex);
            }
            
            // Don't retry, propagate exception
            metrics.recordFailure(attempt);
            throw ex;
        }
    }
    
    private boolean shouldRetry(Throwable ex, int attempt) {
        if (attempt >= config.getMaxAttempts()) {
            return false;
        }
        
        // Check do-not-retry exceptions
        if (config.isDoNotRetryException(ex)) {
            return false;
        }
        
        // Check retry exceptions
        if (config.isRetryException(ex)) {
            return true;
        }
        
        // Check custom predicate
        return config.getRetryPredicate() != null && 
               config.getRetryPredicate().test(ex);
    }
    
    private Behavior<C> scheduleRetry(ActorContext<C> ctx, C msg, int attempt, Throwable error) {
        Duration delay = calculateDelay(attempt);
        
        // Invoke retry callback
        if (config.getOnRetry() != null) {
            config.getOnRetry().onRetry(ctx, msg, attempt, delay, error);
        }
        
        metrics.recordRetry(attempt, delay);
        
        if (attempt >= config.getMaxAttempts()) {
            // Retry exhausted
            if (config.getOnRetryExhausted() != null) {
                config.getOnRetryExhausted().onRetryExhausted(ctx, msg, error);
            }
            metrics.recordExhausted();
            throw new RetryExhaustedException("Max retry attempts reached", error);
        }
        
        // Schedule next attempt
        ctx.scheduleOnce(delay, ctx.getSelf(), msg);
        
        return Behaviors.same();
    }
    
    private Duration calculateDelay(int attempt) {
        Duration baseDelay = config.getBackoff().calculateDelay(attempt);
        
        if (config.getJitter() > 0) {
            return JitterCalculator.applyJitter(baseDelay, config.getJitter());
        }
        
        return baseDelay;
    }
}
```

---

## Example Usage

### Basic Retry
```java
@Component
public class ApiActor implements SpringActor<Command> {
    
    private final ExternalApiClient apiClient;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withRetry(RetryConfig.builder()
                .maxAttempts(5)
                .backoff(Backoff.exponential(
                    Duration.ofMillis(200),
                    Duration.ofSeconds(30),
                    2.0
                ))
                .jitter(0.3)
                .retryOn(TimeoutException.class, IOException.class)
                .build())
            .onMessage(CallApi.class, (context, msg) -> {
                ApiResponse response = apiClient.call(msg.request());
                msg.reply(response);
                return Behaviors.same();
            })
            .build();
    }
}
```

### With Callbacks
```java
.withRetry(RetryConfig.builder()
    .maxAttempts(3)
    .onRetry((ctx, msg, attempt, delay, error) -> {
        ctx.getLog().warn("Retry attempt {} after {}ms: {}", 
            attempt, delay.toMillis(), error.getMessage());
    })
    .onRetryExhausted((ctx, msg, error) -> {
        ctx.getLog().error("All retry attempts failed for message: {}", msg, error);
        // Send to dead letter or alternative handling
    })
    .build())
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class RetryTest {
    
    @Test
    public void testSuccessfulRetry() {
        // Fail first 2 attempts, succeed on 3rd
        // Verify message processed successfully
    }
    
    @Test
    public void testExponentialBackoff() {
        // Verify delays follow exponential pattern
    }
    
    @Test
    public void testJitter() {
        // Verify jitter is applied correctly
    }
    
    @Test
    public void testRetryExhausted() {
        // Exceed max attempts
        // Verify onRetryExhausted callback invoked
    }
    
    @Test
    public void testRetryConditions() {
        // Test retryOn, doNotRetryOn, retryIf
    }
}
```

---

## Acceptance Criteria

- [ ] Retry configuration with builder API
- [ ] Multiple backoff strategies (exponential, fixed, linear, custom)
- [ ] Jitter support to prevent thundering herd
- [ ] Configurable retry conditions
- [ ] Callbacks for retry events
- [ ] `.withRetry()` API in behavior builder
- [ ] Retry metrics exposed
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation with examples
- [ ] No breaking changes

---

## Metrics

Expose retry metrics:
- `actor.retry.attempts` (Counter) - Total retry attempts
  - Tags: `actor`, `attempt_number`
- `actor.retry.success` (Counter) - Successful after retries
  - Tags: `actor`, `attempts_used`
- `actor.retry.exhausted` (Counter) - Max retries exceeded
  - Tags: `actor`
- `actor.retry.delay` (Timer) - Retry delay durations

---

## Documentation

Update:
- README.md with retry examples
- Retry patterns guide
- Backoff strategies documentation
- JavaDoc on all public APIs
