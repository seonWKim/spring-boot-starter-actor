# Projections

A projection reads the events that [event-sourced actors](event-sourcing.md) store and writes them
somewhere else: a read model in your database through Spring Data, or a Kafka topic through Spring
Kafka. It remembers how far it got (its offset) in your database, so after a restart it resumes
where it stopped.

The `actor-projection` module runs projections on Pekko Projections, with no Pekko types in your
code.

## Add the module

```kotlin
dependencies {
    implementation("io.github.seonwkim:actor-spring-boot-starter:$version")
    implementation("io.github.seonwkim:actor-projection:$version") // brings actor-persistence
    implementation("org.springframework.boot:spring-boot-starter-jdbc") // or data-jpa
}
```

## Tag the events

A projection reads the events of one tag. Tag them in the event-sourced actor:

=== "Java"

    ```java
    SpringEventSourcedBehavior.builder(Command.class, Event.class, PersistenceId.of("Account", id), new Account(0))
            // ...
            .withTagger(event -> Set.of("accounts"))
            .build();
    ```

=== "Kotlin"

    ```kotlin
    SpringEventSourcedBehavior.builder(Command::class.java, Event::class.java, PersistenceId.of("Account", id), Account(0))
        // ...
        .withTagger { setOf("accounts") }
        .build()
    ```

Only events stored after you add the tagger carry the tag.

## Write a projection

A projection is a bean that implements `SpringProjection`:

=== "Java"

    ```java
    @Component
    public class BalanceProjection implements SpringProjection<AccountActor.Event> {

        private final BalanceRepository balances; // a Spring Data repository

        public BalanceProjection(BalanceRepository balances) {
            this.balances = balances;
        }

        @Override
        public String name() {
            return "balances";
        }

        @Override
        public String tag() {
            return "accounts";
        }

        @Override
        public void handle(ProjectedEvent<AccountActor.Event> event) {
            if (event.event() instanceof AccountActor.Deposited deposited) {
                Balance balance = balances.findById(event.persistenceId()).orElseGet(() -> new Balance(event.persistenceId()));
                balance.add(deposited.amount());
                balances.save(balance);
            }
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class BalanceProjection(
        private val balances: BalanceRepository, // a Spring Data repository
    ) : SpringProjection<AccountActor.Event> {

        override fun name() = "balances"

        override fun tag() = "accounts"

        override fun handle(event: ProjectedEvent<AccountActor.Event>) {
            val deposited = event.event() as? AccountActor.Deposited ?: return
            val balance = balances.findById(event.persistenceId()).orElseGet { Balance(event.persistenceId()) }
            balance.add(deposited.amount)
            balances.save(balance)
        }
    }
    ```

Every `SpringProjection` bean starts when the application starts. `handle` gets the events of its tag
one at a time, in the order they were stored, with the id of the actor that stored each one
(`persistenceId()`, such as `Account|alice`), its sequence number and its timestamp.

`name()` identifies the stored offset. Rename a projection and it starts again from the first event,
which is also how you rebuild a read model.

## Exactly once or at least once

`delivery()` decides when the offset is stored:

| `delivery()` | Offset stored | Use it for |
|---|---|---|
| `EXACTLY_ONCE` (default) | In the same transaction as `handle` | Writes to the application's database: repositories, `JdbcTemplate` |
| `AT_LEAST_ONCE` | After `handle`, in batches | Writes elsewhere, such as Kafka |

`handle` always runs in a Spring transaction on your `DataSource`, using your
`PlatformTransactionManager` bean when there is exactly one (otherwise a `JdbcTransactionManager` on the
`DataSource`). With
`EXACTLY_ONCE`, the rows `handle` writes and the new offset commit together, or roll back together.

When `handle` throws, the transaction rolls back and the projection restarts after a backoff, from
the stored offset, so the event is handled again.

## Spring Kafka

Publish with `AT_LEAST_ONCE`, and wait for the send so a failed send fails the event:

=== "Java"

    ```java
    @Component
    public class AccountEventsToKafka implements SpringProjection<AccountActor.Event> {

        private final KafkaTemplate<String, AccountActor.Event> kafka;

        public AccountEventsToKafka(KafkaTemplate<String, AccountActor.Event> kafka) {
            this.kafka = kafka;
        }

        @Override public String name() { return "account-events-to-kafka"; }
        @Override public String tag() { return "accounts"; }
        @Override public Delivery delivery() { return Delivery.AT_LEAST_ONCE; }

        @Override
        public void handle(ProjectedEvent<AccountActor.Event> event) throws Exception {
            kafka.send("account-events", event.persistenceId(), event.event()).get();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class AccountEventsToKafka(
        private val kafka: KafkaTemplate<String, AccountActor.Event>,
    ) : SpringProjection<AccountActor.Event> {

        override fun name() = "account-events-to-kafka"

        override fun tag() = "accounts"

        override fun delivery() = Delivery.AT_LEAST_ONCE

        override fun handle(event: ProjectedEvent<AccountActor.Event>) {
            kafka.send("account-events", event.persistenceId(), event.event()).get()
        }
    }
    ```

After a crash the last events are published again: consumers should ignore an event they have
already seen (the persistence id and sequence number identify it).

## Tables

The offsets live in `pekko_projection_offset_store` and `pekko_projection_management`.
`spring.actor.persistence.initialize-schema` creates them together with the journal tables
(`embedded` by default, `always`, or `never`). The scripts are in the `actor-projection` jar as
`io/github/seonwkim/actor/projection/schema-<db>.sql` for `h2`, `postgresql`, `mysql` and `mariadb`
(tested on H2, PostgreSQL 16 and MySQL 8.4); copy them into a Flyway or Liquibase migration for
production.

## Cluster

In cluster mode each projection runs as a [cluster singleton](../cluster/singletons.md) named
`projection-<name>`, so one node handles its events. When that node leaves, the projection starts on
another node and resumes from the stored offset.

## Not supported yet

- **Kafka as the source.** Projections read events stored by event-sourced actors. Reading a Kafka
  topic (`pekko-projection-kafka`) is not wrapped: consume it with Spring Kafka's `@KafkaListener`.
- **Several instances of one projection.** A projection reads its tag with one consumer. To spread a
  busy tag, split it into several tags (for example `accounts-0` to `accounts-3` by entity id) and
  declare one projection per tag.

## Related

- [Event-Sourced Actors](event-sourcing.md): where the tagged events come from
- [Cluster Singletons](../cluster/singletons.md): how a projection runs in a cluster
- [State in a Database](state-in-a-database.md): the read model side, with Spring Data
