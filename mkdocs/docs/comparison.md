# Comparison with Other Actor Frameworks

How spring-boot-starter-actor compares with other actor model implementations.

## Overview

| Feature | spring-boot-starter-actor | Akka (Typed) | Akka Classic | Vert.x | Quasar |
|---------|---------------------------|--------------|--------------|---------|---------|
| **Language** | Java | Java/Scala | Java/Scala | Java | Java |
| **Spring Integration** | ✅ Native | ⚠️ Manual | ⚠️ Manual | ⚠️ Manual | ⚠️ Manual |
| **License** | Apache 2.0 | BSL 1.1* | Apache 2.0* | Apache 2.0 | EPL/LGPL |
| **Cluster Support** | ✅ | ✅ | ✅ | ✅ | Limited |
| **Type Safety** | ✅ | ✅ | ❌ | ❌ | ✅ |
| **Learning Curve** | Low (Spring) | High | High | Medium | Medium |
| **Spring Boot Auto-Config** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Dependency Injection** | ✅ Native | ⚠️ Extension | ⚠️ Extension | ⚠️ Manual | ⚠️ Manual |
| **Community Size** | Growing | Large | Large | Large | Small |

*Akka changed license to BSL in version 2.7+. Akka Classic remains Apache 2.0.

---

## Detailed Comparisons

### vs. Akka (Typed)

**spring-boot-starter-actor** is built on **Pekko** (Apache's fork of Akka), so it inherits Akka's proven actor model.

#### Advantages of spring-boot-starter-actor:

✅ **Spring Boot Native**
```java
// spring-boot-starter-actor - Spring component
@Component
public class MyActor implements SpringActor<Command> {
    private final MyService service;
    
    public MyActor(MyService service) {  // DI works naturally
        this.service = service;
    }
}

// Akka - Manual dependency injection
public class MyActor extends AbstractBehavior<Command> {
    public static Behavior<Command> create(MyService service) {
        return Behaviors.setup(ctx -> new MyActor(ctx, service));
    }
    // More boilerplate needed
}
```

✅ **Auto-Configuration**
```yaml
# spring-boot-starter-actor - Auto-configured
spring:
  actor:
    pekko:
      actor:
        provider: cluster

# Akka - Manual configuration
# Requires programmatic ActorSystem creation
```

✅ **Spring Boot Actuator Integration**
```bash
# Built-in health checks and metrics
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/actor.processing-time
```

✅ **Apache 2.0 License** (via Pekko)
- Akka 2.7+ uses BSL (Business Source License)
- Production use may require Lightbend subscription
- Pekko remains Apache 2.0 (community-driven)

#### When to use Akka instead:

- Need commercial support from Lightbend
- Using Scala extensively
- Need features not yet in Pekko
- Existing Akka investment

---

### vs. Akka Classic

Akka Classic is the older, untyped API.

#### Advantages of spring-boot-starter-actor:

✅ **Type Safety**
```java
// spring-boot-starter-actor - Type-safe messages
public record ProcessData(String data) implements Command {}
actor.tell(new ProcessData("test"));  // Compile-time checking

// Akka Classic - Untyped
actor.tell("PROCESS_DATA", sender);  // No compile-time checking
```

✅ **Better IDE Support**
- Auto-completion works better with typed messages
- Refactoring is safer
- Less runtime errors

✅ **Modern API**
- Built on Akka Typed (more recent)
- Better patterns and practices
- Active development (via Pekko)

#### Migration Note:

Akka Classic is being phased out in favor of Akka Typed. spring-boot-starter-actor uses the modern typed approach via Pekko.

---

### vs. Vert.x

Vert.x is an event-driven, non-blocking framework.

#### Key Differences:

| Aspect | spring-boot-starter-actor | Vert.x |
|--------|---------------------------|---------|
| **Model** | Actor model (message-passing) | Event bus (pub/sub) |
| **State** | Stateful actors | Stateless verticles* |
| **Typed Messages** | Yes | No |
| **Cluster** | Pekko cluster | Hazelcast/Infinispan |
| **Spring Integration** | Native | Via extensions |

*Vert.x verticles can maintain state, but it's not the primary pattern.

#### When to use spring-boot-starter-actor:

- **Need stateful distributed entities**
  ```java
  // Each user session is a separate actor with state
  @Component
  public class UserSessionActor implements SpringShardedActor<Command> {
      private final String userId;
      private final Map<String, Object> sessionData = new HashMap<>();
  }
  ```

- **Want actor supervision and fault tolerance**
  ```java
  // Automatic restart on failure
  actorSystem.actor(MyActor.class)
      .withSupervisionStrategy(SupervisorStrategy.restart())
      .spawn();
  ```

- **Prefer message-passing over event bus**

#### When to use Vert.x:

- Need polyglot support (JavaScript, Ruby, etc.)
- Building reactive APIs (more HTTP-focused)
- Want different concurrency model
- Prefer event bus pattern

#### Can be used together:

```java
@Component
public class MyVerticle extends AbstractVerticle {
    @Autowired
    private SpringActorSystem actorSystem;  // Use actors from Vert.x!
    
    @Override
    public void start() {
        vertx.eventBus().consumer("address", msg -> {
            // Forward to actor
            actorSystem.getOrSpawn(ProcessorActor.class, "processor")
                .thenAccept(actor -> actor.tell(new Process(msg.body())));
        });
    }
}
```

---

### vs. Quasar

Quasar provides actors and fibers (lightweight threads) for Java.

#### Key Differences:

| Aspect | spring-boot-starter-actor | Quasar |
|--------|---------------------------|---------|
| **Active Development** | ✅ Active (Pekko) | ⚠️ Limited |
| **Spring Integration** | ✅ Native | ❌ None |
| **Cluster** | ✅ Full-featured | ⚠️ Limited |
| **Fibers** | Use Virtual Threads (Java 21+) | Custom implementation |
| **Community** | Growing | Small |

#### Advantages of spring-boot-starter-actor:

- **Active ecosystem**: Pekko is actively maintained by Apache
- **Production-ready cluster**: Battle-tested cluster implementation
- **Spring Boot integration**: Works with existing Spring apps
- **Virtual Threads support**: Can use Java 21+ virtual threads

#### When to use Quasar:

- Already using Quasar
- Need specific Quasar features (channels, etc.)
- Want fiber-based concurrency

**Note**: Quasar development has slowed. Consider Java 21+ Virtual Threads as alternative.

---

### vs. Pure Pekko

Since spring-boot-starter-actor is built on Pekko:

#### What spring-boot-starter-actor adds:

✅ **Spring Boot Auto-Configuration**
```java
@SpringBootApplication
@EnableActorSupport  // That's it!
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

✅ **Dependency Injection**
```java
@Component
public class OrderActor implements SpringActor<Command> {
    // All Spring features available
    @Autowired
    private OrderRepository repository;
    
    @Value("${order.timeout}")
    private Duration timeout;
}
```

✅ **Spring Boot Configuration**
```yaml
# YAML configuration instead of HOCON
spring:
  actor:
    pekko:
      actor:
        provider: cluster
```

✅ **Simplified API**
```java
// spring-boot-starter-actor
actorSystem.getOrSpawn(MyActor.class, "my-actor")
    .thenAccept(actor -> actor.tell(new Message()));

// Pure Pekko - more boilerplate
ActorRef<Command> actor = context.spawn(
    MyActor.create(),
    "my-actor",
    Props.empty()
);
actor.tell(new Message());
```

✅ **Metrics Integration**
```gradle
// Built-in metrics via ByteBuddy
implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:0.8.0'
```

#### When to use pure Pekko:

- Not using Spring Boot
- Need low-level Pekko control
- Scala application
- Minimal dependencies required

---

## Migration Paths

### From Akka to spring-boot-starter-actor

1. **Update dependencies** (Akka → Pekko)
2. **Change imports** (`akka.*` → `pekko.*`)
3. **Add Spring integration**:
   ```java
   @Component
   public class MyActor implements SpringActor<Command> {
       // Convert AbstractBehavior to SpringActor
   }
   ```
4. **Update configuration** (HOCON → YAML)

### From Vert.x to spring-boot-starter-actor

1. **Identify stateful components** → Convert to actors
2. **Event bus patterns** → Message-passing patterns
3. **Verticles** → Spring components with actor spawning

### From Akka Classic to spring-boot-starter-actor

1. **Make messages type-safe** (records/classes)
2. **Update actor implementation** (typed API)
3. **Add Spring Boot integration**

---

## Decision Matrix

Choose **spring-boot-starter-actor** if:

- ✅ Using Spring Boot
- ✅ Want actor model benefits with Spring ecosystem
- ✅ Need stateful distributed systems
- ✅ Prefer Apache 2.0 license
- ✅ Want simpler configuration and DI

Choose **Akka** if:

- Using Scala
- Need commercial support
- Require specific Akka features not in Pekko yet

Choose **Vert.x** if:

- Want event-driven, reactive approach
- Need polyglot support
- Building API-heavy applications

Choose **Quasar** if:

- Already invested in Quasar
- Need specific Quasar features
- (Consider Java Virtual Threads instead)

---

## Code Comparison

### Creating an Actor

**spring-boot-starter-actor:**
```java
@Component
public class MyActor implements SpringActor<Command> {
    private final MyService service;
    
    public MyActor(MyService service) {
        this.service = service;
    }
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(Process.class, this::handleProcess)
            .build();
    }
}
```

**Akka Typed:**
```java
public class MyActor extends AbstractBehavior<Command> {
    private final MyService service;
    
    public static Behavior<Command> create(MyService service) {
        return Behaviors.setup(ctx -> new MyActor(ctx, service));
    }
    
    private MyActor(ActorContext<Command> ctx, MyService service) {
        super(ctx);
        this.service = service;
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Process.class, this::handleProcess)
            .build();
    }
}
```

**Vert.x:**
```java
public class MyVerticle extends AbstractVerticle {
    private MyService service;  // Manual injection
    
    @Override
    public void start() {
        vertx.eventBus().consumer("address", msg -> {
            // Handle message
        });
    }
}
```

### Configuration

**spring-boot-starter-actor:**
```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster
      remote:
        artery:
          canonical:
            hostname: "localhost"
            port: 2551
```

**Akka:**
```hocon
akka {
  actor {
    provider = cluster
  }
  remote.artery {
    canonical {
      hostname = "localhost"
      port = 2551
    }
  }
}
```

**Vert.x:**
```java
// Programmatic configuration
VertxOptions options = new VertxOptions()
    .setClusterManager(clusterManager);
Vertx.clusteredVertx(options, res -> {
    // ...
});
```

---

## Performance Comparison

All frameworks have excellent performance. Key factors:

- **Latency**: All have sub-millisecond message passing
- **Throughput**: All handle millions of messages/second
- **Scalability**: All scale horizontally

**spring-boot-starter-actor** inherits Pekko's performance characteristics (similar to Akka).

---

## Summary

**spring-boot-starter-actor** is ideal for:
- Spring Boot applications
- Teams familiar with Spring
- Need for stateful distributed systems
- Apache 2.0 license requirement
- Simplified actor model adoption

It provides the power of the actor model with the familiarity of Spring Boot, making it easier to build distributed, stateful systems without leaving the Spring ecosystem.

---

**Questions?** Join our [Discord](https://discord.com/channels/1439734161614045205/1439734162100846655) or check the [FAQ](faq.md).
