# MongoDB Integration with Actors

This guide demonstrates how to integrate MongoDB with actors using Spring Data MongoDB, including both blocking and reactive approaches.

## Overview

MongoDB is a popular NoSQL document database that pairs well with actor systems. Spring Data MongoDB provides excellent support for both synchronous and asynchronous operations, making it suitable for high-throughput actor applications.

## Prerequisites

Add Spring Data MongoDB dependency:

```gradle
dependencies {
    // For blocking MongoDB operations
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    
    // OR for reactive MongoDB operations
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb-reactive'
    
    implementation 'io.github.seonwkim:spring-boot-starter-actor:1.0.0'
}
```

## Blocking MongoDB Integration

### 1. Define Your Document

```java
package com.example.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
public class Order {
    
    @Id
    private String id;
    
    @Indexed
    @Field("customer_id")
    private String customerId;
    
    private Double amount;
    
    @Indexed
    private OrderStatus status;
    
    @Version
    private Long version; // For optimistic locking
    
    @Field("created_at")
    private Instant createdAt;
    
    @Field("updated_at")
    private Instant updatedAt;
    
    // Embedded documents
    private List<OrderItem> items = new ArrayList<>();
    
    // Nested document class
    public static class OrderItem {
        private String productId;
        private Integer quantity;
        private Double price;
        
        // Constructors, getters, setters
        public OrderItem() {}
        
        public OrderItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
        
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }
    
    // Constructors
    public Order() {}
    
    public Order(String id, String customerId, double amount) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { 
        this.amount = amount;
        this.updatedAt = Instant.now();
    }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { 
        this.status = status;
        this.updatedAt = Instant.now();
    }
    
    public Long getVersion() { return version; }
    
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    
    public void addItem(OrderItem item) {
        this.items.add(item);
        this.updatedAt = Instant.now();
    }
}

enum OrderStatus {
    PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
}
```

### 2. Create a Repository

```java
package com.example.order;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    
    // Derived query methods
    List<Order> findByCustomerId(String customerId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status);
    
    // Custom query using MongoDB query syntax
    @Query("{ 'amount': { $gte: ?0 } }")
    List<Order> findHighValueOrders(double minAmount);
    
    @Query("{ 'customerId': ?0, 'createdAt': { $gte: ?1, $lte: ?2 } }")
    List<Order> findByCustomerIdAndDateRange(String customerId, Instant start, Instant end);
    
    // Query with projection
    @Query(value = "{ 'status': ?0 }", fields = "{ 'id': 1, 'customerId': 1, 'amount': 1 }")
    List<Order> findOrderSummariesByStatus(OrderStatus status);
}
```

### 3. Create the Actor

```java
package com.example.order;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderActor implements SpringActor<OrderActor.Command> {
    
    private final OrderRepository orderRepository;
    
    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    // Commands
    public interface Command {}
    
    public static class CreateOrder extends AskCommand<OrderResponse> implements Command {
        private final String customerId;
        private final double amount;
        private final List<Order.OrderItem> items;
        
        public CreateOrder(String customerId, double amount, List<Order.OrderItem> items) {
            this.customerId = customerId;
            this.amount = amount;
            this.items = items;
        }
        
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
        public List<Order.OrderItem> getItems() { return items; }
    }
    
    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}
    
    public static class UpdateOrder extends AskCommand<OrderResponse> implements Command {
        private final double newAmount;
        
        public UpdateOrder(double newAmount) {
            this.newAmount = newAmount;
        }
        
        public double getNewAmount() { return newAmount; }
    }
    
    public static class AddItem extends AskCommand<OrderResponse> implements Command {
        private final Order.OrderItem item;
        
        public AddItem(Order.OrderItem item) {
            this.item = item;
        }
        
        public Order.OrderItem getItem() { return item; }
    }
    
    public record OrderResponse(boolean success, Order order, String message) {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                Order order = orderRepository.findById(actorContext.actorId()).orElse(null);
                return new OrderActorBehavior(ctx, actorContext, orderRepository, order);
            })
            .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrder)
            .onMessage(GetOrder.class, OrderActorBehavior::handleGetOrder)
            .onMessage(UpdateOrder.class, OrderActorBehavior::handleUpdateOrder)
            .onMessage(AddItem.class, OrderActorBehavior::handleAddItem)
            .build();
    }
    
    private static class OrderActorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderRepository orderRepository;
        private Order currentOrder;
        
        OrderActorBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderRepository orderRepository,
                Order currentOrder) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
            this.currentOrder = currentOrder;
        }
        
        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }
            
            try {
                currentOrder = new Order(
                    actorContext.actorId(),
                    cmd.getCustomerId(),
                    cmd.getAmount()
                );
                
                // Add items if provided
                if (cmd.getItems() != null) {
                    cmd.getItems().forEach(currentOrder::addItem);
                }
                
                currentOrder = orderRepository.save(currentOrder);
                
                ctx.getLog().info("Order created: {}", currentOrder.getId());
                cmd.reply(new OrderResponse(true, currentOrder, "Order created successfully"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, "Failed to create order: " + e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder == null) {
                currentOrder = orderRepository.findById(actorContext.actorId()).orElse(null);
            }
            
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(true, currentOrder, "Order retrieved"));
            } else {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleUpdateOrder(UpdateOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }
            
            try {
                currentOrder.setAmount(cmd.getNewAmount());
                currentOrder = orderRepository.save(currentOrder);
                
                ctx.getLog().info("Order updated: {}", currentOrder.getId());
                cmd.reply(new OrderResponse(true, currentOrder, "Order updated"));
                
            } catch (OptimisticLockingFailureException e) {
                ctx.getLog().warn("Optimistic locking failure, reloading order");
                currentOrder = orderRepository.findById(currentOrder.getId()).orElse(null);
                cmd.reply(new OrderResponse(false, currentOrder, 
                    "Order was modified, please retry"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to update order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleAddItem(AddItem cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }
            
            try {
                currentOrder.addItem(cmd.getItem());
                currentOrder = orderRepository.save(currentOrder);
                
                ctx.getLog().info("Item added to order: {}", currentOrder.getId());
                cmd.reply(new OrderResponse(true, currentOrder, "Item added"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to add item", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
    }
}
```

### 4. Configure MongoDB

```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb://${MONGO_USERNAME:admin}:${MONGO_PASSWORD:password}@${MONGO_HOST:localhost}:27017/${MONGO_DATABASE:orderdb}?authSource=admin
      # Or individual properties:
      # host: localhost
      # port: 27017
      # database: orderdb
      # username: admin
      # password: password
      # authentication-database: admin
```

## Reactive MongoDB Integration

For high-throughput, non-blocking operations, use Spring Data MongoDB Reactive.

### 1. Define Reactive Repository

```java
package com.example.order;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveOrderRepository extends ReactiveMongoRepository<Order, String> {
    
    Flux<Order> findByCustomerId(String customerId);
    Flux<Order> findByStatus(OrderStatus status);
    Mono<Order> findByIdAndCustomerId(String id, String customerId);
    
    @Query("{ 'amount': { $gte: ?0 } }")
    Flux<Order> findHighValueOrders(double minAmount);
}
```

### 2. Create Reactive Actor

```java
package com.example.order;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

@Component
public class ReactiveOrderActor implements SpringActor<ReactiveOrderActor.Command> {
    
    private final ReactiveOrderRepository orderRepository;
    
    public ReactiveOrderActor(ReactiveOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
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
    
    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}
    
    public record OrderResponse(boolean success, Order order, String message) {}
    
    // Internal messages for async completion
    private record OrderSaved(Order order) implements Command {}
    private record OrderLoaded(Order order) implements Command {}
    private record OperationFailed(String message) implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> new ReactiveOrderBehavior(ctx, actorContext, orderRepository))
            .onMessage(CreateOrder.class, ReactiveOrderBehavior::handleCreateOrder)
            .onMessage(GetOrder.class, ReactiveOrderBehavior::handleGetOrder)
            .onMessage(OrderSaved.class, ReactiveOrderBehavior::handleOrderSaved)
            .onMessage(OrderLoaded.class, ReactiveOrderBehavior::handleOrderLoaded)
            .onMessage(OperationFailed.class, ReactiveOrderBehavior::handleOperationFailed)
            .build();
    }
    
    private static class ReactiveOrderBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final ReactiveOrderRepository orderRepository;
        private Order currentOrder;
        private AskCommand<OrderResponse> pendingCommand;
        
        ReactiveOrderBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                ReactiveOrderRepository orderRepository) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
        }
        
        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }
            
            this.pendingCommand = cmd;
            
            // Non-blocking save
            Order newOrder = new Order(
                actorContext.actorId(),
                cmd.getCustomerId(),
                cmd.getAmount()
            );
            
            orderRepository.save(newOrder)
                .subscribe(
                    savedOrder -> ctx.getSelf().tell(new OrderSaved(savedOrder)),
                    error -> ctx.getSelf().tell(new OperationFailed(error.getMessage()))
                );
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(true, currentOrder, "Order retrieved from cache"));
                return Behaviors.same();
            }
            
            this.pendingCommand = cmd;
            
            // Non-blocking load
            orderRepository.findById(actorContext.actorId())
                .subscribe(
                    order -> ctx.getSelf().tell(new OrderLoaded(order)),
                    error -> ctx.getSelf().tell(new OperationFailed(error.getMessage())),
                    () -> ctx.getSelf().tell(new OperationFailed("Order not found"))
                );
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOrderSaved(OrderSaved msg) {
            currentOrder = msg.order();
            ctx.getLog().info("Order saved: {}", currentOrder.getId());
            
            if (pendingCommand != null) {
                pendingCommand.reply(new OrderResponse(true, currentOrder, "Order created"));
                pendingCommand = null;
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOrderLoaded(OrderLoaded msg) {
            currentOrder = msg.order();
            ctx.getLog().info("Order loaded: {}", currentOrder.getId());
            
            if (pendingCommand != null) {
                pendingCommand.reply(new OrderResponse(true, currentOrder, "Order retrieved"));
                pendingCommand = null;
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOperationFailed(OperationFailed msg) {
            ctx.getLog().error("Operation failed: {}", msg.message());
            
            if (pendingCommand != null) {
                pendingCommand.reply(new OrderResponse(false, null, msg.message()));
                pendingCommand = null;
            }
            
            return Behaviors.same();
        }
    }
}
```

### 3. Configure Reactive MongoDB

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/orderdb
  
# Optional: Configure reactive thread pool
spring:
  reactor:
    context-propagation: auto
```

## Advanced Patterns

### Pattern 1: Using MongoTemplate for Complex Queries

```java
@Component
public class OrderQueryService {
    
    private final MongoTemplate mongoTemplate;
    
    public OrderQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }
    
    public List<Order> findOrdersWithComplexCriteria(
            String customerId,
            Double minAmount,
            OrderStatus status,
            int limit) {
        
        Query query = new Query();
        
        if (customerId != null) {
            query.addCriteria(Criteria.where("customerId").is(customerId));
        }
        
        if (minAmount != null) {
            query.addCriteria(Criteria.where("amount").gte(minAmount));
        }
        
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        
        query.limit(limit);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        
        return mongoTemplate.find(query, Order.class);
    }
    
    public long updateOrderStatus(String orderId, OrderStatus newStatus) {
        Query query = Query.query(Criteria.where("_id").is(orderId));
        Update update = new Update()
            .set("status", newStatus)
            .set("updatedAt", Instant.now());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, Order.class);
        return result.getModifiedCount();
    }
}
```

### Pattern 2: Aggregation Pipeline

```java
@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    
    @Aggregation(pipeline = {
        "{ $match: { status: ?0 } }",
        "{ $group: { _id: '$customerId', totalAmount: { $sum: '$amount' }, count: { $sum: 1 } } }",
        "{ $sort: { totalAmount: -1 } }",
        "{ $limit: 10 }"
    })
    List<CustomerOrderSummary> findTopCustomersByStatus(OrderStatus status);
}

public interface CustomerOrderSummary {
    String getCustomerId();
    Double getTotalAmount();
    Integer getCount();
}
```

### Pattern 3: Time-Series Data with MongoDB

```java
@Document(collection = "order_events")
@TimeSeries(
    timeField = "timestamp",
    metaField = "orderId",
    granularity = Granularity.SECONDS
)
public class OrderEvent {
    
    @Id
    private String id;
    
    private String orderId;
    private String eventType;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    // Getters and setters
}

@Repository
public interface OrderEventRepository extends MongoRepository<OrderEvent, String> {
    List<OrderEvent> findByOrderIdOrderByTimestampDesc(String orderId);
}
```

### Pattern 4: Change Streams for Real-Time Updates

```java
@Component
public class OrderChangeStreamListener {
    
    private final ReactiveMongoTemplate mongoTemplate;
    private final SpringActorSystem actorSystem;
    
    @PostConstruct
    public void listenToOrderChanges() {
        ChangeStreamRequest<Order> request = ChangeStreamRequest.builder()
            .collection("orders")
            .filter(newAggregation(
                match(where("operationType").in("insert", "update"))
            ))
            .build();
        
        mongoTemplate.changeStream(request, Order.class)
            .subscribe(changeStreamEvent -> {
                Order order = changeStreamEvent.getBody();
                if (order != null) {
                    // Notify the corresponding actor
                    actorSystem.actorOf(OrderActor.class, order.getId())
                        .tell(new OrderActor.OrderUpdatedExternally());
                }
            });
    }
}
```

## Testing

### Unit Testing with Embedded MongoDB

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class OrderRepositoryTest {
    
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0")
        .withExposedPorts(27017);
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testSaveAndFind() {
        Order order = new Order("order-1", "customer-1", 100.0);
        orderRepository.save(order);
        
        Order found = orderRepository.findById("order-1").orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.getCustomerId()).isEqualTo("customer-1");
    }
}
```

## Performance Optimization

### 1. Create Indexes

```java
@Configuration
public class MongoConfig {
    
    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Collections.emptyList());
    }
    
    @PostConstruct
    public void initIndexes(MongoTemplate mongoTemplate) {
        IndexOperations indexOps = mongoTemplate.indexOps(Order.class);
        
        // Compound index
        indexOps.ensureIndex(new Index()
            .on("customerId", Sort.Direction.ASC)
            .on("status", Sort.Direction.ASC));
        
        // Text index for search
        indexOps.ensureIndex(new Index()
            .on("customerName", Sort.Direction.ASC)
            .text());
    }
}
```

### 2. Use Projections

```java
public interface OrderSummary {
    String getId();
    String getCustomerId();
    Double getAmount();
}

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    List<OrderSummary> findByStatus(OrderStatus status);
}
```

### 3. Connection Pool Configuration

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/orderdb
  mongodb:
    embedded:
      version: 6.0.3
    
# Advanced configuration
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/orderdb?maxPoolSize=50&minPoolSize=10&maxIdleTimeMS=60000&waitQueueTimeoutMS=30000
```

## Summary

MongoDB integration with actors provides:
- ✅ Flexible document model for complex data
- ✅ Excellent performance for read-heavy workloads
- ✅ Native support for reactive/non-blocking operations
- ✅ Powerful query and aggregation capabilities
- ✅ Change streams for real-time updates
- ✅ Easy horizontal scaling

Choose blocking repositories for simpler applications and reactive repositories for high-throughput, non-blocking systems.
