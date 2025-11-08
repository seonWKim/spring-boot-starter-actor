# Actor Registration and Messaging

This guide explains how to register actors, spawn them, and send messages using Spring Boot Starter Actor.

## Registering Actors

In Spring Boot Starter Actor, actors are registered as Spring components. This allows them to be automatically
discovered and managed by the Spring container.

### Creating a Simple Actor

To create an actor, implement the `SpringActor` interface and annotate the class with `@Component`:

```java
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

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

Once you've registered your actor, you can spawn instances of it using the `SpringActorSystem`:

### Simplified API

The recommended way to spawn actors is using the fluent builder API:

```java
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;

import org.springframework.stereotype.Service;

import java.time.Duration;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final SpringActorRef<HelloActor.Command> helloActor;

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

### Async Spawning

For non-blocking actor creation:

```java
@Service
public class HelloService {
    private final CompletionStage<SpringActorRef<HelloActor.Command>> helloActor;

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

The fluent API supports additional configuration options:

```java
SpringActorRef<HelloActor.Command> actor = springActorSystem
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
    helloActor.tell(HelloActor.SayHi::new);
}
```

### Ask Pattern (Request-Response)

The ask pattern is used when you expect a response from the actor:

```java
public Mono<String> hello() {
    return Mono.fromCompletionStage(
            helloActor.ask(new HelloActor.SayHello())
                .withTimeout(Duration.ofSeconds(3))
                .execute());
}
```

## Stopping Actors

You can gracefully stop actors when they are no longer needed:

### Simplified Stop API

```java
actorRef.stop(); 
```

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
CompletionStage<SpringActorRef<Command>> actorRef = actorSystem
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
CompletionStage<SpringActorRef<Command>> actorRef = actorSystem
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
    private final AtomicReference<CompletionStage<SpringActorRef<HelloActor.Command>>> actorRef =
            new AtomicReference<>();

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Lazily initializes the actor on first use and caches the reference
     * for subsequent calls. Use this for high-frequency access scenarios.
     */
    private CompletionStage<SpringActorRef<HelloActor.Command>> getActor() {
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

Use the `child()` method on `SpringActorRef` to spawn child actors:

```java
@Component
public class ParentActor implements SpringActor<ParentActor.Command> {

    public interface Command extends FrameworkCommand {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                // Get reference to self
                SpringActorRef<Command> self = new SpringActorRef<>(
                    ctx.getSystem().scheduler(), ctx.getSelf());

                // Spawn a child actor using fluent API
                self.child(ChildActor.class)
                    .withId("worker-1")
                    .withSupervisionStrategy(SupervisorStrategy.restart())
                    .withTimeout(Duration.ofSeconds(5))
                    .spawn();  // Returns CompletionStage<SpringActorRef>

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
CompletionStage<SpringActorRef<Command>> child = parentRef
    .child(ChildActor.class)
    .withId("child-1")
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawn();  // Async

// Or synchronous spawning
SpringActorRef<Command> child = parentRef
    .child(ChildActor.class)
    .withId("child-2")
    .withSupervisionStrategy(SupervisorStrategy.restart())
    .spawnAndWait();  // Blocks until spawned
```

**Get existing child reference:**
```java
CompletionStage<SpringActorRef<Command>> existing = parentRef
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
CompletionStage<SpringActorRef<Command>> childRef = parentRef
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

SpringActorRef<Command> child = parentRef
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

1. **Use getOrSpawn**: For most cases, use `getOrSpawn()` instead of manually checking exists/get/spawn - it's simpler and reduces boilerplate.
2. **Lazy Initialization**: Use lazy initialization to avoid blocking application startup. For simple cases, use `getOrSpawn` directly; for high-frequency access, add caching with `AtomicReference`.
3. **Actor Hierarchy**: Organize actors in a hierarchy to manage their lifecycle and supervision.
4. **Choose Appropriate Supervision**: Select supervision strategies based on the child actor's role and failure characteristics.
5. **Use Framework Commands**: Make your Command interface extend `FrameworkCommand` when building parent actors that need to spawn children. Framework command handling is automatically enabled.
6. **Message Immutability**: Ensure that messages sent to actors are immutable to prevent concurrency issues.
7. **Timeout Handling**: Always specify reasonable timeouts for ask operations and handle timeout exceptions using `ask().onTimeout()`.
8. **Non-Blocking Operations**: Avoid blocking operations inside actors, as they can lead to thread starvation.
9. **Actor Naming**: Use meaningful and unique names for actors to make debugging easier.
10. **Prefer Fluent API**: Use the fluent builder API for spawning actors as it provides better readability and type safety.

## Next Steps

Now that you know how to register actors, spawn them, and send messages, you can:

1. [Learn how to create sharded actors](sharded-actors.md) for clustered environments
2. Explore the [API Reference](../api-reference.md) for detailed information about the library's APIs
