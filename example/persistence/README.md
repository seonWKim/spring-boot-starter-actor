# Persistence Example

This example demonstrates comprehensive persistence patterns for actors using Spring Data JPA:

## Features

1. **JPA-Based Persistence** (`OrderActor`)
   - Direct repository usage with Spring Data JPA
   - Optimistic locking support
   - One-to-many relationships (Order → OrderItems)

2. **Event Sourcing** (`EventSourcedOrderActor`)
   - All state changes stored as events
   - Complete audit trail
   - State reconstruction from event history

3. **Snapshot Strategy** (`SnapshotOrderActor`)
   - Periodic state snapshots for fast recovery
   - Configurable snapshot strategies (time-based, operation-based, hybrid)
   - Automatic cleanup of old snapshots

## Running the Example

### 1. Build the Project

```bash
./gradlew :example:persistence:build
```

### 2. Run the Application

```bash
./gradlew :example:persistence:bootRun
```

The application will start on `http://localhost:8080`

### 3. Access H2 Console

Visit `http://localhost:8080/h2-console` to view the database

- JDBC URL: `jdbc:h2:mem:orderdb`
- Username: `sa`
- Password: (leave empty)

## API Examples

### JPA-Based Order Actor

```bash
# Create an order
curl -X POST "http://localhost:8080/api/orders/order-1?customerId=customer-1&amount=100.50"

# Add items to order
curl -X POST "http://localhost:8080/api/orders/order-1/items?productId=prod-1&quantity=2&price=50.25"

# Approve order
curl -X POST "http://localhost:8080/api/orders/order-1/approve"

# Get order
curl "http://localhost:8080/api/orders/order-1"
```

### Event-Sourced Order Actor

```bash
# Create event-sourced order
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2?customerId=customer-2&amount=200.00"

# Add items
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2/items?productId=prod-2&quantity=3&price=66.67"

# Approve order
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2/approve?approvedBy=admin"

# Get event history
curl "http://localhost:8080/api/orders/eventsourced/order-2/history"
```

### Snapshot-Based Order Actor

```bash
# Create snapshot order
curl -X POST "http://localhost:8080/api/orders/snapshot/order-3?customerId=customer-3&amount=150.00"

# Update order (triggers snapshot after 10 operations or 60 seconds)
curl -X PUT "http://localhost:8080/api/orders/snapshot/order-3?amount=175.00"

# Manually trigger snapshot
curl -X POST "http://localhost:8080/api/orders/snapshot/order-3/save-snapshot"
```

## Architecture

### JPA Pattern
- **OrderActor**: Manages order lifecycle with direct JPA repository access
- **OrderRepository**: Spring Data JPA repository for orders
- **Order Entity**: JPA entity with optimistic locking (`@Version`)
- **OrderItem Entity**: Related entity demonstrating one-to-many relationships

### Event Sourcing Pattern
- **EventSourcedOrderActor**: Event-sourced actor that stores all state changes as events
- **OrderEvent**: Event store entity
- **OrderDomainEvent**: Sealed interface for domain events
- **Event Types**: OrderCreatedEvent, OrderApprovedEvent, OrderItemAddedEvent, OrderCancelledEvent
- State is reconstructed by replaying events on startup

### Snapshot Pattern
- **SnapshotOrderActor**: Actor with automatic snapshot support
- **ActorSnapshot**: Snapshot entity storing serialized state
- **SnapshotStrategy**: Pluggable strategy for snapshot creation
- **HybridSnapshotStrategy**: Combines operation count and time-based triggers

## Database Schema

The example uses H2 in-memory database with the following tables:

- `orders`: Main order table
- `order_items`: Order line items
- `order_events`: Event store for event sourcing
- `actor_snapshots`: Snapshot storage

## Key Concepts

### 1. Direct Repository Usage
Actors inject Spring Data repositories directly—no custom adapters needed:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository orderRepository;

    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
```

### 2. Explicit State Management
You control when state is persisted:

```java
private Behavior<Command> handleUpdate(UpdateOrder cmd) {
    currentOrder.setAmount(cmd.getNewAmount());
    currentOrder = orderRepository.save(currentOrder);  // Explicit save
    return Behaviors.same();
}
```

### 3. Event Sourcing
All state changes are stored as immutable events:

```java
OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerId, amount);
persistEvent(event);  // Store event
state.apply(event);   // Apply to in-memory state
```

### 4. Snapshot Recovery
Fast recovery from snapshots instead of replaying all events:

```java
Order order = loadFromSnapshot(actorId)
    .orElseGet(() -> orderRepository.findById(actorId).orElse(null));
```

## Testing

Run the tests:

```bash
./gradlew :example:persistence:test
```

## Best Practices Demonstrated

- ✅ Direct repository injection (no custom abstractions)
- ✅ Optimistic locking for concurrent updates
- ✅ Event sourcing for complete audit trails
- ✅ Snapshots for fast recovery
- ✅ Explicit state management
- ✅ Proper error handling
- ✅ Clear separation of patterns

## Production Considerations

For production use, consider:

- Use PostgreSQL instead of H2
- Configure connection pooling (HikariCP)
- Add retry logic (Spring Retry or Resilience4j)
- Implement health checks
- Add metrics and monitoring
- Configure snapshot cleanup policies
- Implement event archival for event sourcing

See the [persistence documentation](../../mkdocs/docs/persistence/) for detailed production configuration.
