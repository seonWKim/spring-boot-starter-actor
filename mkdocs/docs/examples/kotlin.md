# Kotlin Example

The [simple example](simple.md) in Kotlin. The service talks to the actor with `suspend` functions from
`actor-kotlin` instead of `CompletionStage` chains, and the WebFlux controller runs them as coroutines.

- **Source:** [`example/kotlin`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/kotlin)
- **Runs:** one node, no cluster (`spring.actor.provider: local`)
- **Modules:** `actor-spring-boot-starter`, `actor-kotlin`, Spring WebFlux, `kotlinx-coroutines-reactor`

## What it shows

- An actor written in Kotlin: a `sealed interface` for commands, `actorBehavior<Command>(ctx)` to start
  the builder
- `awaitGetOrSpawn<HelloActor, _>(id)` and `awaitAsk(command, timeout = 3.seconds)`
- `suspend` controller functions in WebFlux
- The Gradle setup for a Kotlin Spring app (`kotlin("plugin.spring")`, `-Xjsr305=strict`)

## Run it

```bash
./gradlew :example:kotlin:bootRun
```

```bash
curl http://localhost:8080/hello
# Hello from actor hello-actor
curl http://localhost:8080/hello/restart
curl http://localhost:8080/hello/stop
```

The test calls `/hello` through `WebTestClient`:

```bash
./gradlew :example:kotlin:test
```

## Build setup

```kotlin
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")   // opens @Component and @Configuration classes for Spring
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(project(":actor-spring-boot-starter"))
    implementation(project(":actor-kotlin"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")  // suspend controller functions
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

In your own app, use the published coordinates `io.github.seonwkim:actor-spring-boot-starter` and
`io.github.seonwkim:actor-kotlin`.

## Walkthrough

### The actor: `HelloActor`

```kotlin
@Component
class HelloActor : SpringActor<HelloActor.Command> {
    sealed interface Command

    class SayHello : AskCommand<String>(), Command

    class TriggerFailure : AskCommand<String>(), Command

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        actorBehavior<Command>(actorContext)
            .withState { ctx -> HelloBehavior(ctx, actorContext.actorId()) }
            .onMessage(SayHello::class.java) { state, msg -> state.onSayHello(msg) }
            .onMessage(TriggerFailure::class.java) { state, msg -> state.onTriggerFailure(msg) }
            .onSignal(PreRestart::class.java) { state, _ -> state.onPreRestart() }
            .onSignal(PostStop::class.java) { state, _ -> state.onPostStop() }
            .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
            .build()

    private class HelloBehavior(
        private val ctx: SpringBehaviorContext<Command>,
        private val actorId: String,
    ) {
        fun onSayHello(msg: SayHello): SpringActorBehavior<Command> {
            msg.reply("Hello from actor $actorId")
            return SpringActorBehavior.same()
        }

        fun onTriggerFailure(msg: TriggerFailure): SpringActorBehavior<Command> {
            msg.reply("Triggering failure - actor will restart")
            throw IllegalStateException("Intentional failure to demonstrate PreRestart signal")
        }

        // onPreRestart() and onPostStop() log and return SpringActorBehavior.same()
    }
}
```

`actorBehavior<Command>(actorContext)` is `SpringActorBehavior.builder(Command::class.java, actorContext)`
with a reified type. The supervision strategy uses `java.time.Duration`, because it is the Java API.

### The service: `HelloService`

```kotlin
@Service
class HelloService(
    private val actorSystem: SpringActorSystem,
) {
    suspend fun hello(): String = helloActor().awaitAsk(HelloActor.SayHello(), timeout = 3.seconds)

    suspend fun triggerRestart(): String = helloActor().awaitAsk(HelloActor.TriggerFailure(), timeout = 3.seconds)

    suspend fun stopActor(): String {
        helloActor().stop()
        return "Actor stopped - PostStop signal will be triggered"
    }

    // `_` lets Kotlin infer the command type from HelloActor.
    private suspend fun helloActor() = actorSystem.awaitGetOrSpawn<HelloActor, _>(ACTOR_ID)

    private companion object {
        const val ACTOR_ID = "hello-actor"
    }
}
```

- `awaitGetOrSpawn<HelloActor, _>("hello-actor")` suspends until the actor runs, spawning it if needed.
- `awaitAsk(command, timeout = 3.seconds)` takes a `kotlin.time.Duration` and suspends until the reply.
  It throws `java.util.concurrent.TimeoutException` when no reply arrives in time.

### The controller

```kotlin
@RestController
class HelloController(
    private val helloService: HelloService,
) {
    @GetMapping("/hello")
    suspend fun hello(): String = helloService.hello()

    @GetMapping("/hello/restart")
    suspend fun triggerRestart(): String = helloService.triggerRestart()

    @GetMapping("/hello/stop")
    suspend fun stopActor(): String = helloService.stopActor()
}
```

## Try next

- Give `SayHello` a property (`class SayHello(val name: String) : AskCommand<String>(), Command`) and
  greet by name.
- Write a sharded version with `actorSystem.sharded<MyShardedActor, _>().withId(id).get()` and
  `awaitAsk`, as in the [cluster example](cluster.md).

## Related

- [Kotlin guide](../guides/kotlin.md): every `actor-kotlin` helper
- [Simple example](simple.md): the same app in Java
- [Actors](../guides/core/actors.md) and [Messaging](../guides/core/messaging.md)
