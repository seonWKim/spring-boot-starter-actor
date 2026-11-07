# RFC 0014: Explain common start-up failures with `FailureAnalyzer`s

From row 21 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

When the actor system fails to start, the user sees a long nested stack trace that begins with
"Error creating bean with name 'actorSystem'". The real cause (a remoting port already bound, a malformed
`spring.actor.pekko.*` value) is several `Caused by:` lines down. Spring
Boot turns such failures into a short report with a `FailureAnalyzer`, for example "Web server failed to
start. Port 8080 was already in use. Action: ...".

## Proposal

Add analyzers in `core`, registered under `org.springframework.boot.diagnostics.FailureAnalyzer` in
`META-INF/spring.factories` (the key Boot 2 and Boot 3 both read):

| Analyzer | Matches | Description / Action |
|---|---|---|
| `ActorSystemBindFailureAnalyzer` | `java.net.BindException` from Pekko remoting | Port `<n>` for `pekko.remote.artery.canonical.port` is in use. Set `spring.actor.pekko.remote.artery.canonical.port` or stop the other process. |
| `ActorConfigFailureAnalyzer` | `com.typesafe.config.ConfigException` | Bad value at `<path>`. Check `spring.actor.<path>`. |
| `ActorSystemDisabledFailureAnalyzer` | `NoSuchBeanDefinitionException` for `SpringActorSystem` while `spring.actor.enabled=false` | The actor system is disabled ([RFC 0011](0011-enabled-switch.md)). Remove the property or the injection point. |

## Alternatives considered

- **Do nothing.** The information is in the stack trace.
- **Wrap exceptions in the bean methods with better messages.** Helps, but Boot's report format is what users
  already scan for, and analyzers do not change the exception types callers might catch.

## Risks and rollout

- Additive. An analyzer that does not match returns `null` and Boot falls back to the stack trace.
- Matching must be precise: a `BindException` from the web server must not be reported as a Pekko port problem.
  Match on the Pekko class in the cause chain.
- The third analyzer depends on RFC 0011; ship it with or after that RFC.

## Files it would change

- `core/src/main/java/io/github/seonwkim/core/diagnostics/ActorSystemBindFailureAnalyzer.java` (new)
- `core/src/main/java/io/github/seonwkim/core/diagnostics/ActorConfigFailureAnalyzer.java` (new)
- `core/src/main/java/io/github/seonwkim/core/diagnostics/ActorSystemDisabledFailureAnalyzer.java` (new)
- `core/src/main/resources/META-INF/spring.factories`
