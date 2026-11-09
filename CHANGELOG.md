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
  promise. `ActorConfiguration`, `ActorProperties`, `ActorSystemShutdownLifecycle` and the Pekko Management
  configuration moved to `io.github.seonwkim.actor.autoconfigure`. The metrics instrumentation modules
  (`metrics.modules.*`) moved to `io.github.seonwkim.actor.metrics.internal.modules`, and
  `metrics.api.ActorContext.from(Object)` is removed: it took a Pekko `ActorCell` and only the agent used it.

- **No Pekko types in the public API.** Pekko is now an implementation detail. Each change below is
  listed with its replacement under "Migrating from 0.x". When you need a Pekko feature the starter
  doesn't wrap, `unwrap(Class)` on the actor system, a handle, a ref or a behavior context returns
  the engine's own object, and `SpringActorBehavior.wrap(behavior)` accepts a raw Pekko behavior.
  Code that uses them depends on Pekko and gets no compatibility promise.

### New

- **Typed configuration** with IDE completion and validation at startup:

  | Key | Sets Pekko key |
  |---|---|
  | `spring.actor.name` | `pekko.name` |
  | `spring.actor.provider` (`local`, `remote`, `cluster`) | `pekko.actor.provider` |
  | `spring.actor.remote.hostname`, `.port` | `pekko.remote.artery.canonical.hostname`, `.port` |
  | `spring.actor.cluster.seed-nodes`, `.roles` | `pekko.cluster.seed-nodes`, `.roles` |
  | `spring.actor.shutdown.timeout` (default `20s`) | (how long closing the context waits) |

  Raw keys keep working for everything else: every other key under `spring.actor` is passed to Pekko
  with the prefix removed, as in 0.x (`spring.actor.pekko.loglevel`, a dispatcher at `spring.actor.my-dispatcher`). When a raw key and a typed key
  set the same Pekko key to different values, the raw key wins and a warning names both.

- **Dispatchers on Spring executors.** `spring.actor.dispatchers.<name>.executor=<bean>` declares a
  dispatcher whose actors run on that `Executor` bean (a `ThreadPoolTaskExecutor`, a virtual-thread
  executor, ...); actors pick it with `withDispatcherFromConfig("<name>")`. The Spring context owns
  the executor's lifecycle.
- **Virtual threads.** `spring.actor.virtual-threads.enabled` (`auto`, `true`, `false`) runs the
  blocking dispatcher (`withBlockingDispatcher()`) on virtual threads, and
  `spring.actor.dispatchers.<name>.virtual: true` declares a named dispatcher on virtual threads. The
  default `auto` follows `spring.threads.virtual.enabled`: an application that already sets it to `true`
  now runs its blocking actors on virtual threads; set `spring.actor.virtual-threads.enabled=false` to
  keep platform threads. The default dispatcher stays on platform threads.
- **The application follows the actor system.** If the actor system terminates while the
  application runs (for example after this node is downed), the Spring context closes and the
  process exits with status 1, so Kubernetes or systemd restarts it. Set
  `spring.actor.shutdown.exit-process=false` to only close the context.
  `SpringActorSystem.whenTerminated()` completes when the actor system has terminated.
- `SpringActorSystem.nodeAddress()` and `SpringBehaviorContext.nodeAddress()` give this node's address
  (`pekko://<name>@<host>:<port>`, or `pekko://<name>` locally) without unwrapping Pekko.
- `SpringBehaviorContext` has timers (`startSingleTimer`, `startTimerAtFixedRate`, `cancelTimer`),
  `watch` with the `ActorTerminated` signal, and `messageAdapter`, so actors no longer wrap Pekko behaviors
  for them.
- `spring.actor.cluster.join-self` forms a one-node cluster without seed nodes, also on port 0.
- Actor and sharded actor registries belong to each Spring context. In 0.x they were static, so two
  contexts in one JVM (in tests, for example) spawned each other's actor beans.
- A spawn that cannot start (an unknown dispatcher, for example) now fails the returned stage
  with the cause. In 0.x it stopped the actor system and `spawnAndWait()` never returned.
- **Actuator health.** With Actuator present, `/actuator/health` has an `actorSystem` component and, in
  cluster mode, an `actorCluster` component that is `UP` only while this node is an `Up` member. Add
  `actorCluster` to the readiness group to gate traffic on membership. `management.health.actor.enabled`
  turns both off.
- **`getOrSpawn()` on the spawn builder** returns the running actor or spawns it with the builder's
  dispatcher, mailbox and supervision. `SpringActorSystem.getOrSpawn(Class, id)` always used the
  defaults, so a blocking actor got the default dispatcher.
- **Cluster metrics without the agent.** In cluster mode, `actor-metrics` with Micrometer adds the gauges
  `actor.cluster.members`, `actor.cluster.unreachable` and `actor.cluster.leader`, and the counter
  `actor.cluster.events` tagged by event type. The gauge `actor.cluster.singleton.host` shows the node
  that runs the singletons, and `actor.sharding.regions`, `actor.sharding.shards` and
  `actor.sharding.entities` (tagged by entity type) show Cluster Sharding.
- **`actors` Actuator endpoint.** In cluster mode, a read-only endpoint reports the members this node
  sees and each sharded entity type. It is exposed only when you add it to
  `management.endpoints.web.exposure.include`.
- **Tracing across `tell` and `ask`.** With an `ObservationRegistry` bean and a Micrometer tracer, a trace
  started by an HTTP request continues through `tell`, `ask` and sharded messages, also between nodes (W3C
  `traceparent` fields next to the message), and the trace id is in the MDC of the actor's log lines. Without an
  `ObservationRegistry`, messages are sent as before.
- **Actor metrics without the agent.** With `actor-metrics` and a Micrometer `MeterRegistry`, the actor
  system meters messages processed, processing time, failures, mailbox size and time, running actors,
  restarts and the default dispatchers' pools from hooks the starter owns: a behavior interceptor, an
  instrumented default mailbox and the dispatcher executors. Tags are `actor.class`, `dispatcher` and
  `system`, so 10,000 entities add no series; `spring.actor.metrics.path-tag-limit` opts into a capped
  `actor.path` tag. The Java agent is optional: when it is attached, it keeps the meters it records and
  the hooks add only the ones it lacks.
- **Cluster join without seed nodes or RBAC.** `spring.actor.cluster.join: discovery` with
  `spring.actor.cluster.discovery.method: jdbc` (new `actor-jdbc` module, an `actor_peer` table on the
  application's `DataSource`) or `redis` (new `actor-redis` module, keys with a TTL on its
  `RedisConnectionFactory`) finds peers through a store the application already runs, and Cluster Bootstrap
  forms the cluster. A node removes its entry on a graceful shutdown. A `PeerRegistry` bean of your own
  (`io.github.seonwkim.actor.spi`) replaces the built-in stores; `kubernetes-api`, `dns` and `config` select
  Pekko's own methods.
- **Grafana dashboard and Prometheus alert rules** in `monitoring/` for the actor, cluster and sharding meters.
  The Kubernetes example installs them with `scripts/monitoring.sh` (ServiceMonitor, `PrometheusRule`,
  dashboard ConfigMap for Grafana's sidecar). A test checks every metric they query against the documented
  meters.
- **Kubernetes without RBAC.** `example/kubernetes` has the overlays `discovery-jdbc` and `discovery-redis`:
  no Role or RoleBinding, NetworkPolicies that open only pod-to-pod 25520 and 7626 plus app to store, and a
  grace period long enough for a graceful leave. The README lists the network rules to ask for.

### Migrating from 0.x

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
