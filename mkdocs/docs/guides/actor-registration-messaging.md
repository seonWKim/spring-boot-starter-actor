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
    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Specify the command class this actor can handle
    @Override
    public Class<Command> commandClass() {
        return Command.class;
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
        // Spawn an actor with the name "default"
        this.helloActor = springActorSystem
                .spawn(HelloActor.Command.class, "default", Duration.ofSeconds(3))
                .toCompletableFuture()
                .join();
    }

    // Service methods...
}
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

## Best Practices

1. **Actor Hierarchy**: Organize actors in a hierarchy to manage their lifecycle and supervision.
2. **Message Immutability**: Ensure that messages sent to actors are immutable to prevent concurrency issues.
3. **Timeout Handling**: Always specify reasonable timeouts for ask operations and handle timeout exceptions.
4. **Non-Blocking Operations**: Avoid blocking operations inside actors, as they can lead to thread starvation.
5. **Actor Naming**: Use meaningful and unique names for actors to make debugging easier.

## Next Steps

Now that you know how to register actors, spawn them, and send messages, you can:

1. [Learn how to create sharded actors](sharded-actors.md) for clustered environments
2. Explore the [API Reference](../api-reference.md) for detailed information about the library's APIs
