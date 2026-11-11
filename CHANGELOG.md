# Changelog

## Unreleased

### Breaking changes

- **One failure meter.** The metrics agent no longer records `actor.errors` (tag `error.type`). Failures are
  counted once, as `actor.message.failures` (tags `actor.class`, `dispatcher`, `system`), by the starter's
  hooks with or without the agent; the bundled dashboard and alerts already use it. If you built your own
  dashboards or alerts on `actor_errors_total`, query `actor_message_failures_total` instead.
- **A lookup that times out fails.** `exists`, `find` and the deprecated `get` used to turn a timeout into
  `false` or `null`, so a busy actor system reported running actors as missing. The stage now fails with a
  `TimeoutException`. Their default timeout is 3 s, like the other calls, instead of 100 ms;
  `ActorConstants.DEFAULT_QUERY_TIMEOUT` is deprecated (#188).

### Features

- **Startup checks for actor beans (#249).** The starter warns when an actor bean has a mutable instance
  field, which every actor of its class shares across threads, or is a Spring AOP proxy, whose advice
  (`@Transactional`, `@Async`) never reaches the handlers. Injected fields don't count.
  `spring.actor.validation.actor-beans=fail` stops startup instead, `off` turns the checks off. The actors
  guide has a new "What not to do in an actor bean" section.
- **Blue-green activation with an epoch (#232).** With `spring.actor.generation.color` and
  `spring.actor.generation.store` (`jdbc` or `redis`), the active color is a record in the shared store with
  an epoch that only grows. `POST /actuator/generation` activates a color with a compare-and-set on the
  expected epoch and an operation ID: a competing deploy gets `409`, a retried operation gets its first
  result, and a request without an authenticated user gets `401`. Every attempt publishes an
  `ActivationEvent`, a log line and the `actor.generation.activations` meter. `DeploymentGeneration` stamps
  work with the epoch and refuses stale work with `StaleGenerationException`. New SPI: `GenerationStore`
  (`JdbcGenerationStore`, the `actor_generation` table; `RedisGenerationStore`; `InMemoryGenerationStore`
  and `GenerationStoreContract` in `actor-test`). The endpoint is read-only unless
  `management.endpoint.generation.access=unrestricted`. The Kubernetes example's `blue-green.sh activate`
  and `rollback` go through it.
- **Warm-up health group (#231).** In cluster mode, `/actuator/health/warmup` (component `actorWarmup`) is
  `UP` once this node is an `Up` member, the cluster has `management.health.actor.warmup.min-members` `Up`
  members (default 1) and the `jdbc` or `redis` discovery store answers. It does not depend on traffic
  readiness, so a blue-green pipeline gates on it before activation. The Kubernetes example's
  `blue-green.sh` runs the stages `deploy` (warm-up, then ready), `activate`, `verify` and `retire`; `switch` and
  `finish` are renamed to `activate` and `retire`.
- **Retryable vs permanent delivery outcomes (#233).** A failed reliable send's `ReliableDeliveryException`
  has a `reason()`: `STALE_GENERATION`, `TIMEOUT` and `UNAVAILABLE` are retryable, `PAYLOAD_CONFLICT` (the
  same idempotency key with a different payload) is permanent and is not retried. A handler signals a stale
  generation by throwing `StaleGenerationException`. Retries use full jitter, and
  `spring.actor.delivery.retry.*` sets the default retry policy. The reliable delivery guide gives the HTTP
  mapping. A message whose class keeps `Object`'s `hashCode` is not checked for conflicts. The
  `ReliableDeliveryException` constructor without a reason is deprecated.
- **Builder parity (#190).** The spawn builder takes `withDispatcher(DispatcherConfig)`, and the child
  builder takes `withBlockingDispatcher()`, `withDefaultDispatcher()`, `withDispatcherFromConfig(name)` and
  `withDispatcherSameAsParent()`. Topics get `createAsync()` and `getOrCreateAsync()`, which return a
  `CompletionStage` instead of blocking; the chat example's room actor uses it with `pipeToSelf`.
- **`AskBuilder.failFastOnError()`** fails an ask at once with the handler's exception instead of letting it
  time out; in Kotlin, `awaitAsk(command, timeout, failFast = true)`. It covers `SpringActorBehavior`
  handlers and event-sourced command handlers, local or sharded. Off by default (#189).
- `ActorCluster.Builder.classLoader(node, loader)` in `actor-test`: run one node with its own class loader, for
  example on the next version of a message class, to test a rolling update in one JVM.
- Tests: an event-sourced actor as a sharded entity recovers its state on another node when its node leaves,
  and nodes on two versions of a message (one adds a field) read each other's messages. The Event-Sourced
  Actors and Deployments guides now say so.
- **`SpringActorSystem.find(Class, id)`** returns `CompletionStage<Optional<SpringActorHandle<C>>>`.
  `get(Class, id)`, which completed with `null`, is deprecated. `SpringChildActorBuilder.get()` is
  deprecated too: `handle.child(ChildClass, id).get()` already returns an `Optional` (#188).
- **`SpringBehaviorContext.actorId()` and `getChild(ChildClass.class, id)`.** Handlers read their actor's
  id without capturing it in `create`, and find a child by class and id. `getChild(String)`, which took
  the internal `<ClassName>:<id>` name, is deprecated (#188).
- **One rule for a taken id:** `spawn()` is for a new actor, `getOrSpawn()` reuses a running one. A child
  `spawn()` on a taken id still returns the running child but logs a deprecation warning; in 2.0 it fails
  like a top-level `spawn()`. A child `getOrSpawn()` now checks and spawns in one step (#188).
- **Reliable delivery.** Opt-in at-least-once delivery to actors and sharded entities. The
  `SpringReliableDelivery` bean builds senders whose `send(message[, idempotencyKey])` retries with backoff
  until the receiver acks; the receiver wraps its behavior with `ReliableDelivery.consumer(...)` and runs a
  key's handler once within `spring.actor.delivery.idempotency-ttl` (default 10m). See the Reliable
  Delivery guide.
- **GraalVM native image hints.** The modules register runtime hints in `META-INF/spring/aot.factories`:
  Pekko's `reference.conf` files, the starter's classes Pekko creates by name, `AskCommand`'s reply address,
  and the schema files. Your own serialized messages still need hints, and so do Pekko's own classes. See
  [Running in Production](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/production/#native-images-graalvm).
- **BOM.** `io.github.seonwkim:actor-bom` lists every module at one version: import it with Gradle's
  `platform(...)` or Maven's `<scope>import</scope>` and leave the version off each module. The 0.x
  `spring-boot-starter-actor-bom` (0.0.1, Spring Boot 2.7) is not relocated to it.
- Kotlin: a behavior DSL, `behavior<Command>(actorContext) { onMessage<Greet> { msg -> ...; same() } }`, with
  `onSignal<PostStop> { }`, a `state` factory, `withSupervisionStrategy` and `withMdc`. Handlers run with the
  behavior context (or the state object) as `this`.
- Kotlin: suspend helpers for child actors (`parent.child<WorkerActor, _>().awaitSpawn()`, `awaitGetOrSpawn()`,
  `awaitExists()`, and `parent.child<WorkerActor, _>("id").awaitGet()`), and `kotlin.time.Duration` overloads for the child builder and reference,
  `SupervisionStrategy.restart().withLimit` and the behavior context's timers.
- `spring.actor.cluster.singleton.role`: run cluster singletons only on nodes with this role, without the raw
  `spring.actor.pekko.cluster.singleton.role` key.

### Fixes

- **Hardened CI workflows (#247).** Third-party actions (`softprops/action-gh-release`,
  `graalvm/setup-graalvm`) are pinned to a commit SHA with the version in a comment. `publish.yml` passes its
  `tag` input through the environment and rejects anything but `vMAJOR.MINOR.PATCH`
  (`scripts/release/check-tag.sh`) before it uses it. Every workflow sets read-only `permissions:` at the
  top, with write only on the jobs that push. `.github/dependabot.yml` opens one weekly pull request for
  Gradle and one for GitHub Actions.
- **The Kubernetes example serves Actuator on its own port (#246).** Actuator (health, metrics, info,
  prometheus) was on the application port 8080, which the NetworkPolicy opens to every source. It now runs on
  `management.server.port: 8081`: the probes, the ServiceMonitor, the PodMonitor, the scrape annotation and
  `blue-green.sh` use it, and the NetworkPolicy opens 8081 only to the `monitoring` namespace. The production
  guide recommends the same. If you copied the manifests, move your probes and monitors to the new port.
- **`ClusterMetricsTest` no longer flakes on the leader (#242).** It asserted that the node that joins later
  is not the leader before both nodes saw each other Up. Pekko's leader is the member with the lowest address,
  so once the cluster settled the later node led whenever the OS gave it the lower port. The test now waits
  until both nodes see both members Up and asserts that exactly one of them leads.
- **Blue-green pods stay inside the NetworkPolicy (#235).** In the Kubernetes example, `blue-green.sh deploy`
  labelled the green pods `app=spring-actor-green`, which no NetworkPolicy selected, so green's remoting and
  management ports were open to any pod and its egress was unrestricted. `deploy` now copies blue's
  NetworkPolicies for green first, so each color's cluster ports open only to pods of that color.
- `spring.actor.pekko.management.http.port=0` works with Cluster Bootstrap: with `jdbc` or `redis` discovery
  a node registers the port its Pekko Management server bound, not the configured one, and Bootstrap takes
  the bound port as the node's own contact point. The JDBC and Redis discovery tests bind it at 0 (#229).
- `ActorCluster` (actor-test) no longer races other processes for ports: every node binds port 0, node 1
  joins itself, and the others join it at the address it bound. Before, it picked free ports first and
  bound them later, and a port could be taken in between ("Address already in use") (#224).
- A node with `spring.actor.cluster.join-self` starts with Pekko Management on the classpath. Cluster
  Bootstrap used to start too, and startup failed for want of a discovery method (#224).
- Every worker of a router pool gets its own id (`worker-<uuid>`); they all shared one before. A failed
  child spawn is told apart from "already exists" by a status, not by comparing message text, and throws
  `IllegalStateException` (a `RuntimeException`, as before) naming the child's context id (#190).
- `ask` no longer changes the command you pass: it sends a shallow copy that carries the reply address, so
  one command instance can be asked twice or from several threads. `AskCommand.withReplyTo`, which the
  framework no longer calls, is deprecated (#189).
- A bean can spawn an actor in its constructor or `@PostConstruct` even when Spring hasn't created the
  actor's bean yet. It used to fail with "No factory registered for class" depending on bean creation
  order; the logging example didn't start (#214).
- A single raw value for a Pekko list key (`spring.actor.pekko.cluster.seed-nodes=pekko://a@h:1`) works like
  `seed-nodes[0]` instead of failing with "expecting list but got string".
- `spring.actor.metrics.*` no longer leaks into the Pekko configuration as a raw `metrics.*` key.
- Configuration metadata covers `management.health.actor.enabled` and the `spring.actor.dispatchers.*` sub-keys
  (`executor`, `virtual`, `throughput`), so IDEs complete them.
- The `actor.cluster.singleton.host` gauge honours the singleton role: it is `1` on the oldest `Up` member
  with that role, not on the oldest member overall.
- `stop()` on a cluster singleton's handle throws `UnsupportedOperationException` instead of stopping this
  node's proxy, which left the node unable to reach the singleton.
- Spawning a cluster singleton in local mode fails with a message that names `spring.actor.provider=cluster`
  and the cluster starter.
- The metrics example no longer passes `-DACTOR_METRICS_DEBUG=true`, a flag no code reads.
- **Spawning by id an actor that needs a custom context fails clearly.** `getOrSpawn(Class, id)` and
  `withId(...)` without `withContext(...)` failed with a `ClassCastException` from inside the actor
  system. They now fail with an `IllegalArgumentException` naming the context type and `withContext(...)`
  (#188).
- **`find` and `exists` see cluster singletons** that this node spawned. They used to look only at the
  root guardian's children (#188).
- **`getOrSpawn` no longer races on a new id.** It checked whether the actor existed and then spawned it,
  so two callers asking for the same new id at once could make one fail with "Could not spawn". The root
  guardian now does both in one step, and every caller gets the same actor (#182).
- **Proxied actor beans are registered under their declared class.** An actor bean that Spring wraps in a
  class proxy (for example with `@Transactional`) was registered under the proxy class, so spawning it by
  its own class failed with "No factory registered". Local and sharded actors now use the declared class
  (#183).

### Docs

- **The discovery and lease stores are a trust boundary (#236).** Cluster Formation has a "Securing the
  discovery store" section: whoever can write `actor_peer`, `actor_lease` or the `actor:peer:` and
  `actor:lease:` keys decides which hosts new nodes join and who holds a lease. It gives a Redis ACL user and
  database grants limited to the starter's keys and tables. Linked from the leases section and the production
  checklist.
- The AI agents guide has Kotlin tabs for each of its code samples.
- Kubernetes example: the base ConfigMap's Cluster Bootstrap settings now reach
  `pekko.management.cluster.bootstrap` (they were ignored under `coordinated-shutdown`), pods get a 45s grace
  period, every overlay uses Pekko's default ports (remoting 25520, management 7626), and the `prod` overlay
  renders again (#195).
- Javadoc: the `FrameworkCommand` example declares `SpringActor<ParentActor.Command>` (one type parameter),
  and `ShardingStrategy.Hashed` gives the exact shard function (#197).
- Examples: logging, cluster, chat and synchronization use the typed `spring.actor.*` keys; the logging
  order actor runs on the blocking dispatcher; virtual-threads spawns one actor without blocking WebFlux;
  chat's `bootRun` builds the metrics agent; synchronization increments respond once applied; the
  persistence example's `EventSourcedOrderActor` is now `EventLogOrderActor` (`/api/orders/eventlog`) (#196).

### New

- **The starter auto-configures; `@EnableActorSupport` is optional.** Every actor configuration is registered
  in `AutoConfiguration.imports`, so a `@SpringBootApplication` with the starter on the classpath starts the
  actor system. Your own `SpringActorSystem`, builder, root guardian, topic manager, receptionist and
  store beans replace the starter's. The cluster configuration applies only in cluster mode.
- `spring.actor.enabled=false` keeps the starter on the classpath without starting an actor system or any
  actor auto-configuration (metrics, health, management, streams, persistence, projections).

### Upgrading from 1.0.0

- An application that has the starter on the classpath but no `@EnableActorSupport` now starts an actor
  system. If it should not, set `spring.actor.enabled=false`.
- `@EnableActorSupport` keeps working and can be removed from a Spring Boot application. It is still
  needed where auto-configuration is off (a context without `@EnableAutoConfiguration`); `@ActorTest`
  includes it.
- `ask` sends a copy of the command, so the actor receives a different instance than the one you passed
  (equal fields, its own reply address). Code that compared the received command by identity with the
  sent one must compare fields instead (#189).
- `spring.autoconfigure.exclude` now accepts the actor configurations by class name, for example
  `io.github.seonwkim.actor.autoconfigure.cluster.ClusterConfiguration`.

## 1.0.0 (2026-11-10)

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
- **Split brain resolver and leases.** `spring.actor.cluster.downing.strategy` selects Pekko's split brain
  resolver (`keep-majority`, the default with `join: discovery`, `lease-majority`, `static-quorum`,
  `keep-oldest`, `down-all`); a cluster without downing logs a warning. `spring.actor.cluster.lease.store`
  (`jdbc`: an `actor_lease` table, `redis`, or your `LeaseStore` bean) backs `lease-majority`,
  `singleton.use-lease` and `sharding.use-lease`, so a singleton or shard runs on one node only. Redis leases
  can be lost on a Redis failover; see the guide.
- **`actor-test` module** for application tests: the `@ActorTest` slice starts the actor system and the
  actor beans without web, data or the rest of the application; `ActorCluster` (and the JUnit 5
  `ActorClusterExtension`) starts several nodes in one JVM on free ports; `PeerRegistryContract` and
  `LeaseStoreContract` are the test suites the built-in stores pass, to run against your own store, with
  `InMemoryPeerRegistry` and `InMemoryLeaseStore` fakes.

- **Replicated maps, counters and sets.** In cluster mode, the `ReplicatedData` bean hands out
  `ReplicatedMap`, `ReplicatedCounter` and `ReplicatedSet` (Pekko Distributed Data CRDTs) with
  `CompletionStage` reads and writes and `local`, `majority` or `all` consistency. Every node starts the
  replicator with the actor system.
- **Reactor bridge (`actor-streams`).** An `ActorStreams` bean gives a `Mono` of an ask, `askEach` to feed
  an actor from a `Flux` with at most `parallelism` asks in flight (a slow actor slows the producer instead
  of filling its mailbox), and `messages(...)` for a `Flux` of the messages actors send to a reference, with
  a bounded buffer or an acknowledgement per message. Cancelling the `Flux` stops the reference.
- **SSE, WebSocket and gRPC bridges.** A controller returns the `messages` `Flux` for Server-Sent Events
  (Spring MVC or WebFlux); `ActorWebSocketHandler` connects each WebFlux WebSocket session to actors;
  `ActorGrpc.respond` sends a `Flux` or `Mono` to a gRPC response stream with flow control. A client
  disconnect stops the actor-side reference. The chat example uses the WebSocket bridge.
- **Event-sourced actors (`actor-persistence`).** `SpringEventSourcedBehavior` builds an actor from
  command handlers that return effects (`persist`, `none`, `thenReply`, ...) and event handlers that change
  the state, with snapshots every N events and tags. The journal and snapshots are Pekko Persistence JDBC
  tables on the application's `DataSource` (scripts for H2, PostgreSQL, MySQL and MariaDB), created per
  `spring.actor.persistence.initialize-schema`. R2DBC is not supported yet.
- **Projections (`actor-projection`).** A `SpringProjection` bean reads the events of one tag into the
  application's database (Spring Data, `JdbcTemplate`) or anywhere else, such as Spring Kafka. Its offset is
  stored on the application's `DataSource`, in the handler's transaction (`EXACTLY_ONCE`, the default) or
  after it (`AT_LEAST_ONCE`), so it resumes after a restart. In cluster mode it runs as a cluster singleton.
- **Kotlin support.** The new `actor-kotlin` module adds `suspend` functions (`awaitAsk`, `AskBuilder.await()`,
  `awaitSpawn`, `awaitGetOrSpawn`, `awaitGet`, `awaitExists`), reified helpers (`actor<A, _>()`,
  `sharded<A, _>()`, `actorBehavior<C>()`, `unwrap<X>()`) and `kotlin.time.Duration` timeouts on top of
  the Java API. Every `actor-api` package now declares non-null defaults (`@NonNullApi`, `@NonNullFields`),
  so Kotlin with `-Xjsr305=strict` sees the API's nullability. See the Kotlin guide and `example/kotlin`.

### Fixed

- **Cluster events during startup reach `@EventListener` beans.** Events published while the context
  started, such as the node's own `MemberUp`, used to be lost. The starter now subscribes once the context
  has refreshed, and Pekko replays the current state as events.

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
