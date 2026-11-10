# Getting Started

This page takes you from an empty Spring Boot application to an actor that answers HTTP requests.

## Prerequisites

- Java 21 or higher
- Spring Boot 3.5 or higher

Spring Boot 2 users stay on the 0.x line (`spring-boot-starter-actor`). See [Migrating from 0.x](migrating-from-0x.md).

## 1. Add the dependency

=== "Gradle"

    ```groovy
    implementation 'io.github.seonwkim:actor-spring-boot-starter:1.0.0'
    ```

=== "Maven"

    ```xml
    <dependency>
      <groupId>io.github.seonwkim</groupId>
      <artifactId>actor-spring-boot-starter</artifactId>
      <version>1.0.0</version>
    </dependency>
    ```

The latest version is on [Maven Central](https://central.sonatype.com/artifact/io.github.seonwkim/actor-spring-boot-starter).
For cluster features (sharding, singletons, replicated data), use `actor-spring-boot-starter-cluster`
instead. Other modules are listed on the [home page](index.md#modules).

Writing Kotlin? Add `io.github.seonwkim:actor-kotlin:1.0.0` too, for `suspend` functions and reified
helpers (see the [Kotlin guide](guides/kotlin.md)). Every code sample in these docs has a Java and a
Kotlin tab; your choice applies to the whole site.

### Several modules: import the BOM

From 1.1.0, `io.github.seonwkim:actor-bom` lists every module at one version. Import it and leave the
version off each module. It manages only this project's modules; Spring Boot keeps managing Pekko's
dependencies such as Jackson.

=== "Gradle"

    ```groovy
    implementation platform('io.github.seonwkim:actor-bom:1.1.0')
    implementation 'io.github.seonwkim:actor-spring-boot-starter'
    implementation 'io.github.seonwkim:actor-metrics'
    testImplementation 'io.github.seonwkim:actor-test'
    ```

=== "Maven"

    ```xml
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>io.github.seonwkim</groupId>
          <artifactId>actor-bom</artifactId>
          <version>1.1.0</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>

    <dependencies>
      <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-spring-boot-starter</artifactId>
      </dependency>
      <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-metrics</artifactId>
      </dependency>
      <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-test</artifactId>
        <scope>test</scope>
      </dependency>
    </dependencies>
    ```

!!! note
    Spring Boot 3.5 manages Jackson 2.18+, which satisfies Pekko. No `jackson-bom` override is needed.

## 2. Start the application

The starter is a Spring Boot auto-configuration: with the dependency on the classpath, a
`@SpringBootApplication` starts one actor system for the Spring context and stops it when the context
closes. `spring.actor.enabled=false` keeps the starter on the classpath without starting one.

=== "Java"

    ```java
    @SpringBootApplication
    public class MyApplication {
        public static void main(String[] args) {
            SpringApplication.run(MyApplication.class, args);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @SpringBootApplication
    class MyApplication

    fun main(args: Array<String>) {
        runApplication<MyApplication>(*args)
    }
    ```

!!! note "`@EnableActorSupport` is optional"
    Applications written for 1.0.0 have `@EnableActorSupport` on the application class. It still works and
    adds nothing in a Spring Boot application, so you can remove it. Keep it only where auto-configuration
    is off, such as a context built without `@EnableAutoConfiguration`.

## 3. Write an actor

An actor is a Spring bean that implements `SpringActor<Command>`. `Command` is the type of message it
accepts. A message that expects an answer extends `AskCommand<Reply>` and answers with `reply(...)`.

=== "Java"

    ```java
    @Component
    public class GreeterActor implements SpringActor<GreeterActor.Command> {

        public interface Command {}

        public static class Greet extends AskCommand<String> implements Command {
            public final String name;

            public Greet(String name) {
                this.name = name;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Greet.class, (ctx, msg) -> {
                        msg.reply("Hello, " + msg.name + "!");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class GreeterActor : SpringActor<GreeterActor.Command> {

        sealed interface Command

        class Greet(val name: String) : AskCommand<String>(), Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .onMessage(Greet::class.java) { _, msg ->
                    msg.reply("Hello, ${msg.name}!")
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

The actor handles one message at a time, so state inside it needs no locks. Because it's a bean, its
constructor can take any other bean (a repository, a client).

## 4. Talk to it

Inject `SpringActorSystem`. `getOrSpawn` returns the running actor with that id, or starts it.
`ask` sends a message and waits for the reply, up to the timeout.

=== "Java"

    ```java
    @RestController
    public class GreeterController {

        private final SpringActorSystem actorSystem;

        public GreeterController(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @GetMapping("/greet")
        public CompletionStage<String> greet(@RequestParam String name) {
            return actorSystem.getOrSpawn(GreeterActor.class, "greeter")
                    .thenCompose(greeter -> greeter.ask(new GreeterActor.Greet(name))
                            .withTimeout(Duration.ofSeconds(5))
                            .execute());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @RestController
    class GreeterController(private val actorSystem: SpringActorSystem) {

        @GetMapping("/greet")
        suspend fun greet(@RequestParam name: String): String =
            actorSystem.awaitGetOrSpawn<GreeterActor, _>("greeter")
                .awaitAsk(GreeterActor.Greet(name), timeout = 5.seconds)
    }
    ```

In Kotlin, `awaitGetOrSpawn` and `awaitAsk` suspend instead of returning a `CompletionStage`. A `suspend`
controller method needs Spring WebFlux, or Spring MVC with `kotlinx-coroutines-reactor` on the classpath.

Run the application and call it:

```bash
curl 'http://localhost:8080/greet?name=Ada'
# Hello, Ada!
```

## 5. Configure (optional)

Nothing is required. Every key lives under `spring.actor`:

```yaml
spring:
  actor:
    name: orders        # actor system name, shown in logs and node addresses
    provider: local     # local (default) or cluster
```

The [configuration reference](guides/operations/configuration.md) lists every key.

## Using an AI assistant

If an AI assistant helps you write code with the starter, point it to [llms.txt](llms.txt).
It is a short index of these docs, following the [llms.txt](https://llmstxt.org/) convention, with one line per page.
For the full text of every page in one file, use [llms-full.txt](https://seonwkim.github.io/actor-spring-boot-starter/llms-full.txt).

## Next steps

- [Actors](guides/core/actors.md) and [messaging](guides/core/messaging.md): spawning, ids, tell and ask in depth
- [Supervision](guides/core/supervision.md): what happens when an actor fails
- [Sharded actors](guides/cluster/sharded-actors.md): spread actors over a cluster
- [Testing](guides/testing.md): test actors with `@ActorTest`
- [Examples](examples/index.md): runnable applications, from a single node to Kubernetes
