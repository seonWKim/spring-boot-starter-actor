# Simple Example

This guide demonstrates the basic usage of Actor Spring Boot Starter in a simple, non-clustered environment.

## Overview

The simple example shows how to:

- Create and register actors in a Spring Boot application
- Send messages to actors and receive responses
- Integrate actors with a REST API
- Use lifecycle hooks (PreStart, PreRestart, PostStop)

This example is a great starting point for understanding the core concepts of the actor model and how Actor Spring Boot Starter makes it easy to use actors in your Spring applications.

!!! tip "For Beginners"
    Start here if you're new to the actor model or Actor Spring Boot Starter.

## Source Code

You can find the complete source code for this example on GitHub:

[Simple Example Source Code](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/simple)

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

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command {}

    /** Command to say hello and get a response. */
    public static class SayHello extends AskCommand<String> implements Command {
        public SayHello() {}
    }

    /** Command to trigger an exception and cause the actor to restart (tests PreRestart signal). */
    public static class TriggerFailure extends AskCommand<String> implements Command {
        public TriggerFailure() {}
    }

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param actorContext The context of the actor
     * @return The behavior for the actor
     */
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    ctx.getLog().info("PreStart hook for id={}", actorContext.actorId());
                    return new HelloActorBehavior(ctx, actorContext);
                })
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .onMessage(TriggerFailure.class, HelloActorBehavior::onTriggerFailure)
                .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
                .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
                .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Inner class to isolate stateful behavior logic. This separates the actor's state and behavior
     * from its interface.
     */
    private static class HelloActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringActorContext actorContext;

        /**
         * Creates a new behavior with the given context and actor ID.
         *
         * @param ctx The actor context
         * @param actorContext The context of the actor
         */
        HelloActorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        /**
         * Handles SayHello commands by responding with a greeting.
         *
         * @param msg The SayHello message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onSayHello(SayHello msg) {
            // Log the received message
            ctx.getLog().info("Received SayHello for id={}", actorContext.actorId());

            // Send a response back to the caller
            msg.reply("Hello from actor " + actorContext.actorId());

            return SpringActorBehavior.same();
        }

        /**
         * Handles TriggerFailure commands by throwing an exception.
         * This will trigger the PreRestart signal handler.
         *
         * @param msg The TriggerFailure message
         * @return Never returns (throws exception)
         */
        private SpringActorBehavior<Command> onTriggerFailure(TriggerFailure msg) {
            ctx.getLog().warn("Triggering failure for actor {}", actorContext.actorId());
            // Reply before throwing to confirm the command was received
            msg.reply("Triggering failure - actor will restart");
            // Throw exception to trigger restart
            throw new RuntimeException("Intentional failure to demonstrate PreRestart signal");
        }

        /**
         * Called before the actor is restarted due to a failure.
         * This is a good place to clean up resources or log state before restart.
         *
         * @param signal The PreRestart signal
         * @return The same behavior (actor will be restarted)
         */
        private SpringActorBehavior<Command> onPreRestart(PreRestart signal) {
            ctx.getLog().warn("Actor {} is being restarted due to failure", actorContext.actorId());

            // You can perform cleanup here before the actor restarts
            // For example: closing connections, releasing resources, etc.
            // Note: State will be lost unless you implement state persistence

            return SpringActorBehavior.same();
        }

        /**
         * Called when the actor is stopped (either gracefully or due to failure).
         * This is a good place to release resources and perform final cleanup.
         *
         * @param signal The PostStop signal
         * @return The same behavior
         */
        private SpringActorBehavior<Command> onPostStop(PostStop signal) {
            ctx.getLog().info("Actor {} is stopping. Performing cleanup...", actorContext.actorId());

            // Perform cleanup here
            // For example: close database connections, release file handles,
            // flush buffers, notify other systems, etc.

            return SpringActorBehavior.same();
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
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.SayHello())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    /**
     * Triggers a failure in the actor, causing it to restart.
     * This demonstrates the PreRestart signal handler.
     */
    public Mono<String> triggerRestart() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.TriggerFailure())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    /**
     * Stops the actor gracefully using SpringActorHandle.stop().
     * This demonstrates the PostStop signal handler.
     */
    public Mono<String> stopActor() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> {
                    actor.stop();
                    return Mono.just("Actor stopped - PostStop signal will be triggered")
                            .toFuture();
                }));
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

    /**
     * Creates a new HelloController with the given service.
     *
     * @param helloService The service for interacting with HelloActors
     */
    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    /**
     * Endpoint to send a hello message to the actor and get a response. This is a simple GET endpoint
     * that requires no parameters.
     *
     * @return A Mono containing the response from the actor
     */
    @GetMapping("/hello")
    public Mono<String> hello() {
        return helloService.hello();
    }

    /**
     * Endpoint to trigger a failure in the actor, causing it to restart.
     * This demonstrates the PreRestart lifecycle hook.
     *
     * @return A Mono containing confirmation that the restart was triggered
     */
    @GetMapping("/hello/restart")
    public Mono<String> triggerRestart() {
        return helloService.triggerRestart();
    }

    /**
     * Endpoint to stop the actor gracefully.
     * This demonstrates the PostStop lifecycle hook.
     *
     * @return A Mono containing confirmation that the actor was stopped
     */
    @GetMapping("/hello/stop")
    public Mono<String> stopActor() {
        return helloService.stopActor();
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
    private final SpringBehaviorContext<Command> ctx;
    private final SpringActorContext actorContext;

    public HelloActorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
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
            .withSupervisionStrategy(SupervisionStrategy.restart()
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
            .withSupervisionStrategy(SupervisionStrategy.restart()
                    .withLimit(10, Duration.ofMinutes(1)))
            .build();
}

private static class HelloActorBehavior {
    public HelloActorBehavior(SpringBehaviorContext<Command> ctx, SpringActorContext actorContext) {
        this.ctx = ctx;
        this.actorContext = actorContext;
        // PreStart hook called here
        onPrestart();
    }

    private void onPrestart() {
        ctx.getLog().info("Actor is starting...");
    }

    private SpringActorBehavior<Command> onPreRestart(PreRestart signal) {
        ctx.getLog().warn("Actor is being restarted");
        return SpringActorBehavior.same();
    }

    private SpringActorBehavior<Command> onPostStop(PostStop signal) {
        ctx.getLog().info("Actor is stopping");
        return SpringActorBehavior.same();
    }
}
```

The signal handlers should return `SpringActorBehavior.same()` to indicate that the actor should continue with its normal lifecycle behavior.

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

The simple example uses the default configuration for Actor Spring Boot Starter, which creates a local actor system. You can configure the actor system using application properties:

```yaml
spring:
  application:
    name: spring-pekko
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
