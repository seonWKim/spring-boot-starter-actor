<table>
<tr>
<td width="30%">
  <img src="mkdocs/docs/logo.png" alt="Library Logo" />
</td>
<td>
  <h2>Spring Boot Starter Actor</h2>
  <p>A library that integrates Spring Boot with the actor model using <a href="https://pekko.apache.org/">Pekko</a> (an open-source, community-driven fork of Akka).</p>
</td>
</tr>
</table>

## Live Demo

<div style="border: 2px solid #ccc; display: inline-block; border-radius: 8px; overflow: hidden;">
  <img src="mkdocs/docs/chat.gif" alt="Demo"/>
</div>

## Documentation

For comprehensive documentation, visit our [Documentation Site](https://seonwkim.github.io/spring-boot-starter-actor/).

## Motivation

While Spring is widely used in the Java ecosystem, it doesn't natively support actor-related functionality. Actors have proven to be extremely useful for a wide range of use cases including IoT, telecommunications, real-time chat systems, and more. This project aims to bridge that gap by introducing the actor model to the Spring ecosystem.

The goal is to make actor-based programming accessible to Spring developers, and in the future, we hope to see `spring-boot-starter-actor` become an official Spring project.

## Core Concepts

This project bridges the gap between Spring Boot and the actor model, allowing developers to build 
applications using familiar Spring Boot patterns while leveraging the power of the actor model for managing
state and concurrency.

The actor model is a programming paradigm that:
- Encapsulates logic and state into actors
- Communicates by sending messages between actors
- Provides natural isolation and concurrency control

## Key Features

- Auto-configure Pekko with Spring Boot
- Seamless integration with Spring's dependency injection
- Support for both local and cluster modes
- Easy actor and sharded entity creation and management
- Spring-friendly actor management

## Quick Start

### Prerequisites

- Java 11 or higher
- Spring Boot 2.x or 3.x

### Installation

Add the dependency to your project:

```gradle
// Manually overwrite spring managed jackson dependency 
dependencyManagement {
	imports {
		// pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

// Gradle (Spring Boot 2.7.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.0.38'

// Gradle (Spring Boot 3.2.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.0.38'
```

```xml
<dependencyManagement>
  <dependencies>
    <!-- Override Spring Boot's jackson-bom with 2.17.3 -->
    <dependency>
      <groupId>com.fasterxml.jackson</groupId>
      <artifactId>jackson-bom</artifactId>
      <version>2.17.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Maven (Spring Boot 2.7.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor</artifactId>
  <version>0.0.38</version>
</dependency>

<!-- Maven (Spring Boot 3.2.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor_3</artifactId>
  <version>0.0.38</version>
</dependency>
```

To view the latest versions, refer to the following:
- [spring-boot-starter-actor](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor)
- [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

### Basic Configuration

Add the `@EnableActorSupport` annotation to your Spring Boot application class:

```java
@SpringBootApplication
@EnableActorSupport
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

You can still customize the actor system using application properties:

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: local
```

### Creating Actors

Spring Boot Starter Actor makes it easy to create actors by simply implementing an interface and using Spring's `@Component` annotation.

#### Simple Actor

```java
@Component
public class HelloActor implements SpringActor<HelloActor, HelloActor.Command> {

    public interface Command {}

    public static class SayHello implements Command {
        public final String message;
        public SayHello(String message) { this.message = message; }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx ->
            Behaviors.receive(Command.class)
                .onMessage(SayHello.class, msg -> {
                    ctx.getLog().info("Hello: {}", msg.message);
                    return Behaviors.same();
                })
                .build()
        );
    }
}
```

Spawn and use the actor in your service:

```java
@Service
public class MyService {
    private final SpringActorRef<HelloActor.Command> helloActor;

    public MyService(SpringActorSystem springActorSystem) {
        this.helloActor = springActorSystem
            .spawn(HelloActor.class)
            .withId("default")
            .startAndWait();
    }

    public void greet(String message) {
        helloActor.tell(() -> new HelloActor.SayHello(message));
    }
}
```

#### Sharded Entities (for Distributed Systems)

For clustered environments, create sharded actors that are automatically distributed across nodes:

```java
@Component
public class UserActor implements ShardedActor<UserActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "UserActor");

    public interface Command extends JsonSerializable {}

    public static class UpdateProfile implements Command {
        public final String name;
        @JsonCreator
        public UpdateProfile(@JsonProperty("name") String name) {
            this.name = name;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(context ->
            Behaviors.receive(Command.class)
                .onMessage(UpdateProfile.class, msg -> {
                    context.getLog().info("User {} profile updated: {}",
                        ctx.getEntityId(), msg.name);
                    return Behaviors.same();
                })
                .build()
        );
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(100);
    }
}
```

Use sharded entities in your service:

```java
@Service
public class UserService {
    private final SpringActorSystem springActorSystem;

    public UserService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    public void updateUserProfile(String userId, String name) {
        SpringShardedActorRef<UserActor.Command> userActor =
            springActorSystem.sharded(UserActor.class)
                .withId(userId)
                .get();

        userActor.tell(() -> new UserActor.UpdateProfile(name));
    }
}
```

### Try the Demo

Run the chat example with multiple nodes:

```shell
# Start 3 chat application instances on ports 8080, 8081, and 8082
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Stop the cluster
$ sh cluster-stop.sh
```

#### Using Docker for Deployment

You can also deploy the chat application as a clusterized app using Docker:

```shell
# Navigate to the chat example directory
$ cd example/chat

# Run the init-local-docker.sh script to build and deploy the application
$ sh init-local-docker.sh
```

This script will:
1. Build the chat application JAR file
2. Build a Docker image for the application
3. Deploy a 3-node Pekko cluster using Docker Compose
4. Each node will be accessible at:
   - Node 1: http://localhost:8080
   - Node 2: http://localhost:8081
   - Node 3: http://localhost:8082

To view logs for a specific node:
```shell
$ docker-compose logs -f chat-app-0
```

To stop the Docker deployment:
```shell
$ docker-compose down
```

## Metrics and Monitoring

The library includes a metrics module for monitoring actor performance and a ready-to-use monitoring stack based on Prometheus and Grafana.

### Running the Monitoring Stack

1. Start the chat application cluster:
```shell
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

2. Start the monitoring stack:
```shell
$ cd scripts/monitoring && docker-compose up -d
```

3. Access the monitoring dashboards:
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (username: admin, password: admin)

4. Shutdown the monitoring stack when done:
```shell
$ cd scripts/monitoring && docker-compose down -v
```

The monitoring setup provides dashboards for visualizing actor metrics, including message processing times and message counts by type.

## Contributions 

We welcome and appreciate your contributions! To ensure a smooth collaboration process, please follow these guidelines:

- **Create an Issue First**  
  Before opening a pull request (PR), create an issue ticket that clearly describes the purpose of your contribution.  

- **Submit a Pull Request (PR)**  
  Once the issue has been created and discussed, open a PR referencing the issue. Make sure to provide a detailed explanation of the changes introduced.

- **Code Quality and Style**  
  Ensure that your code follows the project’s style guidelines. You can automatically apply formatting by running:
  ```bash
  ./gradlew spotlessApply
  ```
  
- **Testing**
  All tests must pass before your PR can be merged.
  Please run the test suite locally and confirm everything is working as expected.