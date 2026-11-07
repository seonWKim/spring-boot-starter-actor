# RFC 0005: Instrument `tell` and `ask` with Micrometer Observation

From row 9 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

A trace that reaches an actor stops there. Nothing creates a Micrometer `Observation` for sending a
message, so Boot 3 apps get no span for `ask`, no timer per message type from the tracing side, and no
trace context in the actor's MDC. Spring for Apache Kafka solves the same problem for producers and
consumers with `observationEnabled`. The `metrics` module records meters through bytecode
instrumentation, but it does not create Observations and does not propagate trace context.

## Proposal

1. Add an optional `ObservationRegistry` to `SpringActorSystem` (set by the auto-configuration from an
   `ObjectProvider<ObservationRegistry>`, default `ObservationRegistry.NOOP`).
2. Wrap `SpringActorHandle.tell` and `ask` in an Observation named `spring.actor.message`, with low-cardinality
   keys `actor.class`, `message.class`, `kind` (`tell`, `ask`) and an `ActorObservationConvention` the app can
   replace, as Kafka does with `KafkaTemplateObservationConvention`.
3. Carry the trace context inside the framework envelope, and open a child Observation around the
   behaviour's message handling, so the actor's log lines carry the trace id.
4. Turn it on with `spring.actor.observation.enabled` (default `false` in the first release).
5. Guard with `@ConditionalOnClass(ObservationRegistry.class)`. Observation needs Micrometer 1.10+, so the
   feature is effectively Boot 3 (`core-boot3`); on Boot 2 the guard leaves it off.

## Alternatives considered

- **Do nothing.** Users who trace HTTP to actor flows keep losing the span.
- **Do it in the `metrics` agent.** The agent instruments Pekko classes; Observation is an application-level
  API that users configure with beans. Mixing the two makes both harder to switch off.
- **OpenTelemetry API directly.** Boot 3 standardises on Micrometer Observation with an OTel bridge;
  going direct skips Boot's configuration.

## Risks and rollout

- Overhead per message. Off by default; measure with the existing benchmark before turning it on by default.
- Context must not leak into the wrong message when an actor processes many senders' messages. Restore the
  previous scope after each message; cover with a test using two concurrent senders.
- Cardinality: never use actor path or entity id as a tag.
- `core` and `core-boot3` share sources; the code must compile without Micrometer Observation on the Boot 2
  classpath (keep it behind a separate class loaded only under the condition).

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/SpringActorHandle.java`
- `core/src/main/java/io/github/seonwkim/core/SpringActorSystem.java`
- `core/src/main/java/io/github/seonwkim/core/observation/ActorObservationConvention.java` (new)
- `core/src/main/java/io/github/seonwkim/core/observation/DefaultActorObservationConvention.java` (new)
- `core/src/main/java/io/github/seonwkim/core/observation/ActorObservationAutoConfiguration.java` (new)
- `core/build.gradle.kts` (`compileOnly` Micrometer observation)
- `core-boot3/build.gradle.kts`
- `mkdocs/docs/guides/logging.md`
