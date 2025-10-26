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
public class HelloActor implements SpringActor<HelloActor, HelloActor.Command> {

    // Define the command interface for messages this actor can handle
    public interface Command {}

    // Define a message type 
    public static class SayHi implements Command {}

    // Define a message type
    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Create the behavior for this actor
    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, actorContext).create());
    }

    // Inner class to handle the actor's behavior
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                            .onMessage(SayHi.class, this::onSayHi)
                            .onMessage(SayHello.class, this::onSayHello)
                            .build();
        }

        private Behavior<Command> onSayHi(SayHi msg) {
            ctx.getLog().info("Received SayHi for id={}", actorId);
            return Behaviors.same(); 
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorContext.getId());
            msg.replyTo.tell("Hello from actor " + actorId);
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
                .spawn(HelloActor.class)
                .withId("default")
                .withTimeout(Duration.ofSeconds(3))
                .startAndWait();
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
                .spawn(HelloActor.class)
                .withId("default")
                .withTimeout("3s")  // Can use string format
                .start();
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
            helloActor.thenCompose(actor ->
                actor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3))
            )
        );
    }
}
```

### Advanced Configuration

The fluent API supports additional configuration options:

```java
SpringActorRef<HelloActor.Command> actor = springActorSystem
        .spawn(HelloActor.class)
        .withId("myActor")
        .withTimeout(Duration.ofSeconds(5))
        .withMailbox("bounded")  // or "unbounded", "default"
        .asClusterSingleton()     // For cluster singleton actors
        .withContext(customContext)  // Custom actor context
        .start();
```

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
            helloActor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3)));
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

### Get or Create Pattern

Use the exists and get methods together to implement a "get or create" pattern:

```java
actorSystem.exists(MyActor.class, "my-actor-1")
    .thenCompose(exists -> {
        if (exists) {
            return actorSystem.get(MyActor.class, "my-actor-1");
        } else {
            return actorSystem.spawn(MyActor.class)
                .withId("my-actor-1")
                .start();
        }
    });
```

### Lazy Initialization Pattern

**Best Practice**: Use lazy initialization with caching to avoid blocking application startup and prevent creating multiple instances:

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
     * for subsequent calls. This avoids blocking startup and prevents
     * creating multiple instances of the same actor.
     */
    private CompletionStage<SpringActorRef<HelloActor.Command>> getActor() {
        return actorRef.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }

            // Check if actor exists, get it if it does, otherwise create it
            return actorSystem
                    .exists(HelloActor.class, "hello-actor")
                    .thenCompose(exists -> {
                        if (exists) {
                            return actorSystem.get(HelloActor.class, "hello-actor");
                        } else {
                            return actorSystem
                                    .spawn(HelloActor.class)
                                    .withId("hello-actor")
                                    .withTimeout(Duration.ofSeconds(3))
                                    .start();
                        }
                    });
        });
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                getActor().thenCompose(actor -> actor.ask(HelloActor.SayHello::new)));
    }
}
```

## Best Practices

1. **Lazy Initialization**: Use lazy initialization with caching to avoid blocking application startup and prevent creating duplicate actors.
2. **Get or Create Pattern**: Check if an actor exists before spawning to reuse existing instances.
3. **Actor Hierarchy**: Organize actors in a hierarchy to manage their lifecycle and supervision.
4. **Message Immutability**: Ensure that messages sent to actors are immutable to prevent concurrency issues.
5. **Timeout Handling**: Always specify reasonable timeouts for ask operations and handle timeout exceptions using `askBuilder().onTimeout()`.
6. **Non-Blocking Operations**: Avoid blocking operations inside actors, as they can lead to thread starvation.
7. **Actor Naming**: Use meaningful and unique names for actors to make debugging easier.
8. **Prefer Fluent API**: Use the fluent builder API for spawning actors as it provides better readability and type safety.

## Next Steps

Now that you know how to register actors, spawn them, and send messages, you can:

1. [Learn how to create sharded actors](sharded-actors.md) for clustered environments
2. Explore the [API Reference](../api-reference.md) for detailed information about the library's APIs
