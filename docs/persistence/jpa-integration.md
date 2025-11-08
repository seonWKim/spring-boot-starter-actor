# JPA Integration with Actors

This guide demonstrates how to integrate JPA (Java Persistence API) with actors in the spring-boot-starter-actor framework.

## Overview

JPA is the most common persistence technology in Spring Boot applications. Actors can directly use Spring Data JPA repositories through dependency injection, providing a familiar and powerful way to manage relational data.

## Prerequisites

Add the Spring Data JPA dependency to your project:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.postgresql:postgresql' // or your database driver
    implementation 'io.github.seonwkim:spring-boot-starter-actor:1.0.0'
}
```

## Basic Setup

### 1. Define Your Entity

```java
package com.example.order;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    private String orderId;
    
    @Column(nullable = false)
    private String customerId;
    
    @Column(nullable = false)
    private Double amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Version
    private Long version; // For optimistic locking
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    protected Order() {} // JPA requires default constructor
    
    public Order(String orderId, String customerId, double amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
    }
    
    // Getters and setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    
    public Long getVersion() { return version; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    // Business methods
    public void approve() {
        this.status = OrderStatus.APPROVED;
    }
    
    public void reject() {
        this.status = OrderStatus.REJECTED;
    }
}

enum OrderStatus {
    PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
}
```

### 2. Create a Repository

```java
package com.example.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    
    // Derived query methods
    List<Order> findByCustomerId(String customerId);
    List<Order> findByStatus(OrderStatus status);
    Optional<Order> findByOrderId(String orderId);
    
    // Custom query
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    List<Order> findByCustomerIdAndStatus(
        @Param("customerId") String customerId,
        @Param("status") OrderStatus status
    );
    
    // Native query for complex scenarios
    @Query(value = "SELECT * FROM orders WHERE amount > :minAmount ORDER BY created_at DESC LIMIT :limit", 
           nativeQuery = true)
    List<Order> findHighValueOrders(
        @Param("minAmount") double minAmount,
        @Param("limit") int limit
    );
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

@Component
public class OrderActor implements SpringActor<OrderActor.Command> {
    
    private final OrderRepository orderRepository;
    
    // Constructor injection
    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    // Command definitions
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
    
    public static class ApproveOrder extends AskCommand<OrderResponse> implements Command {}
    
    public static class RejectOrder extends AskCommand<OrderResponse> implements Command {}
    
    public record OrderResponse(boolean success, Order order, String message) {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                // Load existing order if it exists
                Order order = orderRepository.findById(actorContext.actorId()).orElse(null);
                return new OrderActorBehavior(ctx, actorContext, orderRepository, order);
            })
            .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrder)
            .onMessage(GetOrder.class, OrderActorBehavior::handleGetOrder)
            .onMessage(ApproveOrder.class, OrderActorBehavior::handleApproveOrder)
            .onMessage(RejectOrder.class, OrderActorBehavior::handleRejectOrder)
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
                // Create new order entity
                currentOrder = new Order(
                    actorContext.actorId(),
                    cmd.getCustomerId(),
                    cmd.getAmount()
                );
                
                // Save to database
                currentOrder = orderRepository.save(currentOrder);
                
                ctx.getLog().info("Order created: {}", currentOrder.getOrderId());
                cmd.reply(new OrderResponse(true, currentOrder, "Order created successfully"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, "Failed to create order: " + e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleGetOrder(GetOrder cmd) {
            if (currentOrder == null) {
                // Try to load from database
                currentOrder = orderRepository.findById(actorContext.actorId()).orElse(null);
            }
            
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(true, currentOrder, "Order retrieved"));
            } else {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleApproveOrder(ApproveOrder cmd) {
            return updateOrderStatus(cmd, OrderStatus.APPROVED, "approved");
        }
        
        private Behavior<Command> handleRejectOrder(RejectOrder cmd) {
            return updateOrderStatus(cmd, OrderStatus.REJECTED, "rejected");
        }
        
        private Behavior<Command> updateOrderStatus(
                AskCommand<OrderResponse> cmd,
                OrderStatus newStatus,
                String action) {
            
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }
            
            try {
                // Update status
                currentOrder.setStatus(newStatus);
                
                // Save with optimistic locking
                currentOrder = orderRepository.save(currentOrder);
                
                ctx.getLog().info("Order {} {}", currentOrder.getOrderId(), action);
                cmd.reply(new OrderResponse(true, currentOrder, "Order " + action));
                
            } catch (OptimisticLockingFailureException e) {
                // Another process updated the order - reload and retry
                ctx.getLog().warn("Optimistic locking failure for order {}, reloading", 
                    currentOrder.getOrderId());
                
                currentOrder = orderRepository.findById(currentOrder.getOrderId()).orElse(null);
                cmd.reply(new OrderResponse(false, currentOrder, 
                    "Order was modified by another process, please retry"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to update order", e);
                cmd.reply(new OrderResponse(false, null, 
                    "Failed to update order: " + e.getMessage()));
            }
            
            return Behaviors.same();
        }
    }
}
```

### 4. Configure Database

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orderdb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    
    # HikariCP connection pool settings
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
  
  jpa:
    hibernate:
      ddl-auto: validate  # Use 'validate' in production, 'update' for dev
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
    show-sql: false
```

## Advanced Patterns

### Pattern 1: Complex Entity Relationships

```java
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    private String orderId;
    
    // One-to-Many relationship
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    
    // Many-to-One relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    // Helper method for bidirectional relationship
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
    
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}

@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    private String productId;
    private Integer quantity;
    private Double price;
    
    // Getters and setters
}
```

### Pattern 2: Custom Repository Methods

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, String>, OrderRepositoryCustom {
    // Standard JPA methods
}

public interface OrderRepositoryCustom {
    List<Order> findOrdersWithComplexCriteria(OrderSearchCriteria criteria);
}

@Component
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<Order> findOrdersWithComplexCriteria(OrderSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> order = query.from(Order.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getCustomerId() != null) {
            predicates.add(cb.equal(order.get("customerId"), criteria.getCustomerId()));
        }
        
        if (criteria.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(order.get("amount"), criteria.getMinAmount()));
        }
        
        if (criteria.getStatus() != null) {
            predicates.add(cb.equal(order.get("status"), criteria.getStatus()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

### Pattern 3: Handling Blocking Operations

For high-throughput systems, execute JPA operations on a dedicated dispatcher:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private final OrderRepository orderRepository;
    private final Executor blockingExecutor;
    
    public OrderActor(
            OrderRepository orderRepository,
            @Qualifier("blockingExecutor") Executor blockingExecutor) {
        this.orderRepository = orderRepository;
        this.blockingExecutor = blockingExecutor;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> 
                new OrderActorBehavior(actorCtx, ctx, orderRepository, blockingExecutor))
            .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrderAsync)
            .build();
    }
    
    private static class OrderActorBehavior {
        // ... fields ...
        
        private Behavior<Command> handleCreateOrderAsync(CreateOrder cmd) {
            // Execute blocking JPA operation asynchronously
            CompletableFuture.supplyAsync(() -> {
                Order order = new Order(actorContext.actorId(), cmd.getCustomerId(), cmd.getAmount());
                return orderRepository.save(order);
            }, blockingExecutor)
            .thenAccept(savedOrder -> {
                ctx.getSelf().tell(new OrderSaved(savedOrder));
                cmd.reply(new OrderResponse(true, savedOrder, "Order created"));
            })
            .exceptionally(error -> {
                ctx.getLog().error("Failed to create order", error);
                cmd.reply(new OrderResponse(false, null, error.getMessage()));
                return null;
            });
            
            return Behaviors.same();
        }
    }
}

@Configuration
public class ExecutorConfiguration {
    
    @Bean
    @Qualifier("blockingExecutor")
    public Executor blockingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("blocking-io-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

### Pattern 4: Transaction Management with Service Layer

```java
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SpringActorSystem actorSystem;
    
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SpringActorSystem actorSystem) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.actorSystem = actorSystem;
    }
    
    @Transactional
    public Order createOrderWithItems(String orderId, String customerId, 
                                     double amount, List<OrderItemDto> items) {
        // All operations in a single transaction
        Order order = new Order(orderId, customerId, amount);
        order = orderRepository.save(order);
        
        for (OrderItemDto itemDto : items) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemDto.getProductId());
            item.setQuantity(itemDto.getQuantity());
            item.setPrice(itemDto.getPrice());
            orderItemRepository.save(item);
        }
        
        // Notify actor after successful transaction
        actorSystem.actorOf(OrderActor.class, orderId)
            .tell(new OrderActor.OrderCreated());
        
        return order;
    }
    
    @Transactional(readOnly = true)
    public Order findOrderWithItems(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

## Testing

### Unit Testing with Mock Repository

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderActorTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    private OrderActor orderActor;
    
    @BeforeEach
    void setUp() {
        orderActor = new OrderActor(orderRepository);
    }
    
    @Test
    void testCreateOrder() {
        // Arrange
        String orderId = "order-123";
        Order expectedOrder = new Order(orderId, "customer-1", 100.0);
        when(orderRepository.save(any(Order.class))).thenReturn(expectedOrder);
        
        // Act & Assert
        // Test with ActorTestKit
        // ... test implementation ...
        
        verify(orderRepository).save(any(Order.class));
    }
}
```

### Integration Testing with Test Database

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testSaveAndFindOrder() {
        // Create and save order
        Order order = new Order("order-1", "customer-1", 100.0);
        entityManager.persist(order);
        entityManager.flush();
        
        // Find order
        Order found = orderRepository.findById("order-1").orElse(null);
        
        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getCustomerId()).isEqualTo("customer-1");
        assertThat(found.getAmount()).isEqualTo(100.0);
    }
}
```

## Performance Optimization

### 1. Use Batch Operations

```java
// Instead of:
for (Order order : orders) {
    orderRepository.save(order);  // N queries
}

// Do:
orderRepository.saveAll(orders);  // Batch insert
```

### 2. Use Projections for Read-Only Queries

```java
public interface OrderSummary {
    String getOrderId();
    Double getAmount();
    OrderStatus getStatus();
}

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    @Query("SELECT o.orderId as orderId, o.amount as amount, o.status as status " +
           "FROM Order o WHERE o.customerId = :customerId")
    List<OrderSummary> findOrderSummariesByCustomerId(@Param("customerId") String customerId);
}
```

### 3. Optimize Fetch Strategies

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.orderId = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") String orderId);
}
```

## Common Issues and Solutions

### Issue 1: LazyInitializationException

**Problem:** Accessing lazy-loaded associations outside transaction
```java
Order order = orderRepository.findById(orderId).get();
// Later, outside transaction:
order.getItems().size(); // LazyInitializationException!
```

**Solution:** Use fetch joins or DTOs
```java
@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") String id);
```

### Issue 2: N+1 Query Problem

**Problem:** Loading entities with associations triggers multiple queries
```java
List<Order> orders = orderRepository.findAll();
for (Order order : orders) {
    order.getCustomer().getName(); // N+1 queries!
}
```

**Solution:** Use fetch joins
```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer")
List<Order> findAllWithCustomer();
```

## Summary

JPA integration with actors provides:
- ✅ Familiar Spring Data repository patterns
- ✅ Powerful query capabilities (JPQL, Criteria API, native SQL)
- ✅ Built-in optimistic locking
- ✅ Transaction management through Spring
- ✅ Rich entity relationship support
- ✅ Easy testing with mocks or test databases

Follow the patterns in this guide to build robust, scalable actor systems backed by relational databases.
