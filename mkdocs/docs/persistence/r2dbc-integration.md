# R2DBC Integration with Actors

This guide demonstrates how to integrate R2DBC (Reactive Relational Database Connectivity) with actors for non-blocking, reactive SQL database access.

## Overview

R2DBC provides reactive, non-blocking access to relational databases. This is ideal for high-throughput actor systems that need the power of SQL databases without blocking threads. R2DBC works with PostgreSQL, MySQL, H2, Microsoft SQL Server, and other databases.

## Prerequisites

Add Spring Data R2DBC dependency:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    
    // Choose your database driver:
    implementation 'io.r2dbc:r2dbc-postgresql'  // PostgreSQL
    // OR
    // implementation 'io.asyncer:r2dbc-mysql'  // MySQL
    // OR
    // implementation 'io.r2dbc:r2dbc-h2'       // H2
    
    implementation 'io.github.seonwkim:spring-boot-starter-actor:1.0.0'
}
```

## Basic Setup

### 1. Define Your Entity

```java
package com.example.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("orders")
public class Order {
    
    @Id
    private Long id;
    
    @Column("order_id")
    private String orderId;
    
    @Column("customer_id")
    private String customerId;
    
    private Double amount;
    
    private OrderStatus status;
    
    @Version
    private Long version;  // For optimistic locking
    
    @Column("created_at")
    private Instant createdAt;
    
    @Column("updated_at")
    private Instant updatedAt;
    
    // Constructors
    public Order() {}
    
    public Order(String orderId, String customerId, double amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
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
}

enum OrderStatus {
    PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
}
```

### 2. Create Database Schema

```sql
-- schema.sql
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_order_id ON orders(order_id);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

-- Order items table for one-to-many relationship
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### 3. Create R2DBC Repository

```java
package com.example.order;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {
    
    // Derived query methods
    Mono<Order> findByOrderId(String orderId);
    Flux<Order> findByCustomerId(String customerId);
    Flux<Order> findByStatus(OrderStatus status);
    Flux<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status);
    
    // Custom queries
    @Query("SELECT * FROM orders WHERE amount >= :minAmount ORDER BY created_at DESC LIMIT :limit")
    Flux<Order> findHighValueOrders(@Param("minAmount") double minAmount, @Param("limit") int limit);
    
    @Query("SELECT * FROM orders WHERE customer_id = :customerId " +
           "AND created_at >= :start AND created_at <= :end")
    Flux<Order> findByCustomerIdAndDateRange(
        @Param("customerId") String customerId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
    
    @Query("SELECT COUNT(*) FROM orders WHERE status = :status")
    Mono<Long> countByStatus(@Param("status") String status);
}
```

### 4. Create Reactive Actor

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
        
        public CreateOrder(String customerId, double amount) {
            this.customerId = customerId;
            this.amount = amount;
        }
        
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
    }
    
    public static class GetOrder extends AskCommand<OrderResponse> implements Command {}
    
    public static class UpdateAmount extends AskCommand<OrderResponse> implements Command {
        private final double newAmount;
        
        public UpdateAmount(double newAmount) {
            this.newAmount = newAmount;
        }
        
        public double getNewAmount() { return newAmount; }
    }
    
    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {}
    
    public record OrderResponse(boolean success, Order order, String message) {}
    
    // Internal messages for async completion
    private record OrderSaved(Order order, AskCommand<OrderResponse> originalCommand) implements Command {}
    private record OrderLoaded(Order order, AskCommand<OrderResponse> originalCommand) implements Command {}
    private record OrderUpdated(Order order, AskCommand<OrderResponse> originalCommand) implements Command {}
    private record OperationFailed(String message, AskCommand<OrderResponse> originalCommand) implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> new OrderBehavior(ctx, actorContext, orderRepository))
            .onMessage(CreateOrder.class, OrderBehavior::handleCreateOrder)
            .onMessage(GetOrder.class, OrderBehavior::handleGetOrder)
            .onMessage(UpdateAmount.class, OrderBehavior::handleUpdateAmount)
            .onMessage(ApproveOrder.class, OrderBehavior::handleApproveOrder)
            .onMessage(OrderSaved.class, OrderBehavior::handleOrderSaved)
            .onMessage(OrderLoaded.class, OrderBehavior::handleOrderLoaded)
            .onMessage(OrderUpdated.class, OrderBehavior::handleOrderUpdated)
            .onMessage(OperationFailed.class, OrderBehavior::handleOperationFailed)
            .build();
    }
    
    private static class OrderBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderRepository orderRepository;
        private Order currentOrder;
        
        OrderBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderRepository orderRepository) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
        }
        
        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }
            
            Order newOrder = new Order(
                actorContext.actorId(),
                cmd.getCustomerId(),
                cmd.getAmount()
            );
            
            // Non-blocking save
            orderRepository.save(newOrder)
                .subscribe(
                    savedOrder -> ctx.getSelf().tell(new OrderSaved(savedOrder, cmd)),
                    error -> ctx.getSelf().tell(new OperationFailed(error.getMessage(), cmd))
                );
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder != null) {
                // Return cached order
                cmd.reply(new OrderResponse(true, currentOrder, "Order retrieved from cache"));
                return Behaviors.same();
            }
            
            // Non-blocking load
            orderRepository.findByOrderId(actorContext.actorId())
                .subscribe(
                    order -> ctx.getSelf().tell(new OrderLoaded(order, cmd)),
                    error -> ctx.getSelf().tell(new OperationFailed(error.getMessage(), cmd)),
                    () -> ctx.getSelf().tell(new OperationFailed("Order not found", cmd))
                );
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleUpdateAmount(UpdateAmount cmd) {
            if (currentOrder == null) {
                // Load order first
                orderRepository.findByOrderId(actorContext.actorId())
                    .flatMap(order -> {
                        order.setAmount(cmd.getNewAmount());
                        return orderRepository.save(order);
                    })
                    .subscribe(
                        updated -> ctx.getSelf().tell(new OrderUpdated(updated, cmd)),
                        error -> ctx.getSelf().tell(new OperationFailed(error.getMessage(), cmd))
                    );
            } else {
                // Update existing order
                currentOrder.setAmount(cmd.getNewAmount());
                
                orderRepository.save(currentOrder)
                    .subscribe(
                        updated -> ctx.getSelf().tell(new OrderUpdated(updated, cmd)),
                        error -> {
                            if (error instanceof OptimisticLockingFailureException) {
                                ctx.getSelf().tell(new OperationFailed(
                                    "Order was modified by another process, please retry", cmd));
                            } else {
                                ctx.getSelf().tell(new OperationFailed(error.getMessage(), cmd));
                            }
                        }
                    );
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }
            
            currentOrder.setStatus(OrderStatus.APPROVED);
            
            orderRepository.save(currentOrder)
                .subscribe(
                    updated -> ctx.getSelf().tell(new OrderUpdated(updated, cmd)),
                    error -> ctx.getSelf().tell(new OperationFailed(error.getMessage(), cmd))
                );
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOrderSaved(OrderSaved msg) {
            currentOrder = msg.order();
            ctx.getLog().info("Order saved: {}", currentOrder.getOrderId());
            msg.originalCommand().reply(new OrderResponse(true, currentOrder, "Order created"));
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOrderLoaded(OrderLoaded msg) {
            currentOrder = msg.order();
            ctx.getLog().info("Order loaded: {}", currentOrder.getOrderId());
            msg.originalCommand().reply(new OrderResponse(true, currentOrder, "Order retrieved"));
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOrderUpdated(OrderUpdated msg) {
            currentOrder = msg.order();
            ctx.getLog().info("Order updated: {}", currentOrder.getOrderId());
            msg.originalCommand().reply(new OrderResponse(true, currentOrder, "Order updated"));
            return Behaviors.same();
        }
        
        private Behavior<Command> handleOperationFailed(OperationFailed msg) {
            ctx.getLog().error("Operation failed: {}", msg.message());
            msg.originalCommand().reply(new OrderResponse(false, null, msg.message()));
            return Behaviors.same();
        }
    }
}
```

### 5. Configure R2DBC

```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:orderdb}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      max-acquire-time: 3s
      max-create-connection-time: 5s
      
  # For schema initialization
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

## Advanced Patterns

### Pattern 1: Using R2dbcEntityTemplate for Dynamic Queries

```java
@Component
public class OrderQueryService {
    
    private final R2dbcEntityTemplate template;
    
    public OrderQueryService(R2dbcEntityTemplate template) {
        this.template = template;
    }
    
    public Flux<Order> findOrdersByCriteria(OrderSearchCriteria criteria) {
        Criteria condition = Criteria.empty();
        
        if (criteria.getCustomerId() != null) {
            condition = condition.and("customer_id").is(criteria.getCustomerId());
        }
        
        if (criteria.getMinAmount() != null) {
            condition = condition.and("amount").greaterThanOrEquals(criteria.getMinAmount());
        }
        
        if (criteria.getStatus() != null) {
            condition = condition.and("status").is(criteria.getStatus().name());
        }
        
        Query query = Query.query(condition)
            .sort(Sort.by(Sort.Direction.DESC, "created_at"))
            .limit(criteria.getLimit());
        
        return template.select(query, Order.class);
    }
    
    public Mono<Long> updateOrderStatus(String orderId, OrderStatus newStatus) {
        Query query = Query.query(Criteria.where("order_id").is(orderId));
        Update update = Update.update("status", newStatus.name())
            .set("updated_at", Instant.now());
        
        return template.update(query, update, Order.class);
    }
}
```

### Pattern 2: Handling One-to-Many Relationships

```java
@Table("order_items")
public class OrderItem {
    @Id
    private Long id;
    
    @Column("order_id")
    private Long orderId;
    
    @Column("product_id")
    private String productId;
    
    private Integer quantity;
    private Double price;
    
    // Constructors, getters, setters
}

@Repository
public interface OrderItemRepository extends R2dbcRepository<OrderItem, Long> {
    Flux<OrderItem> findByOrderId(Long orderId);
    Mono<Void> deleteByOrderId(Long orderId);
}

@Component
public class OrderAggregateService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    
    public Mono<OrderAggregate> loadOrderWithItems(String orderId) {
        return orderRepository.findByOrderId(orderId)
            .flatMap(order -> 
                orderItemRepository.findByOrderId(order.getId())
                    .collectList()
                    .map(items -> new OrderAggregate(order, items))
            );
    }
    
    @Transactional
    public Mono<Order> saveOrderWithItems(Order order, List<OrderItem> items) {
        return orderRepository.save(order)
            .flatMap(savedOrder -> 
                orderItemRepository.saveAll(
                    items.stream()
                        .peek(item -> item.setOrderId(savedOrder.getId()))
                        .toList()
                )
                .then(Mono.just(savedOrder))
            );
    }
}

public record OrderAggregate(Order order, List<OrderItem> items) {}
```

### Pattern 3: Batching Operations

```java
@Component
public class OrderBatchService {
    
    private final OrderRepository orderRepository;
    
    public Flux<Order> createOrdersBatch(List<Order> orders) {
        // R2DBC will batch these operations efficiently
        return orderRepository.saveAll(orders);
    }
    
    public Mono<Void> updateStatusBatch(List<String> orderIds, OrderStatus newStatus) {
        return Flux.fromIterable(orderIds)
            .flatMap(orderId -> orderRepository.findByOrderId(orderId))
            .doOnNext(order -> order.setStatus(newStatus))
            .flatMap(orderRepository::save)
            .then();
    }
}
```

### Pattern 4: Transaction Management

```java
@Service
public class OrderTransactionService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TransactionalOperator transactionalOperator;
    
    public OrderTransactionService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            TransactionalOperator transactionalOperator) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.transactionalOperator = transactionalOperator;
    }
    
    public Mono<Order> createOrderWithItemsTransactionally(Order order, List<OrderItem> items) {
        return orderRepository.save(order)
            .flatMap(savedOrder -> {
                items.forEach(item -> item.setOrderId(savedOrder.getId()));
                return orderItemRepository.saveAll(items)
                    .then(Mono.just(savedOrder));
            })
            .as(transactionalOperator::transactional); // Wrap in transaction
    }
}

@Configuration
public class R2dbcTransactionConfig {
    
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
        return TransactionalOperator.create(txManager);
    }
}
```

### Pattern 5: Custom Result Mapping

```java
@Component
public class OrderStatisticsService {
    
    private final DatabaseClient databaseClient;
    
    public OrderStatisticsService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }
    
    public Flux<OrderStatistics> getCustomerStatistics() {
        String sql = """
            SELECT 
                customer_id,
                COUNT(*) as order_count,
                SUM(amount) as total_amount,
                AVG(amount) as avg_amount,
                MAX(amount) as max_amount
            FROM orders
            GROUP BY customer_id
            ORDER BY total_amount DESC
            """;
        
        return databaseClient.sql(sql)
            .map((row, metadata) -> new OrderStatistics(
                row.get("customer_id", String.class),
                row.get("order_count", Long.class),
                row.get("total_amount", Double.class),
                row.get("avg_amount", Double.class),
                row.get("max_amount", Double.class)
            ))
            .all();
    }
}

public record OrderStatistics(
    String customerId,
    Long orderCount,
    Double totalAmount,
    Double avgAmount,
    Double maxAmount
) {}
```

## Testing

### Integration Testing with Testcontainers

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Testcontainers
class OrderRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> 
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testSaveAndFind() {
        Order order = new Order("order-1", "customer-1", 100.0);
        
        StepVerifier.create(
            orderRepository.save(order)
                .flatMap(saved -> orderRepository.findByOrderId(saved.getOrderId()))
        )
        .assertNext(found -> {
            assertThat(found.getCustomerId()).isEqualTo("customer-1");
            assertThat(found.getAmount()).isEqualTo(100.0);
        })
        .verifyComplete();
    }
}
```

### Unit Testing with Reactor Test

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderActorTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Test
    void testCreateOrder() {
        Order order = new Order("order-1", "customer-1", 100.0);
        order.setId(1L);
        
        when(orderRepository.save(any(Order.class)))
            .thenReturn(Mono.just(order));
        
        // Test with reactor StepVerifier
        StepVerifier.create(orderRepository.save(new Order()))
            .assertNext(saved -> assertThat(saved.getId()).isEqualTo(1L))
            .verifyComplete();
    }
}
```

## Performance Optimization

### 1. Connection Pooling

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10          # Initial connections
      max-size: 50              # Maximum connections
      max-idle-time: 30m        # Max idle time before closing
      max-acquire-time: 3s      # Max time to acquire connection
      max-create-connection-time: 5s
      validation-query: SELECT 1  # Connection validation
```

### 2. Prepared Statement Caching

```java
@Configuration
public class R2dbcConfig {
    
    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer customizer() {
        return builder -> builder
            .option(Option.valueOf("preparedStatementCacheQueries"), 256)
            .option(Option.valueOf("useServerPrepStmts"), true);
    }
}
```

### 3. Fetch Size Optimization

```java
@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {
    
    @Query("SELECT * FROM orders WHERE status = :status FETCH FIRST 1000 ROWS ONLY")
    Flux<Order> findByStatusWithLimit(@Param("status") OrderStatus status);
}
```

### 4. Pagination

```java
@Component
public class OrderPaginationService {
    
    private final OrderRepository orderRepository;
    
    public Flux<Order> findOrdersPaged(int page, int size) {
        return orderRepository.findAll(
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }
}
```

## Common Issues and Solutions

### Issue 1: Backpressure

**Problem:** Consumer can't keep up with data flow

**Solution:** Use backpressure operators
```java
orderRepository.findAll()
    .limitRate(100)  // Request 100 items at a time
    .subscribe(order -> processOrder(order));
```

### Issue 2: Error Handling in Reactive Streams

**Problem:** Errors terminate the stream

**Solution:** Use error handling operators
```java
orderRepository.findByOrderId(orderId)
    .onErrorResume(error -> {
        log.error("Failed to load order", error);
        return Mono.empty();
    })
    .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
```

### Issue 3: Blocking in Reactive Code

**Problem:** Accidentally blocking the event loop

**Solution:** Never call `.block()` in production reactive code
```java
// DON'T:
Order order = orderRepository.findByOrderId(id).block(); // Blocks!

// DO:
orderRepository.findByOrderId(id)
    .subscribe(order -> processOrder(order));
```

## Migration from Blocking to Reactive

### Step 1: Change Repository Type

```java
// Before: Blocking
public interface OrderRepository extends JpaRepository<Order, Long> {}

// After: Reactive
public interface OrderRepository extends R2dbcRepository<Order, Long> {}
```

### Step 2: Update Return Types

```java
// Before: Blocking
public Order findOrder(Long id) {
    return orderRepository.findById(id).orElse(null);
}

// After: Reactive
public Mono<Order> findOrder(Long id) {
    return orderRepository.findById(id);
}
```

### Step 3: Chain Operations

```java
// Before: Blocking
public Order createAndUpdateOrder(Order order) {
    Order saved = orderRepository.save(order);
    saved.setStatus(OrderStatus.APPROVED);
    return orderRepository.save(saved);
}

// After: Reactive
public Mono<Order> createAndUpdateOrder(Order order) {
    return orderRepository.save(order)
        .flatMap(saved -> {
            saved.setStatus(OrderStatus.APPROVED);
            return orderRepository.save(saved);
        });
}
```

## Summary

R2DBC integration with actors provides:
- ✅ Non-blocking, reactive SQL database access
- ✅ High throughput with efficient resource usage
- ✅ Backpressure support for flow control
- ✅ Full transaction support
- ✅ Compatible with major relational databases
- ✅ Perfect for high-performance actor systems

Use R2DBC when you need:
- Non-blocking database access for high concurrency
- SQL database features (transactions, joins, indexes)
- Better resource utilization than blocking JDBC
- Reactive programming model throughout your stack
