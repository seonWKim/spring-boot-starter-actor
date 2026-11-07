# RFC 0002: Make `@EnableActorSupport` optional in a Boot app

From row 2 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

[`@EnableActorSupport`](https://github.com/seonWKim/spring-boot-starter-actor/blob/main/core/src/main/java/io/github/seonwkim/core/EnableActorSupport.java)
is the only way to start the actor system. In Boot, `@Enable*` annotations for Kafka and Spring Data are
optional because the auto-configuration applies them. Once [RFC 0001](0001-auto-configuration-registration.md)
registers the auto-configuration, the annotation needs a defined meaning: today it `@Import`s the
configuration classes directly, which would load them twice through two paths.

## Proposal

1. Keep `@EnableActorSupport`. Do not deprecate it (out of scope for issue #105 and not needed).
2. In a Boot app, it becomes a no-op marker: the auto-configuration starts the actor system with or without it.
   Change its `@Import` to a small `ImportSelector` that imports `ActorAutoConfiguration` only when Boot
   auto-configuration is not active (no `EnableAutoConfiguration` on the context). That keeps it the switch for
   plain Spring Framework apps, the same split as `@EnableKafka`.
3. Update the Javadoc and getting-started guide: "Optional with Spring Boot; required without it."

## Alternatives considered

- **Leave the `@Import` as is.** Spring processes a configuration class once even when it is reached by
  `@Import` and by auto-configuration, but the import path runs it before the user's configuration, so
  `@ConditionalOnMissingBean` can miss user beans defined later. This is the bug Boot warns about.
- **Deprecate the annotation.** Breaks nothing at once but sends every user a warning for a line that is
  harmless. Out of scope for #105.
- **Turn the annotation into the only switch (no auto-configuration).** Contradicts row 1.

## Risks and rollout

- Plain-Spring (non-Boot) users are unaffected: the selector still imports the configuration.
- Boot users with the annotation see no change in behaviour.
- Ships in the same release as RFC 0001, with tests for the annotation with and without Boot
  auto-configuration.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/EnableActorSupport.java`
- `core/src/main/java/io/github/seonwkim/core/ActorSupportImportSelector.java` (new)
- `mkdocs/docs/getting-started.md`
- `README.md`
