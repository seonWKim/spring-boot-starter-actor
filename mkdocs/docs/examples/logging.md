# Logging Example

Actor logs with context: static MDC values set when an actor spawns, per-message MDC values computed
from each message, actor tags, a request id carried from HTTP through several actors, and Logback writing
both plain text and JSON files.

- **Source:** [`example/logging`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/logging)
- **Runs:** one node, no cluster
- **Modules:** `actor-spring-boot-starter`, Spring WebFlux, Logback, `logstash-logback-encoder`

## What it shows

- **Static MDC**: `withMdc(MdcConfig.of(...))` on the spawn builder, the same on every log line of that actor
- **Per-message MDC**: `withMdc(msg -> ...)` on the behavior builder, computed for each message
- **Tags**: `withTags(TagsConfig.of(...))`, written to the `pekkoTags` MDC key
- **Request tracing**: a `WebFilter` puts a request id in the MDC, services copy it into each message,
  and each actor puts it back in its MDC
- **Async and JSON appenders** in `logback.xml`

## Run it

```bash
./gradlew :example:logging:bootRun
```

The app listens on port 8080 and writes to the console, `logs/application.log` and
`logs/application-json.log` (relative to the directory you start it from).

```bash
# Order: per-message MDC (orderId, userId, amount)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": "CUST-123", "amount": 99.99}'

# Payment: static MDC (service, region, version) plus per-message MDC
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-123", "userId": "CUST-123", "amount": 99.99, "paymentMethod": "credit_card"}'

# Notification: tags and static MDC
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId": "USER-123", "type": "email", "message": "Your order has been processed"}'

# Checkout: order -> payment -> notification, all with the same request id
curl -X POST http://localhost:8080/api/checkout \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: REQ-demo-1" \
  -d '{"userId": "CUST-123", "amount": 99.99, "paymentMethod": "credit_card"}'
```

Then filter the logs:

```bash
grep "REQ-demo-1" logs/application.log               # one request through every actor
grep "pekkoTags.*critical" logs/application.log       # actors tagged critical
tail -f logs/application-json.log | jq .              # JSON lines with MDC fields
```

## Walkthrough

### Static MDC and tags at spawn time

The services spawn one actor each in their constructor:

=== "Java"

    ```java
    Map<String, String> staticMdc = Map.of(
            "service", "payment-service",
            "region", "us-east-1",
            "version", "2.0");

    this.paymentProcessor = actorSystem
            .actor(PaymentProcessorActor.class)
            .withId("payment-processor")
            .withMdc(MdcConfig.of(staticMdc))
            .withTags(TagsConfig.of("payment-service", "critical", "io-bound"))
            .withBlockingDispatcher()
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    ```

=== "Kotlin"

    ```kotlin
    val paymentProcessor = actorSystem.actor<PaymentProcessorActor, _>()
        .withId("payment-processor")
        .withMdc(MdcConfig.of(mapOf("service" to "payment-service", "region" to "us-east-1", "version" to "2.0")))
        .withTags(TagsConfig.of("payment-service", "critical", "io-bound"))
        .withBlockingDispatcher()
        .withTimeout(5.seconds)
        .spawnAndWait()
    ```

All three actors simulate work with `Thread.sleep`. The payment and notification actors run on the
blocking dispatcher; the order actor stays on the default one.

### Per-message MDC

The behavior builder's `withMdc` takes a function from the message to a map. Its values are added to the
static ones for that message's log lines:

=== "Java"

    ```java
    return SpringActorBehavior.builder(Command.class, actorContext)
            .withMdc(msg -> {
                if (msg instanceof ProcessOrder order) {
                    Map<String, String> mdc = new HashMap<>();
                    mdc.put("orderId", order.orderId);
                    mdc.put("userId", order.userId);
                    mdc.put("amount", String.valueOf(order.amount));
                    if (order.requestId != null) {
                        mdc.put("requestId", order.requestId);
                    }
                    return mdc;
                }
                return Map.of();
            })
            .onMessage(ProcessOrder.class, (ctx, msg) -> {
                ctx.getLog().info("Starting order processing");
                // ...
            })
            .build();
    ```

=== "Kotlin"

    ```kotlin
    actorBehavior<Command>(actorContext)
        .withMdc { msg ->
            when (msg) {
                is ProcessOrder -> buildMap {
                    put("orderId", msg.orderId)
                    put("userId", msg.userId)
                    put("amount", msg.amount.toString())
                    msg.requestId?.let { put("requestId", it) }
                }
                else -> emptyMap()
            }
        }
        .onMessage(ProcessOrder::class.java) { ctx, msg ->
            ctx.log.info("Starting order processing")
            // ...
        }
        .build()
    ```

Use `ctx.getLog()` inside handlers: those log lines carry the MDC values.

### Carrying the request id

`MdcWebFilter` reads `X-Request-Id` (or creates a UUID) and `X-User-Id`, puts them in the MDC, and echoes
`X-Request-Id` in the response. An actor runs on another thread, so the MDC does not follow the message
by itself. The services read `MDC.get("requestId")` and put it in the command; the actor's per-message
MDC puts it back.

### Logback

`logback.xml` writes the MDC keys in the console and file patterns, for example:

```
%d{ISO8601} %-5level [%thread] [%X{pekkoSource}] [%X{pekkoTags}] [reqId=%X{requestId}] [userId=%X{userId}] %logger{36} - %msg%n
```

| Appender | Output |
|---|---|
| `CONSOLE` | Plain text |
| `ASYNC_FILE` | `logs/application.log`, rolled daily, 30 days, 10 GB cap |
| `ASYNC_JSON` | `logs/application-json.log` with `LogstashEncoder`, MDC keys as JSON fields |

The file appenders are `AsyncAppender`s with a queue of 1,024 and `neverBlock=false`: when the queue is
full, the logging thread waits rather than dropping lines. Set `neverBlock` to `true` if you prefer to
drop lines instead of slowing actors down.

## Try next

- Add a `traceId` to the per-message MDC and include it in the console pattern.
- Send 100 checkouts in a loop and follow one request id through `application-json.log` with `jq`.

## Related

- [Logging](../guides/core/logging.md): MDC, tags and Pekko log settings in detail
- [Dispatchers](../guides/core/dispatchers.md): the blocking dispatcher
- [Monitoring](../guides/operations/monitoring.md): logs next to metrics and tracing
