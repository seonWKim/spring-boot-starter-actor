# 2. Streams and Backpressure

~~Provide Spring Boot users with fluent builder patterns for stream processing that integrate seamlessly with actors, featuring automatic backpressure and library-native throttling.~~

> **🚨 CRITICAL RECOMMENDATION (2025-01-08):**
> **RECONSIDER THIS ENTIRE FEATURE**
> **Current Approach:** Reimplementing Pekko Streams with custom API
> **Problem:** Massive development effort (6-8 weeks), ongoing maintenance burden, duplicates battle-tested functionality
> **Alternative:** Examples showing how to use Pekko Streams with actors, optional thin wrapper if needed
> **Priority:** HIGH → **LOW** (reconsider need)

---

## 2.1 ~~Fluent Builder Pattern for Actor Streams~~ → Use Pekko Streams + Examples

**Priority:** ~~HIGH~~ **LOW (reconsider entirely)**
**Complexity:** ~~High~~ **HIGH (if implemented)**
**Spring Boot Compatibility:** ~~Excellent~~ **Better as examples**
**Recommendation:** ⚠️ **START WITH EXAMPLES, NOT LIBRARY FEATURES**

### Overview

~~Integrate Pekko Streams with our fluent builder API~~

**CRITICAL ANALYSIS:**

**Why This Approach is Problematic:**
1. **Massive Reimplementation**: You're essentially rebuilding Pekko Streams with a different API
2. **High Maintenance**: Stream processing engines are complex - ongoing maintenance burden
3. **Duplicate Functionality**: Pekko Streams already has:
   - Automatic backpressure handling
   - Error recovery mechanisms
   - Monitoring and instrumentation
   - Battle-tested operators
4. **Development Cost**: 6-8 weeks of effort that could go to higher-value features
5. **Learning Curve**: Users still need to understand stream concepts, different API doesn't help

**Better Alternative:**

**Phase 1: Examples (2-3 weeks)**
- Show how to use Pekko Streams with Spring actors
- Document common patterns (consume from stream → process in actor → publish results)
- Provide ready-to-use examples for common use cases

**Phase 2: Thin Wrapper (ONLY if examples prove too complex) (2-3 weeks)**
- Spring Boot YAML configuration for common stream scenarios
- Helper methods for actor-stream integration
- Don't reinvent stream operators

**Effort Saved:** 6-8 weeks → 2-3 weeks (75% reduction)
**Maintenance Saved:** Ongoing complexity avoided

### Design Philosophy (IF implementing wrapper)

- **Minimal Wrapper**: Don't hide Pekko Streams, enhance it
- **Spring Boot Integration**: YAML configuration only
- **Leverage Pekko**: Use Pekko's backpressure, operators, monitoring
- **Focus on Integration**: Actor↔Stream connection points only

### Implementation Example

```java
@Service
public class DataProcessingService {
    
    private final SpringActorSystem actorSystem;
    
    public DataProcessingService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    public CompletionStage<Done> processLargeDataset(List<String> data) {
        return actorSystem.stream()
            .source(data)                                    // Create source from collection
            .map(this::transform)                            // Transform each element
            .filter(item -> item.length() > 0)              // Filter elements
            .mapAsyncToActor(ProcessorActor.class)          // Process through actor
                .withConcurrency(10)                        // Max 10 concurrent operations
                .withTimeout(Duration.ofSeconds(5))         // Timeout per operation
                .withRetry(3)                               // Retry failed operations
                .build()
            .onBackpressure()                               // Handle backpressure
                .buffer(1000)                               // Buffer up to 1000 elements
                .dropOldest()                               // Drop oldest on overflow
            .toSink()                                        // Terminal operation
            .run();                                          // Execute stream
    }
}
```

### Advanced Stream Builder API

```java
// Complete fluent API for streams
public interface StreamBuilder<T> {
    
    // Source operations
    StreamBuilder<T> source(Collection<T> collection);
    StreamBuilder<T> source(Publisher<T> publisher);
    StreamBuilder<T> fromActor(Class<? extends SpringActor<?>> actorClass, String actorId);
    
    // Transformation operations
    <R> StreamBuilder<R> map(Function<T, R> mapper);
    StreamBuilder<T> filter(Predicate<T> predicate);
    <R> StreamBuilder<R> flatMap(Function<T, Collection<R>> mapper);
    StreamBuilder<T> distinct();
    StreamBuilder<T> take(long n);
    StreamBuilder<T> skip(long n);
    
    // Actor integration
    <R> ActorStreamBuilder<T, R> mapAsyncToActor(Class<? extends SpringActor<?>> actorClass);
    <R> ActorStreamBuilder<T, R> mapAsyncToShardedActor(Class<? extends SpringShardedActor<?>> actorClass);
    
    // Backpressure handling
    BackpressureBuilder<T> onBackpressure();
    
    // Sink operations
    SinkBuilder<T> toSink();
    CompletionStage<Done> runWith(Consumer<T> consumer);
    CompletionStage<List<T>> collect();
}

// Actor stream builder
public interface ActorStreamBuilder<T, R> {
    ActorStreamBuilder<T, R> withConcurrency(int parallelism);
    ActorStreamBuilder<T, R> withTimeout(Duration timeout);
    ActorStreamBuilder<T, R> withRetry(int maxAttempts);
    ActorStreamBuilder<T, R> withFallback(Function<Throwable, R> fallback);
    StreamBuilder<R> build();
}

// Backpressure builder
public interface BackpressureBuilder<T> {
    BackpressureBuilder<T> buffer(int size);
    BackpressureBuilder<T> dropOldest();
    BackpressureBuilder<T> dropNewest();
    BackpressureBuilder<T> fail();
    StreamBuilder<T> build();
}
```

### Real-World Example: Order Processing Pipeline

```java
@Service
public class OrderProcessingService {
    
    private final SpringActorSystem actorSystem;
    private final OrderRepository orderRepository;
    
    public CompletionStage<ProcessingReport> processOrders(List<Order> orders) {
        return actorSystem.stream()
            // 1. Create source from orders
            .source(orders)
            
            // 2. Validate orders
            .filter(order -> order.getAmount() > 0)
            .filter(order -> order.getCustomerId() != null)
            
            // 3. Enrich with customer data (async actor call)
            .mapAsyncToActor(CustomerActor.class)
                .withMessage(order -> new GetCustomerInfo(order.getCustomerId()))
                .withConcurrency(20)
                .withTimeout(Duration.ofSeconds(2))
                .withFallback(error -> CustomerInfo.unknown())
                .build()
            
            // 4. Process payment (sharded actor for scalability)
            .mapAsyncToShardedActor(PaymentActor.class)
                .withEntityId(enrichedOrder -> enrichedOrder.getCustomerId())
                .withMessage(enrichedOrder -> new ProcessPayment(enrichedOrder))
                .withConcurrency(50)
                .withTimeout(Duration.ofSeconds(10))
                .withRetry(3)
                .build()
            
            // 5. Handle backpressure (slow payment gateway)
            .onBackpressure()
                .buffer(5000)
                .dropNewest()
                .build()
            
            // 6. Save to database (batch for efficiency)
            .batch(100, Duration.ofSeconds(5))
            .mapAsync(10, batch -> saveOrderBatch(batch))
            
            // 7. Collect results
            .runWith(new ProcessingReportSink());
    }
    
    private CompletionStage<Void> saveOrderBatch(List<ProcessedOrder> batch) {
        return CompletableFuture.runAsync(() -> orderRepository.saveAll(batch));
    }
}
```

### ActorSource and ActorSink with Fluent API

```java
// Create streams from actors
@Service
public class StreamService {
    
    private final SpringActorSystem actorSystem;
    
    // Actor as source (produces messages)
    public StreamBuilder<Message> streamFromActor(String actorId) {
        return actorSystem.stream()
            .fromActor(MessageProducerActor.class, actorId)
            .withBufferSize(1000)
            .withOverflowStrategy(OverflowStrategy.BACKPRESSURE)
            .build();
    }
    
    // Actor as sink (consumes messages)
    public CompletionStage<Done> streamToActor(
            List<Message> messages, 
            String actorId) {
        
        return actorSystem.stream()
            .source(messages)
            .toActor(MessageConsumerActor.class, actorId)
                .withConcurrency(10)
                .withTimeout(Duration.ofSeconds(5))
                .build()
            .run();
    }
    
    // Bi-directional stream (request-response pattern)
    public StreamBuilder<Response> requestResponseStream(List<Request> requests) {
        return actorSystem.stream()
            .source(requests)
            .askActor(RequestHandlerActor.class)
                .withMessage(req -> new HandleRequest(req))
                .withConcurrency(20)
                .withTimeout(Duration.ofSeconds(3))
                .build();
    }
}
```

### Error Handling and Recovery

```java
// Comprehensive error handling
CompletionStage<Done> result = actorSystem.stream()
    .source(dataList)
    .mapAsyncToActor(ProcessorActor.class)
        .withConcurrency(10)
        .withTimeout(Duration.ofSeconds(5))
        .withRetry(RetryConfig.builder()
            .maxAttempts(3)
            .backoff(Duration.ofMillis(100), Duration.ofSeconds(2))
            .retryOn(TimeoutException.class, TemporaryFailure.class)
            .build())
        .withFallback(error -> {
            log.error("Processing failed", error);
            return ProcessingResult.failed(error.getMessage());
        })
        .withCircuitBreaker(CircuitBreakerConfig.builder()
            .maxFailures(10)
            .callTimeout(Duration.ofSeconds(5))
            .resetTimeout(Duration.ofSeconds(30))
            .build())
        .build()
    .runWith(resultSink);
```

---

## 2.2 ~~Library-Native Throttling~~ → Wrap Pekko's Throttling

**Priority:** ~~HIGH~~ **MEDIUM**
**Complexity:** ~~Low~~ **LOW (if wrapping Pekko)**
**Recommendation:** ⚠️ **WRAP PEKKO'S THROTTLING, DON'T REIMPLEMENT**

### Overview

~~Implement throttling directly in the library (not using Pekko's built-in features)~~

**WHY NOT TO REIMPLEMENT:**
1. Pekko's throttling is battle-tested and production-proven
2. Reinventing adds maintenance burden
3. Risk of bugs in rate limiting algorithms
4. Pekko's implementation is performant and correct

**BETTER APPROACH:**

**Wrap Pekko's throttling with Spring Boot configuration:**
```yaml
spring:
  actor:
    throttling:
      OrderActor:
        max-throughput: 100
        burst-size: 20
```

### Design Philosophy (Wrapper Approach)

- **Leverage Pekko**: Use Pekko's proven throttling implementation
- **Spring Boot Configuration**: YAML-based configuration only
- **Metrics Integration**: Add Spring Boot-friendly metrics on top
- **Don't Reinvent**: Wrap, don't reimplement rate limiting algorithms

### Implementation

```java
@Component
public class RateLimitedActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            // Library-native throttling configuration
            .withThrottling(ThrottleConfig.builder()
                .maxThroughput(100)                        // Max 100 messages/second
                .burstSize(20)                              // Allow bursts of 20
                .windowDuration(Duration.ofSeconds(1))      // Rolling window
                .onLimitExceeded(ThrottleAction.BUFFER)     // Buffer excess messages
                .bufferSize(1000)                           // Max buffer size
                .onBufferFull(BufferFullAction.DROP_OLDEST) // Drop oldest when full
                .build())
            .onMessage(ProcessData.class, this::handleProcessData)
            .build();
    }
}
```

### Throttle Configuration Options

```java
public class ThrottleConfig {
    
    // Rate limit settings
    private int maxThroughput;           // Messages per window
    private int burstSize;                // Allowed burst
    private Duration windowDuration;      // Time window (rolling or fixed)
    
    // Action when limit exceeded
    private ThrottleAction action;        // BUFFER, DROP, REJECT, DELAY
    
    // Buffer settings (if action = BUFFER)
    private int bufferSize;
    private BufferFullAction bufferFullAction;
    
    // Dynamic adjustment
    private boolean dynamicAdjustment;
    private ThrottleStrategy strategy;
}

public enum ThrottleAction {
    BUFFER,      // Buffer messages for later processing
    DROP,        // Drop excess messages
    REJECT,      // Reject with error
    DELAY        // Delay processing
}

public enum BufferFullAction {
    DROP_OLDEST,   // FIFO
    DROP_NEWEST,   // LIFO  
    REJECT_NEW,    // Reject new messages
    BACKPRESSURE   // Signal backpressure upstream
}
```

### Spring Boot Configuration

```yaml
spring:
  actor:
    throttling:
      enabled: true
      # Global defaults
      default:
        max-throughput: 1000
        burst-size: 100
        window-duration: 1s
        action: BUFFER
        buffer-size: 5000
      
      # Per-actor configuration
      actors:
        OrderActor:
          max-throughput: 500
          burst-size: 50
        PaymentActor:
          max-throughput: 100
          burst-size: 10
          action: DELAY
        NotificationActor:
          max-throughput: 1000
          action: DROP
```

### Dynamic Throttle Adjustment

```java
@Service
public class ThrottleManagementService {
    
    private final SpringActorSystem actorSystem;
    
    // Adjust throttle at runtime
    public void adjustThrottle(String actorId, int newThroughput) {
        actorSystem.actor(OrderActor.class)
            .withId(actorId)
            .get()
            .thenAccept(actor -> {
                actor.updateThrottle(ThrottleConfig.builder()
                    .maxThroughput(newThroughput)
                    .build());
            });
    }
    
    // Auto-adjust based on system load
    @Scheduled(fixedRate = 60000) // Every minute
    public void autoAdjustThrottles() {
        SystemMetrics metrics = getSystemMetrics();
        
        if (metrics.getCpuUsage() > 0.8) {
            // Reduce throughput when CPU is high
            reduceAllThrottles(0.8);
        } else if (metrics.getCpuUsage() < 0.5) {
            // Increase throughput when CPU is low
            increaseAllThrottles(1.2);
        }
    }
}
```

### Metrics and Monitoring

```java
// Built-in metrics for throttling
@Component
public class ThrottleMetrics {
    
    @Metric(name = "actor.throttle.messages.total")
    private Counter totalMessages;
    
    @Metric(name = "actor.throttle.messages.throttled")
    private Counter throttledMessages;
    
    @Metric(name = "actor.throttle.messages.dropped")
    private Counter droppedMessages;
    
    @Metric(name = "actor.throttle.buffer.size")
    private Gauge bufferSize;
    
    @Metric(name = "actor.throttle.throughput")
    private Histogram throughput;
}
```

### Health Checks

```java
@Component
public class ThrottleHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        ThrottleStats stats = getThrottleStats();
        
        HealthBuilder health = Health.up();
        
        if (stats.getDropRate() > 0.1) {
            // More than 10% messages dropped
            health = Health.down()
                .withDetail("issue", "High message drop rate")
                .withDetail("dropRate", stats.getDropRate());
        }
        
        return health
            .withDetail("totalMessages", stats.getTotalMessages())
            .withDetail("throttledMessages", stats.getThrottledMessages())
            .withDetail("droppedMessages", stats.getDroppedMessages())
            .withDetail("averageThroughput", stats.getAverageThroughput())
            .build();
    }
}
```

### Integration with Streams

```java
// Throttling in stream pipelines
actorSystem.stream()
    .source(largeDataset)
    .throttle(100, Duration.ofSeconds(1))  // Library-native throttle
    .mapAsyncToActor(ProcessorActor.class)
        .withConcurrency(10)
        .build()
    .run();
```

---

## 2.3 Complete Stream Processing Example

### E-Commerce Order Pipeline

```java
@Service
public class OrderPipelineService {
    
    private final SpringActorSystem actorSystem;
    
    public CompletionStage<PipelineResult> processOrderPipeline(String ordersFile) {
        return actorSystem.stream()
            
            // 1. Read from file
            .fromFile(ordersFile)
            .parseJson(Order.class)
            
            // 2. Validate
            .filter(order -> order.isValid())
            .throttle(1000, Duration.ofSeconds(1))  // Limit ingress rate
            
            // 3. Deduplicate
            .distinctBy(Order::getOrderId)
            
            // 4. Enrich with customer data
            .mapAsyncToActor(CustomerActor.class)
                .withMessage(order -> new GetCustomer(order.getCustomerId()))
                .withConcurrency(50)
                .withTimeout(Duration.ofSeconds(2))
                .withRetry(2)
                .build()
            
            // 5. Check inventory (sharded actors)
            .mapAsyncToShardedActor(InventoryActor.class)
                .withEntityId(order -> order.getWarehouseId())
                .withMessage(order -> new CheckInventory(order.getItems()))
                .withConcurrency(100)
                .withTimeout(Duration.ofSeconds(3))
                .build()
            
            // 6. Filter out insufficient inventory
            .filter(result -> result.isInventoryAvailable())
            
            // 7. Process payment
            .mapAsyncToActor(PaymentActor.class)
                .withMessage(result -> new ProcessPayment(result.getOrder()))
                .withConcurrency(20)  // External API rate limit
                .withTimeout(Duration.ofSeconds(10))
                .withRetry(3)
                .withCircuitBreaker(10, Duration.ofSeconds(30))
                .build()
            
            // 8. Handle backpressure from payment gateway
            .onBackpressure()
                .buffer(5000)
                .dropNewest()
                .build()
            
            // 9. Batch for efficient DB writes
            .batch(100, Duration.ofSeconds(5))
            
            // 10. Save to database
            .mapAsync(10, batch -> saveOrderBatch(batch))
            
            // 11. Send notifications
            .flatMap(savedOrders -> savedOrders)
            .mapAsyncToActor(NotificationActor.class)
                .withMessage(order -> new SendConfirmation(order))
                .withConcurrency(100)
                .withTimeout(Duration.ofSeconds(5))
                .build()
            
            // 12. Collect results
            .runWith(new PipelineResultSink());
    }
}
```

---

## Summary

This approach provides:

1. **Fluent Builder API**: Intuitive, chainable stream operations
2. **Actor Integration**: Seamless integration with Spring actors
3. **Automatic Backpressure**: Handle slow consumers gracefully
4. **Library-Native Throttling**: Full control and monitoring
5. **Spring Boot Configuration**: YAML-based configuration
6. **Production Ready**: Metrics, health checks, error recovery
7. **Developer Friendly**: Familiar patterns for Spring Boot users

The design prioritizes **Developer Experience** with fluent APIs while ensuring **Production Readiness** with robust error handling and monitoring.
