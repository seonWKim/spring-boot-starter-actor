# Logging with MDC and Tags

Logs from actors are harder to follow than logs from request threads: one actor handles messages for
many requests, and one request can pass through several actors. The starter gives you three ways to put
context on every log line an actor writes:

- **Static MDC**: key-value pairs fixed when the actor is spawned (a user id, a service name).
- **Dynamic MDC**: key-value pairs computed from each message (an order id, a request id).
- **Tags**: labels on an actor (`worker`, `critical`), written to the `pekkoTags` field.

MDC (Mapped Diagnostic Context) is SLF4J's map of values that the log pattern can print with `%X{key}`.

Inside actors, log through the actor's logger, `ctx.getLog()` (`ctx.log` in Kotlin): its lines carry the
fields below and the actor's MDC.

## Fields Pekko adds

Every line written through `ctx.getLog()` already carries these MDC fields:

| Field | Value | Example |
|---|---|---|
| `pekkoSource` | The actor's path | `pekko://my-app/user/...` |
| `pekkoAddress` | The actor system's address | `pekko://my-app@10.0.0.5:25520` |
| `pekkoTags` | The actor's tags, comma-separated | `worker,high-priority` |
| `sourceActorSystem` | The actor system's name | `my-app` |

## Static MDC

Set values that stay the same for the actor's whole life when you spawn it:

=== "Java"

    ```java
    actorSystem.actor(SessionActor.class)
            .withId("session-" + sessionId)
            .withMdc(MdcConfig.of(Map.of(
                    "sessionId", sessionId,
                    "userId", userId)))
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    actorSystem.actor<SessionActor, _>()
        .withId("session-$sessionId")
        .withMdc(MdcConfig.of(mapOf("sessionId" to sessionId, "userId" to userId)))
        .awaitSpawn()
    ```

Every line the actor logs then carries `sessionId` and `userId`. `MdcConfig.of` needs at least one entry;
`MdcConfig.empty()` means none.

## Dynamic MDC

Compute values from each message with `withMdc` on the behavior builder. The function receives every
message and returns the map for the time that message is handled:

=== "Java"

    ```java
    @Component
    public class OrderProcessorActor implements SpringActor<OrderProcessorActor.Command> {

        public interface Command {}

        public record ProcessOrder(String orderId, String userId, String requestId) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withMdc(msg -> {
                        if (msg instanceof ProcessOrder order) {
                            Map<String, String> mdc = new HashMap<>();
                            mdc.put("orderId", order.orderId());
                            mdc.put("userId", order.userId());
                            if (order.requestId() != null) {   // MDC values must not be null
                                mdc.put("requestId", order.requestId());
                            }
                            return mdc;
                        }
                        return Map.of();
                    })
                    .onMessage(ProcessOrder.class, (ctx, msg) -> {
                        ctx.getLog().info("Processing order");   // carries orderId, userId, requestId
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class OrderProcessorActor : SpringActor<OrderProcessorActor.Command> {

        sealed interface Command

        data class ProcessOrder(val orderId: String, val userId: String, val requestId: String?) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
            actorBehavior<Command>(actorContext)
                .withMdc { msg ->
                    when (msg) {
                        is ProcessOrder -> buildMap {
                            put("orderId", msg.orderId)
                            put("userId", msg.userId)
                            msg.requestId?.let { put("requestId", it) }   // MDC values must not be null
                        }
                    }
                }
                .onMessage(ProcessOrder::class.java) { ctx, _ ->
                    ctx.log.info("Processing order")   // carries orderId, userId, requestId
                    SpringActorBehavior.same()
                }
                .build()
    }
    ```

Return an empty map for messages without context. Static and dynamic MDC combine: a line carries both the
spawn-time values and the values of the current message.

### Carrying a request id into an actor

The MDC of the thread that sends a message does not travel with the message: the actor runs on another
thread. To see a request id in the actor's logs, read it on the sending side, put it in the message, and
add it back with `withMdc`. The [logging example](../../examples/logging.md) does this: a `WebFilter`
puts `requestId` in the MDC of the request, the service reads it and passes it in `ProcessOrder`, and the
actor's `withMdc` (above) puts it on the actor's lines.

=== "Java"

    ```java
    public Mono<OrderProcessed> processOrder(String userId) {
        String requestId = MDC.get("requestId");      // on the request thread
        String orderId = UUID.randomUUID().toString();
        return Mono.fromCompletionStage(actorSystem.getOrSpawn(OrderProcessorActor.class, "order-processor")
                .thenCompose(actor -> actor
                        .ask(new OrderProcessorActor.ProcessOrder(orderId, userId, requestId))
                        .execute()));
    }
    ```

=== "Kotlin"

    ```kotlin
    suspend fun processOrder(userId: String): OrderProcessed {
        val requestId = MDC.get("requestId")          // read before the first suspension point
        val orderId = UUID.randomUUID().toString()
        return actorSystem.awaitGetOrSpawn<OrderProcessorActor, _>("order-processor")
            .awaitAsk(OrderProcessorActor.ProcessOrder(orderId, userId, requestId))
    }
    ```

(Here `ProcessOrder` is an `AskCommand<OrderProcessed>`, as in the example.) With Micrometer Tracing on,
the trace itself travels with messages, and lines carry `traceId` and `spanId`; see
[Monitoring](../operations/monitoring.md#tracing).

## Tags

Tags label an actor by role, priority or workload. They are written to the `pekkoTags` field of every
line, so you can filter on them:

=== "Java"

    ```java
    actorSystem.actor(PaymentProcessor.class)
            .withId("payment-processor")
            .withTags(TagsConfig.of("payment-service", "critical"))
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend function
    actorSystem.actor<PaymentProcessor, _>()
        .withId("payment-processor")
        .withTags(TagsConfig.of("payment-service", "critical"))
        .awaitSpawn()
    ```

`TagsConfig.of(...)` takes one or more strings or a `Set<String>`; `TagsConfig.empty()` means none.
Duplicate tags count once.

## Children

The child builder has the same `withMdc` and `withTags`. A child does not inherit its parent's MDC or
tags; give it its own:

=== "Java"

    ```java
    ctx.getSelf().child(WorkerActor.class)
            .withId("worker-1")
            .withTags(TagsConfig.of("worker"))
            .withMdc(MdcConfig.of(Map.of("parentId", actorContext.actorId())))
            .spawn();
    ```

=== "Kotlin"

    ```kotlin
    ctx.self.child<WorkerActor, _>()
        .withId("worker-1")
        .withTags(TagsConfig.of("worker"))
        .withMdc(MdcConfig.of(mapOf("parentId" to actorContext.actorId())))
        .spawn()
    ```

## Printing MDC with Logback

MDC values appear only if the log pattern prints them. A console pattern:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} %-5level [%X{pekkoSource}] [%X{pekkoTags}] [reqId=%X{requestId}] [userId=%X{userId}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

For log aggregation (Elasticsearch, Loki), write JSON with the
[Logstash encoder](https://github.com/logfellow/logstash-logback-encoder) and list the MDC keys to
include:

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>pekkoSource</includeMdcKeyName>
        <includeMdcKeyName>pekkoTags</includeMdcKeyName>
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <includeMdcKeyName>orderId</includeMdcKeyName>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
</appender>
```

It needs `net.logstash.logback:logstash-logback-encoder` on the classpath. In production, put file and
JSON appenders behind Logback's `AsyncAppender` so that writing a log line never makes an actor wait on
disk. The logging example's
[`logback.xml`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/logging/src/main/resources/logback.xml)
has console, rolling file and JSON appenders, each async.

## Good practice

- Use the same MDC key names everywhere (`userId`, `requestId`, `orderId`), so one query finds a request
  across services and actors.
- Keep the number of keys small; every key is copied for every message.
- Never put secrets or personal data (passwords, card numbers, tokens) in MDC or tags.
- Use tags for what an actor *is* (role, service, priority) and MDC for what it is *working on*.

## Troubleshooting

| Symptom | Cause |
|---|---|
| MDC keys are empty in the output | The pattern has no `%X{key}` for them, or the line was logged outside the actor's handler (for example in a future callback) |
| `requestId` missing in actor logs | Thread MDC doesn't follow messages; pass the id in the message and add it with `withMdc` |
| `pekkoTags` empty | The actor was spawned without `withTags(...)`, or the pattern lacks `%X{pekkoTags}` |

## Related

- [Actors](actors.md): the spawn and child builders
- [Behaviors](behaviors.md): the behavior builder
- [Monitoring](../operations/monitoring.md): metrics, tracing and log setup in production
- [Logging example](../../examples/logging.md)
- [Pekko logging](https://pekko.apache.org/docs/pekko/current/typed/logging.html)
