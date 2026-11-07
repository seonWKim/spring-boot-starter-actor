# RFC 0013: Decide on the starter's artifact names

From row 20 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

Spring Boot's guide for third-party starters says not to start module names with `spring-boot`, even under
another group id, and to put the project name first (`acme-spring-boot-starter`), because Spring may later
ship an official starter with that name. Temporal (`temporal-spring-boot-starter`) and Resilience4j
(`resilience4j-spring-boot3`) follow it. This project publishes `spring-boot-starter-actor`,
`spring-boot-starter-actor_3` and `spring-boot-starter-actor-metrics` under `io.github.seonwkim`. The `_3`
suffix also reads like a Scala version suffix (Pekko uses `_3` for Scala 3), not a Boot version.

## Proposal

Rename in one release, with Maven relocation for the old coordinates:

| Today | Proposed |
|---|---|
| `spring-boot-starter-actor` | `pekko-actor-spring-boot2-starter` |
| `spring-boot-starter-actor_3` | `pekko-actor-spring-boot3-starter` |
| `spring-boot-starter-actor-metrics` | `pekko-actor-spring-boot-metrics` |
| (new, RFC 0007) | `pekko-actor-spring-boot2-starter-test`, `pekko-actor-spring-boot3-starter-test` |
| (new, RFC 0009) | `pekko-actor-spring-boot-bom` |

1. Publish the old artifact ids one last time as POM-only artifacts with
   `<distributionManagement><relocation>` pointing to the new ids, so Maven and Gradle builds keep resolving.
2. Keep Java package names (`io.github.seonwkim.*`) and the repository name unchanged; only Maven
   coordinates move.
3. The exact names are the owner's call; the table is a starting point.

## Alternatives considered

- **Keep the current names (do nothing).** No migration for users. The risk Boot describes is real but
  small, since the group id differs. A valid outcome of this RFC is "Rejected: keep the names", recorded here.
- **Rename only new artifacts.** Leaves a mixed naming scheme in the BOM.
- **Use `-boot3` instead of `_3` but keep the `spring-boot-starter-` prefix.** Fixes the Scala confusion only.

## Risks and rollout

- Every user changes a dependency line. Relocation POMs soften this, but Gradle prints a warning only.
- Search results, README badges and the docs site all point at the old names; update them in the same release.
- Decide this before RFC 0007 and RFC 0009 publish new artifacts, so they are not renamed twice.

## Files it would change

- `gradle.properties` (artifact ids)
- `build.gradle.kts` (relocation publications)
- `.github/workflows/publish.yml`
- `README.md`
- `mkdocs/docs/getting-started.md`
- `mkdocs/docs/index.md`
