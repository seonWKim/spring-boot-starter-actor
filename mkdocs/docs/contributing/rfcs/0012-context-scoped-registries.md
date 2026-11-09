# RFC 0012: Keep actor registries in the application context, not in static fields

From row 19 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

[`ActorTypeRegistry`](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/core/src/main/java/io/github/seonwkim/core/ActorTypeRegistry.java)
and
[`ShardedActorRegistry`](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/core/src/main/java/io/github/seonwkim/core/shard/ShardedActorRegistry.java)
hold actor factories in `static` maps. The configuration's Javadoc says this avoids circular dependencies.
But the Spring TestContext framework caches contexts, so one JVM often holds several contexts at once.
Each context's actors overwrite the others' entries, and a cached context can create actors from beans of
a context that is already closed. The core tests work around it with `ActorTypeRegistry.clear()`. Spring for
Apache Kafka keeps the equivalent state in a `KafkaListenerEndpointRegistry` bean, one per context.

## Proposal

1. Make both registries instance classes, defined as beans by the auto-configuration:
   `ActorTypeRegistry actorTypeRegistry()` and `ShardedActorRegistry shardedActorRegistry()`, both
   `@ConditionalOnMissingBean`.
2. The registration `BeanPostProcessor` gets the registry lazily through `ObjectProvider<ActorTypeRegistry>`
   (it is `static` after [RFC 0003](0003-conditional-back-off.md), so it cannot take beans as arguments
   eagerly). This keeps the cycle the static maps were avoiding broken.
3. `RootGuardian` and `SpringActorSystem` receive the registry instance through the builder instead of
   calling static methods.
4. Keep the static methods for one minor release as deprecated delegates to a "current" instance, so code
   that calls `ActorTypeRegistry.register(...)` directly keeps working, and log a warning when more than one
   context is active.

## Alternatives considered

- **Do nothing and keep `clear()`.** Fine for this repo's tests; user test suites with context caching hit
  hard-to-explain failures.
- **Key the static maps by context id.** Keeps the static state and adds a lookup key everyone must pass.
  Moves the problem rather than removing it.

## Risks and rollout

- The largest change in this set: it touches actor creation paths. Needs the full core test suite plus a
  test with two live contexts in one JVM.
- Public static API change. Deprecate first, remove in a later minor release.
- Ship after RFC 0001 and RFC 0003.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/ActorTypeRegistry.java`
- `core/src/main/java/io/github/seonwkim/core/shard/ShardedActorRegistry.java`
- `core/src/main/java/io/github/seonwkim/core/RootGuardian.java`
- `core/src/main/java/io/github/seonwkim/core/SpringActorSystem.java`
- `core/src/main/java/io/github/seonwkim/core/SpringActorSystemBuilder.java`
- `core/src/main/java/io/github/seonwkim/core/impl/DefaultSpringActorSystemBuilder.java`
- `core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java` (or `ActorAutoConfiguration.java` after RFC 0001)
