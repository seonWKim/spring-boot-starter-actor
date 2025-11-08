# 3. Routing Patterns

Provide Spring Boot users with intuitive routing capabilities for load balancing and parallel processing. Evaluate library-native implementation vs wrapping Pekko's built-in router support.

---

## Design Decision: Library-Native vs Pekko Wrapper

### Analysis

**Option 1: Wrap Pekko's Built-in Routers**
- ✅ Battle-tested, production-proven implementation
- ✅ Faster time to market
- ✅ All routing strategies included (Round Robin, Smallest Mailbox, etc.)
- ✅ Dynamic resizing already implemented
- ❌ Less control over Spring Boot integration
- ❌ More difficult to add custom metrics
- ❌ Pekko's API exposed (learning curve)

**Option 2: Library-Native Implementation**
- ✅ Full Spring Boot integration (DI, configuration, health checks)
- ✅ Custom metrics and monitoring
- ✅ Simpler API for Spring Boot users
- ✅ Better error messages and debugging
- ❌ Longer development time
- ❌ Need to implement all routing strategies
- ❌ Potential bugs in initial versions

### **Recommendation: Hybrid Approach** ✅

**Use Pekko's routers internally but wrap them with Spring Boot-friendly API:**
- Leverage Pekko's proven routing logic
- Provide Spring Boot native configuration and monitoring  
- Expose simple fluent API
- Add library-specific features (metrics, health checks, Spring DI)
- Users don't need to learn Pekko's API

---

## 3.1 Router Support with Spring Boot Integration

**Priority:** HIGH  
**Complexity:** Medium  
**Implementation:** Wrap Pekko routers with Spring Boot API

### Overview

Provide intuitive routing capabilities that leverage Pekko's proven implementation while offering Spring Boot-friendly configuration and monitoring.

### Design Philosophy

- **Simple API**: Hide Pekko complexity behind fluent builders
- **Spring Boot Native**: Configuration via YAML, Spring DI support
- **Monitoring**: Built-in metrics for router health
- **Flexible**: Support multiple routing strategies
- **Production Ready**: Health checks, dynamic resizing, supervision

### Implementation Example

```java
@Component
public class WorkerPoolRouter implements SpringRouterActor<WorkerActor.Command> {
    
    private final WorkerDependency workerDependency;
    
    // Spring DI works for router configuration
    public WorkerPoolRouter(WorkerDependency workerDependency) {
        this.workerDependency = workerDependency;
    }
    
    @Override
    public SpringRouterBehavior<WorkerActor.Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<WorkerActor.Command>builder()
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withPoolSize(10)
            .withWorkerClass(WorkerActor.class)
            .withSupervisionStrategy(SupervisorStrategy.restart())
            .withMetricsEnabled(true)
            .build();
    }
}

// Usage - same fluent API as regular actors
@Service
public class TaskService {
    
    private final SpringActorSystem actorSystem;
    
    public CompletionStage<Void> distributeTasks(List<Task> tasks) {
        return actorSystem.getOrSpawn(WorkerPoolRouter.class, "worker-pool")
            .thenCompose(router -> {
                // Router looks like a regular actor to users
                tasks.forEach(task -> router.tell(new ProcessTask(task)));
                return CompletableFuture.completedFuture(null);
            });
    }
}
```

### Routing Strategies

```java
public enum RoutingStrategy {
    
    /**
     * Distribute messages evenly across routees in round-robin fashion.
     * Best for: Equal distribution, simple use cases
     */
    ROUND_ROBIN,
    
    /**
     * Send messages to routees randomly.
     * Best for: Simple distribution without state tracking
     */
    RANDOM,
    
    /**
     * Send messages to routee with smallest mailbox.
     * Best for: Load balancing with varying processing times
     */
    SMALLEST_MAILBOX,
    
    /**
     * Broadcast messages to all routees.
     * Best for: Cache invalidation, notifications
     */
    BROADCAST,
    
    /**
     * Route based on consistent hashing of message content.
     * Best for: Stateful processing, session affinity
     */
    CONSISTENT_HASHING,
    
    /**
     * Send to all routees, use first response.
     * Best for: Redundant processing, fastest response
     */
    SCATTER_GATHER_FIRST;
}
```

### Advanced Router Configuration

```java
@Component
public class AdvancedRouter implements SpringRouterActor<Command> {
    
    @Override
    public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<Command>builder()
            
            // Routing strategy
            .withRoutingStrategy(RoutingStrategy.SMALLEST_MAILBOX)
            
            // Pool configuration
            .withPoolSize(PoolSize.builder()
                .initial(5)
                .min(2)
                .max(20)
                .build())
            
            // Dynamic resizing based on load
            .withResizer(Resizer.builder()
                .enabled(true)
                .lowerBound(2)
                .upperBound(20)
                .pressureThreshold(0.8)     // Scale up at 80% load
                .backoffThreshold(0.3)      // Scale down at 30% load
                .rampUpRate(0.2)            // Add 20% more workers
                .backoffRate(0.1)           // Remove 10% workers
                .messagesPerResize(10)      // Check every 10 messages
                .build())
            
            // Supervision strategy for workers
            .withSupervisionStrategy(SupervisorStrategy.restart()
                .withLimit(3, Duration.ofMinutes(1)))
            
            // Worker actor configuration
            .withWorkerClass(WorkerActor.class)
            .withWorkerDispatcher("worker-dispatcher")
            
            // Metrics and monitoring
            .withMetricsEnabled(true)
            .withHealthCheckEnabled(true)
            
            // Timeouts
            .withWorkerInitTimeout(Duration.ofSeconds(10))
            
            .build();
    }
}
```

### Spring Boot Configuration

```yaml
spring:
  actor:
    routers:
      # Global defaults
      default:
        pool-size: 10
        routing-strategy: ROUND_ROBIN
        supervision: RESTART
        metrics-enabled: true
        
      # Per-router configuration
      worker-pool:
        pool-size:
          initial: 5
          min: 2
          max: 20
        routing-strategy: SMALLEST_MAILBOX
        resizer:
          enabled: true
          pressure-threshold: 0.8
          backoff-threshold: 0.3
        supervision:
          strategy: RESTART
          max-retries: 3
          within: 1m
          
      payment-pool:
        pool-size: 10
        routing-strategy: ROUND_ROBIN
        # No dynamic resizing for payment (predictable load)
        supervision:
          strategy: RESTART
          max-retries: 5
          within: 2m
```

---

## 3.2 Consistent Hashing Router

**Priority:** HIGH  
**Complexity:** Medium

### Overview

Route messages to the same worker based on message content (e.g., user ID, session ID). Essential for stateful processing.

### Implementation

```java
@Component
public class UserSessionRouter implements SpringRouterActor<UserCommand> {
    
    @Override
    public SpringRouterBehavior<UserCommand> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<UserCommand>builder()
            // Extract hash key from message
            .withRoutingStrategy(RoutingStrategy.consistentHashing(
                msg -> ((UserCommand) msg).userId()
            ))
            .withPoolSize(20)
            .withWorkerClass(UserSessionActor.class)
            .withVirtualNodesFactor(10)  // Better distribution
            .build();
    }
}

// Messages automatically routed to same worker based on userId
public interface UserCommand {
    String userId();
}

public record UpdateUserSession(String userId, String data) implements UserCommand {}
public record GetUserSession(String userId) implements UserCommand {}

// Usage
@Service
public class SessionService {
    
    private final SpringActorSystem actorSystem;
    
    public void updateSession(String userId, String data) {
        actorSystem.getOrSpawn(UserSessionRouter.class, "session-router")
            .thenAccept(router -> {
                // All messages for same userId go to same worker
                router.tell(new UpdateUserSession(userId, data));
            });
    }
}
```

### Custom Hash Key Extraction

```java
// Complex hash key extraction
.withRoutingStrategy(RoutingStrategy.consistentHashing(msg -> {
    if (msg instanceof OrderCommand orderCmd) {
        // Route orders by customer ID
        return orderCmd.customerId();
    } else if (msg instanceof PaymentCommand paymentCmd) {
        // Route payments by account ID
        return paymentCmd.accountId();
    }
    // Default: use message hash
    return msg.hashCode();
}))
```

---

## 3.3 Dynamic Router Resizing

**Priority:** MEDIUM  
**Complexity:** Medium

### Overview

Automatically adjust pool size based on load. Uses Pekko's resizing logic with Spring Boot configuration.

### Implementation

```java
@Component
public class AutoScalingRouter implements SpringRouterActor<Command> {
    
    @Override
    public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<Command>builder()
            .withRoutingStrategy(RoutingStrategy.ROUND_ROBIN)
            
            // Start with 5 workers
            .withPoolSize(PoolSize.builder()
                .initial(5)
                .min(2)
                .max(50)
                .build())
            
            // Auto-scaling configuration
            .withResizer(Resizer.builder()
                .enabled(true)
                
                // Scale up when pressure > 80%
                .pressureThreshold(0.8)
                // Scale down when pressure < 30%
                .backoffThreshold(0.3)
                
                // Add 20% more workers when scaling up
                .rampUpRate(0.2)
                // Remove 10% workers when scaling down
                .backoffRate(0.1)
                
                // Evaluate every 10 messages
                .messagesPerResize(10)
                
                // Cooldown period between resizing
                .resizeCooldown(Duration.ofSeconds(30))
                
                .build())
            
            .withWorkerClass(WorkerActor.class)
            .build();
    }
}
```

### Manual Resizing Control

```java
@Service
public class RouterManagementService {
    
    private final SpringActorSystem actorSystem;
    
    // Manually resize router
    public CompletionStage<Void> resizeRouter(String routerId, int newSize) {
        return actorSystem.actor(WorkerPoolRouter.class)
            .withId(routerId)
            .get()
            .thenAccept(router -> {
                router.resize(newSize);
            });
    }
    
    // Get current pool size
    public CompletionStage<Integer> getPoolSize(String routerId) {
        return actorSystem.actor(WorkerPoolRouter.class)
            .withId(routerId)
            .get()
            .thenCompose(router -> router.getPoolSize());
    }
}
```

---

## 3.4 Broadcast Router

**Priority:** MEDIUM  
**Complexity:** Low

### Overview

Send messages to all workers. Useful for cache invalidation, notifications, etc.

### Implementation

```java
@Component
public class CacheInvalidationRouter implements SpringRouterActor<InvalidateCommand> {
    
    @Override
    public SpringRouterBehavior<InvalidateCommand> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<InvalidateCommand>builder()
            .withRoutingStrategy(RoutingStrategy.BROADCAST)
            .withPoolSize(10)
            .withWorkerClass(CacheWorkerActor.class)
            .build();
    }
}

// Usage - message sent to all workers
@Service
public class CacheService {
    
    public void invalidateCache(String key) {
        actorSystem.getOrSpawn(CacheInvalidationRouter.class, "cache-router")
            .thenAccept(router -> {
                // All workers receive this message
                router.tell(new InvalidateCache(key));
            });
    }
}
```

---

## 3.5 Router Metrics and Monitoring

**Priority:** HIGH  
**Complexity:** Low

### Built-in Metrics

```java
// Automatic metrics for all routers
@Component
public class RouterMetrics {
    
    @Metric(name = "actor.router.pool.size")
    private Gauge poolSize;
    
    @Metric(name = "actor.router.pool.utilization")
    private Gauge poolUtilization;
    
    @Metric(name = "actor.router.messages.routed")
    private Counter messagesRouted;
    
    @Metric(name = "actor.router.messages.failed")
    private Counter messagesFailed;
    
    @Metric(name = "actor.router.routing.time")
    private Timer routingTime;
    
    @Metric(name = "actor.router.worker.busy")
    private Gauge busyWorkers;
    
    @Metric(name = "actor.router.resizing.events")
    private Counter resizingEvents;
}
```

### Health Checks

```java
@Component
public class RouterHealthIndicator implements HealthIndicator {
    
    private final SpringActorSystem actorSystem;
    
    @Override
    public Health health() {
        RouterStats stats = getRouterStats("worker-pool");
        
        HealthBuilder health = Health.up();
        
        // Check if pool is at max capacity
        if (stats.getPoolSize() >= stats.getMaxPoolSize() 
            && stats.getUtilization() > 0.9) {
            health = Health.down()
                .withDetail("issue", "Router at max capacity");
        }
        
        // Check worker failure rate
        if (stats.getFailureRate() > 0.1) {
            health = Health.degraded()
                .withDetail("issue", "High worker failure rate");
        }
        
        return health
            .withDetail("poolSize", stats.getPoolSize())
            .withDetail("utilization", stats.getUtilization())
            .withDetail("messagesRouted", stats.getMessagesRouted())
            .withDetail("failureRate", stats.getFailureRate())
            .build();
    }
}
```

### Actuator Endpoints

```java
// Custom actuator endpoints for router management
@RestController
@RequestMapping("/actuator/routers")
public class RouterActuatorEndpoint {
    
    @GetMapping
    public List<RouterInfo> listRouters() {
        // List all active routers
    }
    
    @GetMapping("/{routerId}")
    public RouterInfo getRouterInfo(@PathVariable String routerId) {
        // Get detailed router information
    }
    
    @PostMapping("/{routerId}/resize")
    public void resizeRouter(
            @PathVariable String routerId,
            @RequestParam int newSize) {
        // Manually resize router
    }
    
    @GetMapping("/{routerId}/workers")
    public List<WorkerInfo> getWorkers(@PathVariable String routerId) {
        // List all workers in router
    }
}
```

---

## 3.6 Advanced Routing Patterns

### Scatter-Gather Pattern

```java
@Component
public class RedundantProcessingRouter implements SpringRouterActor<Command> {
    
    @Override
    public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<Command>builder()
            // Send to all, use first response
            .withRoutingStrategy(RoutingStrategy.SCATTER_GATHER_FIRST)
            .withPoolSize(3)  // 3 redundant workers
            .withWorkerClass(ProcessorActor.class)
            .withAggregationTimeout(Duration.ofSeconds(5))
            .build();
    }
}
```

### Adaptive Routing

```java
@Component
public class AdaptiveRouter implements SpringRouterActor<Command> {
    
    @Override
    public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<Command>builder()
            // Strategy changes based on metrics
            .withRoutingStrategy(RoutingStrategy.adaptive(ctx -> {
                RouterMetrics metrics = ctx.getMetrics();
                
                if (metrics.getVariance() > 0.5) {
                    // High variance: use smallest mailbox
                    return RoutingStrategy.SMALLEST_MAILBOX;
                } else {
                    // Low variance: use round robin (faster)
                    return RoutingStrategy.ROUND_ROBIN;
                }
            }))
            .withPoolSize(10)
            .withWorkerClass(WorkerActor.class)
            .build();
    }
}
```

---

## 3.7 Testing Routers

### Unit Testing

```java
@SpringBootTest
@ActorTest
public class WorkerPoolRouterTest {
    
    @Autowired
    private ActorTestKit testKit;
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testRoundRobinDistribution() {
        // Spawn router with 3 workers
        SpringActorRef<Command> router = actorSystem
            .spawn(WorkerPoolRouter.class, "test-router")
            .toCompletableFuture().join();
        
        // Send 9 messages
        for (int i = 0; i < 9; i++) {
            router.tell(new ProcessTask("task-" + i));
        }
        
        // Verify each worker received 3 messages
        RouterStats stats = getRouterStats("test-router");
        assertEquals(3, stats.getWorkerMessages().get(0));
        assertEquals(3, stats.getWorkerMessages().get(1));
        assertEquals(3, stats.getWorkerMessages().get(2));
    }
    
    @Test
    public void testConsistentHashing() {
        SpringActorRef<UserCommand> router = actorSystem
            .spawn(UserSessionRouter.class, "session-router")
            .toCompletableFuture().join();
        
        // Same user should go to same worker
        router.tell(new UpdateUserSession("user1", "data1"));
        router.tell(new UpdateUserSession("user1", "data2"));
        router.tell(new UpdateUserSession("user1", "data3"));
        
        // Verify all messages for user1 went to same worker
        RouterStats stats = getRouterStats("session-router");
        assertTrue(stats.hasAffinityFor("user1"));
    }
}
```

---

## Summary

This hybrid approach provides:

1. **Proven Implementation**: Leverages Pekko's battle-tested routing
2. **Spring Boot Native**: YAML configuration, DI, health checks
3. **Simple API**: Users don't need to learn Pekko
4. **Full Metrics**: Built-in monitoring and alerting
5. **Dynamic Resizing**: Auto-scale based on load
6. **Multiple Strategies**: Round Robin, Smallest Mailbox, Consistent Hashing, etc.
7. **Production Ready**: Health checks, actuator endpoints, supervision

The design **wraps Pekko's routers** while providing **Spring Boot-friendly API and monitoring**, giving us the best of both worlds: proven routing logic and excellent developer experience.
