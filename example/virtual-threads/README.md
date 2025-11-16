# Virtual Threads Example

This example demonstrates how to use virtual threads (Java 21+) with the spring-boot-starter-actor library.

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

The virtual thread dispatcher is configured in `application.yml`:

```yaml
spring:
  actor:
    virtual-threads-dispatcher:
      executor: virtual-thread-executor
```
