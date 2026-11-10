# Synchronization Example

A counter that many requests increment at the same time, on three nodes, built three ways: a database
row lock, a Redis lock, and a sharded actor. A load script checks that each one ends at the right
value.

- **Source:** [`example/synchronization`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/synchronization)
- **Runs:** three nodes on one machine, plus MySQL and Redis
- **Modules:** `actor-spring-boot-starter-cluster`, Spring WebFlux, Spring Data JPA, Spring Data Redis Reactive

## What it shows

- Why an actor needs no lock: it handles one message at a time, so `value++` in its handler is safe.
- A sharded actor per counter id: one instance in the cluster, whichever node gets the request.
- The cost of the alternatives: a `SELECT ... FOR UPDATE` per increment, or a Redis lock with retries.

## Run it

You need MySQL on `localhost:3306` (user `root`, password `root`) and Redis on `localhost:6379`. The
app creates the `counter_db` database and the `counter` table on start. For example, with Docker:

```bash
docker run -d --name counter-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
docker run -d --name counter-redis -p 6379:6379 redis:7
```

Start three nodes from the repository root:

```bash
sh cluster-start.sh synchronization io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

Then run the check. It needs Apache Bench (`ab`):

```bash
cd example/synchronization
./verify.sh
```

`verify.sh` sends 9,000 increments for one counter id, split over the three nodes with 10 concurrent
requests each, once per method (`db`, `redis`, `actor`). Each increment responds only after it is
applied, so when the requests are done it reads the counter from node 8080 and compares it with 9,000.
Each run uses a new counter id (`verify-<timestamp>`; set `COUNTER_ID` to choose one), so it can run
again without clearing the database and Redis.

Stop the nodes with `sh cluster-stop.sh` from the repository root.

### Endpoints

| Method | Increment | Read |
|---|---|---|
| Database lock | `GET /counter/db/{counterId}/increment` | `GET /counter/db/{counterId}` |
| Redis lock | `GET /counter/redis/{counterId}/increment` | `GET /counter/redis/{counterId}` |
| Actor | `GET /counter/actor/{counterId}/increment` | `GET /counter/actor/{counterId}` |

The app also includes springdoc, so Swagger UI is at `http://localhost:8080/swagger-ui.html`.

## Walkthrough

### The actor: `CounterActor`

One sharded entity per counter id. The value lives in the actor's state; no lock, no database.

```java
@Component
public class CounterActor implements SpringShardedActor<CounterActor.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "CounterActor");

    public interface Command extends JsonSerializable {}

    public static class Increment extends AskCommand<Long> implements Command {}

    public static class GetValue extends AskCommand<Long> implements Command {}

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(context -> new CounterActorBehavior(ctx.getEntityId()))
                .onMessage(Increment.class, CounterActorBehavior::onIncrement)
                .onMessage(GetValue.class, CounterActorBehavior::onGetValue)
                .build();
    }

    private static class CounterActorBehavior {
        private final String counterId;
        private long value = 0;

        CounterActorBehavior(String counterId) {
            this.counterId = counterId;
        }

        private SpringActorBehavior<Command> onIncrement(Increment msg) {
            value++;
            msg.reply(value);
            return SpringActorBehavior.same();
        }

        private SpringActorBehavior<Command> onGetValue(GetValue msg) {
            msg.reply(value);
            return SpringActorBehavior.same();
        }
    }
}
```

`ActorCounterService` sends both commands with `ask`, so an increment's HTTP response waits until the
actor applied it:

=== "Java"

    ```java
    public Mono<Long> increment(String counterId) {
        var actorRef = springActorSystem.sharded(CounterActor.class).withId(counterId).get();
        return Mono.fromCompletionStage(actorRef.ask(new CounterActor.Increment())
                .withTimeout(Duration.ofSeconds(3))
                .execute());
    }

    public Mono<Long> getValue(String counterId) {
        var actorRef = springActorSystem.sharded(CounterActor.class).withId(counterId).get();
        CompletionStage<Long> response = actorRef.ask(new CounterActor.GetValue())
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> 0L)
                .execute();
        return Mono.fromCompletionStage(response);
    }
    ```

=== "Kotlin"

    ```kotlin
    // Call from a suspend function or a coroutine.
    suspend fun increment(counterId: String): Long =
        actorSystem.sharded<CounterActor, _>()
            .withId(counterId)
            .get()
            .ask(CounterActor.Increment())
            .withTimeout(3.seconds)
            .await()

    suspend fun getValue(counterId: String): Long =
        actorSystem.sharded<CounterActor, _>()
            .withId(counterId)
            .get()
            .ask(CounterActor.GetValue())
            .withTimeout(3.seconds)
            .onTimeout { 0L }
            .await()
    ```

### The database lock: `DbCounterService`

Each increment opens a transaction and locks the row with a native query:

```java
@Query(value = "SELECT * FROM counter WHERE counter_id = :counterId FOR UPDATE", nativeQuery = true)
Optional<Counter> findByIdWithLock(@Param("counterId") String counterId);
```

Concurrent increments of the same counter wait for each other inside the database, and each one holds a
connection while it waits.

### The Redis lock: `RedisCounterService`

Each increment takes a lock key with `setIfAbsent` (2-second expiry), runs `INCR`, and deletes the lock.
When the lock is taken, it retries with backoff, up to 50 times. Every increment costs at least three
Redis round trips, more under contention.

## Things to keep in mind

- The actor counter lives in memory. If the node that hosts the entity stops, the count is lost. For a
  count that must survive, persist it: see [State in a database](../guides/data/state-in-a-database.md)
  or [Event sourcing](../guides/data/event-sourcing.md).

## Try next

- Raise `NUM_REQUESTS` and `CONCURRENCY` in `verify.sh` and compare how long each method takes.
- Use many counter ids instead of one. The actor version spreads them over the nodes; the database
  version spreads them over rows.

## Related

- [Sharded actors](../guides/cluster/sharded-actors.md)
- [Messaging](../guides/core/messaging.md): `tell` and `ask`
- [Cluster example](cluster.md)
