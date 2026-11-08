# Changelog

## 1.0.0 (unreleased)

### Breaking changes

- **Java 21+ and Spring Boot 3.5+ are required.** The Spring Boot 2 build (`spring-boot-starter-actor`,
  Java 11) is no longer published from this line. Boot 2 users stay on 0.x.
- The `jackson-bom` override is no longer needed: Spring Boot 3.5 manages Jackson 2.18+, which Pekko accepts.
  Remove it from your build.

- **New coordinates.** Artifacts are named after their module (`io.github.seonwkim:actor-*`):

  | 0.x | 1.0 |
  |---|---|
  | `spring-boot-starter-actor`, `spring-boot-starter-actor_3` | `actor-spring-boot-starter` |
  | `spring-boot-starter-actor-metrics` | `actor-metrics` (agent jar: `actor-metrics-<version>-agent.jar`) |
  | (cluster features were in the one artifact) | `actor-spring-boot-starter-cluster` |

  The old coordinates get relocation POMs for one release, so builds keep resolving.
- **New package root.** `io.github.seonwkim.core` is now `io.github.seonwkim.actor`, and
  `io.github.seonwkim.metrics` is now `io.github.seonwkim.actor.metrics`. Implementation classes moved
  to `..internal..` (`RootGuardian`, `RootGuardianSupplierWrapper`, `FrameworkCommands`,
  `DefaultShardingMessageExtractor`, the former `impl` and `utils` packages) and carry no compatibility
  promise. `ActorConfiguration`, `ActorProperties` and the Pekko Management configuration moved to
  `io.github.seonwkim.actor.autoconfigure`.

- **No Pekko types in the public API.** Pekko is now an implementation detail. Each change below is
  listed with its replacement under "Migrating from 0.x". When you need a Pekko feature the starter
  doesn't wrap, `unwrap(Class)` on the actor system, a handle, a ref or a behavior context returns
  the engine's own object, and `SpringActorBehavior.wrap(behavior)` accepts a raw Pekko behavior.
  Code that uses them depends on Pekko and gets no compatibility promise.

### Migrating from 0.x

1. Upgrade to Java 21 and Spring Boot 3.5 or later. Boot 2 applications stay on 0.x.
2. Remove the `com.fasterxml.jackson:jackson-bom` override from your build.
3. Depend on `io.github.seonwkim:actor-spring-boot-starter` (and `actor-metrics` if you use metrics).
   If you run with `pekko.actor.provider=cluster` (sharding, singletons, cluster events, management),
   depend on `actor-spring-boot-starter-cluster` instead; without it the application fails at startup
   and says so. If your code uses Pekko Cluster classes directly, declare
   `org.apache.pekko:pekko-cluster-sharding-typed_3` yourself: the starters no longer export it.
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
