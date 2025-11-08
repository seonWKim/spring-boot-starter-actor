# Best Practices for Using Spring Data with Actors

This guide provides best practices for integrating Spring Data repositories with actors in the spring-boot-starter-actor framework.

## Overview

The spring-boot-starter-actor framework seamlessly integrates with Spring's dependency injection, allowing actors to directly use Spring Data repositories without any additional abstraction layers. This approach leverages Spring Boot's battle-tested persistence infrastructure while maintaining the actor model's benefits.

## Key Principles

### 1. Direct Repository Injection

Actors can directly inject Spring Data repositories through constructor injection, just like any other Spring component.

```java
@Component
public class OrderActor implements SpringActor<OrderActor.Command> {
    
    private final OrderRepository orderRepository;
    
    // Direct constructor injection - Spring handles it automatically
    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        // Actor implementation
    }
}
```

### 2. Explicit State Management

Unlike implicit event sourcing, you have full control over when and how state is persisted. This makes the code easier to understand and debug.

**✅ DO:** Explicitly save state when needed
```java
private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
    // Update in-memory state
    currentState.setAmount(cmd.amount());
    currentState.setStatus("CREATED");
    
    // Explicit save - you control when it happens
    orderRepository.save(currentState);
    
    return Behaviors.same();
}
```

**❌ DON'T:** Assume automatic persistence
```java
private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
    // Update in-memory state
    currentState.setAmount(cmd.amount());
    // WRONG: Forgetting to save means state is lost on restart
    return Behaviors.same();
}
```

### 3. State Loading on Actor Startup

Load actor state during initialization to restore previous state after restarts.

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .withState(actorCtx -> {
            // Load existing state on startup
            OrderState state = orderRepository.findById(ctx.actorId())
                .orElse(new OrderState(ctx.actorId()));
            return new OrderActorBehavior(actorCtx, state, orderRepository);
        })
        .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrder)
        .build();
}
```

### 4. Transaction Management

Use Spring's `@Transactional` annotation for operations that need atomicity.

```java
@Component
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final SpringActorSystem actorSystem;
    
    @Transactional
    public void createOrderWithTransaction(String orderId, double amount) {
        // Both operations happen in a single transaction
        OrderState order = new OrderState(orderId, amount);
        orderRepository.save(order);
        
        // Send message to actor after successful commit
        actorSystem.actorOf(OrderActor.class, orderId)
            .tell(new OrderActor.OrderCreated(orderId));
    }
}
```

**Important:** Actor message handlers run outside of transactions by default. If you need transactional behavior, wrap your repository calls in a transactional service method.

### 5. Handling Blocking Operations

Database operations are typically blocking. For high-throughput systems, use one of these patterns:

#### Pattern A: Dedicated Dispatcher
```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        // Run this actor on a dedicated dispatcher for blocking operations
        .withDispatcher("blocking-io-dispatcher")
        .onMessage(SaveOrder.class, this::handleSave)
        .build();
}
```

```yaml
# application.yml
pekko.actor.dispatchers {
  blocking-io-dispatcher {
    type = Dispatcher
    executor = "thread-pool-executor"
    thread-pool-executor {
      fixed-pool-size = 32
    }
    throughput = 1
  }
}
```

#### Pattern B: Async Execution
```java
private Behavior<Command> handleSaveAsync(SaveOrder cmd) {
    // Execute blocking operation on a separate thread pool
    CompletableFuture.supplyAsync(() -> {
        return orderRepository.save(currentState);
    }, blockingExecutor)
    .thenAccept(savedOrder -> {
        // Notify actor about completion
        ctx.getSelf().tell(new SaveCompleted(savedOrder.getId()));
    })
    .exceptionally(error -> {
        ctx.getSelf().tell(new SaveFailed(error.getMessage()));
        return null;
    });
    
    return Behaviors.same();
}
```

### 6. Error Handling

Always handle database errors gracefully in actors.

```java
private Behavior<Command> handleSave(SaveOrder cmd) {
    try {
        orderRepository.save(currentState);
        ctx.getLog().info("Order {} saved successfully", currentState.getId());
        return Behaviors.same();
    } catch (DataAccessException e) {
        ctx.getLog().error("Failed to save order {}: {}", 
            currentState.getId(), e.getMessage());
        
        // Decide on error handling strategy:
        // 1. Retry (throw exception to trigger supervisor retry)
        // 2. Notify caller of failure
        // 3. Use dead letter for failed operations
        
        throw e; // Let supervisor handle it
    }
}
```

### 7. Testing with Repository Mocks

Actors with repository dependencies are easy to test using mocks.

```java
@ExtendWith(MockitoExtension.class)
class OrderActorTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    private ActorTestKit testKit;
    private ActorRef<OrderActor.Command> orderActor;
    
    @BeforeEach
    void setup() {
        testKit = ActorTestKit.create();
        
        // Create actor with mocked repository
        OrderActor actor = new OrderActor(orderRepository);
        orderActor = testKit.spawn(actor.create(mockContext));
    }
    
    @Test
    void testOrderCreation() {
        // Arrange
        when(orderRepository.save(any())).thenReturn(new OrderState());
        
        // Act
        orderActor.tell(new OrderActor.CreateOrder("order-1", 100.0));
        
        // Assert
        verify(orderRepository).save(any(OrderState.class));
    }
}
```

### 8. State Consistency Patterns

#### Pattern A: Read Your Writes
```java
private Behavior<Command> handleUpdate(UpdateOrder cmd) {
    // Update and save
    currentState.setAmount(cmd.newAmount());
    OrderState saved = orderRepository.save(currentState);
    
    // Use the saved instance to ensure consistency
    currentState = saved;
    
    return Behaviors.same();
}
```

#### Pattern B: Optimistic Locking
```java
@Entity
public class OrderState {
    @Id
    private String id;
    
    @Version
    private Long version;  // JPA manages this automatically
    
    private double amount;
}

private Behavior<Command> handleUpdate(UpdateOrder cmd) {
    try {
        currentState.setAmount(cmd.newAmount());
        orderRepository.save(currentState);
        return Behaviors.same();
    } catch (OptimisticLockingFailureException e) {
        // Reload and retry
        currentState = orderRepository.findById(currentState.getId())
            .orElseThrow();
        ctx.getSelf().tell(cmd); // Retry the update
        return Behaviors.same();
    }
}
```

### 9. Caching Strategies

For read-heavy workloads, consider caching strategies:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final OrderRepository orderRepository;
    private final CacheManager cacheManager;
    private OrderState cachedState;
    private Duration cacheTimeout = Duration.ofMinutes(5);
    private Instant lastCacheUpdate;
    
    private Behavior<Command> handleGetOrder(GetOrder cmd) {
        // Check cache validity
        if (cachedState != null && 
            Duration.between(lastCacheUpdate, Instant.now()).compareTo(cacheTimeout) < 0) {
            cmd.replyTo().tell(new OrderResponse(cachedState));
            return Behaviors.same();
        }
        
        // Cache miss or expired - load from DB
        cachedState = orderRepository.findById(cmd.orderId()).orElse(null);
        lastCacheUpdate = Instant.now();
        
        cmd.replyTo().tell(new OrderResponse(cachedState));
        return Behaviors.same();
    }
}
```

### 10. Monitoring and Health Checks

Use Spring Boot Actuator for database health monitoring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  health:
    db:
      enabled: true
  metrics:
    enable:
      jdbc: true
```

## Common Patterns

### Pattern: Command Sourcing (not full Event Sourcing)

Store commands for audit trails without the complexity of event sourcing:

```java
@Entity
public class OrderCommand {
    @Id
    @GeneratedValue
    private Long id;
    private String orderId;
    private String commandType;
    private String payload;
    private LocalDateTime timestamp;
}

@Repository
public interface OrderCommandRepository extends JpaRepository<OrderCommand, Long> {
    List<OrderCommand> findByOrderIdOrderByTimestampDesc(String orderId);
}

private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
    // Save the command for audit
    OrderCommand command = new OrderCommand();
    command.setOrderId(cmd.orderId());
    command.setCommandType("CREATE_ORDER");
    command.setPayload(objectMapper.writeValueAsString(cmd));
    command.setTimestamp(LocalDateTime.now());
    commandRepository.save(command);
    
    // Process the command
    currentState.update(cmd);
    orderRepository.save(currentState);
    
    return Behaviors.same();
}
```

### Pattern: Snapshot Strategy

Periodically save full state snapshots:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private int operationCount = 0;
    private static final int SNAPSHOT_INTERVAL = 100;
    
    private Behavior<Command> handleUpdate(UpdateOrder cmd) {
        // Update state
        currentState.update(cmd);
        operationCount++;
        
        // Save snapshot at intervals
        if (operationCount >= SNAPSHOT_INTERVAL) {
            orderRepository.save(currentState);
            operationCount = 0;
            ctx.getLog().info("Snapshot saved for order {}", currentState.getId());
        }
        
        return Behaviors.same();
    }
}
```

## Anti-Patterns to Avoid

### ❌ Don't Create Unnecessary Abstraction Layers

```java
// DON'T: Creating an unnecessary adapter interface
public interface ActorStateAdapter<S> {
    CompletionStage<S> load(String id);
    CompletionStage<Void> save(S state);
}

// DO: Use Spring Data repositories directly
@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository orderRepository; // Direct usage
}
```

### ❌ Don't Mix Actor State with Database Entities Naively

```java
// DON'T: Directly expose JPA entities as actor state (lazy loading issues)
@Entity
public class Order {
    @OneToMany(fetch = FetchType.LAZY)
    private List<OrderItem> items; // Can cause LazyInitializationException
}

// DO: Use DTOs or explicitly fetch associations
@Entity
public class Order {
    @OneToMany(fetch = FetchType.EAGER)
    private List<OrderItem> items; // Or use fetch joins in repository
}
```

### ❌ Don't Block the Actor Thread for Long Operations

```java
// DON'T: Long-running blocking operation on actor thread
private Behavior<Command> handleBigQuery(BigQuery cmd) {
    // This blocks all messages to this actor!
    List<Order> results = orderRepository.findAllWithComplexJoin(); // Slow query
    return Behaviors.same();
}

// DO: Use async execution or dedicated dispatcher
private Behavior<Command> handleBigQuery(BigQuery cmd) {
    CompletableFuture.supplyAsync(() -> 
        orderRepository.findAllWithComplexJoin(), blockingExecutor)
    .thenAccept(results -> 
        ctx.getSelf().tell(new QueryCompleted(results)));
    return Behaviors.same();
}
```

## Performance Tips

1. **Use batch operations** when processing multiple entities:
   ```java
   orderRepository.saveAll(orders); // Better than multiple save() calls
   ```

2. **Leverage database indices** for actor entity lookups:
   ```sql
   CREATE INDEX idx_order_actor_id ON orders(actor_id);
   ```

3. **Use projections** for read-only queries:
   ```java
   public interface OrderSummary {
       String getId();
       Double getAmount();
   }
   
   @Query("SELECT o.id as id, o.amount as amount FROM OrderState o WHERE o.status = :status")
   List<OrderSummary> findOrderSummaries(@Param("status") String status);
   ```

4. **Configure connection pooling** appropriately:
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20  # Adjust based on load
         minimum-idle: 5
         connection-timeout: 30000
   ```

## Summary

By following these best practices, you can:

- ✅ Leverage Spring Data's battle-tested persistence features
- ✅ Maintain explicit control over state management
- ✅ Write testable actors with clear dependencies
- ✅ Handle blocking operations properly
- ✅ Build production-ready actor systems with reliable persistence

The key is to treat actors as regular Spring components that happen to have an actor lifecycle, not as special entities requiring custom persistence infrastructure.
