# RFCs

An RFC proposes one change to the starter and is agreed before any code is written. Most RFCs here come
from a "Change" row in the [Spring conventions comparison](../spring-conventions.md); each such row links
exactly one RFC.

Items already tracked by an issue do not get an RFC:
[#97](https://github.com/seonWKim/actor-spring-boot-starter/issues/97) (configuration properties),
[#99](https://github.com/seonWKim/actor-spring-boot-starter/issues/99) (shutdown) and
[#100](https://github.com/seonWKim/actor-spring-boot-starter/issues/100) (health).

To write a new RFC, copy the [template](template.md).

## How an RFC moves

1. Open a PR that adds the RFC with status **Proposed** and a line in the table below.
2. Discuss on the PR. When the owner agrees, change the status to **Accepted** (or **Rejected**, with the reason).
3. Implement in one or more later PRs. The last one sets the status to **Implemented** and links itself.

## List

| RFC | Title | Status |
|---|---|---|
| [0001](0001-auto-configuration-registration.md) | Register the starter as a Spring Boot auto-configuration | Proposed |
| [0002](0002-optional-enable-annotation.md) | Make `@EnableActorSupport` optional in a Boot app | Proposed |
| [0003](0003-conditional-back-off.md) | Back off every bean and guard optional classes | Proposed |
| [0004](0004-customizer-beans.md) | Add `SpringActorSystemBuilderCustomizer` beans | Proposed |
| [0005](0005-observation-api.md) | Instrument `tell` and `ask` with Micrometer Observation | Proposed |
| [0006](0006-actuator-endpoint.md) | Add a read-only `actors` Actuator endpoint | Proposed |
| [0007](0007-test-artifact.md) | Publish a `spring-boot-starter-actor-test` artifact | Proposed |
| [0008](0008-test-slice.md) | Add an `@ActorTest` test slice | Proposed |
| [0009](0009-bom.md) | Publish a bill of materials | Proposed |
| [0010](0010-native-hints.md) | Ship GraalVM native image hints for Boot 3 | Proposed |
| [0011](0011-enabled-switch.md) | Add a `spring.actor.enabled` switch | Proposed |
| [0012](0012-context-scoped-registries.md) | Keep actor registries in the application context, not in static fields | Proposed |
| [0013](0013-artifact-naming.md) | Decide on the starter's artifact names | Proposed |
| [0014](0014-failure-analyzers.md) | Explain common start-up failures with `FailureAnalyzer`s | Proposed |

## Suggested order

RFC 0003 and RFC 0011 should land with or before RFC 0001, because registering the auto-configuration
changes what starts by default. RFC 0013 should be decided before RFC 0007 and RFC 0009 publish new
artifacts. The others are independent.
