# Virtual Threads Example

Actors on Java 21 virtual threads, configured with typed `spring.actor.*` keys and no Pekko HOCON. An
endpoint spawns an actor on a virtual-thread dispatcher, and the actor logs whether its thread is
virtual.

- **Source:** [`example/virtual-threads`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/virtual-threads)
- **Runs:** one node, no cluster; needs Java 21 or later
- **Modules:** `actor-spring-boot-starter`, Spring WebFlux, Actuator

## What it shows

- `spring.actor.virtual-threads.enabled`: runs the blocking dispatcher (`withBlockingDispatcher()`) on
  virtual threads
- `spring.actor.dispatchers.<name>.virtual: true`: a named dispatcher on virtual threads
- `withDispatcherFromConfig("<name>")` to spawn an actor on that dispatcher

## Run it

```bash
./gradlew :example:virtual-threads:bootRun
```

```bash
curl http://localhost:8080/api/virtual
```

The response describes the dispatcher. The proof is in the application log:

```
Task 'virtual-thread-executor' - Thread: ..., IsVirtual: true, ThreadId: ...
```

## Configuration

```yaml
spring:
  actor:
    provider: local
    virtual-threads:
      enabled: true            # withBlockingDispatcher() runs on virtual threads
    dispatchers:
      virtual-threads-dispatcher:
        virtual: true          # withDispatcherFromConfig("virtual-threads-dispatcher")
```

| Property | Values | What it does |
|---|---|---|
| `spring.actor.virtual-threads.enabled` | `auto` (default), `true`, `false` | Runs the blocking dispatcher on virtual threads. `auto` follows Spring Boot's `spring.threads.virtual.enabled`. |
| `spring.actor.dispatchers.<name>.virtual` | `true` / `false` | Defines a dispatcher that runs each task on a new virtual thread |
| `spring.actor.dispatchers.<name>.executor` | an `Executor` bean name | The alternative: a dispatcher on a Spring `Executor` bean. Set one of `virtual` and `executor`, not both. |

The default dispatcher always stays on platform threads.

## Walkthrough

### The actor: `VirtualThreadTestActor`

```java
@Component
public class VirtualThreadTestActor
        implements SpringActorWithContext<VirtualThreadTestActor.Command, SpringActorContext> {

    public interface Command extends FrameworkCommand {}

    public static class CheckThread implements Command {
        public final String taskName;

        public CheckThread(String taskName) {
            this.taskName = taskName;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(CheckThread.class, this::onCheckThread)
                .build();
    }

    private SpringActorBehavior<Command> onCheckThread(SpringBehaviorContext<Command> context, CheckThread command) {
        Thread currentThread = Thread.currentThread();
        context.getLog().info("Task '{}' - Thread: {}, IsVirtual: {}, ThreadId: {}",
                command.taskName, currentThread.getName(), currentThread.isVirtual(), currentThread.threadId());
        return SpringActorBehavior.same();
    }
}
```

### Spawning on the virtual-thread dispatcher

=== "Java"

    ```java
    SpringActorHandle<VirtualThreadTestActor.Command> actor = actorSystem
            .actor(VirtualThreadTestActor.class)
            .withId("virtual-actor-" + System.currentTimeMillis())
            .withDispatcherFromConfig("virtual-threads-dispatcher")
            .spawnAndWait();

    actor.tell(new VirtualThreadTestActor.CheckThread("virtual-thread-executor"));
    ```

=== "Kotlin"

    ```kotlin
    val actor = actorSystem.actor<VirtualThreadTestActor, _>()
        .withId("virtual-actor-${System.currentTimeMillis()}")
        .withDispatcherFromConfig("virtual-threads-dispatcher")
        .spawnAndWait()

    actor.tell(VirtualThreadTestActor.CheckThread("virtual-thread-executor"))
    ```

`spawnAndWait()` blocks the calling thread until the actor runs. The example calls it from a WebFlux
controller method, which runs on a request thread that should not block; in your own reactive code, use
`spawn()` and compose the `CompletionStage` (or `awaitSpawn()` from a Kotlin `suspend` function).

## Things to keep in mind

- Each request to `/api/virtual` spawns a new actor with a new id and never stops it. That keeps the
  example short; a real app would reuse one actor or stop it when done.
- A virtual thread that blocks inside a `synchronized` block or a native call pins its carrier thread.
  Prefer `java.util.concurrent` locks in code that blocks on virtual threads.
- Virtual threads help actors that block (JDBC, HTTP clients). Actors that only compute gain nothing.

## Try next

- Spawn the actor with `withBlockingDispatcher()` instead and check that it also runs on a virtual thread
  (because `spring.actor.virtual-threads.enabled` is `true`).
- Set `spring.actor.virtual-threads.enabled: false` and repeat: the blocking dispatcher now logs
  `IsVirtual: false`.

## Related

- [Dispatchers](../guides/core/dispatchers.md): default, blocking, named and virtual-thread dispatchers
- [Configuration](../guides/operations/configuration.md): every `spring.actor.*` property
