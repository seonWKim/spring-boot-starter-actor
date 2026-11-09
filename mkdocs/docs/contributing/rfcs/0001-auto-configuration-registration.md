# RFC 0001: Register the starter as a Spring Boot auto-configuration

From row 1 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

Adding `spring-boot-starter-actor` to a Boot app starts nothing. `core` has no
`META-INF/spring.factories` and no `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
so Boot never sees
[`ActorConfiguration`](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java)
or
[`PekkoManagementAutoConfiguration`](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/core/src/main/java/io/github/seonwkim/core/management/PekkoManagementAutoConfiguration.java).
The app has to add `@EnableActorSupport`. Both classes are plain `@Configuration`, so:

- `@ConditionalOnMissingBean` on their bean methods is evaluated in import order, not after the user's
  configuration, which is the order Boot guarantees only for auto-configurations;
- they are subject to component scanning: an app whose base package is `io.github.seonwkim` (as the core
  tests are) picks them up a second way;
- `@Configuration` proxies bean methods (`proxyBeanMethods = true`), which Boot avoids in
  auto-configurations.

The `metrics` module has a `spring.factories` entry only. Boot 3 ignores
`EnableAutoConfiguration` keys in `spring.factories`, so a Boot 3 app never loads
`ActorMetricsAutoConfiguration` unless it imports it.

## Proposal

1. Turn `ActorConfiguration` into `ActorAutoConfiguration`, annotated `@AutoConfiguration`. Keep
   `ActorConfiguration` as a deprecated subclass or alias for one minor release, so apps that `@Import` it
   still compile.
2. Annotate `PekkoManagementAutoConfiguration` with
   `@AutoConfiguration(after = ActorAutoConfiguration.class)`, since it injects `SpringActorSystem`.
3. Add `core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   listing both classes. Boot 2.7+ and Boot 3 read this file. `core-boot3` gets it through
   `syncResourcesToBoot3`.
4. Add `core/src/main/resources/META-INF/spring.factories` with the same two classes under
   `org.springframework.boot.autoconfigure.EnableAutoConfiguration`, for Boot 2.0 to 2.6 users. Boot 2.7
   reads both files and removes duplicates; Boot 3 ignores the key.
5. Add the same `.imports` file to the `metrics` module next to its `spring.factories`, and annotate
   `ActorMetricsAutoConfiguration` with `@AutoConfiguration(after = CompositeMeterRegistryAutoConfiguration.class, before = ActorAutoConfiguration.class)`.
   This is a metrics-module change and ships as its own PR.
6. `@EnableActorSupport` keeps working; [RFC 0002](0002-optional-enable-annotation.md) defines what it means
   once this RFC lands.

Before:

```java
@SpringBootApplication
@EnableActorSupport
public class App { }
```

After (both forms work):

```java
@SpringBootApplication
public class App { }
```

## Alternatives considered

- **Do nothing.** Keeps the one-annotation opt-in, but every Boot user meets a starter that does not
  start, and Boot 3 users silently lose metrics.
- **Only `spring.factories`.** Works on Boot 2 only; Boot 3 ignores it.
- **Only the `.imports` file.** Works on Boot 2.7+ and 3. Drops Boot 2.0 to 2.6, which `core` does not
  test against today. Acceptable if the owner decides 2.7 is the floor; then step 4 is dropped.

## Risks and rollout

- Apps that have the starter on the classpath but deliberately did not add `@EnableActorSupport` will now
  start an actor system. Ship together with the global switch from
  [RFC 0011](0011-enabled-switch.md) (`spring.actor.enabled=false`) and call it out in the release notes.
- Apps that define their own `SpringActorSystem` keep it: the bean methods already back off. The
  remaining back-off gaps are fixed by [RFC 0003](0003-conditional-back-off.md), which should land first
  or together.
- Ship in a minor release (0.11.0), with `ApplicationContextRunner` tests for: no annotation, annotation
  present, user-defined `SpringActorSystem`, management classes absent.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/ActorConfiguration.java`
- `core/src/main/java/io/github/seonwkim/core/ActorAutoConfiguration.java` (new)
- `core/src/main/java/io/github/seonwkim/core/management/PekkoManagementAutoConfiguration.java`
- `core/src/main/java/io/github/seonwkim/core/EnableActorSupport.java`
- `core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new)
- `core/src/main/resources/META-INF/spring.factories` (new)
- `metrics/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new, separate PR)
- `metrics/src/main/java/io/github/seonwkim/metrics/autoconfigure/ActorMetricsAutoConfiguration.java` (separate PR)
- `mkdocs/docs/getting-started.md`
