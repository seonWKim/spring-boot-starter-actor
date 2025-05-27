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

```java
@Component
public class HelloActor implements SpringActor {
    // Command interface and message types
    public interface Command {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public Class<?> commandClass() {
        return Command.class;
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, actorContext).create());
    }

    // Actor behavior implementation
    private static class HelloActorBehavior {
        // Implementation details...

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, this::onSayHello)
                    .build();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            // Send response
            msg.replyTo.tell("Hello from actor " + actorContext.actorId());
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
        // Spawn a single actor with the name "default"
        this.helloActor = springActorSystem
                .spawn(HelloActor.Command.class, "default", Duration.ofSeconds(3))
                .toCompletableFuture()
                .join();
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

## Key Takeaways

- Spring Boot Starter Actor makes it easy to integrate actors with Spring Boot applications
- Actors provide a simple and effective way to handle concurrent operations
- The library handles the complexity of actor creation and message passing
- Integration with Spring WebFlux allows for reactive programming with actors
