# Migrating from 0.x

1.0 renames the artifacts and packages, requires **Java 21+ and Spring Boot 3.5+**, and removes Pekko
types from the public API. Spring Boot 2 users stay on the 0.x line.

## New coordinates

| 0.x | 1.0 |
|---|---|
| `spring-boot-starter-actor`, `spring-boot-starter-actor_3` | `actor-spring-boot-starter` |
| `spring-boot-starter-actor-metrics` | `actor-metrics` |
| (cluster features were in the one artifact) | `actor-spring-boot-starter-cluster` |

The old coordinates publish relocation POMs for 1.0.0, so a build that still names them resolves to the
new artifacts and prints a relocation warning. Switch to the new names; the relocations are not kept
after this release.

=== "Gradle"

    ```groovy
    implementation 'io.github.seonwkim:actor-spring-boot-starter:1.0.0'
    // cluster applications use this instead:
    // implementation 'io.github.seonwkim:actor-spring-boot-starter-cluster:1.0.0'
    ```

=== "Maven"

    ```xml
    <dependency>
      <groupId>io.github.seonwkim</groupId>
      <artifactId>actor-spring-boot-starter</artifactId>
      <version>1.0.0</version>
    </dependency>
    ```

## Step by step

1. Upgrade to Java 21 and Spring Boot 3.5 or later. Boot 2 applications stay on 0.x.
2. Remove the `com.fasterxml.jackson:jackson-bom` override from your build.
3. Depend on `io.github.seonwkim:actor-spring-boot-starter` (and `actor-metrics` if you use metrics).
   If you run with `pekko.actor.provider=cluster` (sharding, singletons, cluster events, management),
   depend on `actor-spring-boot-starter-cluster` instead; without it the application fails at startup
   and says so. If your code uses Pekko Cluster classes directly, declare
   `org.apache.pekko:pekko-cluster-sharding-typed_3` yourself, and code that uses any Pekko class
   (through `unwrap(...)` or `SpringActorBehavior.wrap(...)`) declares `org.apache.pekko:pekko-actor-typed_3`:
   the starters no longer export Pekko.
4. Replace `io.github.seonwkim.core` with `io.github.seonwkim.actor` and `io.github.seonwkim.metrics` with
   `io.github.seonwkim.actor.metrics` in your imports. If you extended `RootGuardian` or used other classes
   now under `internal`, import them from `io.github.seonwkim.actor.internal`.
5. Behaviors: handlers return `SpringActorBehavior`, not Pekko's `Behavior`.
   - `Behaviors.same()`, `Behaviors.stopped()`, `Behaviors.unhandled()` → `SpringActorBehavior.same()`,
     `.stopped()`, `.unhandled()`. Keep a raw Pekko behavior with `SpringActorBehavior.wrap(behavior)`.
   - `onSignal(PreRestart.class, ...)` and `onSignal(PostStop.class, ...)` take
     `io.github.seonwkim.actor.PreRestart` and `io.github.seonwkim.actor.PostStop`.
   - `SupervisorStrategy.restart()` / `.resume()` / `.stop()` → `SupervisionStrategy.restart()` /
     `.resume()` / `.stop()`, with `restart().withLimit(maxRetries, within)` as before.
     `withSupervisionStrategy` takes the new type.
6. `MailboxConfig`, `DispatcherConfig` and `TagsConfig` keep their factory methods
   (`MailboxConfig.bounded(n)`, `DispatcherConfig.blocking()`, `TagsConfig.of(...)`, ...). They are
   now sealed records and enums, so their Pekko conversions (`toMailboxSelector()`, `toProps()`,
   `applyToProps(...)`) and `describe()` are gone; `TagsConfig.getTags()` → `tags()`.
7. Refs and handles: `getUnderlying()` → `unwrap(ActorRef.class)`, `getPath()` → `path()`,
   `SpringActorSystem.getRaw()` → `unwrap(ActorSystem.class)`, a behavior context's `getUnderlying()`
   → `unwrap(ActorContext.class)`. `AskCommand.replyTo` is a `SpringActorRef`. Messages that carry a
   reply address declare a `SpringActorRef<T>` field; it serializes across the cluster.
   `SpringActorRefBuilder` is removed: use `ask(...)` on the handle.
8. `SpringActorSystem`, `SpringActorSpawnBuilder`, `SpringChildActorBuilder` and
   `SpringChildActorReference` are interfaces. Code that constructed them must get them from the
   actor system instead. `ActorSpawner` and `ActorTypeRegistry` moved to `internal`.
9. Cluster: `getCluster()`, `getClusterSharding()` and `getClusterSingleton()` are removed. Use
   `isClusterMode()`, or `unwrap(Cluster.class)` (and the other Pekko classes) for raw access.
10. Cluster events: `ClusterEventBehavior.ClusterDomainWrappedEvent` is removed. Listen for
    `io.github.seonwkim.actor.cluster.ClusterEvent` or one of its records
    (`@EventListener void on(ClusterEvent.MemberUp event)`). Member addresses are strings and
    statuses are the `MemberStatus` enum.
11. Sharded actors:
    - `EntityTypeKey<T> typeKey()` → `ShardedEntityType<T> entityType()`, built with
      `ShardedEntityType.of(Command.class, "name")`. Keep the same name so running clusters route
      to the same entities.
    - `create(...)` returns `SpringActorBehavior<T>` built with `SpringActorBehavior.builder(Command.class, ctx)`;
      `SpringShardedActorBehavior` is removed. Sharded entities get supervision, signals and MDC
      like local actors.
    - `extractor()` → `sharding()`, returning `ShardingStrategy.hashed(numberOfShards)`. The shard
      ids are the same as `DefaultShardingMessageExtractor` produced.
    - `SpringShardedActorContext.getEntityContext()` is removed; `getEntityId()` stays.
    - `SpringShardedActorHandle` is an interface; `getUnderlying()` → `unwrap(EntityRef.class)`.
      Set the ask timeout with `sharded(...).withId(id).withTimeout(d).get()`;
      `SpringShardedActorHandle.builder(...)` is removed.
12. Topics: `topicManager.topic(Message.class).withName("news")` →
    `topicManager.topic(TopicName.of(Message.class, "news"))`; `withTimeout`, `create()` and
    `getOrCreate()` are unchanged. `SpringTopicManager` and `SpringTopicRef` are interfaces you get as
    beans. `SpringTopicRef.getTopicName()` → `name().name()`, `getUnderlying()` → `unwrap(ActorRef.class)`.
13. Receptionist: `ServiceKey<T>` → `ServiceName<T>`, and `SpringReceptionist.serviceKey(Message.class, "id")`
    → `ServiceName.of(Message.class, "id")`. `register` and `deregister` take any `SpringActorRef`, so the
    `ActorRef` overload is removed: pass the actor's handle or `ctx.self()`. In a behavior,
    `ctx.registerWithReceptionist(ServiceName.of(...))`. `SpringReceptionist` is an interface you get as
    a bean; `SpringReceptionistSubscription.getServiceKey()` → `serviceName()`, and `getHelperPath()` is removed.
14. Routers: `RoutingStrategy.roundRobin()`, `.random()`, `.broadcast()` and `.consistentHashing(n)` are
    unchanged. The `router.strategy` classes (`RoundRobinRoutingStrategy`, ...) are removed, and
    `RoutingStrategy` is a sealed type: `getName()` and `applyToPool(...)` are gone. Code that branched on
    `getName()` can switch on the type.

## After migrating

- Move raw `spring.actor.pekko.*` keys that have a typed equivalent to the typed keys
  (`spring.actor.name`, `provider`, `remote.*`, `cluster.*`). See the
  [configuration reference](guides/operations/configuration.md).
- New in 1.0 and worth a look: [health checks and metrics](guides/operations/monitoring.md),
  [graceful shutdown](guides/operations/production.md), [`@ActorTest`](guides/testing.md) and the
  [Kotlin API](guides/kotlin.md).

The full list of changes is in the
[changelog](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/CHANGELOG.md).
