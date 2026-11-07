# RFC 0007: Publish a `spring-boot-starter-actor-test` artifact

From row 12 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

Testing an app that uses the starter means wiring Pekko's `pekko-actor-testkit-typed` by hand and knowing
that the actor registries are `static`: the core tests call `ActorTypeRegistry.clear()` between contexts.
Spring for Apache Kafka (`spring-kafka-test`), Spring Cloud Stream (`spring-cloud-stream-test-binder`)
and Temporal (`temporal-testing`, wired by its starter) each ship a test artifact for this.

## Proposal

1. New Gradle modules `test` and `test-boot3`, published as `spring-boot-starter-actor-test` and
   `spring-boot-starter-actor-test_3` (names follow [RFC 0013](0013-artifact-naming.md) if it is accepted).
2. Contents:
    - `ActorTestKitAutoConfiguration`: when on the test classpath, defines an `ActorTestKit` bean that shares the
      app's `SpringActorSystem`, and `TestProbe` factory helpers.
    - `@ActorTestKitTest` meta-annotation for JUnit 5 that resets the registries after the context closes
      (needed until [RFC 0012](0012-context-scoped-registries.md) removes the static state).
    - `SpringActorAssertions`: `await().untilAsked(handle, command, expected)`-style helpers built on Awaitility.
    - A local-mode default (`spring.actor.pekko.actor.provider=local`) unless the test sets a provider.
3. Depends on `pekko-actor-testkit-typed_3` and `spring-boot-test` as `api`.

## Alternatives considered

- **Do nothing.** Users copy the setup from the examples.
- **Put the helpers in `core` under a `test` package.** Drags testkit and JUnit into production classpaths.
- **Gradle test fixtures of `core`.** Not consumable from Maven in a clean way, and not a convention Spring
  users know.

## Risks and rollout

- Two more artifacts to publish and keep in step; the BOM in [RFC 0009](0009-bom.md) keeps the versions aligned.
- The testkit API is Pekko's; we must not hide it, only wire it.
- Ship as 0.x "incubating" in the release notes so the helper API can change.

## Files it would change

- `settings.gradle.kts`
- `gradle.properties` (artifact ids)
- `test/build.gradle.kts` (new)
- `test/src/main/java/io/github/seonwkim/test/ActorTestKitAutoConfiguration.java` (new)
- `test/src/main/java/io/github/seonwkim/test/ActorTestKitTest.java` (new)
- `test/src/main/java/io/github/seonwkim/test/SpringActorAssertions.java` (new)
- `test/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new)
- `test-boot3/build.gradle.kts` (new)
- `mkdocs/docs/guides/testing.md` (new)
