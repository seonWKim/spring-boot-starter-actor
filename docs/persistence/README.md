# Persistence Patterns Documentation

Comprehensive guides for integrating persistence with actors in the spring-boot-starter-actor framework.

## Overview

This documentation shows how to use Spring Data repositories directly with actors. No custom abstraction layers are needed - actors work seamlessly with Spring's dependency injection to access any Spring Data repository.

## Documentation Structure

### Getting Started

Start with **[best-practices.md](best-practices.md)** for fundamental concepts and patterns.

### Database Integration Guides

Choose the guide based on your database technology:

- **[jpa-integration.md](jpa-integration.md)** - PostgreSQL, MySQL, and other SQL databases via JPA
- **[mongodb-integration.md](mongodb-integration.md)** - MongoDB with blocking and reactive patterns
- **[r2dbc-integration.md](r2dbc-integration.md)** - Reactive SQL databases (PostgreSQL, MySQL, H2)

### Advanced Patterns

- **[event-sourcing.md](event-sourcing.md)** - Event sourcing patterns for complete audit trails
- **[snapshots.md](snapshots.md)** - Snapshot strategies for fast recovery and optimization

### Production Deployment

- **[production-config.md](production-config.md)** - Connection pooling, retries, health checks, and monitoring

## Quick Navigation

### By Use Case

| Use Case | Recommended Guide |
|----------|------------------|
| Traditional SQL database with blocking operations | [JPA Integration](jpa-integration.md) |
| High-throughput SQL with non-blocking operations | [R2DBC Integration](r2dbc-integration.md) |
| Document store with flexible schema | [MongoDB Integration](mongodb-integration.md) |
| Complete audit trail and temporal queries | [Event Sourcing](event-sourcing.md) |
| Fast actor recovery and optimization | [Snapshots](snapshots.md) |
| Production deployment configuration | [Production Config](production-config.md) |

### By Technology

| Technology | Guide | Best For |
|-----------|-------|----------|
| PostgreSQL + JPA | [JPA Integration](jpa-integration.md) | Traditional applications, complex queries |
| PostgreSQL + R2DBC | [R2DBC Integration](r2dbc-integration.md) | High-throughput, non-blocking |
| MongoDB (blocking) | [MongoDB Integration](mongodb-integration.md) | Document store, simple setup |
| MongoDB (reactive) | [MongoDB Integration](mongodb-integration.md) | Document store, high-throughput |
| MySQL + JPA | [JPA Integration](jpa-integration.md) | Traditional applications |
| MySQL + R2DBC | [R2DBC Integration](r2dbc-integration.md) | High-throughput, non-blocking |
| H2 (testing) | [JPA Integration](jpa-integration.md) or [R2DBC Integration](r2dbc-integration.md) | Development and testing |

## Key Principles

### 1. Direct Repository Usage

Actors inject Spring Data repositories directly through constructor injection:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final OrderRepository orderRepository;
    
    // Direct injection - no adapters needed!
    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
```

### 2. Explicit State Management

You control when state is persisted:

```java
private Behavior<Command> handleUpdate(UpdateOrder cmd) {
    // Update in-memory state
    currentState.setAmount(cmd.newAmount());
    
    // Explicitly save when ready
    orderRepository.save(currentState);
    
    return Behaviors.same();
}
```

### 3. Leverage Spring Boot Features

Use existing Spring Boot infrastructure:
- Connection pooling (HikariCP, R2DBC Pool)
- Retry logic (Spring Retry, Resilience4j)
- Health checks (Spring Boot Actuator)
- Metrics (Micrometer)
- Transactions (Spring Transaction Management)

### 4. Choose the Right Pattern

| Pattern | When to Use |
|---------|-------------|
| **Blocking (JPA/MongoDB)** | Simpler code, lower concurrency needs |
| **Reactive (R2DBC/Reactive MongoDB)** | High throughput, non-blocking requirements |
| **Event Sourcing** | Complete audit trail, temporal queries needed |
| **Snapshots** | Fast recovery, long event histories |

## Common Patterns

### Pattern 1: Basic State Persistence

```java
@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository repository;
    private Order currentState;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> {
                // Load state on startup
                Order state = repository.findById(ctx.actorId()).orElse(null);
                return new OrderBehavior(actorCtx, repository, state);
            })
            .onMessage(UpdateOrder.class, OrderBehavior::handleUpdate)
            .build();
    }
}
```

### Pattern 2: Reactive Non-Blocking

```java
@Component
public class ReactiveOrderActor implements SpringActor<Command> {
    private final ReactiveOrderRepository repository;
    
    private Behavior<Command> handleSave(SaveOrder cmd) {
        // Non-blocking save
        repository.save(currentState)
            .subscribe(
                saved -> ctx.getSelf().tell(new SaveCompleted(saved)),
                error -> ctx.getSelf().tell(new SaveFailed(error))
            );
        return Behaviors.same();
    }
}
```

### Pattern 3: Event Sourcing

```java
@Component
public class EventSourcedActor implements SpringActor<Command> {
    private final EventRepository eventRepository;
    
    private Behavior<Command> handleCommand(CreateOrder cmd) {
        // Create event
        OrderCreatedEvent event = new OrderCreatedEvent(cmd.orderId(), cmd.amount());
        
        // Persist event
        eventRepository.save(toEntity(event));
        
        // Apply to state
        currentState.apply(event);
        
        return Behaviors.same();
    }
}
```

## Examples by Feature

### Transaction Management
See [best-practices.md - Transaction Management](best-practices.md#4-transaction-management)

### Error Handling
See [best-practices.md - Error Handling](best-practices.md#6-error-handling)

### Testing
- JPA: [jpa-integration.md - Testing](jpa-integration.md#testing)
- MongoDB: [mongodb-integration.md - Testing](mongodb-integration.md#testing)
- R2DBC: [r2dbc-integration.md - Testing](r2dbc-integration.md#testing)

### Performance Optimization
- JPA: [jpa-integration.md - Performance Optimization](jpa-integration.md#performance-optimization)
- MongoDB: [mongodb-integration.md - Performance Optimization](mongodb-integration.md#performance-optimization)
- R2DBC: [r2dbc-integration.md - Performance-optimization](r2dbc-integration.md#performance-optimization)
- Production: [production-config.md - Performance Optimization](production-config.md#performance-optimization)

### Connection Pooling
See [production-config.md - Connection Pool Configuration](production-config.md#connection-pool-configuration)

### Retry Logic
See [production-config.md - Retry Configuration](production-config.md#retry-configuration)

### Health Checks
See [production-config.md - Health Checks](production-config.md#health-checks)

### Monitoring
See [production-config.md - Monitoring and Metrics](production-config.md#monitoring-and-metrics)

## What NOT to Do

❌ **Don't create custom ActorStateAdapter interfaces** - Use Spring Data repositories directly

❌ **Don't implement custom connection pooling** - Use HikariCP, R2DBC Pool, or MongoDB's built-in pooling

❌ **Don't implement custom retry logic** - Use Spring Retry or Resilience4j

❌ **Don't implement custom health checks** - Use Spring Boot Actuator

❌ **Don't block reactive streams** - Never call `.block()` in production reactive code

## Getting Help

- Review the [best-practices.md](best-practices.md) guide for general patterns
- Check the specific database guide for detailed examples
- Refer to [production-config.md](production-config.md) for deployment configuration
- See Spring Data documentation for repository-specific features

## Contributing

Found an issue or have a suggestion? Please open an issue in the GitHub repository.

## License

This documentation is part of the spring-boot-starter-actor project and follows the same license.
