# RFC 0004: Add `SpringActorSystemBuilderCustomizer` beans

From row 4 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

To change one part of the actor system, an app must replace the whole `SpringActorSystemBuilder` bean
(built in [`ActorConfiguration.actorSystemBuilder`](https://github.com/seonWKim/spring-boot-starter-actor/blob/main/core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java))
and repeat what the starter does: read `ActorProperties`, set the root guardian and the event publisher.
Spring Boot, Boot's Kafka support, Resilience4j and Temporal let an app add a `*Customizer` bean instead.

## Proposal

1. Add a functional interface in `core`:

    ```java
    @FunctionalInterface
    public interface SpringActorSystemBuilderCustomizer {
        void customize(SpringActorSystemBuilder builder);
    }
    ```

2. In the builder bean method, take `ObjectProvider<SpringActorSystemBuilderCustomizer>` and apply
   `orderedStream()` after the starter's own settings, so `@Order` decides who wins.
3. Example:

    ```java
    @Bean
    SpringActorSystemBuilderCustomizer extraConfig() {
        return builder -> builder.withConfig(Map.of("pekko.actor.default-dispatcher.throughput", 10));
    }
    ```

4. Document that `withConfig` entries from a customizer are merged over the `spring.actor.*` properties.
   If `SpringActorSystemBuilder` cannot merge today, add a `withAdditionalConfig(Map)` method in the same change.

## Alternatives considered

- **Do nothing.** Replacing the builder keeps working, but it is all or nothing.
- **A `BeanPostProcessor` on the builder.** Works, but users have to know the bean name and the
  post-processor rules; Boot moved away from this for the same reason.
- **One customizer per concern** (config, guardian, serialization). More types to learn; one builder
  customizer covers all of them. Can be split later without breaking this one.

## Risks and rollout

- Additive: no customizer beans means today's behaviour.
- The customizer runs before `build()`, so it cannot see the built system. Document it.
- Ships in a minor release with an `ApplicationContextRunner` test for ordering.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/SpringActorSystemBuilderCustomizer.java` (new)
- `core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java` (or `ActorAutoConfiguration.java` after RFC 0001)
- `core/src/main/java/io/github/seonwkim/core/SpringActorSystemBuilder.java` (only if a merge method is needed)
- `core/src/main/java/io/github/seonwkim/core/impl/DefaultSpringActorSystemBuilder.java` (only if a merge method is needed)
- `mkdocs/docs/getting-started.md`
