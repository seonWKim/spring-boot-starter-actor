# Virtual Threads Example

This example demonstrates how to use virtual threads (Java 21+) with the spring-boot-starter-actor library.

## Requirements

- Java 21 or higher

## What This Example Demonstrates

1. **Default Dispatcher**: Tests actors running on the default Pekko dispatcher
2. **Blocking Dispatcher**: Tests actors running on Pekko's blocking I/O dispatcher
3. **Virtual Thread Dispatcher**: Tests actors running with a custom virtual-thread-executor
4. **Stress Test**: Demonstrates multiple actors performing blocking work concurrently

## Running the Example

```bash
./gradlew :example:virtual-threads:bootRun
```

## Testing the Endpoints

After starting the application, use these simple GET endpoints to test:

### Test Default Dispatcher
```bash
curl http://localhost:8080/api/default
```

### Test Blocking Dispatcher
```bash
curl http://localhost:8080/api/blocking
```

### Test Virtual Thread Dispatcher
```bash
curl http://localhost:8080/api/virtual
```

### Test All Dispatchers at Once
```bash
curl http://localhost:8080/api/test-all
```

### Run Stress Test
```bash
# Default: 10 actors, 100ms work each
curl http://localhost:8080/api/stress

# Custom: 50 actors, 500ms work each
curl "http://localhost:8080/api/stress?count=50&duration=500"
```

## What to Look For in the Logs

When running the tests, check the logs for:

- **Thread Name**: Shows which thread pool is executing the actor
- **IsVirtual**: `true` indicates the thread is a virtual thread (Java 21+), `false` indicates a platform thread
- **ThreadId**: Unique identifier for the thread

Example log output:
```
Task 'blocking-dispatcher-test' - Thread: spring-pekko-example-pekko.actor.default-blocking-io-dispatcher-8, IsVirtual: false, ThreadId: 42
```

## Virtual Threads vs Platform Threads

- **Platform Threads**: Traditional OS threads, limited in number, heavyweight
- **Virtual Threads**: Lightweight threads managed by the JVM (Java 21+), can have millions of them

The blocking dispatcher in Pekko is designed for I/O operations. When combined with Java 21's virtual threads, it becomes even more efficient for handling blocking operations.

## Configuration

The custom dispatcher is configured in `application.yml`:

```yaml
spring:
  actor:
    pekko:
      virtual-threads-dispatcher:
        type: Dispatcher
        executor: thread-pool-executor
        thread-pool-executor:
          fixed-pool-size: 1
        throughput: 1
```

## Notes

- This example uses Spring Boot 3.x which requires Java 17+
- Virtual threads are available in Java 21+
- The `--enable-preview` flag is set in the build configuration for preview features
