# RFC 0006: Add a read-only `actors` Actuator endpoint

From row 11 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

Operators cannot see the actor system from Actuator. Spring Cloud Stream (`bindings`), Spring Integration
(`integrationgraph`) and Resilience4j (`circuitbreakers`) all expose their runtime state as an endpoint.
Here, the only view is Pekko Management HTTP, which is optional, listens on its own port, and covers the
cluster only. Health is a separate topic, tracked in
[#100](https://github.com/seonWKim/actor-spring-boot-starter/issues/100), and is not part of this RFC.

## Proposal

1. Add `ActorsEndpoint`, annotated `@Endpoint(id = "actors")`, with read operations only:
    - `GET /actuator/actors`: system name, mode (local or cluster), registered actor classes from the
      actor type registry, registered sharded entity type keys and their roles.
    - `GET /actuator/actors/cluster`: self address, member list with status and roles, unreachable members
      (cluster mode only; 404 otherwise).
2. Register it in an `ActorEndpointAutoConfiguration` with `@ConditionalOnClass(Endpoint.class)`,
   `@ConditionalOnAvailableEndpoint` and `@ConditionalOnBean(SpringActorSystem.class)`.
3. Not exposed over HTTP by default, like every Actuator endpoint other than `health`; users opt in with
   `management.endpoints.web.exposure.include=actors`.
4. No write operations (no stop or spawn). A later RFC can add them if there is a need.

## Alternatives considered

- **Do nothing.** Users keep using Pekko Management HTTP or logs.
- **Proxy Pekko Management's routes.** Ties Actuator to an optional dependency and its port.
- **Expose live actor paths.** Unbounded output and a cardinality problem on large systems. Classes and
  entity type keys are bounded.

## Risks and rollout

- Information exposure: the endpoint lists class names and cluster addresses. It is off over HTTP by
  default and follows Actuator security.
- `core` compiles against Boot 2.7 and `core-boot3` against Boot 3.2; the `@Endpoint` API is the same in both.
- Actuator becomes a `compileOnly` dependency of `core`.
- Ships after [RFC 0001](0001-auto-configuration-registration.md), since it is itself an auto-configuration.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/actuate/ActorsEndpoint.java` (new)
- `core/src/main/java/io/github/seonwkim/core/actuate/ActorEndpointAutoConfiguration.java` (new)
- `core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `core/build.gradle.kts`
- `core-boot3/build.gradle.kts`
- `mkdocs/docs/examples/monitoring.md`
