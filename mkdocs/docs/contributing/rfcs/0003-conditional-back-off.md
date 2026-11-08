# RFC 0003: Back off every bean and guard optional classes

From row 3 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

In [`ActorConfiguration`](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java):

- `actorRegistrationBeanPostProcessor()` has no `@ConditionalOnMissingBean`, and it is an instance method.
  A non-static `@Bean` method returning a `BeanPostProcessor` forces Spring to create `ActorConfiguration`
  early, and Spring logs "is not eligible for getting processed by all BeanPostProcessors".
- `clusterShardingInitializer(...)` has no `@ConditionalOnMissingBean`, so an app cannot replace the
  sharding start-up step (for example to delay it until the cluster is up).
- Only `PekkoManagementAutoConfiguration` has an `@ConditionalOnClass` guard. The core configuration has
  none, so if it is ever registered by an app that lacks Pekko cluster jars (they are `api` today, but a
  user can exclude them) it fails with `NoClassDefFoundError` instead of backing off.

## Proposal

1. Make `actorRegistrationBeanPostProcessor` `static` and add
   `@ConditionalOnMissingBean(name = "actorRegistrationBeanPostProcessor")`.
2. Introduce a named type for the sharding initializer, `ClusterShardingInitializer implements SmartInitializingSingleton`,
   and add `@ConditionalOnMissingBean(ClusterShardingInitializer.class)`. A bare `SmartInitializingSingleton`
   type would back off for any unrelated bean of that type.
3. Add `@ConditionalOnClass(org.apache.pekko.actor.typed.ActorSystem.class)` on the auto-configuration class.
4. Add a short "How to replace a bean" table to the docs: bean name, type, condition.

## Alternatives considered

- **Do nothing.** Users who need to change sharding start-up must fork the configuration.
- **`@ConditionalOnMissingBean(SmartInitializingSingleton.class)`.** Wrong: backs off whenever any library
  defines one.
- **Guard with `@ConditionalOnClass` by class name strings.** Needed only for classes that are not on the
  starter's own compile classpath; Pekko typed is, so the class literal is safe.

## Risks and rollout

- Low. Existing apps define none of these beans, so nothing backs off for them.
- An app that subclasses `ActorConfiguration` and overrides `actorRegistrationBeanPostProcessor` breaks
  when the method becomes static; call it out in the release notes.
- Ships with or before [RFC 0001](0001-auto-configuration-registration.md), with `ApplicationContextRunner`
  tests that each replaced bean wins.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java` (or `ActorAutoConfiguration.java` after RFC 0001)
- `core/src/main/java/io/github/seonwkim/core/shard/ClusterShardingInitializer.java` (new)
- `mkdocs/docs/guides/actor-registration-messaging.md`
