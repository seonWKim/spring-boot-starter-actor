# Event Sourcing Patterns with Actors

This guide demonstrates event sourcing patterns for actors using Spring Data repositories. Event sourcing stores all changes as a sequence of events, enabling full audit trails and state reconstruction.

## Overview

Event sourcing is a pattern where state changes are stored as events rather than updating state directly. This provides:
- Complete audit trail of all changes
- Ability to reconstruct state at any point in time
- Support for temporal queries
- Natural fit for event-driven architectures

**Note:** This guide shows manual event sourcing patterns using Spring Data. For production event sourcing systems, consider frameworks like Axon Framework or EventStoreDB.

## Basic Event Sourcing Pattern

### 1. Define Events

```java
package com.example.order;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_events")
public class OrderEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_id", nullable = false)
    private String orderId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "event_data", columnDefinition = "jsonb")
    private String eventData;
    
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "user_id")
    private String userId;
    
    @Version
    private Long version;
    
    // Constructors
    protected OrderEvent() {}
    
    public OrderEvent(String orderId, String eventType, String eventData, long sequenceNumber) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = Instant.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public Instant getTimestamp() { return timestamp; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}

// Domain event types
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "ORDER_CREATED"),
    @JsonSubTypes.Type(value = OrderApprovedEvent.class, name = "ORDER_APPROVED"),
    @JsonSubTypes.Type(value = OrderItemAddedEvent.class, name = "ORDER_ITEM_ADDED"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = "ORDER_CANCELLED")
})
public interface OrderDomainEvent {
    String getEventType();
}

public record OrderCreatedEvent(
    String orderId,
    String customerId,
    double amount
) implements OrderDomainEvent {
    @Override
    public String getEventType() { return "ORDER_CREATED"; }
}

public record OrderApprovedEvent(
    String orderId,
    String approvedBy
) implements OrderDomainEvent {
    @Override
    public String getEventType() { return "ORDER_APPROVED"; }
}

public record OrderItemAddedEvent(
    String orderId,
    String productId,
    int quantity,
    double price
) implements OrderDomainEvent {
    @Override
    public String getEventType() { return "ORDER_ITEM_ADDED"; }
}

public record OrderCancelledEvent(
    String orderId,
    String reason
) implements OrderDomainEvent {
    @Override
    public String getEventType() { return "ORDER_CANCELLED"; }
}
```

### 2. Create Event Repository

```java
package com.example.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    
    List<OrderEvent> findByOrderIdOrderBySequenceNumberAsc(String orderId);
    
    @Query("SELECT MAX(e.sequenceNumber) FROM OrderEvent e WHERE e.orderId = :orderId")
    Long findMaxSequenceNumber(@Param("orderId") String orderId);
    
    List<OrderEvent> findByOrderIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
        String orderId, long sequenceNumber);
    
    List<OrderEvent> findByOrderIdAndTimestampBetweenOrderBySequenceNumberAsc(
        String orderId, Instant start, Instant end);
    
    @Query("SELECT DISTINCT e.orderId FROM OrderEvent e")
    List<String> findAllOrderIds();
}
```

### 3. Create Event-Sourced Actor

```java
package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EventSourcedOrderActor implements SpringActor<EventSourcedOrderActor.Command> {
    
    private final OrderEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    
    public EventSourcedOrderActor(
            OrderEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }
    
    // Commands
    public interface Command {}
    
    public static class CreateOrder extends AskCommand<OrderResponse> implements Command {
        private final String customerId;
        private final double amount;
        
        public CreateOrder(String customerId, double amount) {
            this.customerId = customerId;
            this.amount = amount;
        }
        
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
    }
    
    public static class AddItem extends AskCommand<OrderResponse> implements Command {
        private final String productId;
        private final int quantity;
        private final double price;
        
        public AddItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
        
        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
    }
    
    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {
        private final String approvedBy;
        
        public ApproveOrder(String approvedBy) {
            this.approvedBy = approvedBy;
        }
        
        public String getApprovedBy() { return approvedBy; }
    }
    
    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}
    
    public static class GetHistory extends AskCommand<HistoryResponse> implements Command {}
    
    public record OrderResponse(boolean success, OrderState state, String message) {}
    public record HistoryResponse(List<OrderEvent> events) {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                // Rebuild state from events
                OrderState state = rebuildState(actorContext.actorId());
                return new EventSourcedBehavior(
                    ctx, actorContext, eventRepository, objectMapper, state);
            })
            .onMessage(CreateOrder.class, EventSourcedBehavior::handleCreateOrder)
            .onMessage(AddItem.class, EventSourcedBehavior::handleAddItem)
            .onMessage(ApproveOrder.class, EventSourcedBehavior::handleApproveOrder)
            .onMessage(GetOrder.class, EventSourcedBehavior::handleGetOrder)
            .onMessage(GetHistory.class, EventSourcedBehavior::handleGetHistory)
            .build();
    }
    
    private OrderState rebuildState(String orderId) {
        List<OrderEvent> events = eventRepository.findByOrderIdOrderBySequenceNumberAsc(orderId);
        OrderState state = new OrderState(orderId);
        
        for (OrderEvent event : events) {
            try {
                OrderDomainEvent domainEvent = deserializeEvent(event);
                state.apply(domainEvent);
            } catch (Exception e) {
                throw new RuntimeException("Failed to rebuild state from events", e);
            }
        }
        
        return state;
    }
    
    private OrderDomainEvent deserializeEvent(OrderEvent event) throws Exception {
        return objectMapper.readValue(event.getEventData(), OrderDomainEvent.class);
    }
    
    // State class
    public static class OrderState {
        private final String orderId;
        private String customerId;
        private double amount;
        private OrderStatus status;
        private List<OrderItem> items;
        private long sequenceNumber;
        
        public OrderState(String orderId) {
            this.orderId = orderId;
            this.items = new ArrayList<>();
            this.status = OrderStatus.PENDING;
            this.sequenceNumber = 0;
        }
        
        public void apply(OrderDomainEvent event) {
            switch (event) {
                case OrderCreatedEvent e -> {
                    this.customerId = e.customerId();
                    this.amount = e.amount();
                    this.status = OrderStatus.PENDING;
                }
                case OrderItemAddedEvent e -> {
                    this.items.add(new OrderItem(e.productId(), e.quantity(), e.price()));
                }
                case OrderApprovedEvent e -> {
                    this.status = OrderStatus.APPROVED;
                }
                case OrderCancelledEvent e -> {
                    this.status = OrderStatus.CANCELLED;
                }
                default -> throw new IllegalArgumentException("Unknown event type: " + event);
            }
            this.sequenceNumber++;
        }
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
        public OrderStatus getStatus() { return status; }
        public List<OrderItem> getItems() { return items; }
        public long getSequenceNumber() { return sequenceNumber; }
    }
    
    public record OrderItem(String productId, int quantity, double price) {}
    
    public enum OrderStatus {
        PENDING, APPROVED, CANCELLED
    }
    
    // Behavior class
    private static class EventSourcedBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderEventRepository eventRepository;
        private final ObjectMapper objectMapper;
        private final OrderState state;
        
        EventSourcedBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderEventRepository eventRepository,
                ObjectMapper objectMapper,
                OrderState state) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.eventRepository = eventRepository;
            this.objectMapper = objectMapper;
            this.state = state;
        }
        
        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (state.getSequenceNumber() > 0) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }
            
            try {
                // Create event
                OrderCreatedEvent domainEvent = new OrderCreatedEvent(
                    actorContext.actorId(), cmd.getCustomerId(), cmd.getAmount());
                
                // Persist event
                OrderEvent event = persistEvent(domainEvent, state.getSequenceNumber() + 1);
                
                // Apply to state
                state.apply(domainEvent);
                
                ctx.getLog().info("Order created: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Order created"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleAddItem(AddItem cmd) {
            try {
                OrderItemAddedEvent domainEvent = new OrderItemAddedEvent(
                    actorContext.actorId(), cmd.getProductId(), cmd.getQuantity(), cmd.getPrice());
                
                persistEvent(domainEvent, state.getSequenceNumber() + 1);
                state.apply(domainEvent);
                
                ctx.getLog().info("Item added to order: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Item added"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to add item", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            try {
                OrderApprovedEvent domainEvent = new OrderApprovedEvent(
                    actorContext.actorId(), cmd.getApprovedBy());
                
                persistEvent(domainEvent, state.getSequenceNumber() + 1);
                state.apply(domainEvent);
                
                ctx.getLog().info("Order approved: {}", actorContext.actorId());
                cmd.reply(new OrderResponse(true, state, "Order approved"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to approve order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            cmd.reply(new OrderResponse(true, state, "Order retrieved"));
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetHistory(GetHistory cmd) {
            List<OrderEvent> events = eventRepository
                .findByOrderIdOrderBySequenceNumberAsc(actorContext.actorId());
            cmd.reply(new HistoryResponse(events));
            return Behaviors.same();
        }
        
        private OrderEvent persistEvent(OrderDomainEvent domainEvent, long sequenceNumber) 
                throws Exception {
            String eventData = objectMapper.writeValueAsString(domainEvent);
            OrderEvent event = new OrderEvent(
                actorContext.actorId(),
                domainEvent.getEventType(),
                eventData,
                sequenceNumber
            );
            return eventRepository.save(event);
        }
    }
}
```

### 4. Database Schema

```sql
-- Event store table
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    sequence_number BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    version BIGINT DEFAULT 0,
    
    CONSTRAINT unique_order_sequence UNIQUE (order_id, sequence_number)
);

CREATE INDEX idx_order_events_order_id ON order_events(order_id);
CREATE INDEX idx_order_events_timestamp ON order_events(timestamp);
CREATE INDEX idx_order_events_event_type ON order_events(event_type);
```

## Advanced Patterns

### Pattern 1: Snapshots for Performance

```java
@Entity
@Table(name = "order_snapshots")
public class OrderSnapshot {
    @Id
    private String orderId;
    
    @Column(columnDefinition = "jsonb")
    private String stateData;
    
    private Long sequenceNumber;
    
    private Instant timestamp;
    
    // Constructors, getters, setters
}

@Repository
public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, String> {
    Optional<OrderSnapshot> findTopByOrderIdOrderBySequenceNumberDesc(String orderId);
}

@Component
public class SnapshotStrategy {
    
    private static final int SNAPSHOT_INTERVAL = 100;
    private final OrderSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    
    public OrderState rebuildStateWithSnapshot(
            String orderId,
            OrderEventRepository eventRepository) {
        
        // Try to load latest snapshot
        Optional<OrderSnapshot> snapshot = 
            snapshotRepository.findTopByOrderIdOrderBySequenceNumberDesc(orderId);
        
        OrderState state;
        long fromSequence = 0;
        
        if (snapshot.isPresent()) {
            // Deserialize state from snapshot
            try {
                state = objectMapper.readValue(
                    snapshot.get().getStateData(), OrderState.class);
                fromSequence = snapshot.get().getSequenceNumber();
            } catch (Exception e) {
                state = new OrderState(orderId);
            }
        } else {
            state = new OrderState(orderId);
        }
        
        // Apply events after snapshot
        List<OrderEvent> events = eventRepository
            .findByOrderIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                orderId, fromSequence);
        
        for (OrderEvent event : events) {
            OrderDomainEvent domainEvent = deserializeEvent(event);
            state.apply(domainEvent);
        }
        
        return state;
    }
    
    public void saveSnapshotIfNeeded(String orderId, OrderState state) {
        if (state.getSequenceNumber() % SNAPSHOT_INTERVAL == 0) {
            try {
                String stateData = objectMapper.writeValueAsString(state);
                OrderSnapshot snapshot = new OrderSnapshot();
                snapshot.setOrderId(orderId);
                snapshot.setStateData(stateData);
                snapshot.setSequenceNumber(state.getSequenceNumber());
                snapshot.setTimestamp(Instant.now());
                
                snapshotRepository.save(snapshot);
            } catch (Exception e) {
                // Log error but don't fail - snapshots are optimization
            }
        }
    }
}
```

### Pattern 2: Event Projections

```java
// Read model for queries
@Entity
@Table(name = "order_projections")
public class OrderProjection {
    @Id
    private String orderId;
    
    private String customerId;
    private Double totalAmount;
    private String status;
    private Integer itemCount;
    private Instant lastUpdated;
    
    // Getters and setters
}

@Repository
public interface OrderProjectionRepository extends JpaRepository<OrderProjection, String> {
    List<OrderProjection> findByCustomerId(String customerId);
    List<OrderProjection> findByStatus(String status);
}

@Component
public class OrderProjectionBuilder {
    
    private final OrderProjectionRepository projectionRepository;
    
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        OrderProjection projection = new OrderProjection();
        projection.setOrderId(event.orderId());
        projection.setCustomerId(event.customerId());
        projection.setTotalAmount(event.amount());
        projection.setStatus("PENDING");
        projection.setItemCount(0);
        projection.setLastUpdated(Instant.now());
        
        projectionRepository.save(projection);
    }
    
    @EventListener
    public void onOrderItemAdded(OrderItemAddedEvent event) {
        projectionRepository.findById(event.orderId()).ifPresent(projection -> {
            projection.setItemCount(projection.getItemCount() + 1);
            projection.setLastUpdated(Instant.now());
            projectionRepository.save(projection);
        });
    }
    
    @EventListener
    public void onOrderApproved(OrderApprovedEvent event) {
        projectionRepository.findById(event.orderId()).ifPresent(projection -> {
            projection.setStatus("APPROVED");
            projection.setLastUpdated(Instant.now());
            projectionRepository.save(projection);
        });
    }
}
```

### Pattern 3: Temporal Queries

```java
@Component
public class OrderHistoryService {
    
    private final OrderEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    
    public OrderState getStateAtTime(String orderId, Instant timestamp) {
        List<OrderEvent> events = eventRepository
            .findByOrderIdAndTimestampBetweenOrderBySequenceNumberAsc(
                orderId, Instant.EPOCH, timestamp);
        
        OrderState state = new OrderState(orderId);
        for (OrderEvent event : events) {
            OrderDomainEvent domainEvent = deserializeEvent(event);
            state.apply(domainEvent);
        }
        
        return state;
    }
    
    public List<OrderState> getStateHistory(String orderId, int limit) {
        List<OrderEvent> events = eventRepository
            .findByOrderIdOrderBySequenceNumberAsc(orderId);
        
        List<OrderState> history = new ArrayList<>();
        OrderState state = new OrderState(orderId);
        
        for (OrderEvent event : events) {
            OrderDomainEvent domainEvent = deserializeEvent(event);
            state.apply(domainEvent);
            
            // Clone state for history
            history.add(cloneState(state));
            
            if (history.size() >= limit) {
                break;
            }
        }
        
        return history;
    }
}
```

### Pattern 4: CQRS (Command Query Responsibility Segregation)

```java
// Command side - Event sourced
@Component
public class OrderCommandService {
    
    private final SpringActorSystem actorSystem;
    
    public CompletionStage<OrderResponse> createOrder(
            String orderId, String customerId, double amount) {
        
        return actorSystem.actorOf(EventSourcedOrderActor.class, orderId)
            .ask(new EventSourcedOrderActor.CreateOrder(customerId, amount));
    }
    
    public CompletionStage<OrderResponse> approveOrder(String orderId, String approvedBy) {
        return actorSystem.actorOf(EventSourcedOrderActor.class, orderId)
            .ask(new EventSourcedOrderActor.ApproveOrder(approvedBy));
    }
}

// Query side - Optimized projections
@Component
public class OrderQueryService {
    
    private final OrderProjectionRepository projectionRepository;
    
    public List<OrderProjection> findOrdersByCustomer(String customerId) {
        return projectionRepository.findByCustomerId(customerId);
    }
    
    public List<OrderProjection> findPendingOrders() {
        return projectionRepository.findByStatus("PENDING");
    }
    
    public OrderStatistics getOrderStatistics(String customerId) {
        List<OrderProjection> orders = projectionRepository.findByCustomerId(customerId);
        
        return new OrderStatistics(
            customerId,
            orders.size(),
            orders.stream().mapToDouble(OrderProjection::getTotalAmount).sum(),
            orders.stream().mapToInt(OrderProjection::getItemCount).sum()
        );
    }
}

public record OrderStatistics(
    String customerId,
    int totalOrders,
    double totalAmount,
    int totalItems
) {}
```

## Testing Event Sourcing

```java
@SpringBootTest
class EventSourcedOrderActorTest {
    
    @Autowired
    private OrderEventRepository eventRepository;
    
    @Autowired
    private EventSourcedOrderActor actor;
    
    @Test
    void testEventSourcingFlow() {
        String orderId = "order-123";
        
        // Create order
        actor.handleCreateOrder(new CreateOrder("customer-1", 100.0));
        
        // Add items
        actor.handleAddItem(new AddItem("product-1", 2, 50.0));
        
        // Verify events stored
        List<OrderEvent> events = eventRepository
            .findByOrderIdOrderBySequenceNumberAsc(orderId);
        
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(events.get(1).getEventType()).isEqualTo("ORDER_ITEM_ADDED");
        
        // Verify state can be rebuilt
        OrderState rebuilt = rebuildState(orderId);
        assertThat(rebuilt.getCustomerId()).isEqualTo("customer-1");
        assertThat(rebuilt.getItems()).hasSize(1);
    }
}
```

## Best Practices

1. **Always use sequence numbers** to ensure event ordering
2. **Make events immutable** - never modify persisted events
3. **Use snapshots** for actors with many events
4. **Implement idempotency** for event handlers
5. **Version your events** for schema evolution
6. **Keep events small** - don't embed large payloads
7. **Use projections** for complex queries
8. **Monitor event store growth** and implement archival strategies

## Summary

Event sourcing with actors provides:
- ✅ Complete audit trail of all state changes
- ✅ Ability to reconstruct state at any point
- ✅ Support for temporal queries
- ✅ Natural integration with event-driven architectures
- ✅ Excellent debugging capabilities

Use event sourcing when you need:
- Full audit trail for compliance
- Ability to replay events
- Temporal queries
- Event-driven integrations
