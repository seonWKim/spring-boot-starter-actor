# RFC 0011: Add a `spring.actor.enabled` switch

From row 18 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

There is no way to keep the starter on the classpath and not start an actor system. Today that is done by
leaving out `@EnableActorSupport`; once [RFC 0001](0001-auto-configuration-registration.md) registers the
auto-configuration, it no longer works. Spring Boot (`spring.jmx.enabled`), Spring Cloud
(`spring.cloud.discovery.enabled`) and Boot's Kafka support (`spring.kafka.listener.auto-startup`) all offer a
property for this. The starter already has two narrower switches: `spring.actor.pekko.management.enabled` and
`spring.actor.metrics.enabled`.

## Proposal

1. Add `@ConditionalOnProperty(prefix = "spring.actor", name = "enabled", havingValue = "true", matchIfMissing = true)`
   to the core auto-configuration and to every auto-configuration that depends on `SpringActorSystem`
   (management, metrics, endpoint).
2. Document the key in the configuration metadata. The metadata file itself is part of
   [#97](https://github.com/seonWKim/spring-boot-starter-actor/issues/97); this RFC only adds one key to it.
3. Keep the two narrower switches as they are.

Example, a batch job that shares the app's jar but needs no actors:

```yaml
spring:
  actor:
    enabled: false
```

## Alternatives considered

- **Do nothing.** Users exclude the auto-configuration with
  `spring.autoconfigure.exclude=io.github.seonwkim.core.ActorAutoConfiguration`. That works, but the class
  name is an implementation detail and they must list every actor auto-configuration.
- **Use `spring.actor.pekko.enabled`.** Everything under `spring.actor.pekko.*` is passed to Pekko as
  configuration; a starter key there would leak into Pekko's config tree.

## Risks and rollout

- `spring.actor.enabled` is bound into the Pekko config map today, because `ActorProperties` binds all of
  `spring.actor.*`. The value lands at the top-level `enabled` key in Pekko's config, which Pekko ignores, but
  the binding should skip it once #97 introduces typed properties.
- Beans that inject `SpringActorSystem` fail when it is off; that is the expected outcome and the
  failure message should say so (see [RFC 0014](0014-failure-analyzers.md)).
- Ships with RFC 0001.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java` (or `ActorAutoConfiguration.java` after RFC 0001)
- `core/src/main/java/io/github/seonwkim/core/management/PekkoManagementAutoConfiguration.java`
- `metrics/src/main/java/io/github/seonwkim/metrics/autoconfigure/ActorMetricsAutoConfiguration.java` (separate PR)
- `mkdocs/docs/getting-started.md`
