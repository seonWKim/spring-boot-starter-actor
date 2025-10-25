<table>
<tr>
<td width="15%">
  <img src="mkdocs/docs/logo.png" alt="Library Logo" />
</td>
<td>
  <h2>Spring Boot Starter Actor</h2>
  <p>Bring the power of the actor model to your Spring Boot applications using <a href="https://pekko.apache.org/">Pekko</a> (an open-source fork of Akka).</p>
</td>
</tr>
</table>

## Why spring-boot-starter-actor?

Spring is the go-to framework in the Java ecosystem, but it lacks native support for actors. The actor model is incredibly useful for building scalable, concurrent systems—think IoT platforms, real-time chat, telecommunications, and more.

This library bridges that gap, letting you build actor-based systems with the Spring Boot patterns you already know. The goal? Make actor programming accessible to every Spring developer. We'd love to see this become an official Spring project someday.

**What's the actor model?**
- **Encapsulation**: Logic and state live inside actors
- **Message-passing**: Actors communicate by sending messages (no shared state!)
- **Concurrency**: Built-in isolation makes concurrent programming safer and easier

## Live Demo

<div style="border: 2px solid #ccc; display: inline-block; border-radius: 8px; overflow: hidden;">
  <img src="mkdocs/docs/chat.gif" alt="Demo"/>
</div>

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

Creating actors is as simple as implementing an interface and adding `@Component`. Here's how:

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
        helloActor.tell(new HelloActor.SayHello(message));
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

        userActor.tell(new UserActor.UpdateProfile(name));
    }
}
```

## Examples

Check out our [full documentation](https://seonwkim.github.io/spring-boot-starter-actor/) for more examples and guides.

### Try the Demo

Want to see it in action? Run the chat example with multiple nodes:

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

## Monitoring

Need to monitor your actors? We've got you covered with built-in metrics and a ready-to-use Prometheus + Grafana stack.

**Quick start:**

```shell
# Start the monitoring stack
$ cd scripts/monitoring && docker-compose up -d
```

Access dashboards at:
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

The dashboards show message processing times, message counts by type, and other actor performance metrics.

## Contributing

Contributions are welcome! Here's how to get started:

1. **Create an issue** describing your contribution
2. **Open a PR** with a clear explanation of your changes
3. **Run code formatting**: `./gradlew spotlessApply`
4. **Make sure tests pass** before submitting

We appreciate your help in making this library better!