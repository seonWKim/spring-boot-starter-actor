# Pekko Streams Integration Examples

This module demonstrates how to integrate **Pekko Streams** with the **Spring Boot Actor system**. These examples show production-ready patterns for stream processing with actors, leveraging Pekko Streams' built-in features rather than reimplementing them.

## 🎯 Overview

These examples demonstrate:

- **File processing pipelines** - Read, transform, and write files using streams and actors
- **Data transformation pipelines** - Complex multi-stage data processing
- **Actors as stream sources** - Generate stream data from actors
- **Actors as stream sinks** - Consume stream data with actors
- **Backpressure handling** - Automatic flow control using Pekko's built-in backpressure
- **Throttling** - Rate limiting using Pekko's built-in throttling

## 🚨 Important Design Principle

**This module does NOT reimplement Pekko Streams.** Instead, it shows how to:
- Use Pekko Streams directly with Spring actors
- Leverage Pekko's battle-tested features (backpressure, throttling, error handling)
- Integrate streams with Spring Boot configuration
- Apply common integration patterns

## 📋 Prerequisites

- Java 11 or higher
- Spring Boot 2.7.x
- Apache Pekko 1.1.x

## 🚀 Quick Start

### Running the Application

```bash
# Build the project
./gradlew :example:streams:build

# Run the application
./gradlew :example:streams:bootRun
```

### Running Individual Examples

The examples are provided as Spring services that you can inject and use in your application:

```java
@RestController
public class StreamsDemoController {
    
    private final FileProcessingExample fileProcessing;
    private final DataPipelineExample dataPipeline;
    private final BackpressureExample backpressure;
    private final ThrottlingExample throttling;
    
    // Inject the example services
    public StreamsDemoController(
            FileProcessingExample fileProcessing,
            DataPipelineExample dataPipeline,
            BackpressureExample backpressure,
            ThrottlingExample throttling) {
        this.fileProcessing = fileProcessing;
        this.dataPipeline = dataPipeline;
        this.backpressure = backpressure;
        this.throttling = throttling;
    }
    
    @GetMapping("/demo/file-processing")
    public CompletionStage<String> demoFileProcessing() throws Exception {
        fileProcessing.createSampleFile("/tmp/input.txt", 100);
        return fileProcessing.processFile("/tmp/input.txt", "/tmp/output.txt")
            .thenApply(count -> "Processed " + count + " bytes");
    }
}
```

## 📚 Examples

### 1. File Processing Example

**Class:** `FileProcessingExample`

Shows how to process large files using Pekko Streams with actor processing:

```java
public CompletionStage<Long> processFile(String inputPath, String outputPath) {
    ActorRef<DataProcessorActor.Command> processor = 
        actorSystem.actorOf(DataProcessorActor.class, "processor");
    
    return FileIO.fromPath(Paths.get(inputPath))
        .via(Framing.delimiter(ByteString.fromString("\n"), 1024, true))
        .map(ByteString::utf8String)
        .filter(line -> !line.trim().isEmpty())
        .mapAsync(10, line -> actorSystem.ask(
            processor,
            replyTo -> new DataProcessorActor.ProcessData(line),
            Duration.ofSeconds(5)))
        .map(result -> result.getProcessed() + "\n")
        .map(ByteString::fromString)
        .runWith(FileIO.toPath(Paths.get(outputPath)), actorSystem.materializer())
        .thenApply(ioResult -> ioResult.count());
}
```

**Key Features:**
- Automatic backpressure when actor processing is slow
- Memory-efficient line-by-line processing
- Error handling with supervision
- Tunable parallelism and timeouts

### 2. Data Pipeline Example

**Class:** `DataPipelineExample`

Demonstrates complex multi-stage data transformation pipelines:

```java
public CompletionStage<List<ProcessedResult>> simpleTransformPipeline(List<String> data) {
    ActorRef<DataProcessorActor.Command> processor = 
        actorSystem.actorOf(DataProcessorActor.class, "pipeline-processor");
    
    return Source.from(data)
        .filter(item -> item != null && !item.trim().isEmpty())
        .map(String::toLowerCase)
        .mapAsync(5, item -> actorSystem.ask(
            processor, 
            replyTo -> new DataProcessorActor.ProcessData(item),
            Duration.ofSeconds(5)))
        .runWith(Sink.seq(), actorSystem.materializer());
}
```

**Key Features:**
- Multiple transformation stages
- Actor processing at any stage
- Batching for efficiency
- Throughput monitoring

### 3. Actor Source Example

**Class:** `ActorSourceExample`

Shows different patterns for using actors as stream sources:

```java
// Polling pattern
public CompletionStage<List<String>> streamFromActorPolling() {
    ActorRef<MessageProducerActor.Command> producer = 
        actorSystem.actorOf(MessageProducerActor.class, "producer");
    
    return Source.tick(Duration.ZERO, Duration.ofMillis(100), "tick")
        .take(20)
        .mapAsync(5, tick -> actorSystem.ask(
            producer,
            replyTo -> new MessageProducerActor.GetMessage(),
            Duration.ofSeconds(1)))
        .runWith(Sink.seq(), actorSystem.materializer());
}

// Queue pattern
public SourceQueueWithComplete<String> createQueueSource() {
    Source<String, SourceQueueWithComplete<String>> queueSource = 
        Source.queue(100, OverflowStrategy.backpressure());
    
    return queueSource
        .map(msg -> msg.toUpperCase())
        .to(Sink.foreach(result -> System.out.println("Result: " + result)))
        .run(actorSystem.materializer());
}
```

**Key Features:**
- Polling actors for data
- Queue-backed sources
- Actor pushing to streams
- Bounded and unbounded sources

### 4. Actor Sink Example

**Class:** `ActorSinkExample`

Demonstrates using actors as stream sinks:

```java
// Tell pattern (fire-and-forget)
public CompletionStage<Done> streamToActorWithTell(List<String> data) {
    ActorRef<MessageConsumerActor.Command> consumer = 
        actorSystem.actorOf(MessageConsumerActor.class, "consumer");
    
    return Source.from(data)
        .map(item -> {
            consumer.tell(new MessageConsumerActor.ProcessMessage(item));
            return item;
        })
        .runWith(Sink.ignore(), actorSystem.materializer());
}

// Ask pattern (with backpressure)
public CompletionStage<Done> streamToActorWithAsk(List<String> data) {
    ActorRef<MessageConsumerActor.Command> consumer = 
        actorSystem.actorOf(MessageConsumerActor.class, "consumer-ask");
    
    return Source.from(data)
        .mapAsync(5, item -> actorSystem.ask(
            consumer,
            replyTo -> new MessageConsumerActor.ProcessAndAck(item),
            Duration.ofSeconds(5)))
        .runWith(Sink.ignore(), actorSystem.materializer());
}
```

**Key Features:**
- Fire-and-forget with tell
- Request-response with ask
- Automatic backpressure
- Batching support
- Result accumulation

### 5. Backpressure Example

**Class:** `BackpressureExample`

Shows how backpressure works automatically in Pekko Streams:

```java
public CompletionStage<Long> automaticBackpressure(List<String> data) {
    ActorRef<DataProcessorActor.Command> processor = 
        actorSystem.actorOf(DataProcessorActor.class, "slow-processor");
    
    return Source.from(data)
        .mapAsync(2, item -> actorSystem.ask(
            processor,
            replyTo -> new DataProcessorActor.ProcessData(item),
            Duration.ofSeconds(10)))
        .map(result -> 1L)
        .runWith(Sink.reduce(0L, Long::sum), actorSystem.materializer());
}
```

**Key Features:**
- Automatic backpressure (no manual handling needed)
- Buffer strategies (backpressure, drop oldest, drop newest)
- Overflow handling
- Parallelism control
- Async boundaries

**Buffer Strategies:**

```java
// Backpressure strategy (slow down source)
.buffer(100, OverflowStrategy.backpressure())

// Drop oldest elements
.buffer(100, OverflowStrategy.dropHead())

// Drop newest elements
.buffer(100, OverflowStrategy.dropTail())
```

### 6. Throttling Example

**Class:** `ThrottlingExample`

Demonstrates rate limiting using Pekko's built-in throttling:

```java
public CompletionStage<Long> fixedRateThrottle(List<String> data, int elementsPerSecond) {
    ActorRef<DataProcessorActor.Command> processor = 
        actorSystem.actorOf(DataProcessorActor.class, "throttled-processor");
    
    return Source.from(data)
        .throttle(elementsPerSecond, Duration.ofSeconds(1))
        .mapAsync(5, item -> actorSystem.ask(
            processor,
            replyTo -> new DataProcessorActor.ProcessData(item),
            Duration.ofSeconds(5)))
        .runWith(Sink.ignore(), actorSystem.materializer());
}
```

**Key Features:**
- Fixed rate throttling
- Burst throttling (allow temporary bursts)
- Enforcing mode (fail if rate exceeded)
- API rate limiting patterns
- Time-based windows
- Multi-stage throttling

**Throttle Modes:**

```java
// Shaping mode: delay elements to meet rate (default)
.throttle(10, Duration.ofSeconds(1), ThrottleMode.shaping())

// Enforcing mode: fail if rate exceeded
.throttle(10, Duration.ofSeconds(1), ThrottleMode.enforcing())

// With burst allowance
.throttle(10, Duration.ofSeconds(1), 5, ThrottleMode.shaping())
```

## ⚙️ Configuration

See `application.yml` for configuration examples:

```yaml
streams:
  # File processing configuration
  file-processing:
    parallelism: 10
    timeout-seconds: 5
    
  # Backpressure configuration
  backpressure:
    buffer-size: 1000
    
  # Throttling configuration
  throttling:
    api:
      calls-per-minute: 60
      burst-size: 10
```

## 🔑 Key Concepts

### Backpressure

Pekko Streams provides **automatic backpressure**. You don't need to implement it:

- **Fast producer, slow consumer**: Stream automatically slows down the producer
- **Buffering**: Use `.buffer()` to smooth out temporary bursts
- **Overflow strategies**: Choose what happens when buffers fill up
- **Parallelism control**: Use `mapAsync(n, ...)` to control concurrent operations

### Throttling

Pekko provides **battle-tested throttling**. Use it instead of reimplementing:

- **Rate limiting**: Control elements per time unit
- **Burst handling**: Allow temporary bursts within limits
- **API compliance**: Easily comply with external API rate limits
- **Multi-stage**: Different stages can have different rate limits

### Actor Integration

Integration patterns:

1. **Actor processing in stream**: Use `mapAsync()` with actor ask pattern
2. **Actor as source**: Poll actor or use queue-backed source
3. **Actor as sink**: Send to actor with tell (no backpressure) or ask (with backpressure)
4. **Error handling**: Use actor supervision strategies with stream error recovery

## 🎓 Best Practices

### 1. Choose the Right Parallelism

```java
// Low parallelism for expensive operations
.mapAsync(2, expensiveOperation)

// High parallelism for fast operations
.mapAsync(20, fastOperation)
```

### 2. Use Appropriate Timeouts

```java
// Short timeout for fast actors
Duration.ofSeconds(1)

// Longer timeout for external API calls
Duration.ofSeconds(30)
```

### 3. Handle Errors Gracefully

```java
.mapAsync(5, item -> actorSystem.ask(...)
    .exceptionally(ex -> {
        // Provide fallback value
        return new ProcessedResult(item, "FAILED", System.currentTimeMillis());
    }))
```

### 4. Use Batching for Efficiency

```java
// Group elements for batch processing
.grouped(100)
.mapAsync(3, batch -> processBatch(batch))
```

### 5. Monitor Your Streams

```java
.map(result -> {
    // Log progress
    System.out.println("Processed: " + result);
    return result;
})
```

## 🔗 Related Documentation

- [Apache Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/index.html)
- [Spring Boot Starter Actor](../../README.md)
- [Actor System Configuration](../../docs/configuration.md)

## 📝 Common Use Cases

### Use Case 1: Process Large CSV Files

```java
FileIO.fromPath(Paths.get("data.csv"))
    .via(CsvParsing.lineScanner())
    .map(csvRow -> new Order(csvRow))
    .mapAsync(10, order -> processOrder(order))
    .runWith(FileIO.toPath(Paths.get("results.csv")), materializer);
```

### Use Case 2: Rate-Limited API Calls

```java
Source.from(userIds)
    .throttle(10, Duration.ofSeconds(1)) // API limit: 10 calls/sec
    .mapAsync(5, userId -> fetchUserData(userId))
    .runWith(Sink.seq(), materializer);
```

### Use Case 3: Event Processing Pipeline

```java
Source.actorRef(100, OverflowStrategy.dropHead())
    .map(event -> enrichEvent(event))
    .mapAsync(10, event -> processEventInActor(event))
    .grouped(50)
    .mapAsync(2, batch -> saveToDatabase(batch))
    .run(materializer);
```

## 🐛 Troubleshooting

### Stream Doesn't Start

- Ensure you call `.run()` to materialize the stream
- Check that actor system is initialized

### Slow Processing

- Increase parallelism in `mapAsync`
- Use `.async()` to add async boundaries
- Check actor processing time

### Memory Issues

- Use backpressure with buffer limits
- Process files line-by-line instead of loading all
- Use `FileIO` instead of reading entire files

### Rate Limit Errors

- Use throttling to stay within limits
- Add retry logic with backoff
- Use burst allowance for temporary spikes

## 🤝 Contributing

When adding new examples:

1. Follow existing patterns
2. Use Pekko's built-in features (don't reimplement)
3. Add comprehensive documentation
4. Include error handling
5. Test with various data sizes

## 📄 License

Apache License 2.0 - See main project LICENSE file.
