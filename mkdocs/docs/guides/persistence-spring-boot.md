# Spring Boot Integration

How to use actor-spring-boot-starter with Spring Boot's ecosystem.

## Core Philosophy

Actors are **just Spring components**. They use dependency injection, configuration, and all standard Spring Boot features—no special setup required.

!!! success "Spring-Native"
    The library is designed to feel like native Spring Boot. If you know Spring Boot, you already know how to use actors.

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
        private final SpringBehaviorContext<Command> ctx;
        private final OrderRepository repository;
        private final OrderService service;

        public OrderBehavior(SpringBehaviorContext<Command> ctx, OrderRepository repository, OrderService service) {
            this.ctx = ctx;
            this.repository = repository;
            this.service = service;
        }

        private SpringActorBehavior<Command> handleCreate(CreateOrder cmd) {
            // Implementation here
            return SpringActorBehavior.same();
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
        private final SpringBehaviorContext<Command> ctx;
        private final OrderRepository orderRepository;
        private Order currentOrder;

        public OrderBehavior(SpringBehaviorContext<Command> ctx, OrderRepository orderRepository, Order order) {
            this.ctx = ctx;
            this.orderRepository = orderRepository;
            this.currentOrder = order;
        }

        private SpringActorBehavior<Command> handleUpdate(UpdateOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return SpringActorBehavior.same();
            }

            currentOrder.setAmount(cmd.getNewAmount());
            currentOrder = orderRepository.save(currentOrder);  // Explicit save
            ctx.getLog().info("Order updated: {}", currentOrder.getOrderId());
            cmd.reply(new OrderResponse(true, currentOrder, "Order updated"));
            return SpringActorBehavior.same();
        }
    }
}
```

### Reactive Repositories (R2DBC)

A JPA call blocks its thread until the database answers. Spring Data R2DBC returns a `Mono` instead, so
the actor never waits. Turn the result into a message with `pipeToSelf`: the actor handles it like any
other message, one at a time, so its state stays safe without locks.

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
            .onMessage(SaveDone.class, ReactiveBehavior::handleSaveDone)
            .build();
    }

    // The result of a save, sent by the actor to itself. It carries the caller's command, so the
    // actor can reply once the database has answered.
    private record SaveDone(SaveOrder cmd, Order saved, Throwable failure) implements Command {}

    private static class ReactiveBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final ReactiveOrderRepository repository;
        private Order currentOrder;

        ReactiveBehavior(SpringBehaviorContext<Command> ctx, ReactiveOrderRepository repository) {
            this.ctx = ctx;
            this.repository = repository;
        }

        private SpringActorBehavior<Command> handleSave(SaveOrder cmd) {
            // Returns at once; the save runs on R2DBC's threads, not the actor's.
            ctx.pipeToSelf(
                repository.save(cmd.order()).toFuture(),
                (saved, failure) -> new SaveDone(cmd, saved, failure));
            return SpringActorBehavior.same();
        }

        private SpringActorBehavior<Command> handleSaveDone(SaveDone done) {
            if (done.failure() != null) {
                ctx.getLog().error("Save failed", done.failure());
                done.cmd().reply(new OrderResponse(false, null, "Save failed"));
                return SpringActorBehavior.same();
            }
            currentOrder = done.saved();  // state changes only inside a message handler
            done.cmd().reply(new OrderResponse(true, currentOrder, "Order saved"));
            return SpringActorBehavior.same();
        }
    }
}
```

!!! warning "Don't touch state in a callback"
    Never change actor state in `subscribe(...)`, `thenAccept(...)` or any other callback: it runs on
    another thread, alongside the actor. Send the result to the actor with `pipeToSelf` and change state
    in the handler.

### Choosing an approach

| Approach | Use it when | What to watch |
|---|---|---|
| JPA or JDBC on the blocking dispatcher | The app already uses JPA, and most Spring apps do | Each call holds a thread of the blocking dispatcher. Size its pool, or run it on virtual threads (`spring.actor.virtual-threads.enabled`, see [Dispatchers](dispatchers.md)) |
| R2DBC with `pipeToSelf` | Many concurrent actors talk to the database, or the app is already reactive | Every result comes back as a message, so each call needs a result message type |

The [persistence example](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/persistence)
uses JPA on purpose, because that is what most Spring users have. Its controller spawns the JPA-backed
actors with `withBlockingDispatcher().getOrSpawn()`, as shown below.

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
            .withBlockingDispatcher()  // Configure dispatcher here
            .getOrSpawn()              // the running actor, or a new one on this dispatcher
            .thenCompose(ref -> ref.ask(new CreateOrder(...))
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }
}
```

!!! danger "Thread Starvation"
    Never run blocking database operations on the default dispatcher. Always use a blocking dispatcher or configure a custom thread pool.

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
        private final SpringBehaviorContext<Command> ctx;
        private final OrderService orderService;

        public OrderBehavior(SpringBehaviorContext<Command> ctx, OrderService orderService) {
            this.ctx = ctx;
            this.orderService = orderService;
        }

        private SpringActorBehavior<Command> handleCreate(CreateOrder cmd) {
            List<OrderItem> items = cmd.getItems();
            Order order = orderService.createOrderWithItems(ctx.actorId(), items);
            // Transaction is managed by Spring
            ctx.getLog().info("Order created with items: {}", order.getOrderId());
            cmd.reply(new OrderResponse(true, order, "Order created"));
            return SpringActorBehavior.same();
        }
    }
}
```

## Complete Example

See `example/persistence` for a complete working example demonstrating:

- JPA integration with actors
- Transaction management
- Blocking dispatcher configuration
- State loading and persistence

!!! info "Example Code"
    The persistence example shows a complete implementation of actors with Spring Data JPA integration.

## Summary

Spring Boot integration is seamless:

1. **Actors are Spring components** - Use `@Component`, inject dependencies normally
2. **SpringActorSystem is auto-configured** - Just inject and use
3. **Use Spring Data directly** - No adapters or wrappers needed
4. **Standard Spring Boot features work** - Configuration, profiles, actuator, testing
5. **CompletionStage integrates with Spring MVC/WebFlux** - Async responses just work

!!! success "No Special Setup"
    No special setup. No custom infrastructure. Just Spring Boot + actors.

## Next Steps

- [Dispatchers](dispatchers.md) - Configure thread execution for blocking operations
- [Sharded Actors](sharded-actors.md) - Distribute actors across a cluster
- [Logging with MDC](logging.md) - Add observability to your actors
