# Migrating from 0.x

1.0 requires **Java 21+ and Spring Boot 3.5+**. Spring Boot 2 users stay on the 0.x line, which keeps
its coordinates (`spring-boot-starter-actor` for Boot 2, `spring-boot-starter-actor_3` for Boot 3).

The main steps for a Boot 3 application:

1. Upgrade to Java 21 and Spring Boot 3.5 or later.
2. Remove any `com.fasterxml.jackson:jackson-bom` override you added for Pekko.
3. Depend on `io.github.seonwkim:actor-spring-boot-starter` (and `actor-metrics` if you use metrics).
   Cluster applications (sharding, singletons, cluster events) depend on
   `actor-spring-boot-starter-cluster` instead. The 0.x coordinates are relocated to the new ones for this release. The starters no longer export Pekko: code that uses
   Pekko classes directly declares `org.apache.pekko:pekko-actor-typed_3` (or the cluster module) itself.
4. Replace the package `io.github.seonwkim.core` with `io.github.seonwkim.actor`, and
   `io.github.seonwkim.metrics` with `io.github.seonwkim.actor.metrics`. Classes under
   `io.github.seonwkim.actor.internal` are implementation details with no compatibility promise.
5. Handlers return `SpringActorBehavior` instead of Pekko's `Behavior`: `Behaviors.same()` becomes
   `SpringActorBehavior.same()`, and a raw Pekko behavior is kept with `SpringActorBehavior.wrap(behavior)`.
6. Move `spring.actor.pekko.*` keys that have a typed equivalent to the typed keys
   (`spring.actor.name`, `provider`, `remote.*`, `cluster.*`). See [Configuration](guides/configuration.md).

The full list of changes, with the migration for each (supervision, sharding, topics, receptionist,
routers), is in the [changelog](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/CHANGELOG.md).
