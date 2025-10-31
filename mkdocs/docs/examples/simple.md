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

            // Create the base behavior
            Behavior<Command> behavior = Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, this::onSayHello)
                    .onMessage(TriggerFailure.class, this::onTriggerFailure)
                    // Signal handlers for lifecycle events
                    .onSignal(PreRestart.class, this::onPreRestart)
                    .onSignal(PostStop.class, this::onPostStop)
                    .build();

            // Wrap with supervision strategy to restart on failure
            return Behaviors.supervise(behavior)
                    .onFailure(SupervisorStrategy.restart()
                            .withLimit(10, Duration.ofMinutes(1)));
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            // Send response
            msg.replyTo.tell("Hello from actor " + actorContext.actorId());
            return Behaviors.same();
        }

        private Behavior<Command> onTriggerFailure(TriggerFailure msg) {
            // Throw exception to trigger restart (requires supervision strategy)
            ctx.getLog().warn("Triggering failure for actor {}", actorContext.actorId());
            msg.replyTo.tell("Triggering failure - actor will restart");
            throw new RuntimeException("Intentional failure to demonstrate PreRestart");
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

### Supervision Strategy

**Important:** To enable actor restarts on failure, you must wrap your behavior with a supervision strategy. By default, Pekko stops actors when they throw exceptions. A supervision strategy tells Pekko what to do when an actor fails.

```java
// Create the base behavior
Behavior<Command> behavior = Behaviors.receive(Command.class)
    .onMessage(SayHello.class, this::onSayHello)
    .onSignal(PreRestart.class, this::onPreRestart)
    .build();

// Wrap with supervision strategy to restart on failure
return Behaviors.supervise(behavior)
    .onFailure(SupervisorStrategy.restart()
        .withLimit(10, Duration.ofMinutes(1)));
```

This supervision strategy:
- **Restarts** the actor when any exception is thrown
- Limits restarts to **10 times within 1 minute** to prevent infinite restart loops
- Enables the **PreRestart signal** to be triggered before restart

Common supervision strategies:
- **`restart()`**: Restart the actor, discarding its state
- **`resume()`**: Resume processing, keeping the actor's state
- **`stop()`**: Stop the actor permanently (default behavior)
- **`restartWithBackoff()`**: Restart with exponential backoff delays

### PreRestart Signal

The `PreRestart` signal is sent to an actor before it is restarted due to a failure. **Note: This requires a supervision strategy with restart behavior.** This is useful for:

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

1. **PreStart**: Call a method directly in `create()` before building the behavior
2. **PreRestart and PostStop**: Use the `onSignal` method when building your behavior
3. **Supervision Strategy**: Wrap the behavior with `Behaviors.supervise()` to enable restarts

```java
public Behavior<Command> create() {
    // 1. PreStart: call directly
    onPrestart();

    // 2. Build behavior with signal handlers
    Behavior<Command> behavior = Behaviors.receive(Command.class)
        .onMessage(SayHello.class, this::onSayHello)
        .onSignal(PreRestart.class, this::onPreRestart)
        .onSignal(PostStop.class, this::onPostStop)
        .build();

    // 3. Wrap with supervision strategy to enable restart
    return Behaviors.supervise(behavior)
        .onFailure(SupervisorStrategy.restart()
            .withLimit(10, Duration.ofMinutes(1)));
}
```

The signal handlers should return `Behaviors.same()` to indicate that the actor should continue with its normal lifecycle behavior.

### Testing Lifecycle Hooks

The simple example provides API endpoints to test each lifecycle hook:

#### 1. Test PreStart Hook
```bash
curl http://localhost:8080/hello
```
Check the logs for: `PreStart hook for id=hello-actor`

#### 2. Test PreRestart Signal
```bash
curl http://localhost:8080/hello/restart
```
This triggers an intentional failure. Check the logs for:
- `Triggering failure for actor hello-actor`
- `Actor hello-actor is being restarted due to failure` (PreRestart)
- `PreStart hook for id=hello-actor` (actor restarts and PreStart is called again)

#### 3. Test PostStop Signal
```bash
curl http://localhost:8080/hello/stop
```
This stops the actor using `SpringActorRef.stop()`. Check the logs for:
- `Actor hello-actor is stopping. Performing cleanup...` (PostStop)

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
