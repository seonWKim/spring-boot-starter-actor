# Receptionist (Service Discovery)

The receptionist finds actors by a **service name** instead of by class and id. Actors register under a
name; other code looks the name up once, or subscribes and is told whenever the set of registered actors
changes. In cluster mode, the registrations are shared across nodes, so a lookup on one node finds
actors on every node. It wraps Pekko's typed
[Receptionist](https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html).

Use it when the caller doesn't know (or shouldn't know) the ids of the actors it needs:

- finding all instances of a worker, wherever they run;
- reacting when instances of a service start and stop;
- reaching actors on other cluster nodes without sharding them.

When you know the id, use `getOrSpawn` or `get` ([Actors](actors.md)) or a
[sharded actor](../cluster/sharded-actors.md) instead. To send one message to many actors, a
[topic](pub-sub-topics.md) is simpler.

The `SpringReceptionist` bean comes with the core starter. It is created with
`@ConditionalOnMissingBean`, so you can replace it with your own bean.

## Service names

A `ServiceName` combines a message type and an id. Names with the same type and id are equal on every
node. The type is the command type of the registered actors, so lookups return handles you can `tell`
and `ask` directly.

=== "Java"

    ```java
    public static final ServiceName<WorkerActor.Command> WORKERS =
            ServiceName.of(WorkerActor.Command.class, "workers");
    ```

=== "Kotlin"

    ```kotlin
    val WORKERS: ServiceName<WorkerActor.Command> = ServiceName.of(WorkerActor.Command::class.java, "workers")
    ```

The id must not be blank. In cluster mode the messages must implement `JsonSerializable` or
`CborSerializable`, as for any message that crosses nodes.

## Registering

An actor can register itself when it starts, in its `withState` factory:

=== "Java"

    ```java
    @Component
    public class WorkerActor implements SpringActor<WorkerActor.Command> {

        public interface Command extends JsonSerializable {}

        public record DoJob(String jobId) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> {
                        ctx.registerWithReceptionist(WORKERS);
                        return ctx;
                    })
                    .onMessage(DoJob.class, (ctx, msg) -> {
                        ctx.getLog().info("Running job {}", msg.jobId());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class WorkerActor : SpringActor<WorkerActor.Command> {

        sealed interface Command : JsonSerializable

        data class DoJob(val jobId: String) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withState { ctx ->
                    ctx.registerWithReceptionist(WORKERS)
                    ctx
                }
                .onMessage(DoJob::class.java) { ctx, msg ->
                    ctx.log.info("Running job {}", msg.jobId)
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

Or any code can register an actor it has a ref to, with `receptionist.register(name, ref)`:

=== "Java"

    ```java
    SpringActorHandle<WorkerActor.Command> worker = actorSystem.actor(WorkerActor.class)
            .withId("worker-1")
            .spawnAndWait();
    receptionist.register(WORKERS, worker);
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    val worker = actorSystem.actor<WorkerActor, _>().withId("worker-1").awaitSpawn()
    receptionist.register(WORKERS, worker)
    ```

A stopped actor is removed from its services automatically. To remove a running one, call
`receptionist.deregister(name, ref)`.

## Finding

`find(name)` returns the actors registered under the name right now. The set may be empty.

=== "Java"

    ```java
    @Service
    public class JobDispatcher {
        private final SpringReceptionist receptionist;

        public JobDispatcher(SpringReceptionist receptionist) {
            this.receptionist = receptionist;
        }

        public CompletionStage<Integer> dispatch(String jobId) {
            return receptionist.find(WORKERS).thenApply(workers -> {
                workers.forEach(w -> w.tell(new WorkerActor.DoJob(jobId)));
                return workers.size();
            });
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class JobDispatcher(private val receptionist: SpringReceptionist) {

        suspend fun dispatch(jobId: String): Int {
            val workers = receptionist.find(WORKERS).await()
            workers.forEach { it.tell(WorkerActor.DoJob(jobId)) }
            return workers.size
        }
    }
    ```

`find(name)` waits up to 3 seconds for the receptionist's answer; `find(name, timeout)` sets another
limit. The result is a `Set<SpringActorHandle<T>>`; each handle supports `tell` and `ask`.

## Subscribing to changes

`subscribe(name, listener)` calls the listener with the current set right away, then with the full new
set every time an actor registers, deregisters or stops:

=== "Java"

    ```java
    SpringReceptionistSubscription<WorkerActor.Command> subscription =
            receptionist.subscribe(WORKERS, workers -> log.info("{} workers available", workers.size()));

    // later, for example in a @PreDestroy method
    subscription.cancel();
    ```

=== "Kotlin"

    ```kotlin
    val subscription: SpringReceptionistSubscription<WorkerActor.Command> =
        receptionist.subscribe(WORKERS) { workers -> log.info("{} workers available", workers.size) }

    // later, for example in a @PreDestroy method
    subscription.cancel()
    ```

- The listener runs on an actor thread. Keep it short and never block in it. A common pattern is to copy
  the set into a field (an `AtomicReference`) that request code reads.
- If the listener throws, the error is logged and the subscription stays.
- Each subscription is backed by a small helper actor. `cancel()` stops it and is safe to call twice;
  after `cancel()` returns, the listener isn't called again. `isCancelled()` tells whether it was
  cancelled, and `whenTerminated()` completes once the helper actor has stopped.

## Cluster mode

In a cluster, registrations are replicated to all nodes. `find` on node 2 can return an actor that runs
on node 1, and `tell` on the returned handle goes over the network.

!!! warning "Eventually consistent"
    Replication takes time. Right after an actor registers on one node, a lookup on another node can
    still return an empty or older set. Don't treat an empty result as final: retry with a bounded wait,
    or use `subscribe` and act when the actor appears.

Registrations of a node that leaves the cluster are removed once the node is removed from membership.

## API summary

| Method | Description |
|---|---|
| `ServiceName.of(type, id)` | Creates a service name |
| `register(name, ref)` | Registers an actor under a name |
| `deregister(name, ref)` | Removes an actor from a name |
| `find(name)`, `find(name, timeout)` | One-time lookup; default timeout 3 s |
| `subscribe(name, listener)` | Calls the listener now and on every change; returns a cancellable subscription |
| `SpringBehaviorContext.registerWithReceptionist(name)` | Registers the current actor from inside it |

## Related

- [Actors](actors.md): finding actors by class and id
- [Pub/sub topics](pub-sub-topics.md): one message to many actors
- [Sharded actors](../cluster/sharded-actors.md): one actor per id across a cluster
- [Cluster formation](../cluster/cluster-formation.md): running a cluster
