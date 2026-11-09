# Simple Example

One local actor behind a REST API. It is the smallest complete app: define an actor, get or spawn it,
ask it a question, and watch it restart and stop.

Start here if you are new to the library.

- **Source:** [`example/simple`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/simple)
- **Runs:** one node, no cluster (`spring.actor.provider: local`)
- **Modules:** `actor-spring-boot-starter`, Spring WebFlux

## What it shows

- An actor as a Spring `@Component` (`SpringActor`)
- Commands with a typed reply (`AskCommand<String>`)
- `getOrSpawn` to get the actor if it runs, or start it if not
- `ask` with a timeout, turned into a `Mono` for WebFlux
- A restart supervision strategy with a limit
- Lifecycle signals: `PreRestart` and `PostStop`

## Run it

From the repository root:

```bash
./gradlew :example:simple:bootRun
```

The app listens on port 8080. In another terminal:

```bash
curl http://localhost:8080/hello
# Hello from actor hello-actor

curl http://localhost:8080/hello/restart
# Triggering failure - actor will restart

curl http://localhost:8080/hello/stop
# Actor stopped - PostStop signal will be triggered
```

Watch the application log: `/hello/restart` logs "is being restarted due to failure" (the `PreRestart`
handler), and `/hello/stop` logs "is stopping" (the `PostStop` handler). The next `/hello` after a stop
spawns a new actor.

## Walkthrough

### Turn on actor support

```java
@SpringBootApplication
@EnableActorSupport
public class SpringPekkoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringPekkoApplication.class, args);
    }
}
```

`application.yml` runs a local actor system:

```yaml
spring:
  actor:
    provider: local
```

### The actor: `HelloActor`

The actor is a Spring bean. Its commands share one interface. A command that expects an answer extends
`AskCommand<R>`, where `R` is the reply type.

```java
@Component
public class HelloActor implements SpringActor<HelloActor.Command> {

    public interface Command {}

    public static class SayHello extends AskCommand<String> implements Command {}

    public static class TriggerFailure extends AskCommand<String> implements Command {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new HelloActorBehavior(ctx, actorContext))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .onMessage(TriggerFailure.class, HelloActorBehavior::onTriggerFailure)
                .onSignal(PreRestart.class, HelloActorBehavior::onPreRestart)
                .onSignal(PostStop.class, HelloActorBehavior::onPostStop)
                .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .build();
    }
}
```

- `withState` creates the actor's state object when the actor starts (and again after each restart).
  Handlers are methods on that object.
- `withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))` restarts
  the actor when a handler throws, up to 10 times a minute.

The handlers reply with `msg.reply(...)` and return the next behavior:

```java
private SpringActorBehavior<Command> onSayHello(SayHello msg) {
    msg.reply("Hello from actor " + actorContext.actorId());
    return SpringActorBehavior.same();
}

private SpringActorBehavior<Command> onTriggerFailure(TriggerFailure msg) {
    msg.reply("Triggering failure - actor will restart");
    throw new RuntimeException("Intentional failure to demonstrate PreRestart signal");
}
```

`onTriggerFailure` replies first and then throws, so the caller gets an answer and the actor restarts.

### Talking to the actor: `HelloService`

```java
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor")
                        .thenCompose(actor -> actor.ask(new HelloActor.SayHello())
                                .withTimeout(Duration.ofSeconds(3))
                                .execute()));
    }

    public Mono<String> stopActor() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> {
                    actor.stop();
                    return Mono.just("Actor stopped - PostStop signal will be triggered").toFuture();
                }));
    }
}
```

- `getOrSpawn(HelloActor.class, "hello-actor")` returns the running actor with that id, or spawns it.
- `ask(...).withTimeout(...).execute()` sends the command and returns a `CompletionStage` of the reply.
- `actor.stop()` stops the actor; its `PostStop` handler runs.

`HelloController` maps `/hello`, `/hello/restart` and `/hello/stop` to these methods.

## Try next

- Call `/hello/restart` more than 10 times within a minute. After the limit, supervision stops the actor
  instead of restarting it.
- Add a counter to `HelloActorBehavior` and return it in the reply. A restart resets it, because
  `withState` builds a new state object.
- Spawn a second actor with another id and check that each has its own state.
- Compare with the [Kotlin example](kotlin.md), the same app with `suspend` functions.

## Related

- [Actors](../guides/core/actors.md): defining, spawning and stopping actors
- [Messaging](../guides/core/messaging.md): `tell`, `ask`, timeouts and replies
- [Supervision](../guides/core/supervision.md): restart strategies and limits
- [Testing](../guides/testing.md)
