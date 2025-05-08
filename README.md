# Spring Boot Actor Starter

A library that integrates Spring Boot with the actor model using [Pekko](https://pekko.apache.org/) (an open-source, community-driven fork of Akka).

## What is this project about?

This project bridges the gap between Spring Boot and the actor model, allowing developers to build stateful applications using familiar Spring Boot patterns while leveraging the power of the actor model for managing state and concurrency.

### Why?

Many use Java with Spring (usually Spring Boot). Modern programming guides recommend building stateless architectures. But sometimes, stateful features are needed, such as in chat applications where the server needs to know where clients in the same chatroom are located.

The actor model is a well-known programming model suited for stateful applications:
- Encapsulate logic into actors
- Communicate by sending messages between them

This project aims to bring together the best of both worlds:
- Spring Boot's ease of use and extensive ecosystem
- The actor model's natural approach to encapsulation and state management

## Features

- Auto-configure Pekko with Spring Boot
- Seamless integration with Spring's dependency injection
- Support for both local and cluster modes
- Easy actor creation and management
- Spring-friendly actor references

## Getting Started

### Prerequisites

- Java 8 or higher
- Spring Boot 2.x or 3.x

### Installation

Add the dependency to your project:

// TODO 

## Usage

### Enable Actor Support

Add the following to your `application.yml`:

```yaml
spring:
  actor-enabled: true
```

### Local Mode

For simple applications running on a single node:

```yaml
spring:
  actor-enabled: true
  actor:
    pekko:
      actor:
        provider: local
```

### Cluster Mode

For distributed applications running on multiple nodes:

```yaml
spring:
  actor-enabled: true
  actor:
    pekko:
      name: your-application-name
      actor:
        provider: cluster
        allow-java-serialization: on
      remote:
        artery:
          canonical:
            hostname: 127.0.0.1
            port: 2551
      cluster:
        name: your-application-name
        seed-nodes:
          - pekko://your-application-name@127.0.0.1:2551
          - pekko://your-application-name@127.0.0.1:2552
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
```
Make sure your `spring.actor.pekko.name` and the cluster's name and seed node hostnames are the same.  

### Creating an Actor

```java
@Component
public class HelloActor implements SpringActor {

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

    public static Behavior<Command> create(String id) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, id).create());
    }

    // Inner class to isolate stateful behavior logic
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final String actorId;

        HelloActorBehavior(ActorContext<Command> ctx, String actorId) {
            this.ctx = ctx;
            this.actorId = actorId;
        }

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                            .onMessage(SayHello.class, this::onSayHello)
                            .build();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorId);
            msg.replyTo.tell("Hello from actor " + actorId);
            return Behaviors.same();
        }
    }
}
```

### Using the Actor in a Service

```java
@Service
public class HelloService {

    private final SpringActorRef<Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        this.helloActor = springActorSystem.spawn(HelloActor.Command.class, "default", Duration.ofSeconds(3))
                                           .toCompletableFuture()
                                           .join();
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(helloActor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3)));
    }
}
```

## Examples

The project includes two example applications:

1. **Simple Example**: Demonstrates using actors in local mode
2. **Cluster Example**: Demonstrates using actors in a clustered environment

## License

This project is licensed under the MIT License - see the LICENSE file for details.
