# RFC 0010: Ship GraalVM native image hints for Boot 3

From row 15 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

A Boot 3 app using `spring-boot-starter-actor_3` cannot run as a GraalVM native image. Pekko reads
`reference.conf` resources from every jar and creates extensions, serializers, mailboxes, dispatchers and the
user's actor classes by reflection from configuration strings. None of this is visible to Spring AOT, and the
starter registers no `RuntimeHintsRegistrar`. Spring for Apache Kafka ships `KafkaRuntimeHints` for the same
reason.

## Proposal

1. Phase 1, spike: build the `example/simple` app with `nativeCompile` and the GraalVM tracing agent, and
   record the hints it needs. Write the result into this RFC before phase 2 starts.
2. Phase 2: add `ActorRuntimeHints implements RuntimeHintsRegistrar` in `core-boot3` only, registered in
   `META-INF/spring/aot.factories`:
    - resources: `reference.conf`, `version.conf` of the Pekko modules on the classpath;
    - reflection: the classes named in Pekko's default configuration (providers, serializers, mailboxes,
      dispatchers, extensions) and the starter's own `RootGuardian` and framework commands;
    - user actors: a `BeanFactoryInitializationAotProcessor` that registers reflection hints for every
      `SpringActor` and `SpringShardedActor` bean class and for Jackson-serialized messages
      (`JsonSerializable` / `CborSerializable` implementors).
3. Mark native support "experimental" in the docs until a native build runs in CI.

## Alternatives considered

- **Do nothing; document "no native".** Honest, and cheapest. Kept as the fallback if the spike shows Pekko's
  dynamic loading cannot be covered by static hints.
- **Ship the tracing agent's raw output as `reachability-metadata`.** Brittle across Pekko versions, and it
  does not cover user actor classes.

## Risks and rollout

- Pekko has no official GraalVM support. A Pekko upgrade can break the hints; the CI native build is the guard.
- `core-boot3` sources are generated from `core`, and `RuntimeHintsRegistrar` does not exist in Boot 2. The
  hints classes need a source set that `syncAllBoot3Sources` does not overwrite, for example
  `core-boot3/src/aot/java`.
- Native build time in CI is long; run it nightly, not on every PR.

## Files it would change

- `core-boot3/build.gradle.kts` (extra source set, `org.graalvm.buildtools.native` for the CI check)
- `core-boot3/src/aot/java/io/github/seonwkim/core/aot/ActorRuntimeHints.java` (new)
- `core-boot3/src/aot/java/io/github/seonwkim/core/aot/ActorBeanAotProcessor.java` (new)
- `core-boot3/src/aot/resources/META-INF/spring/aot.factories` (new)
- `.github/workflows/native.yml` (new)
- `mkdocs/docs/guides/native-image.md` (new)
