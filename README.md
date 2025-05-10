# Spring Boot Starter Actor  

<p align="center">
  <img src="./logo.png" alt="Library Logo" width="200"/>
</p>

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

```gradle
// Gradle
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.0.3'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.seonwkim</groupId>
    <artifactId>spring-boot-starter-actor</artifactId>
    <version>0.0.3</version>
</dependency>
```

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
          - pekko://your-application-name@127.0.0.1:2553
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
```
Make sure your `spring.actor.pekko.name` and the cluster's name and seed node hostnames are the same.  

### Sharded Entities

Sharded entities allow you to distribute actor instances across a cluster. This is useful for stateful actors that need to be distributed for scalability and fault tolerance.

To create a sharded actor:

```java
@Component
public class MyShardedActor implements ShardedActor<MyShardedActor.Command> {

    // Define a type key for this actor type
    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "MyShardedActor");

    // Define the command interface
    public interface Command extends Serializable {}

    // Define command messages
    public static class DoSomething implements Command {
        public final ActorRef<String> replyTo;
        public final String data;

        public DoSomething(ActorRef<String> replyTo, String data) {
            this.replyTo = replyTo;
            this.data = data;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(
                context ->
                        Behaviors.receive(Command.class)
                                 .onMessage(DoSomething.class, msg -> {
                                     // The entityId identifies this specific entity instance
                                     final String entityId = ctx.getEntityId();
                                     msg.replyTo.tell("Processed by entity " + entityId + ": " + msg.data);
                                     return Behaviors.same();
                                 })
                                 .build()
        );
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
```

To use a sharded actor in a service:

```java
@Service
public class MyService {

    private final SpringActorSystem springActorSystem;

    public MyService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    public Mono<String> processData(String data, String entityId) {
        // Get a reference to the sharded actor with the specified entityId
        SpringShardedActorRef<MyShardedActor.Command> actorRef = 
            springActorSystem.entityRef(MyShardedActor.TYPE_KEY, entityId);

        // Send a message to the actor and get a response
        CompletionStage<String> response = actorRef.ask(
                replyTo -> new MyShardedActor.DoSomething(replyTo, data), 
                Duration.ofSeconds(3));

        return Mono.fromCompletionStage(response);
    }
}
```

The `entityId` parameter determines which entity instance will handle the message. Messages with the same entityId will always be routed to the same actor instance, ensuring that state is maintained correctly.

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
```shell 
# start cluster 
$ sh cluster-start.sh 

# stop cluster 
$ sh cluster-stop.sh  
```
