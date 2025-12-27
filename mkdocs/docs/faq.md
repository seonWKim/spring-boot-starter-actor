# Frequently Asked Questions (FAQ)

Common questions about spring-boot-starter-actor.

## General Questions

### What is spring-boot-starter-actor?

spring-boot-starter-actor is a Spring Boot library that integrates the actor model using Pekko (Apache's open-source fork of Akka). It provides auto-configuration, dependency injection, and simplified APIs for building distributed, concurrent systems with Spring Boot.

### Why use the actor model?

The actor model provides:
- **Concurrency**: Actors process messages sequentially, eliminating race conditions
- **Distribution**: Actors can be distributed across multiple nodes
- **Fault Tolerance**: Built-in supervision and error recovery
- **Scalability**: Horizontal scaling by adding more nodes
- **Isolation**: Actor state is encapsulated and accessed only through messages

### How is this different from regular Spring?

Traditional Spring applications are typically:
- Request-response oriented (REST APIs)
- Stateless (or use external state stores like Redis)
- Vertically scaled

With spring-boot-starter-actor, you can build:
- Event-driven, stateful applications
- Distributed systems without external middleware
- Horizontally scaled actor clusters
- While still using familiar Spring features (DI, configuration, etc.)

### Should I use this for all Spring Boot projects?

No. Use spring-boot-starter-actor when you need:
- Stateful distributed systems
- Event-driven architectures
- High concurrency with isolation
- Actor-based patterns (sagas, workflows, etc.)

For simple REST APIs or CRUD applications, traditional Spring Boot is simpler.

---

## Installation & Setup

### Which artifact should I use?

- **Spring Boot 2.7.x**: `spring-boot-starter-actor`
- **Spring Boot 3.2.x**: `spring-boot-starter-actor_3`

```gradle
// Spring Boot 2.7.x
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.8.0'

// Spring Boot 3.2.x
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.8.0'
```

### Do I need to configure anything after adding the dependency?

Minimal configuration:

1. Add `@EnableActorSupport` to your main class
2. Ensure Jackson 2.17.3+ is in your dependency management

That's it for local mode! Cluster mode requires additional configuration.

### Can I use this with Spring Boot 2.5 or earlier?

Spring Boot 2.7+ is required. For older versions, you would need to manually configure Pekko.

---

## Actor Development

### How do I create an actor?

```java
@Component
public class MyActor implements SpringActor<MyActor.Command> {
    
    public interface Command {}
    
    public record ProcessData(String data) implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(ProcessData.class, (context, msg) -> {
                // Handle message
                return Behaviors.same();
            })
            .build();
    }
}
```

### Can actors use Spring dependency injection?

Yes! Actors are Spring components and support full dependency injection:

```java
@Component
public class MyActor implements SpringActor<Command> {
    
    private final MyService myService;
    private final MyRepository myRepository;
    
    public MyActor(MyService myService, MyRepository myRepository) {
        this.myService = myService;
        this.myRepository = myRepository;
    }
    
    // ... rest of actor implementation
}
```

### When should I use tell vs ask?

- **tell** (fire-and-forget): When you don't need a response
  ```java
  actor.tell(new ProcessData("data"));
  ```

- **ask** (request-response): When you need a response
  ```java
  CompletionStage<String> result = actor
      .ask(new GetData())
      .withTimeout(Duration.ofSeconds(5))
      .execute();
  ```

### Can I do blocking operations in actors?

**No!** Actors should never block. Use async operations:

```java
// ❌ BAD - Blocking
.onMessage(FetchData.class, (ctx, msg) -> {
    String data = blockingHttpClient.get("/api/data");
    return Behaviors.same();
})

// ✅ GOOD - Non-blocking
.onMessage(FetchData.class, (ctx, msg) -> {
    CompletionStage<String> future = asyncHttpClient.get("/api/data");
    future.thenAccept(data -> {
        ctx.getSelf().tell(new DataReceived(data));
    });
    return Behaviors.same();
})
```

For truly blocking operations, use a separate dispatcher with a thread pool.

---

## Clustering & Distribution

### Do I need a cluster for production?

Not necessarily. Local mode works fine for single-instance applications. Use cluster mode when you need:
- High availability (node failures)
- Horizontal scaling
- Distributed state
- Load distribution across nodes

### How do I enable cluster mode?

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster
      remote:
        artery:
          canonical:
            hostname: "node1.example.com"
            port: 2551
      cluster:
        seed-nodes:
          - "pekko://ActorSystem@node1.example.com:2551"
          - "pekko://ActorSystem@node2.example.com:2551"
```

### What's the difference between regular actors and sharded actors?

**Regular Actors**:
- You manually spawn and manage
- Fixed to one node (unless using cluster-aware routers)
- Good for singletons or manually distributed workloads

**Sharded Actors**:
- Automatically distributed across cluster
- Created on-demand when first message arrives
- Automatically rebalanced when nodes join/leave
- Good for distributed entities (users, sessions, etc.)

### Can I use Redis or Kafka with actors?

Yes! Actors can interact with any Spring component:

```java
@Component
public class MyActor implements SpringActor<Command> {
    
    private final KafkaTemplate<String, String> kafka;
    private final RedisTemplate<String, String> redis;
    
    public MyActor(KafkaTemplate kafka, RedisTemplate redis) {
        this.kafka = kafka;
        this.redis = redis;
    }
    
    // Use kafka/redis in message handlers
}
```

However, one advantage of actors is you often don't need external middleware for state or messaging.

---

## Performance & Scaling

### How many actors can I create?

Actors are lightweight (~400 bytes overhead). You can easily have millions of actors in a cluster. However, consider:
- Active actors consume memory for state
- Mailbox size affects memory
- Too many concurrent actors may overwhelm the dispatcher

### How do I improve actor performance?

1. **Avoid blocking**: Use async operations
2. **Use appropriate dispatchers**: Configure thread pools
3. **Batch messages**: Process multiple items per message
4. **Use routers**: Distribute load across multiple workers
5. **Monitor metrics**: Use the metrics module to identify bottlenecks

### Can actors scale horizontally?

Yes, in cluster mode:
- Add more nodes to the cluster
- Sharded actors automatically rebalance
- Use routers to distribute load
- Scale up to hundreds of nodes

---

## Monitoring & Debugging

### How do I monitor actors in production?

Use the metrics module:

```gradle
implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:0.8.0'
```

Then access metrics via Spring Boot Actuator:
```bash
curl http://localhost:8080/actuator/metrics/actor.processing-time
curl http://localhost:8080/actuator/metrics/system.active-actors
```

### How do I debug actor issues?

1. **Enable debug logging**:
   ```yaml
   logging:
     level:
       org.apache.pekko: DEBUG
       io.github.seonwkim: DEBUG
   ```

2. **Use Pekko TestKit** for unit testing
3. **Monitor dead letters** for undelivered messages
4. **Check actor lifecycle logs**

### Can I use distributed tracing?

Yes, distributed tracing support is planned. Currently, you can propagate trace context manually in messages.

---

## Persistence & State

### How do I persist actor state?

Actors can use any Spring Data repository:

```java
@Component
public class MyActor implements SpringActor<Command> {
    
    private final MyRepository repository;
    private String currentState;
    
    public MyActor(MyRepository repository) {
        this.repository = repository;
    }
    
    // Load state on startup, save on changes
}
```

For event sourcing patterns, see the Persistence Guide in the documentation.

### What about Pekko Persistence?

Pekko Persistence is available, but we recommend using familiar Spring Data patterns instead. This provides:
- Consistency with existing Spring applications
- Freedom to choose any database
- Familiar APIs (JPA, MongoDB, etc.)

---

## Security

### Is cluster communication secure?

By default, no. For production, enable TLS:

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          transport: tls-tcp
          ssl:
            enabled: true
```

See [SECURITY.md](../SECURITY.md) for details.

### How do I authenticate actors?

Authentication and authorization support is planned for a future release. Currently, you can:
- Use Spring Security at the application boundary
- Implement custom message validation
- Use Spring's `@Secured` annotations on services called by actors

---

## Migration & Compatibility

### Can I migrate from Akka to Pekko?

Pekko is a fork of Akka with similar APIs. Migration usually involves:
1. Changing package names (`akka.*` → `pekko.*`)
2. Updating configuration keys
3. Testing thoroughly

This library abstracts many Pekko details, making migration easier.

### Will my code work with future versions?

We follow semantic versioning:
- **Patch versions** (0.8.x): Bug fixes, no breaking changes
- **Minor versions** (0.x.0): New features, backward compatible
- **Major versions** (x.0.0): May include breaking changes

See [CHANGELOG.md](../CHANGELOG.md) for version history.

### How do I upgrade versions?

1. Check the [CHANGELOG.md](../CHANGELOG.md) for breaking changes
2. Update the dependency version
3. Run tests
4. For major versions, review migration guide (if provided)

---

## Common Patterns

### How do I implement request-response with timeout?

```java
actor.ask(new GetData())
    .withTimeout(Duration.ofSeconds(5))
    .onTimeout(() -> "default-value")
    .execute()
    .thenAccept(result -> {
        // Handle result
    });
```

### How do I implement fan-out/fan-in?

Use a router or spawn multiple workers:

```java
List<CompletionStage<Result>> futures = workers.stream()
    .map(worker -> worker.ask(new Process(data))
        .withTimeout(Duration.ofSeconds(5))
        .execute())
    .collect(Collectors.toList());

CompletionStage<List<Result>> allResults = 
    CompletionStage.allOf(futures.toArray(new CompletionStage[0]))
        .thenApply(v -> futures.stream()
            .map(CompletionStage::toCompletableFuture)
            .map(CompletableFuture::join)
            .collect(Collectors.toList()));
```

### How do I implement a state machine?

Use different behaviors for different states:

```java
private Behavior<Command> idle() {
    return SpringActorBehavior.builder(Command.class, ctx)
        .onMessage(Start.class, (context, msg) -> processing())
        .build();
}

private Behavior<Command> processing() {
    return SpringActorBehavior.builder(Command.class, ctx)
        .onMessage(Complete.class, (context, msg) -> idle())
        .build();
}
```

---

## Getting Help

### Where can I ask questions?

- **Discord**: [Join our server](https://discord.com/channels/1439734161614045205/1439734162100846655) for real-time help
- **GitHub Discussions**: [Q&A forum](https://github.com/seonwkim/spring-boot-starter-actor/discussions)
- **GitHub Issues**: For bug reports and feature requests

### Where is the documentation?

- **Documentation Site**: [seonwkim.github.io/spring-boot-starter-actor](https://seonwkim.github.io/spring-boot-starter-actor/)
- **README**: Comprehensive overview and quick start
- **Examples**: [example directory](https://github.com/seonwkim/spring-boot-starter-actor/tree/main/example)
- **Troubleshooting**: [Guide for common issues](troubleshooting.md)

### How can I contribute?

See [CONTRIBUTING.md](../CONTRIBUTING.md) for detailed guidelines.

---

**Have a question not answered here?** Ask on [Discord](https://discord.com/channels/1439734161614045205/1439734162100846655) or [GitHub Discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions)!
