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
            .withState(actorCtx -> new OrderBehavior(repository, service))
            .onMessage(CreateOrder.class, OrderBehavior::handleCreate)
            .build();
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
                Order order = orderRepository.findById(ctx.actorId()).orElse(null);
                return new Behavior(order);
            })
            .onMessage(UpdateOrder.class, Behavior::handleUpdate)
            .build();
    }

    private static class Behavior {
        private Order order;

        private org.apache.pekko.actor.typed.Behavior<Command> handleUpdate(UpdateOrder cmd) {
            order.setAmount(cmd.amount());
            orderRepository.save(order);  // Explicit save
            return Behaviors.same();
        }
    }
}
```

**Configuration** (`application.yml`):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 20
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
            .withState(actorCtx -> new ReactiveBehavior())
            .onMessage(SaveOrder.class, ReactiveBehavior::handleSave)
            .onMessage(SaveComplete.class, ReactiveBehavior::handleComplete)
            .build();
    }

    // Internal messages for async results
    private record SaveComplete(Order order) implements Command {}

    private static class ReactiveBehavior {
        private org.apache.pekko.actor.typed.Behavior<Command> handleSave(SaveOrder cmd) {
            repository.save(order)
                .subscribe(
                    saved -> ctx.getSelf().tell(new SaveComplete(saved)),
                    error -> ctx.getLog().error("Save failed", error)
                );
            return Behaviors.same();
        }
    }
}
```

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
    private final OrderConfig config;  // Injected automatically

    public OrderActor(OrderRepository repo, OrderConfig config) {
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
                return new Behavior(strategy);
            })
            .build();
    }
}
```

### Jackson Serialization

Use Spring's `ObjectMapper` for JSON serialization:

```java
@Component
public class EventSourcedActor implements SpringActor<Command> {
    private final ObjectMapper objectMapper;  // Spring Boot auto-configured

    public EventSourcedActor(EventRepository repo, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private String serialize(DomainEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    private DomainEvent deserialize(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, DomainEvent.class);
    }
}
```

**Custom configuration**:
```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }
}
```

### Spring Actuator

Actor system integrates with Spring Boot Actuator automatically:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  health:
    db:
      enabled: true
  metrics:
    enable:
      jvm: true
      system: true
```

Access endpoints:
- `GET /actuator/health` - Health status (includes database)
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

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

    private org.apache.pekko.actor.typed.Behavior<Command> handleCreate(CreateOrder cmd) {
        Order order = orderService.createOrderWithItems(orderId, cmd.items());
        // Transaction is managed by Spring
        return Behaviors.same();
    }
}
```

### Profiles

Use Spring profiles for environment-specific actor configuration:

```yaml
# application.yml
spring:
  profiles:
    active: dev

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev

  datasource:
    url: jdbc:h2:mem:testdb

app:
  orders:
    snapshot:
      operation-interval: 10  # Snapshot more frequently in dev

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod

  datasource:
    url: jdbc:postgresql://prod-db:5432/orders

app:
  orders:
    snapshot:
      operation-interval: 1000  # Less frequent in production
```

## Async Operations

### CompletableFuture Integration

Actors return `CompletionStage` which works seamlessly with Spring WebFlux and `@Async`:

```java
@RestController
public class OrderController {
    private final SpringActorSystem actorSystem;

    @GetMapping("/orders/{orderId}")
    public CompletionStage<OrderResponse> getOrder(@PathVariable String orderId) {
        // Returns CompletionStage - Spring handles async execution
        return actorSystem.getOrSpawn(OrderActor.class, orderId)
            .thenCompose(ref -> ref.ask(new GetOrder())
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }
}
```

Spring MVC automatically:
- Handles the asynchronous response
- Releases the request thread
- Completes when `CompletionStage` finishes

### Custom Thread Pools

Configure custom dispatchers using Spring's thread pool:

```yaml
# application.yml
pekko:
  actor:
    default-dispatcher:
      fork-join-executor:
        parallelism-min: 8
        parallelism-max: 64

    blocking-io-dispatcher:
      type: Dispatcher
      executor: "thread-pool-executor"
      thread-pool-executor:
        fixed-pool-size: 32
```

Use in actors:

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .withDispatcher("blocking-io-dispatcher")  // Use custom dispatcher
        .withState(...)
        .build();
}
```

## Testing

### Spring Boot Test Integration

```java
@SpringBootTest
class OrderActorIntegrationTest {
    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testCreateOrder() throws Exception {
        // Spawn actor
        SpringActorRef<OrderActor.Command> actor =
            actorSystem.actor(OrderActor.class)
                .withId("test-order-1")
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        // Send message
        OrderActor.OrderResponse response = actor
            .ask(new OrderActor.CreateOrder("customer-1", 100.0))
            .withTimeout(Duration.ofSeconds(5))
            .execute()
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        // Verify
        assertThat(response.success()).isTrue();
        assertThat(orderRepository.findById("test-order-1")).isPresent();
    }
}
```

### Test Containers

Use Testcontainers for database testing:

```java
@SpringBootTest
@Testcontainers
class OrderActorDatabaseTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SpringActorSystem actorSystem;

    @Test
    void testPersistence() {
        // Test with real PostgreSQL database
    }
}
```

## Best Practices

### ✅ DO

**Leverage Spring's dependency injection**
```java
@Component
public class OrderActor implements SpringActor<Command> {
    // Inject any Spring beans
    private final OrderRepository repository;
    private final OrderService service;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
}
```

**Use Spring Boot configuration**
```yaml
# Standard Spring Boot configuration
spring:
  datasource: ...
  jpa: ...

# Custom actor configuration
app:
  actors:
    orders:
      snapshot-interval: 100
```

**Return CompletionStage from controllers**
```java
@GetMapping("/orders/{id}")
public CompletionStage<OrderResponse> getOrder(@PathVariable String id) {
    return actorSystem.getOrSpawn(OrderActor.class, id)
        .thenCompose(ref -> ref.ask(new GetOrder()).execute());
}
```

### ❌ DON'T

**Create custom actor factories**
```java
// DON'T - Spring handles injection
public class ActorFactory {
    public OrderActor create() { ... }
}

// DO - Use @Component
@Component
public class OrderActor implements SpringActor<Command> { ... }
```

**Bypass Spring Boot autoconfiguration**
```java
// DON'T - Manual configuration
@Bean
public DataSource dataSource() {
    return new HikariDataSource(...);
}

// DO - Use application.yml
spring:
  datasource:
    url: jdbc:postgresql://...
```

**Block in actors unnecessarily**
```java
// DON'T - Blocking I/O on default dispatcher
Order order = repository.findById(id).get();  // Blocking!

// DO - Load on startup or use reactive
.withState(ctx -> repository.findById(ctx.actorId()).orElse(null))
```

## Complete Example

See `example/persistence` for a complete working example demonstrating:

- JPA repository integration
- Event sourcing with Jackson serialization
- Snapshot strategies with configurable intervals
- REST controllers with async responses
- Spring Boot configuration
- H2 console integration
- Actuator endpoints

```bash
# Run the example
./gradlew :example:persistence:bootRun

# Access H2 console
open http://localhost:8080/h2-console

# Health check
curl http://localhost:8080/actuator/health

# Create order via actor
curl -X POST "http://localhost:8080/api/orders/order-1?customerId=cust-1&amount=100"
```

## Summary

Spring Boot integration is seamless:

1. **Actors are Spring components** - Use `@Component`, inject dependencies normally
2. **SpringActorSystem is auto-configured** - Just inject and use
3. **Use Spring Data directly** - No adapters or wrappers needed
4. **Standard Spring Boot features work** - Configuration, profiles, actuator, testing
5. **CompletionStage integrates with Spring MVC/WebFlux** - Async responses just work

No special setup. No custom infrastructure. Just Spring Boot + actors.
