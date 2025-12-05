<table>
<tr>
<td width="15%">
  <img src="mkdocs/docs/logo.png" alt="Library Logo" />
</td>
<td>
  <h2>Spring Boot Starter Actor</h2>
  <p>Bring the power of the actor model to your Spring Boot applications with <a href="https://pekko.apache.org/">Pekko</a> (an open-source fork of Akka).</p>
</td>
</tr>
</table>

[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?style=flat&logo=discord&logoColor=white)](https://discord.com/channels/1439734161614045205/1439734162100846655)
[![Documentation](https://img.shields.io/badge/docs-latest-blue)](https://seonwkim.github.io/spring-boot-starter-actor/)

## Why spring-boot-starter-actor?

I'm a Java developer, and I love using the actor model. However, Spring is everywhere in production. The goal of this project is to help Spring Boot projects easily integrate actor models with a great developer experience.

**Key Features:**

- Auto-configure necessary actor components (e.g., ActorSystem) within Spring Boot's context
- Dependency injection for actors
- Simplify cluster and sharding
- Built-in metrics using ByteBuddy to intercept actors and collect metrics

<div style="border: 2px solid #ccc; display: inline-block; border-radius: 8px; overflow: hidden; margin: 20px 0;">
  <img src="mkdocs/docs/chat.gif" alt="Live Demo - Distributed Chat Application"/>
</div>

With spring-boot-starter-actor, you can build stateful distributed systems without third-party middleware (e.g., Redis, Kafka).

## Quick Start

### Prerequisites

- Java 11 or higher
- Spring Boot 2.x or 3.x

### Installation

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
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.3.0'

// Spring Boot 3.2.x
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.3.0'
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
<version>0.3.0</version>
</dependency>

        <!-- Spring Boot 3.2.x -->
<dependency>
<groupId>io.github.seonwkim</groupId>
<artifactId>spring-boot-starter-actor_3</artifactId>
<version>0.3.0</version>
</dependency>
```

Latest
versions: [spring-boot-starter-actor](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor) | [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

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

### Create Your First Actor

Create an actor by implementing `SpringActor`:

```java

@Component
public class GreeterActor implements SpringActor<GreeterActor.Command> {

    public interface Command {
    }

    public static class Greet extends AskCommand<String> implements Command {
        public final String name;

        public Greet(String name) {
            this.name = name;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(Greet.class, (ctx, msg) -> {
                    msg.reply("Hello, " + msg.name + "!");
                    return Behaviors.same();
                })
                .build();
    }
}
```

### Use Actors in Your Services

Inject `SpringActorSystem` and interact with actors:

```java

@Service
public class GreeterService {
    private final SpringActorSystem actorSystem;

    public GreeterService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public CompletionStage<String> greet(String name) {
        return actorSystem.getOrSpawn(GreeterActor.class, "greeter")
                .thenCompose(actor -> actor
                        .ask(new GreeterActor.Greet(name))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute()
                );
    }
}
```

## Core Concepts

### Actor Lifecycle Management

**Spawn a New Actor:**

```java
// Create and start a new actor
CompletionStage<SpringActorRef<Command>> actorRef = actorSystem
                .actor(MyActor.class)
                .withId("my-actor-1")
                .withTimeout(Duration.ofSeconds(5))  // Optional
                .spawn();
```

**Get Existing Actor:**

```java
// Get reference to existing actor (returns null if not found)
CompletionStage<SpringActorRef<Command>> actorRef = actorSystem
                .get(MyActor.class, "my-actor-1");
```

**Get or Spawn (Recommended):**

```java
// Automatically gets existing or spawns new actor
CompletionStage<SpringActorRef<Command>> actorRef = actorSystem
                .getOrSpawn(MyActor.class, "my-actor-1");
```

**Check if Actor Exists:**

```java
CompletionStage<Boolean> exists = actorSystem
        .exists(MyActor.class, "my-actor-1");
```

**Stop an Actor:**

```java
actorRef.thenAccept(actor -> actor.stop());
```

### Communication Patterns

**Fire-and-forget (tell):**

```java
actor.tell(new ProcessOrder("order-123"));
```

**Request-response (ask):**

```java
CompletionStage<String> response = actor
        .ask(new GetValue())
        .withTimeout(Duration.ofSeconds(5))
        .execute();
```

**With error handling:**

```java
CompletionStage<String> response = actor
        .ask(new GetValue())
        .withTimeout(Duration.ofSeconds(5))
        .onTimeout(() -> "default-value")
        .execute();
```

### Spring Dependency Injection

Actors are Spring components with full DI support:

```java

@Component
public class OrderActor implements SpringActor<OrderActor.Command> {

    private final OrderRepository orderRepository;

    public OrderActor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public interface Command {
    }

    public record ProcessOrder(String orderId) implements Command {
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(ProcessOrder.class, (ctx, msg) -> {
                    Order order = orderRepository.findById(msg.orderId);
                    // Process order...
                    return Behaviors.same();
                })
                .build();
    }
}
```

## Configuration

### Local Mode (Default)

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: local
```

### Cluster Mode

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster
      remote:
        artery:
          canonical:
            hostname: "127.0.0.1"
            port: 2551
      cluster:
        seed-nodes:
          - "pekko://MyActorSystem@127.0.0.1:2551"
```

## Advanced Features

### Sharded Actors (Cluster Mode)

For distributed systems, use sharded actors that are automatically distributed across cluster nodes:

**Define a Sharded Actor:**

```java

@Component
public class UserSessionActor implements SpringShardedActor<UserSessionActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "UserSession");

    public interface Command extends JsonSerializable {
    }

    public record UpdateActivity(String activity) implements Command {
    }

    public static class GetActivity extends AskCommand<String> implements Command {
        public GetActivity() {
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(entityCtx -> new UserSessionBehavior(ctx.getEntityId()))
                .onMessage(UpdateActivity.class, UserSessionBehavior::onUpdateActivity)
                .onMessage(GetActivity.class, UserSessionBehavior::onGetActivity)
                .build();
    }

    private static class UserSessionBehavior {
        private final String userId;
        private String activity = "idle";

        UserSessionBehavior(String userId) {
            this.userId = userId;
        }

        Behavior<Command> onUpdateActivity(UpdateActivity msg) {
            this.activity = msg.activity;
            return Behaviors.same();
        }

        Behavior<Command> onGetActivity(GetActivity msg) {
            msg.reply(activity);
            return Behaviors.same();
        }
    }
}
```

**Using Sharded Actors:**

```java
// Get reference (entity created on-demand)
SpringShardedActorRef<Command> actor = actorSystem
                .sharded(UserSessionActor.class)
                .withId("user-123")
                .get();

// Fire-and-forget
actor.tell(new UpdateActivity("logged-in"));

// Request-response
CompletionStage<String> activity = actor
        .ask(new GetActivity())
        .withTimeout(Duration.ofSeconds(5))
        .execute();
```

**Key Differences from Regular Actors:**

- Created automatically when first message arrives (no `spawn()` needed)
- Always available, even if not currently running
- Automatically distributed across cluster nodes
- Passivated after idle timeout (configurable)
- Use `get()` to obtain reference (not `spawn()`)

### Supervision and Fault Tolerance

Build self-healing systems with supervision strategies:

**Available Strategies:**

```java
// Restart on failure (default)
SupervisorStrategy.restart()

// Restart with limit (e.g., 3 times within 1 minute)
SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1))

// Stop on failure
SupervisorStrategy.stop()

// Resume and ignore failure
SupervisorStrategy.resume()
```

**Spawn Actors with Supervision:**

```java
// Top-level actor
actorSystem.actor(WorkerActor.class)
    .withId("worker-1")
    .withSupervisionStrategy(SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1)))
    .spawn();
```

**Spawn Child Actors with Supervision:**

```java

@Component
public class SupervisorActor implements SpringActor<SupervisorActor.Command> {

    public interface Command {
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(DelegateWork.class, (ctx, msg) -> {
                    SpringActorRef<Command> self = new SpringActorRef<>(ctx.getSystem().scheduler(), ctx.getSelf());

                    // Spawn supervised child
                    self.child(WorkerActor.class)
                            .withId("worker-1")
                            .withSupervisionStrategy(SupervisorStrategy.restart())
                            .spawn();

                    return Behaviors.same();
                })
                .build();
    }
}
```

**Child Actor Operations:**

```java
// Spawn new child
CompletionStage<SpringActorRef<Command>> child = parentRef
                .child(ChildActor.class)
                .withId("child-1")
                .spawn();

// Get existing child
CompletionStage<SpringActorRef<Command>> existing = parentRef
        .child(ChildActor.class)
        .withId("child-1")
        .get();

// Get or spawn (recommended)
CompletionStage<SpringActorRef<Command>> childRef = parentRef
        .child(ChildActor.class)
        .withId("child-1")
        .getOrSpawn();
```

## Running Examples

### Chat Application (Distributed)

Run a distributed chat application across multiple nodes:

```bash
# Start 3-node cluster on ports 8080, 8081, 8082
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# run frontend 
$ cd example/chat/frontend
$ npm run dev 

# Stop cluster
$ sh cluster-stop.sh
```

## Monitoring

WIP

## Documentation

Full
documentation: [https://seonwkim.github.io/spring-boot-starter-actor/](https://seonwkim.github.io/spring-boot-starter-actor/)

## Community & Support

Join our community to ask questions, share ideas, and get help:

- **Discord**: [Join our Discord server](https://discord.com/channels/1439734161614045205/1439734162100846655) -
  Real-time chat with the community
- **Issues**: [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues) - Bug reports and feature
  requests
- **Discussions**: [GitHub Discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions) - Q&A and
  general discussions

## Contributing

Contributions welcome! Please:

1. Create an issue describing your contribution
2. Open a PR with clear explanation
3. Run `./gradlew spotlessApply` for formatting
4. Ensure tests pass

See [CONTRIBUTION.md](CONTRIBUTION.md) for detailed guidelines and [roadmap/ROADMAP.md](roadmap/ROADMAP.md) for the
implementation roadmap.

## License

This project is licensed under the Apache License 2.0.
