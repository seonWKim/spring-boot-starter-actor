<table>
<tr>
<td width="15%">
  <img src="mkdocs/docs/logo.png" alt="Library Logo" />
</td>
<td>
  <h2>Spring Boot Starter Actor</h2>
  <p>Bring the power of the actor model to your Spring Boot applications using <a href="https://pekko.apache.org/">Pekko</a> (an open-source fork of Akka).</p>
</td>
</tr>
</table>

## Why spring-boot-starter-actor?

Spring is the go-to framework in the Java ecosystem, but it lacks native support for actors. The actor model is incredibly useful for building scalable, concurrent systems—think IoT platforms, real-time chat, telecommunications, and more.

This library bridges that gap, letting you build actor-based systems with the Spring Boot patterns you already know. The goal? Make actor programming accessible to every Spring developer. We'd love to see this become an official Spring project someday.

**What's the actor model?**
- **Encapsulation**: Logic and state live inside actors
- **Message-passing**: Actors communicate by sending messages (no shared state!)
- **Concurrency**: Built-in isolation makes concurrent programming safer and easier

## Installation

### Prerequisites
- Java 11 or higher
- Spring Boot 2.x or 3.x

### Dependency Setup

Add the dependency to your project:

**Gradle:**
```gradle
dependencyManagement {
    imports {
        // Pekko requires Jackson 2.17.3+
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

// Spring Boot 2.7.x
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.0.38'

// Spring Boot 3.2.x
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.0.38'
```

**Maven:**
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson</groupId>
      <artifactId>jackson-bom</artifactId>
      <version>2.17.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Spring Boot 2.7.x -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor</artifactId>
  <version>0.0.38</version>
</dependency>

<!-- Spring Boot 3.2.x -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor_3</artifactId>
  <version>0.0.38</version>
</dependency>
```

Latest versions: [spring-boot-starter-actor](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor) | [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

### Enable Actor Support

Add `@EnableActorSupport` to your application:

```java
@SpringBootApplication
@EnableActorSupport
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## Core Concepts

### The Actor Model in 30 Seconds

- **Actors** are isolated units that process messages one at a time
- **Messages** are sent asynchronously (no shared state)
- **Concurrency** is handled automatically—no locks, no race conditions
- **Location transparency** means actors can run locally or distributed across nodes

### Spring Integration

Actors are Spring `@Component` beans, so you can:
- Inject Spring dependencies into actors
- Use Spring configuration (`application.yml`)
- Leverage Spring Boot auto-configuration

## Best Practices

### 1. When to Use Actors

**Use actors for:**
- **Stateful workflows** (order processing, game sessions, user sessions)
- **High concurrency** (processing thousands of concurrent requests)
- **Event-driven systems** (chat, notifications, real-time updates)
- **Distributed systems** (microservices that need location transparency)

**Stick with Spring services for:**
- **Stateless operations** (simple CRUD, request-response APIs)
- **Database-heavy operations** (complex queries, transactions)
- **Synchronous workflows** (traditional REST endpoints)

### 2. Actor Lifecycle: Lazy Initialization Pattern

**✅ Recommended: Lazy async initialization**
```java
@Service
public class OrderService {
    private final SpringActorSystem actorSystem;
    private final AtomicReference<CompletionStage<SpringActorRef<OrderActor.Command>>> actorRef;

    public OrderService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        this.actorRef = new AtomicReference<>();
    }

    private CompletionStage<SpringActorRef<OrderActor.Command>> getActor() {
        return actorRef.updateAndGet(existing -> {
            if (existing != null) return existing;
            return actorSystem
                .spawn(OrderActor.class)
                .withId("order-processor")
                .start();
        });
    }

    public CompletionStage<Void> processOrder(Order order) {
        return getActor().thenAccept(actor ->
            actor.tell(new OrderActor.ProcessOrder(order))
        );
    }
}
```

**Why?** Spawning actors during Spring initialization blocks application startup. Lazy async initialization defers spawning until first use without blocking.

**❌ Avoid: Blocking constructor initialization**
```java
// This blocks Spring startup!
public OrderService(SpringActorSystem actorSystem) {
    this.orderActor = actorSystem.spawn(OrderActor.class)
        .withId("order-processor")
        .startAndWait(); // Blocks here
}
```

### 3. Message Design

**Make messages immutable and serializable:**

```java
@Component
public class OrderActor implements SpringActor<OrderActor, OrderActor.Command> {

    public interface Command {}

    // Immutable message with final fields
    public static class ProcessOrder implements Command {
        public final String orderId;
        public final BigDecimal amount;

        public ProcessOrder(String orderId, BigDecimal amount) {
            this.orderId = orderId;
            this.amount = amount;
        }
    }

    // Request-response message
    public static class GetOrderStatus implements Command {
        public final ActorRef<OrderStatus> replyTo;

        public GetOrderStatus(ActorRef<OrderStatus> replyTo) {
            this.replyTo = replyTo;
        }
    }
}
```

**For clustered actors, use `JsonSerializable`:**
```java
public interface Command extends JsonSerializable {}

public static class ProcessOrder implements Command {
    public final String orderId;

    @JsonCreator
    public ProcessOrder(@JsonProperty("orderId") String orderId) {
        this.orderId = orderId;
    }
}
```

### 4. Communication Patterns

#### Fire-and-Forget (Tell)
Use when you don't need a response:
```java
actor.tell(new ProcessOrder("order-123"));
```

#### Request-Response (Ask)

**Default timeout (3 seconds):**
```java
CompletionStage<OrderStatus> status = actor.ask(GetOrderStatus::new);
```

**Custom timeout:**
```java
CompletionStage<OrderStatus> status =
    actor.ask(GetOrderStatus::new, Duration.ofSeconds(10));
```

**Advanced with error handling:**
```java
CompletionStage<OrderStatus> status = actor
    .askBuilder(GetOrderStatus::new)
    .withTimeout(Duration.ofSeconds(10))
    .onTimeout(() -> OrderStatus.UNKNOWN)
    .execute();
```

**Choosing the right pattern:**
| Pattern | Use When |
|---------|----------|
| `ask()` | Default choice for request-response |
| `askBuilder()` | Need error handling or fallback values |

### 5. Local vs Sharded Actors

#### Local Actors (Single Instance)
Use for singletons or when you manage lifecycle:

```java
@Component
public class HelloActor implements SpringActor<HelloActor, HelloActor.Command> {

    public interface Command {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;
        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.receive(Command.class)
            .onMessage(SayHello.class, msg -> {
                msg.replyTo.tell("Hello from " + actorContext.actorId());
                return Behaviors.same();
            })
            .build();
    }
}
```

**Usage:**
```java
SpringActorRef<Command> actor = actorSystem
    .spawn(HelloActor.class)
    .withId("hello-1")
    .startAndWait();
```

#### Sharded Actors (Distributed)
Use for distributed state or when you need automatic scaling:

```java
@Component
public class UserSessionActor implements ShardedActor<UserSessionActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "UserSession");

    public interface Command extends JsonSerializable {}

    public static class UpdateActivity implements Command {
        public final String activity;

        @JsonCreator
        public UpdateActivity(@JsonProperty("activity") String activity) {
            this.activity = activity;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        String userId = ctx.getEntityId();
        return Behaviors.receive(Command.class)
            .onMessage(UpdateActivity.class, msg -> {
                // Handle message
                return Behaviors.same();
            })
            .build();
    }
}
```

**Usage (location transparent):**
```java
SpringShardedActorRef<Command> userSession = actorSystem
    .sharded(UserSessionActor.class)
    .withId("user-123")  // Automatically routed to correct node
    .get();

userSession.tell(new UpdateActivity("logged-in"));
```

**When to use sharded actors:**
- You have many instances (1000s of user sessions, game rooms, etc.)
- You need horizontal scaling across cluster nodes
- You want automatic load balancing
- You need location transparency (don't care which node handles it)

### 6. State Management

Actors manage state safely through message processing:

```java
@Override
public Behavior<Command> create(EntityContext<Command> ctx) {
    return Behaviors.setup(context ->
        active(0, new ArrayList<>())  // Initial state
    );
}

private Behavior<Command> active(int count, List<String> events) {
    return Behaviors.receive(Command.class)
        .onMessage(IncrementCounter.class, msg -> {
            // Return new behavior with updated state
            return active(count + 1, events);
        })
        .onMessage(AddEvent.class, msg -> {
            events.add(msg.event);  // Mutable collections are OK
            return Behaviors.same();
        })
        .onMessage(GetState.class, msg -> {
            msg.replyTo.tell(new State(count, new ArrayList<>(events)));
            return Behaviors.same();
        })
        .build();
}
```

**State management rules:**
- ✅ Primitive types can be immutable (return new behavior)
- ✅ Collections can be mutable (single-threaded access guaranteed)
- ✅ Always return `Behaviors.same()` or new behavior
- ❌ Never share state between actors
- ❌ Never expose mutable state outside actor

### 7. Error Handling and Supervision

Actors can supervise child actors and handle failures:

```java
@Override
public Behavior<Command> create(SpringActorContext actorContext) {
    return Behaviors.setup(ctx -> {
        // Spawn supervised child
        ActorRef<ChildActor.Command> child = ctx.spawn(
            ChildActor.create(),
            "child-actor",
            SupervisorStrategy.restart()  // Restart on failure
        );

        return Behaviors.receive(Command.class)
            .onMessage(DelegateToChild.class, msg -> {
                child.tell(msg.childCommand);
                return Behaviors.same();
            })
            .build();
    });
}
```

### 8. Spring Dependency Injection

Inject Spring beans into actors:

```java
@Component
public class OrderActor implements SpringActor<OrderActor, OrderActor.Command> {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;

    // Constructor injection works as expected
    public OrderActor(OrderRepository orderRepository,
                      PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.receive(Command.class)
            .onMessage(ProcessOrder.class, msg -> {
                // Use injected dependencies
                Order order = orderRepository.findById(msg.orderId);
                paymentService.processPayment(order);
                return Behaviors.same();
            })
            .build();
    }
}
```

### 9. Configuration

Configure actors via `application.yml`:

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: local  # or "cluster"

      # Cluster configuration (when provider is "cluster")
      remote:
        artery:
          canonical:
            hostname: "127.0.0.1"
            port: 2551

      cluster:
        seed-nodes:
          - "pekko://MyActorSystem@127.0.0.1:2551"

        # Sharding configuration
        sharding:
          number-of-shards: 100
```

### 10. Testing

Test actors using Pekko's TestKit:

```java
@SpringBootTest
class OrderActorTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Test
    void testOrderProcessing() {
        // Spawn test actor
        SpringActorRef<OrderActor.Command> actor = actorSystem
            .spawn(OrderActor.class)
            .withId("test-order")
            .startAndWait();

        // Test request-response
        CompletionStage<OrderStatus> future =
            actor.ask(OrderActor.GetStatus::new);

        OrderStatus status = future.toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

        assertEquals(OrderStatus.PENDING, status);
    }
}
```

## Quick Start Example

Here's a complete example showing a simple actor:

```java
// 1. Enable actor support
@SpringBootApplication
@EnableActorSupport
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// 2. Define actor
@Component
public class GreetingActor implements SpringActor<GreetingActor, GreetingActor.Command> {

    public interface Command {}

    public static class Greet implements Command {
        public final String name;
        public final ActorRef<String> replyTo;

        public Greet(String name, ActorRef<String> replyTo) {
            this.name = name;
            this.replyTo = replyTo;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.receive(Command.class)
            .onMessage(Greet.class, msg -> {
                msg.replyTo.tell("Hello, " + msg.name + "!");
                return Behaviors.same();
            })
            .build();
    }
}

// 3. Use actor in service
@Service
public class GreetingService {
    private final SpringActorSystem actorSystem;
    private final AtomicReference<CompletionStage<SpringActorRef<GreetingActor.Command>>> actorRef;

    public GreetingService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        this.actorRef = new AtomicReference<>();
    }

    private CompletionStage<SpringActorRef<GreetingActor.Command>> getActor() {
        return actorRef.updateAndGet(existing -> {
            if (existing != null) return existing;
            return actorSystem
                .spawn(GreetingActor.class)
                .withId("greeter")
                .start();
        });
    }

    public CompletionStage<String> greet(String name) {
        return getActor().thenCompose(actor ->
            actor.ask(replyTo -> new GreetingActor.Greet(name, replyTo))
        );
    }
}
```

## Live Examples

### Chat Application Demo

<div style="border: 2px solid #ccc; display: inline-block; border-radius: 8px; overflow: hidden;">
  <img src="mkdocs/docs/chat.gif" alt="Demo"/>
</div>

Run the distributed chat example:

```bash
# Start 3-node cluster on ports 8080, 8081, 8082
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Stop cluster
$ sh cluster-stop.sh
```

Or use Docker:
```bash
cd example/chat
sh init-local-docker.sh

# Access at http://localhost:8080, 8081, 8082
# View logs: docker-compose logs -f chat-app-0
# Stop: docker-compose down
```

## Monitoring

Built-in Prometheus metrics and Grafana dashboards:

```bash
cd scripts/monitoring
docker-compose up -d
```

Access:
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## Migration Guide

### From Plain Akka/Pekko

**Before:**
```java
ActorSystem<Void> system = ActorSystem.create(...);
ActorRef<Command> actor = system.systemActorOf(...);
```

**After:**
```java
@EnableActorSupport  // In Spring Boot app
class MyApp {}

@Service
class MyService {
    private final AtomicReference<CompletionStage<SpringActorRef<Command>>> actorRef;

    MyService(SpringActorSystem actorSystem) {
        this.actorRef = new AtomicReference<>();
    }

    private CompletionStage<SpringActorRef<Command>> getActor() {
        return actorRef.updateAndGet(existing -> {
            if (existing != null) return existing;
            return actorSystem
                .spawn(MyActor.class)
                .withId("my-actor")
                .start();
        });
    }
}
```

### From Spring @Async

**Before:**
```java
@Service
class OrderService {
    @Async
    public CompletableFuture<Order> processOrder(String id) {
        // Processing logic
    }
}
```

**After (with actor):**
```java
@Component
class OrderActor implements SpringActor<OrderActor, Command> {
    // Actor handles concurrency safely
}

@Service
class OrderService {
    private final AtomicReference<CompletionStage<SpringActorRef<Command>>> actorRef;

    OrderService(SpringActorSystem actorSystem) {
        this.actorRef = new AtomicReference<>();
    }

    private CompletionStage<SpringActorRef<Command>> getActor() {
        return actorRef.updateAndGet(existing -> {
            if (existing != null) return existing;
            return actorSystem
                .spawn(OrderActor.class)
                .withId("order-processor")
                .start();
        });
    }

    public CompletionStage<Order> processOrder(String id) {
        return getActor().thenCompose(actor ->
            actor.ask(replyTo -> new ProcessOrder(id, replyTo))
        );
    }
}
```

## Contributing

Contributions welcome! Please:

1. Create an issue describing your contribution
2. Open a PR with clear explanation
3. Run `./gradlew spotlessApply` for formatting
4. Ensure tests pass

## Documentation

Full documentation: [https://seonwkim.github.io/spring-boot-starter-actor/](https://seonwkim.github.io/spring-boot-starter-actor/)

## License

This project is licensed under the Apache License 2.0.
