# Production-Ready Logging Example

This example demonstrates production-ready logging configuration with MDC (Mapped Diagnostic Context) and actor tags using Spring Boot Starter Actor.

## What This Example Demonstrates

1. **Static MDC** - Context values set at actor spawn time
2. **Dynamic MDC** - Context values computed per message
3. **Actor Tags** - Categorization and filtering of actors
4. **Async Logging** - Non-blocking log appenders for better performance
5. **JSON Logging** - Structured logs for log aggregation systems
6. **Request Tracing** - Tracking requests across multiple actors

## Architecture

The example includes three main actors:

- **OrderProcessorActor** - Processes orders with dynamic MDC
- **PaymentProcessorActor** - Processes payments with static + dynamic MDC
- **NotificationActor** - Sends notifications with tags and MDC

## Running the Example

### Prerequisites

- Java 11 or higher
- Gradle

### Build and Run

```bash
# Build the project
./gradlew :example:logging:build

# Run the application
./gradlew :example:logging:bootRun
```

The application will start on `http://localhost:8080`.

## API Endpoints

### 1. Process Order (Dynamic MDC)

Demonstrates dynamic MDC where order details are added to each log entry.

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "CUST-123",
    "amount": 99.99
  }'
```

**Log Output:**
```
[INFO] [pekkoSource=...] [pekkoTags=order-service,critical,cpu-intensive] [orderId=ORD-xxx] [userId=CUST-123] [amount=99.99] Starting order processing
```

### 2. Process Payment (Static + Dynamic MDC)

Demonstrates both static MDC (service, region) and dynamic MDC (payment details).

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-123",
    "userId": "CUST-123",
    "amount": 99.99,
    "paymentMethod": "credit_card"
  }'
```

**Log Output:**
```
[INFO] [pekkoSource=...] [pekkoTags=payment-service,critical,io-bound] [service=payment-service] [region=us-east-1] [paymentId=PAY-xxx] [orderId=ORD-123] Starting payment processing
```

### 3. Send Notification (Tags + MDC)

Demonstrates actor tags for categorization and filtering.

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER-123",
    "type": "email",
    "message": "Your order has been processed"
  }'
```

**Log Output:**
```
[INFO] [pekkoSource=...] [pekkoTags=notification,low-priority,io-bound] [service=notification-service] [notificationId=NOTIF-xxx] [userId=USER-123] Sending email notification
```

### 4. Complete Checkout (Request Tracing)

Demonstrates end-to-end request tracing across multiple actors.

```bash
curl -X POST http://localhost:8080/api/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "CUST-123",
    "amount": 99.99,
    "paymentMethod": "credit_card"
  }'
```

This endpoint chains order -> payment -> notification, allowing you to trace a request through multiple actors using MDC context.

## Logging Configuration

### Console Logs (Development)

Console output includes human-readable logs with MDC context:

```
2024-11-06 10:30:45,123 INFO  [pekkoSource=akka://LoggingExampleSystem/user/order-processor] [pekkoTags=order-service,critical,cpu-intensive] [reqId=REQ-abc] [userId=CUST-123] [orderId=ORD-123] io.github.seonwkim.example.logging.OrderProcessorActor - Starting order processing
```

### File Logs (Production)

Logs are written to two files:

1. **logs/application.log** - Human-readable format with rolling policy
2. **logs/application-json.log** - JSON structured logs for log aggregation

Example JSON log entry:
```json
{
  "@timestamp": "2024-11-06T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "io.github.seonwkim.example.logging.OrderProcessorActor",
  "message": "Starting order processing",
  "pekkoSource": "akka://LoggingExampleSystem/user/order-processor",
  "pekkoTags": "order-service,critical,cpu-intensive",
  "orderId": "ORD-123",
  "userId": "CUST-123",
  "amount": "99.99"
}
```

### Log Appenders

The example uses three appenders:

1. **CONSOLE** - Console output for development
2. **ASYNC_FILE** - Async file appender with rolling policy
3. **ASYNC_JSON** - Async JSON appender for structured logs

All file appenders are asynchronous to prevent blocking actor processing.

## Filtering Logs

### By Actor Tags

Filter logs by actor tags to see specific categories:

```bash
# View all order-service logs
grep "pekkoTags.*order-service" logs/application.log

# View all critical actors
grep "pekkoTags.*critical" logs/application.log

# View all I/O-bound actors
grep "pekkoTags.*io-bound" logs/application.log
```

### By MDC Values

Filter logs by MDC values:

```bash
# View all logs for a specific order
grep "orderId=ORD-123" logs/application.log

# View all logs for a specific customer
grep "userId=CUST-123" logs/application.log

# View all payment-service logs
grep "service=payment-service" logs/application.log
```

## Production Configuration

### Logback Configuration (`logback.xml`)

The example includes production-ready logback configuration:

- **Async appenders** - Non-blocking log processing
- **Rolling file policy** - Daily rotation with 30-day retention
- **Size cap** - Maximum 10GB total log size
- **JSON encoding** - Structured logs with Logstash encoder
- **MDC inclusion** - All relevant MDC keys included in logs

### Actor Configuration (`application.yml`)

Custom dispatchers for different workload types:

- **blocking-io-dispatcher** - For I/O-bound operations (payment, notification)
- **cpu-dispatcher** - For CPU-intensive operations (order processing)

## Key Concepts Demonstrated

### 1. Static MDC

Set at actor spawn time, remains constant for actor's lifetime:

```java
Map<String, String> staticMdc = Map.of(
    "service", "payment-service",
    "region", "us-east-1"
);

actorSystem
    .actor(PaymentProcessorActor.class)
    .withMdc(MdcConfig.of(staticMdc))
    .spawn();
```

### 2. Dynamic MDC

Computed per message, adds message-specific context:

```java
.withMdc(msg -> {
    if (msg instanceof ProcessOrder) {
        ProcessOrder order = (ProcessOrder) msg;
        return Map.of(
            "orderId", order.orderId,
            "userId", order.userId
        );
    }
    return Map.of();
})
```

### 3. Actor Tags

Categorize actors for filtering and monitoring:

```java
actorSystem
    .actor(OrderProcessorActor.class)
    .withTags(TagsConfig.of("order-service", "critical", "cpu-intensive"))
    .spawn();
```

Tags appear in logs:
```
[pekkoTags=order-service,critical,cpu-intensive]
```

## Best Practices Shown

1. **Use Async Appenders** - Prevents logging from blocking actors
2. **Structured Logging** - JSON format for easy parsing and aggregation
3. **MDC for Context** - Adds request/order/user IDs to all logs
4. **Tags for Categorization** - Easy filtering by actor role/priority
5. **Rolling Policies** - Automatic log rotation and retention
6. **Dispatcher Isolation** - Separate dispatchers for different workloads

## Monitoring and Observability

### Log Aggregation

JSON logs can be shipped to:
- **Elasticsearch/Kibana** - Search and visualize logs
- **Splunk** - Enterprise log management
- **Grafana Loki** - Log aggregation for Grafana

### Queries

Example Kibana/Elasticsearch queries:

```
# Find all failed orders
pekkoTags:order-service AND level:ERROR

# Find slow payments (if you add duration logging)
pekkoTags:payment-service AND duration:>5000

# Find all operations for a customer
userId:"CUST-123"
```

## Testing the Logging

Run multiple requests to see logging in action:

```bash
# Process multiple orders
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"userId\": \"CUST-$i\", \"amount\": $((RANDOM % 100 + 50))}"
  echo
done

# Check the logs
tail -f logs/application.log

# Check JSON logs
tail -f logs/application-json.log | jq .
```

## Further Reading

- [Logging Guide](../../mkdocs/docs/guides/logging.md) - Comprehensive logging documentation
- [Pekko Logging](https://pekko.apache.org/docs/pekko/current/typed/logging.html) - Official Pekko logging docs
- [Logback Documentation](http://logback.qos.ch/manual/configuration.html) - Logback configuration
- [Logstash Encoder](https://github.com/logfellow/logstash-logback-encoder) - JSON logging encoder

## Troubleshooting

### Logs not appearing

Check that log directory exists:
```bash
mkdir -p logs
```

### JSON logs not formatted

Install `jq` for pretty-printing:
```bash
# macOS
brew install jq

# Ubuntu/Debian
apt-get install jq

# Then view logs
tail -f logs/application-json.log | jq .
```

### Performance issues

Adjust async appender queue size in `logback.xml`:
```xml
<appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>2048</queueSize>  <!-- Increase if needed -->
    <neverBlock>true</neverBlock>  <!-- Don't block if queue is full -->
</appender>
```
