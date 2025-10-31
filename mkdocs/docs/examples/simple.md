# Simple Example

This guide demonstrates the basic usage of Spring Boot Starter Actor in a simple, non-clustered environment.

## Overview

The simple example shows how to:

- Create and register actors in a Spring Boot application
- Send messages to actors and receive responses
- Integrate actors with a REST API

This example is a great starting point for understanding the core concepts of the actor model and how Spring Boot Starter Actor makes it easy to use actors in your Spring applications.

## Source Code

You can find the complete source code for this example on GitHub:
[https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/simple](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/simple)

## Key Components

### HelloActor

`HelloActor` is a simple actor that responds to "hello" messages. It demonstrates:

- How to implement the `SpringActor` interface
- How to define message types (commands)
- How to create actor behaviors
- How to handle messages and send responses
- How to use lifecycle hooks: PreStart (called in `create()`), PreRestart and PostStop (using `onSignal`)

```java
@Component
public class HelloActor implements SpringActor<HelloActor, HelloActor.Command> {
    // Command interface and message types
    public interface Command {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, actorContext).create());
    }

    // Actor behavior implementation
    private static class HelloActorBehavior {
        // Implementation details...

        public Behavior<Command> create() {
            // Call prestart hook for initialization logic
            onPrestart();

            return Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, this::onSayHello)
                    // Signal handlers for lifecycle events
                    .onSignal(PreRestart.class, this::onPreRestart)
                    .onSignal(PostStop.class, this::onPostStop)
                    .build();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            // Send response
            msg.replyTo.tell("Hello from actor " + actorContext.actorId());
            return Behaviors.same();
        }

        private void onPrestart() {
            // Initialize resources when actor starts
            ctx.getLog().info("PreStart hook for id={}", actorContext.actorId());
        }

        private Behavior<Command> onPreRestart(PreRestart signal) {
            // Cleanup before restart (e.g., close connections, release resources)
            ctx.getLog().warn("Actor {} is being restarted", actorContext.actorId());
            return Behaviors.same();
        }

        private Behavior<Command> onPostStop(PostStop signal) {
            // Final cleanup when actor stops
            ctx.getLog().info("Actor {} is stopping", actorContext.actorId());
            return Behaviors.same();
        }
    }
}
```

### HelloService

`HelloService` acts as an intermediary between the REST API and the actor system:

- It spawns a HelloActor instance
- It provides methods to send messages to the actor and return the responses
- It converts actor responses to reactive Mono objects for use with Spring WebFlux

```java
@Service
public class HelloService {
    private final SpringActorRef<Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        // Spawn a single actor using the simplified fluent API
        this.helloActor = springActorSystem
                .actor(HelloActor.class)
                .withId("default")
                .withTimeout(Duration.ofSeconds(3))
                .startAndWait();
    }

    public Mono<String> hello() {
        // Send a SayHello message to the actor and convert the response to a Mono
        return Mono.fromCompletionStage(
                helloActor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3)));
    }
}
```

### HelloController

`HelloController` exposes the actor functionality via a REST API:

- It injects the HelloService
- It defines REST endpoints that call the service methods
- It returns the actor responses as HTTP responses

```java
@RestController
public class HelloController {
    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public Mono<String> hello() {
        return helloService.hello();
    }
}
```

## Actor Lifecycle Hooks

The HelloActor demonstrates how to use lifecycle hooks to handle important events during an actor's lifetime. Pekko provides different mechanisms for handling startup and shutdown events.

### PreStart Hook

Unlike restart and stop events which use signals, the **prestart hook is implemented by calling a method directly in your `create()` method**. This is the Pekko-recommended approach for initialization logic.

```java
public Behavior<Command> create() {
    // Call prestart hook for initialization
    onPrestart();

    return Behaviors.receive(Command.class)
        .onMessage(SayHello.class, this::onSayHello)
        .build();
}

private void onPrestart() {
    // Initialize resources when actor starts
    ctx.getLog().info("Actor is starting...");
    // Open database connections, subscribe to events, etc.
}
```

The prestart hook is useful for:

- Initializing resources (e.g., opening database connections, file handles)
- Subscribing to event streams or topics
- Setting up timers or scheduled tasks
- Loading initial state or configuration
- Logging actor startup

### PreRestart Signal

The `PreRestart` signal is sent to an actor before it is restarted due to a failure. This is useful for:

- Cleaning up resources that won't be automatically released (e.g., closing database connections, file handles)
- Logging the state before restart for debugging
- Notifying other parts of the system about the restart
- Releasing locks or semaphores

**Note:** The actor's state will be lost during restart unless you implement state persistence.

### PostStop Signal

The `PostStop` signal is sent when an actor is stopped, either gracefully or due to a failure. This is the last chance to:

- Release resources (e.g., close connections, flush buffers)
- Perform final cleanup operations
- Notify other systems that the actor is shutting down
- Log final state information

### Using Lifecycle Hooks

To implement lifecycle hooks in your actor:

1. **PreStart**: Call a method directly in `create()` before returning the behavior
2. **PreRestart and PostStop**: Use the `onSignal` method when building your behavior

```java
public Behavior<Command> create() {
    // PreStart: call directly
    onPrestart();

    return Behaviors.receive(Command.class)
        .onMessage(SayHello.class, this::onSayHello)
        // Signals: use onSignal
        .onSignal(PreRestart.class, this::onPreRestart)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
}
```

The signal handlers should return `Behaviors.same()` to indicate that the actor should continue with its normal lifecycle behavior.

## Configuration

The simple example uses the default configuration for Spring Boot Starter Actor, which creates a local actor system. You can configure the actor system using application properties:

```yaml
spring:
  application:
    name: spring-pekko
  actor:
    pekko:
      actor:
        provider: local
```

## Running the Example

To run the simple example:

1. Start the application using Gradle or Maven
2. Access the `/hello` endpoint to send a message to the actor
3. Observe the response from the actor

## Key Points

- Easy integration with Spring Boot
- Simple concurrency handling
- Reactive programming support
