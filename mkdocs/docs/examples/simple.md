# Simple Example

This guide demonstrates the basic usage of Spring Boot Starter Actor in a simple, non-clustered environment.

## Overview

The simple example shows how to:

- Create and register actors in a Spring Boot application
- Send messages to actors and receive responses
- Integrate actors with a REST API
- Use lifecycle hooks (PreStart, PreRestart, PostStop)

This example is a great starting point for understanding the core concepts of the actor model and how Spring Boot Starter Actor makes it easy to use actors in your Spring applications.

!!! tip "For Beginners"
    Start here if you're new to the actor model or Spring Boot Starter Actor.

## Source Code

You can find the complete source code for this example on GitHub:

[Simple Example Source Code](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/simple)

!!! info "Example Structure"
    The example includes actors, services, controllers, and configuration demonstrating best practices.

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
public class HelloActor implements SpringActor<HelloActor.Command> {
    // Command interface and message types
    public interface Command {}

    public static class SayHello extends AskCommand<String> implements Command {
        public SayHello() {}
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .onMessage(TriggerFailure.class, HelloActorBehavior::onTriggerFailure)
                .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
                .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
                .withSupervisionStrategy(SupervisorStrategy.restart()
                        .withLimit(10, Duration.ofMinutes(1)))
                .build();
    }

    // Actor behavior implementation
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        public HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            // Call prestart hook for initialization logic
            onPrestart();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            // Send response
            msg.reply("Hello from actor " + actorContext.actorId());
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

Unlike restart and stop events which use signals, the **prestart hook is implemented by calling a method in your behavior handler's constructor**. This is the recommended approach for initialization logic when using the builder pattern.

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
    return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
            .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
            .build();
}

private static class HelloActorBehavior {
    private final ActorContext<Command> ctx;
    private final SpringActorContext actorContext;

    public HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
        this.ctx = ctx;
        this.actorContext = actorContext;
        // Call prestart hook for initialization
        onPrestart();
    }

    private void onPrestart() {
        // Initialize resources when actor starts
        ctx.getLog().info("Actor is starting...");
        // Open database connections, subscribe to events, etc.
    }
}
```

The prestart hook is useful for:

- Initializing resources (e.g., opening database connections, file handles)
- Subscribing to event streams or topics
- Setting up timers or scheduled tasks
- Loading initial state or configuration
- Logging actor startup

### Supervision Strategy

**Important:** To enable actor restarts on failure, you must specify a supervision strategy using the builder's `withSupervisionStrategy()` method. By default, Pekko stops actors when they throw exceptions. A supervision strategy tells Pekko what to do when an actor fails.

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
    return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
            .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
            .onMessage(TriggerFailure.class, HelloActorBehavior::onTriggerFailure)
            .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
            .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
            .withSupervisionStrategy(SupervisorStrategy.restart()
                    .withLimit(10, Duration.ofMinutes(1)))
            .build();
}
```

This supervision strategy:

- **Restarts** the actor when any exception is thrown
- Limits restarts to **10 times within 1 minute** to prevent infinite restart loops
- Enables the **PreRestart signal** to be triggered before restart

!!! warning "Default Behavior"
    Without a supervision strategy, actors stop permanently on failure. Always configure a strategy for production use.

**Common supervision strategies:**

- **`restart()`** - Restart the actor, discarding its state
- **`resume()`** - Resume processing, keeping the actor's state
- **`stop()`** - Stop the actor permanently (default behavior)
- **`restartWithBackoff()`** - Restart with exponential backoff delays

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

To implement lifecycle hooks in your actor using the builder pattern:

1. **PreStart**: Call a method in your behavior handler's constructor (within `.withState()`)
2. **PreRestart and PostStop**: Use the `.onSignal()` method when building your behavior
3. **Supervision Strategy**: Use `.withSupervisionStrategy()` to enable restarts

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
    return SpringActorBehavior.builder(Command.class, actorContext)
            // 1. PreStart: called in behavior handler constructor
            .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
            // 2. Message and signal handlers
            .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
            .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
            .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
            // 3. Supervision strategy to enable restart
            .withSupervisionStrategy(SupervisorStrategy.restart()
                    .withLimit(10, Duration.ofMinutes(1)))
            .build();
}

private static class HelloActorBehavior {
    public HelloActorBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
        this.ctx = ctx;
        this.actorContext = actorContext;
        // PreStart hook called here
        onPrestart();
    }

    private void onPrestart() {
        ctx.getLog().info("Actor is starting...");
    }

    private Behavior<Command> onPreRestart(PreRestart signal) {
        ctx.getLog().warn("Actor is being restarted");
        return Behaviors.same();
    }

    private Behavior<Command> onPostStop(PostStop signal) {
        ctx.getLog().info("Actor is stopping");
        return Behaviors.same();
    }
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
- `Actor hello-actor is being restarted` (PreRestart)
- `PreStart hook for id=hello-actor` (actor restarts and PreStart is called again)

#### 3. Test PostStop Signal

```bash
curl http://localhost:8080/hello/stop
```

This stops the actor using `SpringActorHandle.stop()`. Check the logs for:

- `Actor hello-actor is stopping` (PostStop)

!!! tip "Testing Tips"
    Watch the logs in your terminal when testing these endpoints to see the lifecycle hooks in action.

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

1. Navigate to the example directory:
```bash
cd example/simple
```

2. Start the application using Gradle:
```bash
./gradlew bootRun
```

3. Access the `/hello` endpoint to send a message to the actor:
```bash
curl http://localhost:8080/hello
```

4. Observe the response from the actor in the terminal

!!! success "Expected Output"
    You should see `Hello from actor hello-actor` as the response.

## Key Points

- **Easy integration with Spring Boot** - Actors work naturally with Spring's dependency injection
- **Simple concurrency handling** - Message-based processing eliminates need for locks
- **Reactive programming support** - Integrates seamlessly with Spring WebFlux and Mono/Flux
- **Lifecycle management** - Control actor initialization and cleanup with lifecycle hooks

## Next Steps

- [Cluster Example](cluster.md) - Learn about distributed actors and cluster sharding
- [Chat Example](chat.md) - Build a real-world application with pub/sub topics
