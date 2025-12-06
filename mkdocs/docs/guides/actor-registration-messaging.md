# Actor Registration and Messaging

This guide explains how to register actors, spawn them, and send messages using Spring Boot Starter Actor.

## Registering Actors

In Spring Boot Starter Actor, actors are registered as Spring components. This allows them to be automatically discovered and managed by the Spring container.

### Creating a Simple Actor

To create an actor, implement the `SpringActor` interface and annotate the class with `@Component`:

```java
@Component
public class HelloActor implements SpringActor<HelloActor.Command> {

    // Define the command interface for messages this actor can handle
    public interface Command {}

    // Define a message type
    public static class SayHi implements Command {}

    // Define a message type
    public static class SayHello extends AskCommand<String> implements Command {
        public SayHello() {}
    }

    // Create the behavior for this actor
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
            .onMessage(SayHi.class, HelloActorBehavior::onSayHi)
            .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
            .build();
    }

    // Inner class to handle the actor's behavior
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        private Behavior<Command> onSayHi(SayHi msg) {
            ctx.getLog().info("Received SayHi for id={}", actorContext.actorId());
            return Behaviors.same();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorContext.actorId());
            msg.reply("Hello from actor " + actorContext.actorId());
            return Behaviors.same();
        }
    }
}
```

## Spawning Actors

Once you've registered your actor, you can spawn instances of it using the `SpringActorSystem`.

### Recommended: Use getOrSpawn (Simplified API)

The **recommended way** to work with actors is using `getOrSpawn()`, which automatically handles the actor lifecycle:

```java
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Best practice: Use getOrSpawn for simple cases where you don't need caching.
     * It automatically handles the exists -> get -> spawn logic in a single call.
     */
    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
                        .thenCompose(actor -> actor.ask(new HelloActor.SayHello())
                                .withTimeout(Duration.ofSeconds(3))
                                .execute()));
    }
}
```

!!! tip "When to use getOrSpawn"
    Use `getOrSpawn()` for most cases as it simplifies actor lifecycle management and reduces boilerplate code.

### Advanced: Explicit Spawning with Builder API

For advanced scenarios requiring custom configuration (supervision strategies, dispatchers, mailboxes), use the fluent builder API:

```java
@Service
public class HelloService {

    private final SpringActorHandle<HelloActor.Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        // Spawn an actor using the fluent builder API
        this.helloActor = springActorSystem
                .actor(HelloActor.class)
                .withId("default")
                .withTimeout(Duration.ofSeconds(3))
                .spawnAndWait();
    }

    // Service methods...
}
```

### Advanced: Async Spawning with Configuration

For non-blocking actor creation with custom configuration:

```java
@Service
public class HelloService {
    private final CompletionStage<SpringActorHandle<HelloActor.Command>> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        // Spawn asynchronously
        this.helloActor = springActorSystem
                .actor(HelloActor.class)
                .withId("default")
                .withTimeout("3s")  // Can use string format
                .spawn();
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
            helloActor.thenCompose(actor ->
                actor.ask(new HelloActor.SayHello())
                    .withTimeout(Duration.ofSeconds(3))
                    .execute()
            )
        );
    }
}
```

### Advanced Configuration

The fluent API supports additional configuration options for advanced use cases:

```java
SpringActorHandle<HelloActor.Command> actor = springActorSystem
        .actor(HelloActor.class)
        .withId("myActor")
        .withTimeout(Duration.ofSeconds(5))
        .withMailbox(MailboxConfig.bounded(100)) // For mailbox customization 
        .withBlockingDispatcher()  // For blocking I/O operations
        .asClusterSingleton()     // For cluster singleton actors
        .withContext(customContext)  // Custom actor context
        .spawn();
```

**Learn more about dispatchers:**

- See the [Dispatchers Guide](dispatchers.md) for detailed information about thread management and dispatcher configuration

## Sending Messages to Actors

Once you have a reference to an actor, you can send messages to it:

### Tell Pattern (Fire-and-Forget)

The tell pattern is used when you don't need a response from the actor:

```java
public void hi() {
    actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
            .thenAccept(actor -> actor.tell(new HelloActor.SayHi()));
}
```

### Ask Pattern (Request-Response)

The ask pattern is used when you expect a response from the actor:

```java
public Mono<String> hello() {
    return Mono.fromCompletionStage(
            actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
                    .thenCompose(actor -> actor.ask(new HelloActor.SayHello())
                            .withTimeout(Duration.ofSeconds(3))
                            .execute()));
}
```

## Stopping Actors

You can gracefully stop actors when they are no longer needed:

### Simplified Stop API

```java
actorRef.stop();
```

!!! warning "Actor Termination"
    Stopping an actor is permanent. You'll need to spawn a new instance if you need the actor again.

## Actor Lifecycle Operations

### Checking if an Actor Exists

Check if an actor is already running before creating a new instance:

```java
CompletionStage<Boolean> exists = actorSystem
    .exists(MyActor.class, "my-actor-1");

// With custom timeout
exists = actorSystem.exists(MyActor.class, "my-actor-1", Duration.ofMillis(500));
```

### Getting an Existing Actor Reference

Retrieve a reference to an existing actor without creating a new instance:

```java
CompletionStage<SpringActorHandle<Command>> actorRef = actorSystem
    .get(MyActor.class, "my-actor-1");

// With custom timeout
actorRef = actorSystem.get(MyActor.class, "my-actor-1", Duration.ofMillis(500));

// Returns null if actor doesn't exist
```

### Get or Create Pattern (Recommended)

**Simple Approach - Use `getOrSpawn`:**

The `getOrSpawn` method automatically handles the exists/get/spawn logic:

```java
// Recommended: Use getOrSpawn for simplified actor lifecycle management
CompletionStage<SpringActorHandle<Command>> actorRef = actorSystem
    .getOrSpawn(MyActor.class, "my-actor-1");

// With custom timeout
actorRef = actorSystem.getOrSpawn(MyActor.class, "my-actor-1", Duration.ofSeconds(5));
```

**Manual Approach (for advanced cases):**

If you need more control, you can manually check exists and spawn:

```java
actorSystem.exists(MyActor.class, "my-actor-1")
    .thenCompose(exists -> {
        if (exists) {
            return actorSystem.get(MyActor.class, "my-actor-1");
        } else {
            return actorSystem.actor(MyActor.class)
                .withId("my-actor-1")
                .spawn();
        }
    });
```

### Lazy Initialization Pattern

**Simple Approach (Recommended for most cases):**

Use `getOrSpawn` directly - it's simple, efficient, and handles the lifecycle automatically:

```java
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * getOrSpawn handles the exists/get/spawn logic automatically.
     * This is the recommended approach for most use cases.
     */
    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
                        .thenCompose(actor ->
                            actor.ask(new HelloActor.SayHello())
                                .withTimeout(Duration.ofSeconds(3))
                                .execute()));
    }
}
```

**With Caching (For high-frequency access):**

If the same actor is accessed very frequently, cache the reference to avoid repeated lookups:

```java
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;
    private final AtomicReference<CompletionStage<SpringActorHandle<HelloActor.Command>>> actorRef =
            new AtomicReference<>();

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Lazily initializes the actor on first use and caches the reference
     * for subsequent calls. Use this for high-frequency access scenarios.
     */
    private CompletionStage<SpringActorHandle<HelloActor.Command>> getActor() {
        return actorRef.updateAndGet(existing ->
                existing != null ? existing : actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
        );
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                getActor().thenCompose(actor ->
                    actor.ask(new HelloActor.SayHello())
                        .withTimeout(Duration.ofSeconds(3))
                        .execute()));
    }
}
```

## Spawning Child Actors

Child actors can be spawned from within parent actors using the fluent child builder API. This provides a consistent and type-safe way to manage actor hierarchies.

### Basic Child Spawning

Use the `child()` method on `SpringActorHandle` to spawn child actors:

```java
@Component
public class ParentActor implements SpringActor<ParentActor.Command> {

    public interface Command extends FrameworkCommand {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                // Get reference to self
                SpringActorHandle<Command> self = new SpringActorHandle<>(
                    ctx.getSystem().scheduler(), ctx.getSelf());

                // Spawn a child actor using fluent API
                self.child(ChildActor.class)
                    .withId("worker-1")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .withTimeout(Duration.ofSeconds(5))
                    .spawn();  // Returns CompletionStage<SpringActorHandle>

                return new ParentBehavior(ctx, actorContext);
            })
            .build();
    }
}
```

### Child Actor Operations

The child builder API supports several operations:

**Spawn a new child:**
```java
CompletionStage<SpringActorHandle<Command>> child = parentRef
    .child(ChildActor.class)
    .withId("child-1")
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawn();  // Async

// Or synchronous spawning
SpringActorHandle<Command> child = parentRef
    .child(ChildActor.class)
    .withId("child-2")
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawnAndWait();  // Blocks until spawned
```

**Get existing child reference:**
```java
CompletionStage<SpringActorHandle<Command>> existing = parentRef
    .child(ChildActor.class)
    .withId("child-1")
    .get();  // Returns null if not found
```

**Check if child exists:**
```java
CompletionStage<Boolean> exists = parentRef
    .child(ChildActor.class)
    .withId("child-1")
    .exists();
```

**Get existing or spawn new (recommended):**
```java
CompletionStage<SpringActorHandle<Command>> childRef = parentRef
    .child(ChildActor.class)
    .withId("child-1")
    .getOrSpawn();  // Gets existing or creates new
```

### Child Actor with Custom Context

You can provide custom context for child actors:

```java
SpringActorContext customContext = new SpringActorContext() {
    @Override
    public String actorId() {
        return "custom-child-id";
    }
};

SpringActorHandle<Command> child = parentRef
    .child(ChildActor.class)
    .withContext(customContext)
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawnAndWait();
```

### Supervision Strategies for Children

Apply different supervision strategies based on the child's role:

```java
// Restart strategy for stateless workers
self.child(WorkerActor.class)
    .withId("worker")
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawn();

// Limited restart for production systems
self.child(WorkerActor.class)
    .withId("critical-worker")
    .withSupervisionStrategy(SupervisorStrategy.restart().withLimit(3, Duration.ofMinutes(1)))
    .spawn();

// Stop strategy for actors that should fail fast
self.child(ValidatorActor.class)
    .withId("validator")
    .withSupervisionStrategy(SupervisorStrategy.stop())
    .spawn();

// Resume strategy for non-critical failures
self.child(LoggerActor.class)
    .withId("logger")
    .withSupervisionStrategy(SupervisorStrategy.resume())
    .spawn();
```

## Best Practices

1. **Use getOrSpawn**: For most cases, use `getOrSpawn()` instead of manually checking exists/get/spawn - it's simpler and reduces boilerplate
2. **Lazy Initialization**: Use lazy initialization to avoid blocking application startup. For simple cases, use `getOrSpawn` directly; for high-frequency access, add caching with `AtomicReference`
3. **Actor Hierarchy**: Organize actors in a hierarchy to manage their lifecycle and supervision
4. **Choose Appropriate Supervision**: Select supervision strategies based on the child actor's role and failure characteristics
5. **Use Framework Commands**: Make your Command interface extend `FrameworkCommand` when building parent actors that need to spawn children. Framework command handling is automatically enabled
6. **Message Immutability**: Ensure that messages sent to actors are immutable to prevent concurrency issues
7. **Timeout Handling**: Always specify reasonable timeouts for ask operations and handle timeout exceptions using `ask().onTimeout()`
8. **Non-Blocking Operations**: Avoid blocking operations inside actors, as they can lead to thread starvation
9. **Actor Naming**: Use meaningful and unique names for actors to make debugging easier
10. **Use Fluent API for Advanced Cases**: For advanced scenarios requiring custom configuration (supervision, dispatchers, mailboxes), use the fluent builder API with `.actor()`. For simple cases, prefer `getOrSpawn()`

## Next Steps

Now that you know how to register actors, spawn them, and send messages, you can:

1. [Learn how to use pub/sub topics for distributed messaging](pub-sub-topics.md)
2. [Explore routers for load balancing and parallel processing](routers.md)
3. [Create sharded actors for clustered environments](sharded-actors.md)
