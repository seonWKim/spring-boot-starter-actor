# Task 3.2: Retry Integration with Ask Operations

**Priority:** HIGH
**Estimated Effort:** 2-3 days
**Dependencies:** Task 3.1 (Retry Mechanisms)
**Assignee:** AI Agent

---

## Objective

Integrate retry support with `actor.ask()` API for request-response patterns with automatic retries on failure.

---

## Requirements

### 1. Ask with Retry

Enable retry support in ask operations:

```java
// Basic ask with retry
CompletionStage<Response> result = actorRef.ask(new ProcessRequest(data))
    .withRetry(RetryConfig.builder()
        .maxAttempts(3)
        .backoff(Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(10), 2.0))
        .build())
    .execute();

// With timeout and retry
CompletionStage<Response> result = actorRef.ask(new ProcessRequest(data))
    .withTimeout(Duration.ofSeconds(5))
    .withRetry(RetryConfig.builder()
        .maxAttempts(5)
        .retryOn(TimeoutException.class)
        .build())
    .execute();
```

### 2. Circuit Breaker Integration

Integrate retry with circuit breaker - fail fast when circuit is open:

```java
CompletionStage<Response> result = actorRef.ask(new ProcessRequest(data))
    .withRetry(RetryConfig.builder()
        .maxAttempts(3)
        .failFastWhenCircuitOpen(true)  // Don't retry if circuit breaker is open
        .build())
    .withCircuitBreaker("payment-service")
    .execute();

// Alternative: Configure at circuit breaker level
CircuitBreakerConfig.builder()
    .name("payment-service")
    .failFastEnabled(true)  // Ask operations fail immediately when open
    .build()
```

### 3. Retry Context Propagation

Pass retry context to message handlers:

```java
public interface RetryAwareCommand extends Command {
    default RetryContext getRetryContext() {
        return null;
    }
    
    default void setRetryContext(RetryContext context) {
        // No-op by default
    }
}

// In ask with retry
actorRef.ask(new ProcessPayment(amount))
    .withRetry(RetryConfig.builder()
        .maxAttempts(3)
        .propagateContext(true)  // Set retry context on message
        .build())
    .execute();

// Actor can access retry context
.onMessage(ProcessPayment.class, (ctx, msg) -> {
    RetryContext retryCtx = msg.getRetryContext();
    if (retryCtx != null && retryCtx.getAttempt() > 1) {
        ctx.getLog().warn("Processing retry attempt {}", retryCtx.getAttempt());
    }
    // Handle message
    return Behaviors.same();
})
```

### 4. Fluent API

Create a fluent builder API for ask operations with retry:

```java
actorRef.ask(new ProcessOrder(orderId))
    .withTimeout(Duration.ofSeconds(10))
    .withRetry(RetryConfig.builder()
        .maxAttempts(5)
        .backoff(Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(5), 2.0))
        .jitter(0.2)
        .retryOn(TimeoutException.class)
        .onRetry((attempt, delay, error) -> {
            log.warn("Retry attempt {} after {}ms", attempt, delay.toMillis());
        })
        .build())
    .withCircuitBreaker("order-service")
    .execute()
    .thenApply(response -> {
        log.info("Order processed: {}", response);
        return response;
    })
    .exceptionally(ex -> {
        log.error("Order processing failed", ex);
        return null;
    });
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/retry/AskWithRetry.java`**
   - Fluent API for ask operations with retry
   - Integration with ask pattern

2. **`core/src/main/java/io/github/seonwkim/core/retry/RetryContext.java`**
   - Context object tracking retry state
   - Passed to messages and handlers

3. **`core/src/main/java/io/github/seonwkim/core/retry/RetryableAskExecutor.java`**
   - Executes ask operations with retry logic
   - Handles backoff and scheduling

4. **`core/src/main/java/io/github/seonwkim/core/SpringActorRef.java`** (modify)
   - Add `.ask()` method returning AskWithRetry builder

---

## AskWithRetry Implementation

```java
public class AskWithRetry<T> {
    
    private final ActorRef<?> actorRef;
    private final Object message;
    private final Class<T> responseClass;
    private Duration timeout = Duration.ofSeconds(3);
    private RetryConfig retryConfig = null;
    private String circuitBreakerName = null;
    
    public AskWithRetry<T> withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public AskWithRetry<T> withRetry(RetryConfig config) {
        this.retryConfig = config;
        return this;
    }
    
    public AskWithRetry<T> withCircuitBreaker(String name) {
        this.circuitBreakerName = name;
        return this;
    }
    
    public CompletionStage<T> execute() {
        if (retryConfig == null) {
            // No retry, execute directly
            return executeAsk(1);
        }
        
        // Execute with retry
        return executeWithRetry(1);
    }
    
    private CompletionStage<T> executeWithRetry(int attempt) {
        // Check circuit breaker first
        if (circuitBreakerName != null && retryConfig.isFailFastWhenCircuitOpen()) {
            CircuitBreaker cb = circuitBreakerRegistry.get(circuitBreakerName);
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                return CompletableFuture.failedFuture(
                    new CallNotPermittedException("Circuit breaker is open: " + circuitBreakerName));
            }
        }
        
        // Set retry context on message if supported
        if (retryConfig.isPropagateContext() && message instanceof RetryAwareCommand) {
            ((RetryAwareCommand) message).setRetryContext(new RetryContext(attempt));
        }
        
        return executeAsk(attempt)
            .exceptionallyCompose(ex -> {
                // Check if should retry
                if (shouldRetry(ex, attempt)) {
                    Duration delay = calculateDelay(attempt);
                    
                    // Invoke retry callback
                    if (retryConfig.getOnRetry() != null) {
                        retryConfig.getOnRetry().onRetry(attempt, delay, ex);
                    }
                    
                    // Schedule next attempt
                    return CompletableFuture
                        .delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(() -> executeWithRetry(attempt + 1));
                } else {
                    // No more retries
                    if (retryConfig.getOnRetryExhausted() != null) {
                        retryConfig.getOnRetryExhausted().onRetryExhausted(message, ex);
                    }
                    return CompletableFuture.failedFuture(ex);
                }
            });
    }
    
    private CompletionStage<T> executeAsk(int attempt) {
        // Wrap with circuit breaker if configured
        if (circuitBreakerName != null) {
            CircuitBreaker cb = circuitBreakerRegistry.get(circuitBreakerName);
            return cb.executeCompletionStage(() -> doAsk());
        } else {
            return doAsk();
        }
    }
    
    private CompletionStage<T> doAsk() {
        return AskPattern.ask(
            actorRef,
            replyTo -> createMessage(message, replyTo),
            timeout,
            scheduler
        );
    }
    
    private boolean shouldRetry(Throwable ex, int attempt) {
        if (attempt >= retryConfig.getMaxAttempts()) {
            return false;
        }
        
        // Check circuit breaker state
        if (circuitBreakerName != null && retryConfig.isFailFastWhenCircuitOpen()) {
            CircuitBreaker cb = circuitBreakerRegistry.get(circuitBreakerName);
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                return false;  // Don't retry when circuit is open
            }
        }
        
        return retryConfig.shouldRetry(ex);
    }
    
    private Duration calculateDelay(int attempt) {
        Duration baseDelay = retryConfig.getBackoff().calculateDelay(attempt);
        
        if (retryConfig.getJitter() > 0) {
            return JitterCalculator.applyJitter(baseDelay, retryConfig.getJitter());
        }
        
        return baseDelay;
    }
}
```

---

## SpringActorRef Integration

```java
public class SpringActorRef<T> {
    
    private final ActorRef<T> actorRef;
    
    // Existing ask method
    public <R> CompletionStage<R> ask(
            Function<ActorRef<R>, T> messageFactory,
            Duration timeout) {
        return AskPattern.ask(actorRef, messageFactory, timeout, scheduler);
    }
    
    // New ask method with retry support
    public <R> AskWithRetry<R> ask(Object message, Class<R> responseClass) {
        return new AskWithRetry<>(actorRef, message, responseClass, context);
    }
    
    // Convenience method for messages implementing AskCommand
    public <R> AskWithRetry<R> ask(AskCommand<R> message) {
        return new AskWithRetry<>(actorRef, message, message.responseClass(), context);
    }
}
```

---

## Example Usage

### Simple Ask with Retry
```java
@Service
public class OrderService {
    
    private final SpringActorRef<OrderActor.Command> orderActor;
    
    public CompletionStage<OrderResult> processOrder(String orderId) {
        return orderActor.ask(new ProcessOrder(orderId), OrderResult.class)
            .withTimeout(Duration.ofSeconds(10))
            .withRetry(RetryConfig.builder()
                .maxAttempts(3)
                .backoff(Backoff.exponential(
                    Duration.ofMillis(200),
                    Duration.ofSeconds(5),
                    2.0
                ))
                .retryOn(TimeoutException.class)
                .build())
            .execute();
    }
}
```

### With Circuit Breaker
```java
public CompletionStage<PaymentResult> processPayment(Payment payment) {
    return paymentActor.ask(new ProcessPayment(payment), PaymentResult.class)
        .withTimeout(Duration.ofSeconds(15))
        .withRetry(RetryConfig.builder()
            .maxAttempts(5)
            .backoff(Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(30), 2.0))
            .jitter(0.3)
            .retryOn(TimeoutException.class, GatewayException.class)
            .failFastWhenCircuitOpen(true)
            .build())
        .withCircuitBreaker("payment-gateway")
        .execute()
        .thenApply(result -> {
            log.info("Payment processed: {}", result);
            return result;
        });
}
```

### With Callbacks
```java
public CompletionStage<ApiResponse> callExternalApi(ApiRequest request) {
    return apiActor.ask(new CallApi(request), ApiResponse.class)
        .withTimeout(Duration.ofSeconds(20))
        .withRetry(RetryConfig.builder()
            .maxAttempts(5)
            .backoff(Backoff.exponential(Duration.ofMillis(500), Duration.ofMinutes(1), 2.0))
            .jitter(0.2)
            .retryOn(TimeoutException.class, IOException.class)
            .onRetry((attempt, delay, error) -> {
                log.warn("API call failed, retry attempt {} after {}ms: {}", 
                    attempt, delay.toMillis(), error.getMessage());
                metrics.recordRetry("external-api", attempt);
            })
            .onRetryExhausted((msg, error) -> {
                log.error("API call exhausted all retries: {}", msg, error);
                alerts.sendAlert("External API unavailable", error);
            })
            .build())
        .execute();
}
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class AskWithRetryTest {
    
    @Test
    public void testSuccessfulAskWithoutRetry() {
        // Ask succeeds on first attempt
        // Verify no retries
    }
    
    @Test
    public void testAskWithRetry() {
        // Fail first 2 attempts, succeed on 3rd
        // Verify retries and success
    }
    
    @Test
    public void testAskWithTimeout() {
        // Test timeout triggers retry
    }
    
    @Test
    public void testCircuitBreakerIntegration() {
        // Test circuit breaker prevents retry when open
    }
    
    @Test
    public void testRetryContextPropagation() {
        // Verify retry context passed to actor
    }
}
```

---

## Acceptance Criteria

- [ ] `ask()` method returns AskWithRetry builder
- [ ] Fluent API for configuring timeout, retry, circuit breaker
- [ ] Retry logic integrated with ask pattern
- [ ] Circuit breaker integration - fail fast when open
- [ ] Retry context propagation to message handlers
- [ ] Callbacks for retry events
- [ ] Comprehensive tests
- [ ] Documentation with examples
- [ ] Backward compatible with existing ask API

---

## Documentation

Update:
- Ask pattern documentation
- Retry integration guide
- Circuit breaker integration
- Example use cases
- Best practices
