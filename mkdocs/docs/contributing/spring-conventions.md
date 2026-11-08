# Spring conventions: how this starter compares

This page compares the starter with the conventions Spring users already know from
Spring Boot, Spring for Apache Kafka, Spring Data, Spring Cloud Stream, Micrometer,
Resilience4j and Temporal. Each row says what we keep and what we change. Every change
is written up as an RFC first, so the fix is agreed before any code is written.
See the [RFC index](rfcs/index.md).

!!! note "Snapshot"
    "This starter today" describes the 0.10.0 release (the last 0.x `main`), before the 1.0 refactor. The 1.0 changes are in the [changelog](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/CHANGELOG.md). The core configuration is
    [`ActorConfiguration`](https://github.com/seonWKim/actor-spring-boot-starter/blob/14d6a9805e0ccab6a14ec90520cb182a8fde7b7b/core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java),
    which is imported by
    [`@EnableActorSupport`](https://github.com/seonWKim/actor-spring-boot-starter/blob/14d6a9805e0ccab6a14ec90520cb182a8fde7b7b/core/src/main/java/io/github/seonwkim/core/EnableActorSupport.java).
    `core-boot3` is generated from `core` sources by the `syncAllBoot3Sources` Gradle task,
    so anything said about `core` also holds for `core-boot3`.

## How to read a row

Every row has four parts:

- **Reference libraries**: how the named libraries do it.
- **This starter today**: what the code does now.
- **Verdict**: one of
    - **Keep**: we already follow the convention, or we depart from it on purpose (the reason is given).
    - **Change**: we should follow the convention. The row links to exactly one RFC.
    - **Tracked**: the gap is already tracked by an open issue. The row links the issue and gets no second RFC.
- **Source**: a link to the official documentation of the reference.

Rows 1 to 17 cover the eight topics of
[issue #105](https://github.com/seonWKim/actor-spring-boot-starter/issues/105).
Rows 18 to 24 are other conventions found in the same libraries that apply to this
starter; each names the library it comes from.

## Summary

The RFC and issue links are in each row below.

| Row | Convention | Verdict |
|---|---|---|
| 1 | Auto-configuration registration (Boot 2 and 3) | Change (RFC 0001) |
| 2 | `@Enable*` annotations | Change (RFC 0002) |
| 3 | `@ConditionalOn*` back-off | Change (RFC 0003) |
| 4 | `*Customizer` beans | Change (RFC 0004) |
| 5 | `@ConfigurationProperties` and metadata | Tracked (#97) |
| 6 | Property namespace | Keep |
| 7 | `SmartLifecycle` phases and graceful shutdown | Tracked (#99) |
| 8 | Micrometer meters | Keep |
| 9 | Micrometer Observation | Change (RFC 0005) |
| 10 | Actuator health indicator | Tracked (#100) |
| 11 | Actuator endpoint | Change (RFC 0006) |
| 12 | Test artifact | Change (RFC 0007) |
| 13 | Test slice | Change (RFC 0008) |
| 14 | BOM | Change (RFC 0009) |
| 15 | Native image hints | Change (RFC 0010) |
| 16 | Starter and auto-configure module split | Keep |
| 17 | Optional dependencies | Keep |
| 18 | Global on/off switch (Spring Boot, Spring Cloud, Spring for Apache Kafka) | Change (RFC 0011) |
| 19 | Registries scoped to the application context (Spring for Apache Kafka) | Change (RFC 0012) |
| 20 | Starter artifact naming (Spring Boot) | Change (RFC 0013) |
| 21 | `FailureAnalyzer` for startup errors (Spring Boot) | Change (RFC 0014) |
| 22 | Components discovered as beans (Spring for Apache Kafka, Spring Cloud Stream, Temporal) | Keep |
| 23 | Framework events as `ApplicationEvent` (Spring for Apache Kafka) | Keep |
| 24 | Configuration ordering with `@AutoConfiguration(after = ...)` (Spring Boot) | Keep |

## 1. Registration and activation

### Row 1: Auto-configuration registration (Boot 2 and 3)

- **Reference libraries**: A Spring Boot starter registers its configuration classes so that adding the
  dependency is enough. Boot 2.0 to 2.6 read
  `org.springframework.boot.autoconfigure.EnableAutoConfiguration` keys in `META-INF/spring.factories`.
  Boot 2.7 added `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  and the `@AutoConfiguration` annotation (which also sets `proxyBeanMethods = false`). Boot 3.0 reads
  only the `.imports` file. Spring for Apache Kafka, Spring Data, Resilience4j and Temporal all ship
  their Boot integration this way: the Kafka and Spring Data auto-configurations live in Spring Boot
  itself, `resilience4j-spring-boot2`/`-3` and `temporal-spring-boot-autoconfigure` list theirs in these files.
- **This starter today**: `core` has no `src/main/resources` at all, so it has neither file. Nothing
  starts until the app adds `@EnableActorSupport`. `ActorConfiguration` and
  `PekkoManagementAutoConfiguration` are plain `@Configuration` classes. The `metrics` module has a
  `spring.factories` entry only, so Boot 3 never loads `ActorMetricsAutoConfiguration` on its own.
  The core tests pass without `@EnableActorSupport` only because the test application sits in the
  `io.github.seonwkim.actor` package, and component scanning picks `ActorConfiguration` up.
- **Verdict**: Change: [RFC 0001](rfcs/0001-auto-configuration-registration.md).
- **Source**: [Spring Boot: Creating your own auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html),
  [Spring Boot 2.7 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.7-Release-Notes),
  [Spring Boot 3.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide).

### Row 2: `@Enable*` annotations

- **Reference libraries**: In a Boot app the `@Enable*` annotation is optional, because the
  auto-configuration applies it for you. Boot's Kafka auto-configuration adds `@EnableKafka` when the app
  has not, so `@KafkaListener` works with the dependency alone. Boot enables Spring Data repositories for
  you; `@EnableJpaRepositories` is needed only to change the scanned packages or the defaults.
  Outside Boot (plain Spring Framework), the annotation remains the switch.
- **This starter today**: `@EnableActorSupport` is required. It is the only way to import
  `ActorConfiguration` and `PekkoManagementAutoConfiguration`.
- **Verdict**: Change: [RFC 0002](rfcs/0002-optional-enable-annotation.md). The annotation stays and is
  not deprecated; it becomes optional in a Boot app.
- **Source**: [Spring for Apache Kafka: `@KafkaListener` annotation](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html),
  [Spring Boot: Apache Kafka support](https://docs.spring.io/spring-boot/reference/messaging/kafka.html),
  [Spring Data JPA: Creating repository instances](https://docs.spring.io/spring-data/jpa/reference/repositories/create-instances.html).

## 2. Back-off and customization

### Row 3: `@ConditionalOn*` back-off

- **Reference libraries**: Every bean an auto-configuration defines backs off when the user defines one
  (`@ConditionalOnMissingBean`). Each class that touches an optional library is guarded with
  `@ConditionalOnClass`, so it never loads when the library is absent. Boot's Kafka auto-configuration
  backs off on `KafkaTemplate`, `ProducerFactory`, `ConsumerFactory` and the listener container
  factory. Boot also asks that `BeanPostProcessor` `@Bean` methods be `static`, so the configuration class
  is not created early.
- **This starter today**: `actorProperties`, `rootGuardianSupplierWrapper`, `actorSystemBuilder`,
  `actorSystem` and `topicManager` back off. `actorRegistrationBeanPostProcessor` and
  `clusterShardingInitializer` do not, and the post-processor method is not `static`.
  `PekkoManagementAutoConfiguration` is the only class with `@ConditionalOnClass`.
- **Verdict**: Change: [RFC 0003](rfcs/0003-conditional-back-off.md).
- **Source**: [Spring Boot: Condition annotations](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html),
  [Spring Framework: `BeanPostProcessor` `@Bean` methods](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html).

### Row 4: `*Customizer` beans

- **Reference libraries**: Boot lets an app tweak a bean it builds without replacing it: the app
  declares one or more `*Customizer` beans, and the auto-configuration applies them in `@Order` order
  through an `ObjectProvider`. Examples: `RestTemplateCustomizer` and `Jackson2ObjectMapperBuilderCustomizer`
  (Spring Boot); `DefaultKafkaProducerFactoryCustomizer` and `DefaultKafkaConsumerFactoryCustomizer`
  (Boot's Kafka support); `CircuitBreakerConfigCustomizer` (Resilience4j); `TemporalOptionsCustomizer`
  and `WorkerFactoryOptionsCustomizer` (Temporal); `MeterRegistryCustomizer` (Micrometer in Boot).
- **This starter today**: There is no customizer type. To change one thing (for example a Pekko setting
  computed at startup, or an extra root guardian behaviour) the app replaces the whole
  `SpringActorSystemBuilder` or `RootGuardianSupplierWrapper` bean.
- **Verdict**: Change: [RFC 0004](rfcs/0004-customizer-beans.md).
- **Source**: [Spring Boot: Apache Kafka support](https://docs.spring.io/spring-boot/reference/messaging/kafka.html),
  [Resilience4j: Spring Boot 2 getting started](https://resilience4j.readme.io/docs/getting-started-3),
  [Temporal: Spring Boot integration](https://docs.temporal.io/develop/java/spring-boot-integration),
  [Spring Boot: Metrics customization](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).

## 3. Configuration properties

### Row 5: `@ConfigurationProperties` and metadata

- **Reference libraries**: Properties bind to a typed `@ConfigurationProperties` class
  (`KafkaProperties`, `DataSourceProperties`, `TemporalProperties`, Resilience4j's
  `CircuitBreakerProperties`). The `spring-boot-configuration-processor` generates
  `META-INF/spring-configuration-metadata.json`, so IDEs complete and document the keys, and deprecated
  keys are marked.
- **This starter today**: `ActorProperties` is a plain class that binds `spring.actor.*` into a
  `Map<String, Object>` with the `Binder`. There is no metadata file.
- **Verdict**: Tracked: [#97](https://github.com/seonWKim/actor-spring-boot-starter/issues/97). Not
  re-specified here.
- **Source**: [Spring Boot: Type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html),
  [Spring Boot: Configuration metadata](https://docs.spring.io/spring-boot/specification/configuration-metadata/index.html).

### Row 6: Property namespace

- **Reference libraries**: Spring Boot asks third-party starters to keep their keys in their own
  namespace and not to use `spring`, `server` or `management`. Resilience4j uses `resilience4j.*`.
  Temporal's own starter uses `spring.temporal.*`, and Spring-team projects (Kafka, Cloud Stream) use
  `spring.kafka.*` and `spring.cloud.stream.*`.
- **This starter today**: All keys live under `spring.actor.*`, and `spring.actor.pekko.*` maps to the
  Pekko configuration.
- **Verdict**: Keep. Moving every key breaks every user's configuration, and Temporal shows that a
  `spring.*` prefix is accepted in practice. If the typed properties work in
  [#97](https://github.com/seonWKim/actor-spring-boot-starter/issues/97) wants to revisit the prefix,
  it should do so there, with deprecation metadata for the old keys.
- **Source**: [Spring Boot: Creating your own starter, configuration keys](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html),
  [Temporal: Spring Boot integration](https://docs.temporal.io/develop/java/spring-boot-integration).

## 4. Lifecycle

### Row 7: `SmartLifecycle` phases and graceful shutdown

- **Reference libraries**: Components that run work start and stop in a `SmartLifecycle` phase, not in
  a constructor or `destroy()`. Spring for Apache Kafka's listener containers are `SmartLifecycle`
  beans with `autoStartup` and a phase, and stop before the beans they use are destroyed. Boot's web
  server graceful shutdown runs in its own phase with a bounded timeout
  (`spring.lifecycle.timeout-per-shutdown-phase`).
- **This starter today**: The Pekko actor system starts when the `actorSystem` bean is built.
  `SpringActorSystem` implements `DisposableBean`, and `destroy()` calls `terminate()` and then waits on
  `getWhenTerminated()` with no timeout.
- **Verdict**: Tracked: [#99](https://github.com/seonWKim/actor-spring-boot-starter/issues/99). Not
  re-specified here.
- **Source**: [Spring Framework: Startup and shutdown callbacks](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html),
  [Spring for Apache Kafka: Message listener containers](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html),
  [Spring Boot: Graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).

## 5. Observability

### Row 8: Micrometer meters

- **Reference libraries**: Libraries register meters on the app's `MeterRegistry` bean, backed off
  with `@ConditionalOnClass(MeterRegistry.class)` and `@ConditionalOnBean(MeterRegistry.class)`. Spring
  for Apache Kafka binds client meters through `MicrometerConsumerListener`; Resilience4j ships
  `resilience4j-micrometer`.
- **This starter today**: The separate `metrics` module (an instrumentation agent) registers actor
  meters on the `MeterRegistry` through `ActorMetricsAutoConfiguration`, guarded with
  `@ConditionalOnClass`, `@ConditionalOnBean`, `@ConditionalOnMissingBean` and
  `spring.actor.metrics.enabled`.
- **Verdict**: Keep. Its missing Boot 3 registration file is part of
  [row 1](#row-1-auto-configuration-registration-boot-2-and-3); this change does not touch the metrics module.
- **Source**: [Spring Boot: Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html),
  [Spring for Apache Kafka: Monitoring](https://docs.spring.io/spring-kafka/reference/kafka/micrometer.html).

### Row 9: Micrometer Observation

- **Reference libraries**: Since Micrometer 1.10 and Boot 3, libraries instrument a unit of work once
  with the Observation API, and Boot turns it into a timer and a trace span. Spring for Apache Kafka
  enables it with `observationEnabled` on the template and the container (Boot properties
  `spring.kafka.template.observation-enabled`, `spring.kafka.listener.observation-enabled`) and lets the
  app plug in its own `ObservationConvention`.
- **This starter today**: No Observation is created for `tell`, `ask` or actor spawn, so a trace that
  enters an actor ends there.
- **Verdict**: Change: [RFC 0005](rfcs/0005-observation-api.md).
- **Source**: [Micrometer: Observation API](https://docs.micrometer.io/micrometer/reference/observation.html),
  [Spring for Apache Kafka: Monitoring](https://docs.spring.io/spring-kafka/reference/kafka/micrometer.html).

### Row 10: Actuator health indicator

- **Reference libraries**: A library with a remote dependency contributes a `HealthIndicator`
  (Spring Data's Redis, Mongo and Cassandra indicators; the Spring Cloud Stream binders indicator),
  enabled by `management.health.<name>.enabled`.
- **This starter today**: No health indicator. Cluster membership is visible only through Pekko
  Management HTTP when it is on the classpath.
- **Verdict**: Tracked: [#100](https://github.com/seonWKim/actor-spring-boot-starter/issues/100). Not
  re-specified here.
- **Source**: [Spring Boot: Actuator endpoints, health](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

### Row 11: Actuator endpoint

- **Reference libraries**: Libraries with runtime state expose a read-only Actuator `@Endpoint`:
  Spring Cloud Stream's `bindings` and `channels` endpoints; Spring Integration's `integrationgraph`;
  Resilience4j's `circuitbreakers` and `circuitbreakerevents`.
- **This starter today**: No endpoint. Actor and shard state is available only through Pekko APIs.
- **Verdict**: Change: [RFC 0006](rfcs/0006-actuator-endpoint.md).
- **Source**: [Spring Boot: Implementing custom endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html),
  [Spring Cloud Stream: Binding visualization and control](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/binding_visualization_control.html),
  [Resilience4j: Spring Boot 2 getting started](https://resilience4j.readme.io/docs/getting-started-3).

## 6. Testing

### Row 12: Test artifact

- **Reference libraries**: Libraries publish a test artifact with a ready-made fixture:
  `spring-kafka-test` with `@EmbeddedKafka` and `EmbeddedKafkaBroker`; `spring-cloud-stream-test-binder`
  with `InputDestination` and `OutputDestination`; `temporal-testing` with `TestWorkflowEnvironment`, which
  Temporal's starter wires in when `spring.temporal.test-server.enabled=true`.
- **This starter today**: No test artifact. Users wire Pekko's `pekko-actor-testkit-typed` by hand, and
  the core tests use `ActorTypeRegistry.clear()` to reset static state between contexts.
- **Verdict**: Change: [RFC 0007](rfcs/0007-test-artifact.md).
- **Source**: [Spring for Apache Kafka: Testing applications](https://docs.spring.io/spring-kafka/reference/testing.html),
  [Spring Cloud Stream: Testing](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/spring_integration_test_binder.html),
  [Temporal: Spring Boot integration](https://docs.temporal.io/develop/java/spring-boot-integration),
  [Apache Pekko: Testing](https://pekko.apache.org/docs/pekko/current/typed/testing.html).

### Row 13: Test slice

- **Reference libraries**: Boot test slices load one layer of the app and only the auto-configuration
  it needs: `@DataJpaTest` (Spring Data), `@JsonTest`, `@WebMvcTest`. A library can ship its own slice with
  `@ImportAutoConfiguration` and a `META-INF/spring/<annotation>.imports` file.
- **This starter today**: No slice. A test that needs actors uses `@SpringBootTest` and loads the
  whole application.
- **Verdict**: Change: [RFC 0008](rfcs/0008-test-slice.md).
- **Source**: [Spring Boot: Testing Spring Boot applications, auto-configured tests](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html).

## 7. Distribution

### Row 14: BOM

- **Reference libraries**: A project with more than one artifact publishes a bill of materials so
  users pick one version: `spring-cloud-dependencies` (Spring Cloud Stream), `micrometer-bom`,
  `resilience4j-bom`, `temporal-bom`, `spring-boot-dependencies`; Pekko itself ships `pekko-bom_<scala>`.
- **This starter today**: Three published artifacts (`spring-boot-starter-actor`,
  `spring-boot-starter-actor_3`, `spring-boot-starter-actor-metrics`) and no BOM. Users repeat the version
  on each.
- **Verdict**: Change: [RFC 0009](rfcs/0009-bom.md).
- **Source**: [Spring Boot: Dependency management](https://docs.spring.io/spring-boot/reference/using/build-systems.html),
  [Micrometer: Installing](https://docs.micrometer.io/micrometer/reference/installing.html).

### Row 15: Native image hints

- **Reference libraries**: Boot 3 libraries that use reflection, resources or proxies register a
  `RuntimeHintsRegistrar` in `META-INF/spring/aot.factories`, so GraalVM native images work. Spring for
  Apache Kafka ships `KafkaRuntimeHints`.
- **This starter today**: No hints. Pekko loads `reference.conf` resources and creates extensions,
  serializers and actor classes by reflection, so a native image cannot start the actor system.
- **Verdict**: Change: [RFC 0010](rfcs/0010-native-hints.md).
- **Source**: [Spring Framework: Ahead of time optimizations, runtime hints](https://docs.spring.io/spring-framework/reference/core/aot.html),
  [Spring Boot: GraalVM native images](https://docs.spring.io/spring-boot/reference/packaging/native-image/index.html).

### Row 16: Starter and auto-configure module split

- **Reference libraries**: Spring Boot describes a starter as two modules, an `-autoconfigure` module
  with the code and an empty `-starter` module with the dependencies, and allows one combined module when
  there is no need to separate them. Temporal ships both
  (`temporal-spring-boot-autoconfigure` and `temporal-spring-boot-starter`).
- **This starter today**: One combined module per Boot line (`core`, `core-boot3`).
- **Verdict**: Keep. Boot allows a combined module, and the starter has no optional feature large
  enough to need a separate auto-configure jar.
- **Source**: [Spring Boot: Creating your own starter](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).

### Row 17: Optional dependencies

- **Reference libraries**: Spring Boot asks auto-configure modules to declare the libraries they
  configure as optional, so the app chooses them, and to guard the code that uses them with
  `@ConditionalOnClass`.
- **This starter today**: Pekko Management and Cluster Bootstrap are `compileOnly`, and
  `PekkoManagementAutoConfiguration` is guarded by `@ConditionalOnClass` on their class names. The
  metrics module declares Spring Boot `compileOnly`.
- **Verdict**: Keep. The remaining guard gaps are in row 3.
- **Source**: [Spring Boot: Creating your own starter](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).

## 8. Other conventions found

### Row 18: Global on/off switch

Source libraries: Spring Boot, Spring Cloud, Spring for Apache Kafka.

- **Reference libraries**: A starter that starts something can be turned off with one property, without
  removing the dependency: `spring.cloud.discovery.enabled` and `spring.cloud.config.enabled` (Spring
  Cloud), `spring.jmx.enabled` (Spring Boot), `spring.kafka.listener.auto-startup` (Boot's Kafka support).
- **This starter today**: `spring.actor.pekko.management.enabled` turns off Pekko Management and
  `spring.actor.metrics.enabled` turns off metrics, but there is no switch for the actor system itself.
- **Verdict**: Change: [RFC 0011](rfcs/0011-enabled-switch.md).
- **Source**: [Spring Boot: Condition annotations, property conditions](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html),
  [Spring Cloud Commons](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/common-abstractions.html).

### Row 19: Registries scoped to the application context

Source library: Spring for Apache Kafka.

- **Reference libraries**: Spring for Apache Kafka keeps its listener endpoints in a
  `KafkaListenerEndpointRegistry` bean, one per application context. The Spring TestContext framework
  caches and reuses contexts, so several contexts can live in one JVM at once, and state kept in `static`
  fields leaks between them.
- **This starter today**: `ActorTypeRegistry` and `ShardedActorRegistry` keep actor factories in
  `static` maps. Tests call `ActorTypeRegistry.clear()` to isolate contexts.
- **Verdict**: Change: [RFC 0012](rfcs/0012-context-scoped-registries.md).
- **Source**: [Spring for Apache Kafka: `@KafkaListener` lifecycle management](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/kafkalistener-lifecycle.html),
  [Spring Framework: Context caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).

### Row 20: Starter artifact naming

Source library: Spring Boot.

- **Reference libraries**: Spring Boot reserves the `spring-boot` prefix for its own modules and asks
  third-party starters to put the project name first: `acme-spring-boot-starter`. Temporal follows it
  (`temporal-spring-boot-starter`), as does Resilience4j (`resilience4j-spring-boot3`).
- **This starter today**: The artifacts are `spring-boot-starter-actor`, `spring-boot-starter-actor_3`
  and `spring-boot-starter-actor-metrics`, under the `io.github.seonwkim` group.
- **Verdict**: Change: [RFC 0013](rfcs/0013-artifact-naming.md). The RFC weighs a rename against
  keeping the published names.
- **Source**: [Spring Boot: Creating your own starter, naming](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).

### Row 21: `FailureAnalyzer` for startup errors

Source library: Spring Boot.

- **Reference libraries**: Boot turns well-known startup failures into a short "Description / Action"
  report with a `FailureAnalyzer` registered in `spring.factories` (for example "Port 8080 was already in
  use", or a missing bean).
- **This starter today**: A Pekko startup failure (remoting port already bound, a bad `spring.actor.pekko.*`
  value, a missing seed node) reaches the user as a nested stack trace from the `actorSystem` bean.
- **Verdict**: Change: [RFC 0014](rfcs/0014-failure-analyzers.md).
- **Source**: [Spring Boot: Create your own FailureAnalyzer](https://docs.spring.io/spring-boot/how-to/application.html).

### Row 22: Components discovered as beans

Source libraries: Spring for Apache Kafka, Spring Cloud Stream, Temporal.

- **Reference libraries**: The user's handlers are ordinary beans the library finds in the context:
  `@KafkaListener` methods, `Function`/`Consumer` beans in Spring Cloud Stream, and workflow and activity
  beans found by Temporal's `spring.temporal.workers-auto-discovery.packages`.
- **This starter today**: Actors are `@Component` beans implementing `SpringActor` or
  `SpringShardedActor`, found by a `BeanPostProcessor`. They get dependency injection like any bean.
- **Verdict**: Keep.
- **Source**: [Spring for Apache Kafka: `@KafkaListener` annotation](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html),
  [Spring Cloud Stream: Functional binding names](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/functional-binding-names.html),
  [Temporal: Spring Boot integration](https://docs.temporal.io/develop/java/spring-boot-integration).

### Row 23: Framework events as `ApplicationEvent`

Source library: Spring for Apache Kafka.

- **Reference libraries**: Spring for Apache Kafka publishes container state changes as Spring
  application events (`ListenerContainerIdleEvent`, `ConsumerStartedEvent`, `ConsumerStoppedEvent`), so the
  app reacts with `@EventListener`.
- **This starter today**: In cluster mode, `SpringActorSystem` publishes Pekko cluster events through
  the `ApplicationEventPublisher`.
- **Verdict**: Keep.
- **Source**: [Spring for Apache Kafka: Application events](https://docs.spring.io/spring-kafka/reference/kafka/events.html).

### Row 24: Configuration ordering

Source library: Spring Boot.

- **Reference libraries**: An auto-configuration that depends on another declares it with
  `@AutoConfiguration(after = ...)` or `@AutoConfigureAfter`, never by relying on class names or scan order.
- **This starter today**: `ActorMetricsAutoConfiguration` declares
  `@AutoConfigureAfter(CompositeMeterRegistryAutoConfiguration.class)`. The core classes have no ordering
  yet because they are not auto-configurations; RFC 0001 adds the ordering they need.
- **Verdict**: Keep. The core ordering is part of [row 1](#row-1-auto-configuration-registration-boot-2-and-3).
- **Source**: [Spring Boot: Locating auto-configuration candidates](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).
