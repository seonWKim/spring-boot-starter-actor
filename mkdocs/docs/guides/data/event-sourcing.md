# Event-Sourced Actors

An event-sourced actor stores what happened (events) instead of its current state. Each command
decides which events to store; each event changes the state. When the actor starts again, after a
restart, a deploy or a crash, it rebuilds its state from the stored events.

The `actor-persistence` module runs event-sourced actors on Pekko Persistence. The journal and the
snapshots live in tables on your application's `DataSource`: no second connection pool, no Pekko
configuration, and no Pekko types in your code.

!!! tip "Event sourcing or a state store?"
    If you only need the latest state of an actor, keep it in a table with Spring Data, as in
    [State in a Database](state-in-a-database.md). Use event sourcing when the history
    matters: an audit trail, rebuilding read models with [projections](#tags-for-projections), or
    undoing a bug by replaying events.

## Add the module

```kotlin
dependencies {
    implementation("io.github.seonwkim:actor-spring-boot-starter:$version")
    implementation("io.github.seonwkim:actor-persistence:$version")
    implementation("org.springframework.boot:spring-boot-starter-jdbc") // or data-jpa: any DataSource
    runtimeOnly("org.postgresql:postgresql")
}
```

## Write an event-sourced actor

An event-sourced actor is a `SpringActor` whose `create` returns a `SpringEventSourcedBehavior`:

=== "Java"

    ```java
    @Component
    public class AccountActor implements SpringActor<AccountActor.Command> {

        public interface Command {}

        public static final class Deposit extends AskCommand<Long> implements Command {
            public final long amount;
            public Deposit(long amount) { this.amount = amount; }
        }

        public static final class GetBalance extends AskCommand<Long> implements Command {}

        // Events and the state are stored: they must be serializable.
        public interface Event extends JsonSerializable {}
        public record Deposited(long amount) implements Event {}

        public record Account(long balance) implements JsonSerializable {
            Account add(long amount) { return new Account(balance + amount); }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringEventSourcedBehavior.builder(
                            Command.class,
                            Event.class,
                            PersistenceId.of("Account", actorContext.actorId()),
                            new Account(0))
                    .onCommand(Deposit.class, (ctx, account, cmd) -> ctx.persist(new Deposited(cmd.amount))
                            .thenReply(cmd, Account::balance))
                    .onCommand(GetBalance.class, (ctx, account, cmd) -> ctx.none()
                            .thenReply(cmd, Account::balance))
                    .onEvent(Deposited.class, (account, event) -> account.add(event.amount()))
                    .snapshotEvery(100)
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class AccountActor : SpringActor<AccountActor.Command> {

        sealed interface Command

        class Deposit(val amount: Long) : AskCommand<Long>(), Command

        class GetBalance : AskCommand<Long>(), Command

        // Events and the state are stored: they must be serializable.
        sealed interface Event : JsonSerializable
        data class Deposited(val amount: Long) : Event

        data class Account(val balance: Long) : JsonSerializable {
            fun add(amount: Long) = Account(balance + amount)
        }

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            SpringEventSourcedBehavior.builder(
                Command::class.java,
                Event::class.java,
                PersistenceId.of("Account", actorContext.actorId()),
                Account(0),
            )
                .onCommand(Deposit::class.java) { ctx, _, cmd ->
                    ctx.persist(Deposited(cmd.amount)).thenReply(cmd) { it.balance }
                }
                .onCommand(GetBalance::class.java) { ctx, _, cmd ->
                    ctx.none().thenReply(cmd) { it.balance }
                }
                .onEvent(Deposited::class.java) { account, event -> account.add(event.amount) }
                .snapshotEvery(100)
                .build()
    }
    ```

Spawn it and send it commands like any other actor:

=== "Java"

    ```java
    SpringActorHandle<AccountActor.Command> account =
            actorSystem.actor(AccountActor.class).withId("alice").spawnAndWait();
    long balance = account.ask(new AccountActor.Deposit(5)).execute().toCompletableFuture().join();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend fun or a coroutine
    val account = actorSystem.actor<AccountActor, _>().withId("alice").awaitSpawn()
    val balance: Long = account.awaitAsk(AccountActor.Deposit(5))
    ```

Spawn `alice` again after a restart and it has the same balance.

### The parts

| Part | What it does |
|---|---|
| `PersistenceId.of(entityType, entityId)` | The key of the actor's events: `Account\|alice`. It must be unique in the application and the same on every start. |
| empty state | The state before any event. |
| `onCommand(type, (ctx, state, command) -> effect)` | Decides what to do with a command. It returns an `Effect`; it must not change the state itself. |
| `onEvent(type, (state, event) -> newState)` | Applies a stored event. It runs after the event is stored and again on every recovery, so it must not have side effects. |
| `snapshotEvery(n)` | Stores a snapshot of the state every `n` events and keeps the latest two. Recovery loads the latest snapshot and replays only the events after it. |
| `withTagger(event -> Set.of(...))` | Tags stored with each event, for projections. |

### Effects

Start from the context, which knows the event and state types:

| Effect | Meaning |
|---|---|
| `ctx.persist(event)`, `ctx.persist(List.of(e1, e2))` | Store the events (a list atomically), then apply them |
| `ctx.none()` | Store nothing |
| `ctx.unhandled()` | The command isn't handled in this state |
| `ctx.stop()` | Store nothing and stop the actor |
| `.thenReply(cmd, state -> value)` | Reply to an `AskCommand` with the state after the events |
| `.thenRun(state -> ...)` | Any other side effect, after the events are stored |
| `.thenStop()` | Stop after the side effects |

Side effects run only after the events are stored, never during recovery. `ctx.actorContext()` gives
the actor's own context (`getSelf()`, `getLog()`, timers).

### Serialization

Events and states are written to the database with Jackson. Implement `JsonSerializable` (or
`CborSerializable`) from `io.github.seonwkim.actor.serialization` on your events and your state.
Once events are stored, their JSON shape is a contract: add fields with defaults rather than renaming
or removing them.

## Tables

The journal (`event_journal`, `event_tag`) and the snapshots (`snapshot`) are the tables of
[Pekko Persistence JDBC](https://pekko.apache.org/docs/pekko-persistence-jdbc/current/) 1.1.
`spring.actor.persistence.initialize-schema` decides when the starter creates them:

| Value | When |
|---|---|
| `embedded` (default) | Only in an embedded database, such as H2 in tests |
| `always` | On every start (the scripts use `CREATE TABLE IF NOT EXISTS`) |
| `never` | You create them, for example with Flyway |

The scripts are in the `actor-persistence` jar as `io/github/seonwkim/actor/persistence/schema-<db>.sql`
for `h2`, `postgresql`, `mysql` and `mariadb` (tested on H2, PostgreSQL 16 and MySQL 8.4). Copy the one
for your database into a Flyway or Liquibase migration for production. For Oracle and SQL Server the
starter picks the matching dialect but ships no script and has no test: create their tables from the
`schema/oracle` or `schema/sqlserver` scripts in the `pekko-persistence-jdbc` jar.

The actor system starts after the database is initialized (the starter's scripts, Flyway or
Liquibase), so an actor can recover as soon as it is spawned.

The JDBC calls run on their own threads, as many as your Hikari pool has connections (10 otherwise).
The starter never closes your `DataSource`.

## Tags for projections

A projection reads the events of one tag in the order they were stored, for example to fill a
read model or publish to Kafka:

=== "Java"

    ```java
    .withTagger(event -> Set.of("accounts"))
    ```

=== "Kotlin"

    ```kotlin
    .withTagger { setOf("accounts") }
    ```

See [Projections](projections.md).

## Failures

If storing an event fails, the actor stops (Pekko's default) and the command gets no reply; an `ask`
times out. Spawn the actor again to recover it from the stored events. Commands are never stored:
a command that was being handled when the application stopped is lost, so let the caller retry.

## In a cluster

Run at most one actor per persistence id at a time: two actors that store events under the same id
conflict on their sequence numbers. On one node, a regular actor with a given id runs once. On several
nodes, each node could spawn its own copy, so make sure only one node spawns a given id, for example by
routing all requests for an id to one place, or by using a [cluster singleton](../cluster/singletons.md)
for a single global actor.

## Not supported yet

- **R2DBC.** The R2DBC plugin for Pekko 1.1 (`pekko-persistence-r2dbc` 1.0) supports only PostgreSQL and
  Yugabyte, and its next version needs Pekko 1.2. The journal runs on JDBC for now.
- **Framework commands** (`FrameworkCommand`, spawning children by message) are not handled by
  event-sourced actors.

## Related

- [Projections](projections.md): read the stored events into read models or Kafka
- [State in a Database](state-in-a-database.md): keep only the latest state with Spring Data
- [Persistence example](../../examples/persistence.md)
