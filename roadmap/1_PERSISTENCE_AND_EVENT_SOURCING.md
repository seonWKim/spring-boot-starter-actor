# 1. Persistence and Event Sourcing

Spring Boot users need explicit, manual control over state persistence with familiar patterns and non-blocking support for production systems.

---

## 1.1 Manual State Persistence with Adapter Pattern

**Priority:** HIGH  
**Complexity:** Medium  
**Spring Boot Compatibility:** Excellent

### Overview

Instead of implicit event sourcing, provide Spring Boot users with explicit, manual state persistence using familiar repository patterns. This approach gives developers full control over when and how state is persisted, integrating seamlessly with existing Spring Data infrastructure.

### Design Philosophy

- **Explicit over Implicit**: Users explicitly call `.save()` methods
- **Spring Boot Native**: Leverages Spring Data repositories
- **Database Agnostic**: Works with JPA, MongoDB, R2DBC, etc.
- **Non-Blocking Ready**: Full support for reactive/async operations
- **Production Ready**: Built-in connection pooling, retries, and health checks

### Implementation Example

```java
// 1. Define your actor state as a Spring Data entity
@Entity
@Table(name = "order_state")
public class OrderState {
    @Id
    private String orderId;
    private Double amount;
    private String status;
    private LocalDateTime lastModified;
    
    // Standard getters/setters
}

// 2. Create a Spring Data repository
@Repository
public interface OrderStateRepository extends JpaRepository<OrderState, String> {
    // Spring Data magic - no implementation needed
}

// 3. Actor with manual state management
@Component
public class OrderActor implements SpringActor<OrderActor.Command> {
    
    private final OrderStateRepository stateRepository;
    private OrderState currentState;
    
    // Spring DI injection
    public OrderActor(OrderStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }
    
    public interface Command {}
    
    public record CreateOrder(String orderId, double amount) implements Command {}
    public record LoadState(String orderId) implements Command {}
    public record SaveState() implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(LoadState.class, this::handleLoadState)
            .onMessage(CreateOrder.class, this::handleCreateOrder)
            .onMessage(SaveState.class, this::handleSaveState)
            .build();
    }
    
    private Behavior<Command> handleLoadState(LoadState cmd) {
        // Manual load from database
        currentState = stateRepository.findById(cmd.orderId())
            .orElse(new OrderState());
        return Behaviors.same();
    }
    
    private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
        // Update in-memory state
        currentState = new OrderState();
        currentState.setOrderId(cmd.orderId());
        currentState.setAmount(cmd.amount());
        currentState.setStatus("CREATED");
        currentState.setLastModified(LocalDateTime.now());
        
        // Manual save - user has full control
        stateRepository.save(currentState);
        
        return Behaviors.same();
    }
    
    private Behavior<Command> handleSaveState(SaveState cmd) {
        // Explicit save on demand
        stateRepository.save(currentState);
        return Behaviors.same();
    }
}
```

### Non-Blocking Support for Reactive Databases

For production systems requiring high throughput, support reactive/non-blocking operations:

```java
// 1. Use Spring Data R2DBC for reactive databases
@Repository
public interface OrderStateRepository extends R2dbcRepository<OrderState, String> {
    Mono<OrderState> findByOrderId(String orderId);
}

// 2. Actor with non-blocking state management
@Component
public class ReactiveOrderActor implements SpringActor<Command> {
    
    private final OrderStateRepository stateRepository;
    private final ActorContext<Command> actorContext;
    
    public ReactiveOrderActor(OrderStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        this.actorContext = ctx;
        
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(CreateOrder.class, this::handleCreateOrderAsync)
            .build();
    }
    
    private Behavior<Command> handleCreateOrderAsync(CreateOrder cmd) {
        OrderState newState = new OrderState(cmd.orderId(), cmd.amount(), "CREATED");
        
        // Non-blocking save - converts Mono to CompletionStage
        stateRepository.save(newState)
            .toFuture()
            .thenAccept(savedState -> {
                // Notify self after save completes
                actorContext.getSelf().tell(new StateSaved(savedState.getOrderId()));
            })
            .exceptionally(error -> {
                // Handle errors
                actorContext.getSelf().tell(new SaveFailed(error.getMessage()));
                return null;
            });
        
        return Behaviors.same();
    }
}
```

### Spring Boot Configuration

```yaml
spring:
  actor:
    persistence:
      enabled: true
      # Async execution pool for blocking DB operations
      executor:
        core-pool-size: 10
        max-pool-size: 50
        queue-capacity: 1000
      # Health check configuration  
      health-check:
        enabled: true
        test-query: "SELECT 1"
  
  # Standard Spring Data configuration
  datasource:
    url: jdbc:postgresql://localhost:5432/actordb
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### Adapter Pattern for Different Databases

Support multiple database backends through adapters:

```java
// Base adapter interface
public interface ActorStateAdapter<S> {
    CompletionStage<S> load(String entityId);
    CompletionStage<Void> save(String entityId, S state);
    CompletionStage<Void> delete(String entityId);
}

// JPA adapter implementation
@Component
public class JpaActorStateAdapter<S> implements ActorStateAdapter<S> {
    
    private final JpaRepository<S, String> repository;
    private final Executor asyncExecutor;
    
    @Override
    public CompletionStage<S> load(String entityId) {
        return CompletableFuture.supplyAsync(
            () -> repository.findById(entityId).orElse(null),
            asyncExecutor
        );
    }
    
    @Override
    public CompletionStage<Void> save(String entityId, S state) {
        return CompletableFuture.runAsync(
            () -> repository.save(state),
            asyncExecutor
        );
    }
}

// MongoDB adapter implementation  
@Component
public class MongoActorStateAdapter<S> implements ActorStateAdapter<S> {
    
    private final ReactiveMongoRepository<S, String> repository;
    
    @Override
    public CompletionStage<S> load(String entityId) {
        return repository.findById(entityId).toFuture();
    }
    
    @Override
    public CompletionStage<Void> save(String entityId, S state) {
        return repository.save(state).then().toFuture();
    }
}

// Redis adapter for caching
@Component  
public class RedisActorStateAdapter<S> implements ActorStateAdapter<S> {
    
    private final ReactiveRedisTemplate<String, S> redisTemplate;
    
    @Override
    public CompletionStage<S> load(String entityId) {
        return redisTemplate.opsForValue()
            .get(entityId)
            .toFuture();
    }
    
    @Override
    public CompletionStage<Void> save(String entityId, S state) {
        return redisTemplate.opsForValue()
            .set(entityId, state)
            .then()
            .toFuture();
    }
}
```

### Using Adapters in Actors

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final ActorStateAdapter<OrderState> stateAdapter;
    private OrderState currentState;
    
    // Spring Boot auto-wires the appropriate adapter based on configuration
    public OrderActor(ActorStateAdapter<OrderState> stateAdapter) {
        this.stateAdapter = stateAdapter;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withStateAdapter(stateAdapter)  // Register adapter
            .onMessage(CreateOrder.class, (context, cmd) -> {
                currentState = new OrderState(cmd.orderId(), cmd.amount());
                
                // Explicit async save
                stateAdapter.save(cmd.orderId(), currentState)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            context.getSelf().tell(new SaveFailed(error));
                        }
                    });
                
                return Behaviors.same();
            })
            .build();
    }
}
```

### Benefits

✅ **Spring Boot Native**: Uses familiar Spring Data patterns  
✅ **Explicit Control**: Developers decide when to save  
✅ **Database Agnostic**: Works with any Spring-supported database  
✅ **Non-Blocking**: Full reactive support for high throughput  
✅ **Production Ready**: Connection pooling, retries, health checks  
✅ **Easy Testing**: Mock repositories in tests  
✅ **Migration Friendly**: Works with existing database schemas  

### Production Considerations

**Connection Pooling:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Retry Configuration:**
```java
@Configuration
public class PersistenceConfiguration {
    
    @Bean
    public ActorStateAdapter<OrderState> resilientAdapter(
            JpaRepository<OrderState, String> repository) {
        
        return new ResilientActorStateAdapter<>(
            new JpaActorStateAdapter<>(repository),
            RetryConfig.builder()
                .maxAttempts(3)
                .backoff(Duration.ofMillis(100), Duration.ofSeconds(2))
                .retryOnException(TransientDataAccessException.class)
                .build()
        );
    }
}
```

**Health Checks:**
```java
@Component
public class ActorStateHealthIndicator implements HealthIndicator {
    
    private final ActorStateAdapter<?> stateAdapter;
    
    @Override
    public Health health() {
        try {
            stateAdapter.load("health-check-key")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## 1.2 Snapshot Support (Optional Enhancement)

**Priority:** MEDIUM  
**Complexity:** Low

For actors that benefit from periodic state snapshots, provide optional snapshot support:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final ActorStateAdapter<OrderState> stateAdapter;
    private OrderState currentState;
    private int operationsSinceSnapshot = 0;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withStateAdapter(stateAdapter)
            .withSnapshotStrategy(
                SnapshotStrategy.afterNOperations(100)  // Snapshot every 100 ops
            )
            .onMessage(CreateOrder.class, (context, cmd) -> {
                currentState.update(cmd);
                operationsSinceSnapshot++;
                
                // Auto-save when threshold reached
                if (operationsSinceSnapshot >= 100) {
                    stateAdapter.save(cmd.orderId(), currentState);
                    operationsSinceSnapshot = 0;
                }
                
                return Behaviors.same();
            })
            .build();
    }
}
```

---

## 1.3 Event Log (Audit Trail) Support

**Priority:** MEDIUM  
**Complexity:** Medium

For audit requirements, provide optional event logging alongside state persistence:

```java
// Event log entity
@Entity
public class ActorEvent {
    @Id
    @GeneratedValue
    private Long id;
    private String entityId;
    private String eventType;
    private String payload;
    private LocalDateTime timestamp;
}

// Event log repository
@Repository
public interface ActorEventRepository extends JpaRepository<ActorEvent, Long> {
    List<ActorEvent> findByEntityIdOrderByTimestampDesc(String entityId);
}

// Actor with event logging
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final ActorStateAdapter<OrderState> stateAdapter;
    private final ActorEventRepository eventRepository;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withEventLogging(enabled: true)
            .onMessage(CreateOrder.class, (context, cmd) -> {
                // Update state
                currentState.update(cmd);
                
                // Log event for audit trail
                ActorEvent event = new ActorEvent(
                    cmd.orderId(),
                    "ORDER_CREATED",
                    toJson(cmd),
                    LocalDateTime.now()
                );
                eventRepository.save(event);
                
                // Save state
                stateAdapter.save(cmd.orderId(), currentState);
                
                return Behaviors.same();
            })
            .build();
    }
}
```

---

## Summary

This approach provides:

1. **Manual Control**: Users explicitly manage persistence
2. **Spring Boot Native**: Leverages Spring Data patterns
3. **Non-Blocking**: Full reactive support
4. **Adapter Pattern**: Works with any database
5. **Production Ready**: Pooling, retries, health checks
6. **Optional Enhancements**: Snapshots and event logging

The design prioritizes **Developer Experience** for Spring Boot users while maintaining **Production Readiness** for enterprise deployments.
