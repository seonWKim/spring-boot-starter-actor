# Changelog

## 1.0.0 (unreleased)

### Breaking changes

- **Java 21+ and Spring Boot 3.5+ are required.** The Spring Boot 2 build (`spring-boot-starter-actor`,
  Java 11) is no longer published from this line. Boot 2 users stay on 0.x.
- The `jackson-bom` override is no longer needed: Spring Boot 3.5 manages Jackson 2.18+, which Pekko accepts.
  Remove it from your build.

- **New coordinates.** Artifacts are named after their module (`io.github.seonwkim:actor-*`):

  | 0.x | 1.0 |
  |---|---|
  | `spring-boot-starter-actor`, `spring-boot-starter-actor_3` | `actor-spring-boot-starter` |
  | `spring-boot-starter-actor-metrics` | `actor-metrics` (agent jar: `actor-metrics-<version>-agent.jar`) |

  The old coordinates get relocation POMs for one release, so builds keep resolving.
- **New package root.** `io.github.seonwkim.core` is now `io.github.seonwkim.actor`, and
  `io.github.seonwkim.metrics` is now `io.github.seonwkim.actor.metrics`. Implementation classes moved
  to `..internal..` (`RootGuardian`, `RootGuardianSupplierWrapper`, `FrameworkCommands`,
  `DefaultShardingMessageExtractor`, the former `impl` and `utils` packages) and carry no compatibility
  promise. `ActorConfiguration`, `ActorProperties` and the Pekko Management configuration moved to
  `io.github.seonwkim.actor.autoconfigure`.

### Migrating from 0.x

1. Upgrade to Java 21 and Spring Boot 3.5 or later. Boot 2 applications stay on 0.x.
2. Remove the `com.fasterxml.jackson:jackson-bom` override from your build.
3. Depend on `io.github.seonwkim:actor-spring-boot-starter` (and `actor-metrics` if you use metrics).
4. Replace `io.github.seonwkim.core` with `io.github.seonwkim.actor` and `io.github.seonwkim.metrics` with
   `io.github.seonwkim.actor.metrics` in your imports. If you extended `RootGuardian` or used other classes
   now under `internal`, import them from `io.github.seonwkim.actor.internal`.
