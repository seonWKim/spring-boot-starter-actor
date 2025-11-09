# Spring Boot Integration

How to leverage spring-boot-starter-actor with Spring Boot's ecosystem.

## Core Philosophy

Actors are **just Spring components**. They use dependency injection, configuration, and all standard Spring Boot features—no special setup required.

## Quick Start

### 1. Enable Actor Support

```java
@SpringBootApplication
@EnableActorSupport  // That's it!
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. Create an Actor

```java
@Component  // Regular Spring component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository repository;  // Injected automatically
    private final OrderService service;        // Any Spring bean works

    // Constructor injection like any Spring component
    public OrderActor(OrderRepository repository, OrderService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> new OrderBehavior(actorCtx, repository, service))
            .onMessage(CreateOrder.class, OrderBehavior::handleCreate)
            .build();
    }

    private static class OrderBehavior {
        private final ActorContext<Command> ctx;
        private final OrderRepository repository;
        private final OrderService service;

        public OrderBehavior(ActorContext<Command> ctx, OrderRepository repository, OrderService service) {
            this.ctx = ctx;
            this.repository = repository;
            this.service = service;
        }

        private Behavior<Command> handleCreate(CreateOrder cmd) {
            // Implementation here
            return Behaviors.same();
        }
    }
}
```

### 3. Use Actors from Controllers

```java
@RestController
public class OrderController {
    private final SpringActorSystem actorSystem;  // Auto-configured

    public OrderController(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @PostMapping("/orders/{orderId}")
    public CompletionStage<OrderResponse> createOrder(
            @PathVariable String orderId,
            @RequestBody CreateOrderRequest request) {

        return actorSystem.getOrSpawn(OrderActor.class, orderId)
            .thenCompose(ref -> ref.ask(new CreateOrder(request))
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }
}
```

## Spring Data Integration

### JPA Repositories

Inject JPA repositories directly into actors:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository orderRepository;

    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> {
                // Load state on startup from database
                Order order = orderRepository.findByOrderId(ctx.actorId()).orElse(null);
                return new OrderBehavior(actorCtx, orderRepository, order);
            })
            .onMessage(UpdateOrder.class, OrderBehavior::handleUpdate)
            .build();
    }

    private static class OrderBehavior {
        private final ActorContext<Command> ctx;
        private final OrderRepository orderRepository;
        private Order currentOrder;

        public OrderBehavior(ActorContext<Command> ctx, OrderRepository orderRepository, Order order) {
            this.ctx = ctx;
            this.orderRepository = orderRepository;
            this.currentOrder = order;
        }

        private Behavior<Command> handleUpdate(UpdateOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }

            currentOrder.setAmount(cmd.getNewAmount());
            currentOrder = orderRepository.save(currentOrder);  // Explicit save
            ctx.getLog().info("Order updated: {}", currentOrder.getOrderId());
            cmd.reply(new OrderResponse(true, currentOrder, "Order updated"));
            return Behaviors.same();
        }
    }
}
```

### Reactive Repositories

Use reactive repositories for non-blocking operations:

```java
@Component
public class ReactiveOrderActor implements SpringActor<Command> {
    private final ReactiveOrderRepository repository;

    public ReactiveOrderActor(ReactiveOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> new ReactiveBehavior(actorCtx, repository))
            .onMessage(SaveOrder.class, ReactiveBehavior::handleSave)
            .onMessage(SaveComplete.class, ReactiveBehavior::handleComplete)
            .build();
    }

    // Internal messages for async results
    private record SaveComplete(Order order) implements Command {}

    private static class ReactiveBehavior {
        private final ActorContext<Command> ctx;
        private final ReactiveOrderRepository repository;
        private Order currentOrder;

        public ReactiveBehavior(ActorContext<Command> ctx, ReactiveOrderRepository repository) {
            this.ctx = ctx;
            this.repository = repository;
        }

        private Behavior<Command> handleSave(SaveOrder cmd) {
            repository.save(currentOrder)
                .subscribe(
                    saved -> ctx.getSelf().tell(new SaveComplete(saved)),
                    error -> ctx.getLog().error("Save failed", error)
                );
            return Behaviors.same();
        }

        private Behavior<Command> handleComplete(SaveComplete msg) {
            this.currentOrder = msg.order();
            ctx.getLog().info("Order saved successfully: {}", currentOrder.getId());
            return Behaviors.same();
        }
    }
}
```

## Handling Blocking Operations

Database operations (JPA, JDBC) are **blocking** and should not run on the default actor dispatcher. Use a dedicated blocking dispatcher to prevent thread starvation.

```java
@RestController
public class OrderController {
    private final SpringActorSystem actorSystem;

    @PostMapping("/orders/{orderId}")
    public CompletionStage<OrderResponse> createOrder(@PathVariable String orderId, ...) {
        return actorSystem.actor(OrderActor.class)
            .withId(orderId)
            .withDispatcher(DispatcherConfig.blocking())  // Configure dispatcher here
            .spawn()
            .thenCompose(ref -> ref.ask(new CreateOrder(...))
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }
}
```

See the [Dispatchers guide](dispatchers.md) for detailed configuration and best practices.

## Spring Boot Features

### Configuration Properties

Use `@ConfigurationProperties` for actor configuration:

```java
@ConfigurationProperties(prefix = "app.orders")
public class OrderConfig {
    private int maxRetries = 3;
    private Duration timeout = Duration.ofSeconds(5);
    private SnapshotConfig snapshot = new SnapshotConfig();

    public static class SnapshotConfig {
        private int operationInterval = 100;
        private long timeIntervalMillis = 60000;
        // getters/setters
    }
    // getters/setters
}
```

```yaml
app:
  orders:
    max-retries: 5
    timeout: 10s
    snapshot:
      operation-interval: 50
      time-interval-millis: 30000
```

Inject into actors:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderRepository orderRepository;
    private final OrderConfig config;  // Injected automatically

    public OrderActor(OrderRepository orderRepository, OrderConfig config) {
        this.orderRepository = orderRepository;
        this.config = config;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> {
                SnapshotStrategy strategy = new HybridSnapshotStrategy(
                    config.getSnapshot().getOperationInterval(),
                    config.getSnapshot().getTimeIntervalMillis()
                );
                return new OrderBehavior(actorCtx, orderRepository, strategy);
            })
            .onMessage(CreateOrder.class, OrderBehavior::handleCreate)
            .build();
    }
}
```

### Transaction Management

Actors can use Spring's declarative transactions:

```java
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;

    @Transactional  // Standard Spring transaction
    public Order createOrderWithItems(String orderId, List<OrderItem> items) {
        Order order = new Order(orderId);
        order = orderRepository.save(order);

        for (OrderItem item : items) {
            item.setOrder(order);
            itemRepository.save(item);
        }

        return order;
    }
}

@Component
public class OrderActor implements SpringActor<Command> {
    private final OrderService orderService;  // Inject service with @Transactional

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withState(actorCtx -> new OrderBehavior(actorCtx, orderService))
            .onMessage(CreateOrder.class, OrderBehavior::handleCreate)
            .build();
    }

    private static class OrderBehavior {
        private final ActorContext<Command> ctx;
        private final OrderService orderService;

        public OrderBehavior(ActorContext<Command> ctx, OrderService orderService) {
            this.ctx = ctx;
            this.orderService = orderService;
        }

        private Behavior<Command> handleCreate(CreateOrder cmd) {
            List<OrderItem> items = cmd.getItems();
            Order order = orderService.createOrderWithItems(ctx.actorId(), items);
            // Transaction is managed by Spring
            ctx.getLog().info("Order created with items: {}", order.getOrderId());
            cmd.reply(new OrderResponse(true, order, "Order created"));
            return Behaviors.same();
        }
    }
}
```

## Complete Example

See `example/persistence` for a complete working example demonstrating:

## Summary

Spring Boot integration is seamless:

1. **Actors are Spring components** - Use `@Component`, inject dependencies normally
2. **SpringActorSystem is auto-configured** - Just inject and use
3. **Use Spring Data directly** - No adapters or wrappers needed
4. **Standard Spring Boot features work** - Configuration, profiles, actuator, testing
5. **CompletionStage integrates with Spring MVC/WebFlux** - Async responses just work

No special setup. No custom infrastructure. Just Spring Boot + actors.
