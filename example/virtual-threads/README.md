# Virtual Threads Example

This example demonstrates how to use virtual threads (Java 21+) with actor-spring-boot-starter.

## Requirements

- Java 21 or higher

## Running the Example

```bash
./gradlew :example:virtual-threads:bootRun
```

## Testing

```bash
curl http://localhost:8080/api/virtual
```

Check the logs for thread information:
```
Task 'virtual-thread-executor' - Thread: ..., IsVirtual: true, ThreadId: ...
```

The `IsVirtual: true` indicates the actor is running on a Java 21 virtual thread.

## Configuration

Virtual threads are configured in `application.yml` with typed keys, no Pekko HOCON:

```yaml
spring:
  actor:
    virtual-threads:
      enabled: true            # withBlockingDispatcher() runs on virtual threads
    dispatchers:
      virtual-threads-dispatcher:
        virtual: true          # withDispatcherFromConfig("virtual-threads-dispatcher")
```

`spring.actor.virtual-threads.enabled` is `auto` by default: it follows Spring Boot's
`spring.threads.virtual.enabled`. The default dispatcher always stays on platform threads.

A virtual thread that blocks inside a `synchronized` block or a native call pins its carrier
thread. Prefer `java.util.concurrent` locks in code that blocks on virtual threads.
