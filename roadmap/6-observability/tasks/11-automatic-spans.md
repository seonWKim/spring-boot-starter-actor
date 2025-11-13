# Task 7.2: Automatic Span Creation

**Priority:** MEDIUM
**Estimated Effort:** 3-4 days
**Dependencies:** Task 7.1
**Assignee:** AI Agent

---

## Objective

Implement automatic span creation for actor message processing with rich span attributes.

---

## Features to Implement

### 1. Automatic Span Creation

**For every actor message:**
- Create span on message receive
- Set span attributes
- End span on message completion or error

```java
public class AutomaticSpanInterceptor {
    
    public void onMessageReceive(Object message, ActorRef<?> actor) {
        Span span = tracer.spanBuilder("actor.process-message")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("actor.path", actor.path().toString())
            .setAttribute("actor.class", actor.getClass().getSimpleName())
            .setAttribute("message.type", message.getClass().getSimpleName())
            .startSpan();
        
        // Store span for later completion
    }
    
    public void onMessageComplete(Object message, Duration processingTime) {
        Span span = getCurrentSpan();
        span.setAttribute("message.processing.duration", processingTime.toMillis());
        span.setStatus(StatusCode.OK);
        span.end();
    }
    
    public void onMessageError(Object message, Throwable error) {
        Span span = getCurrentSpan();
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.end();
    }
}
```

### 2. Span Attributes

**Standard Attributes:**
- `actor.path`: Full actor path
- `actor.class`: Actor class name
- `actor.id`: Actor ID (if available)
- `message.type`: Message class name
- `message.size`: Message size in bytes (if applicable)
- `message.processing.duration`: Processing time in milliseconds
- `dispatcher.name`: Dispatcher name

**Custom Attributes:**
```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withTracing(TracingConfig.builder()
                .enabled(true)
                .sampleRate(0.1)
                .addAttribute("service", "order-service")
                .extractAttributesFromMessage(msg -> Map.of(
                    "order.id", ((OrderCommand) msg).orderId(),
                    "order.amount", ((OrderCommand) msg).amount()
                ))
                .build())
            .onMessage(CreateOrder.class, (context, cmd) -> {
                // Span automatically created with all attributes
                
                // Access current span for nested operations
                Span span = context.currentSpan();
                span.setAttribute("order.status", "processing");
                
                // Create child span for database operation
                Span dbSpan = tracer.spanBuilder("database.save")
                    .setParent(Context.current())
                    .startSpan();
                try (Scope scope = dbSpan.makeCurrent()) {
                    repository.save(order);
                    dbSpan.setStatus(StatusCode.OK);
                } catch (Exception e) {
                    dbSpan.recordException(e);
                    dbSpan.setStatus(StatusCode.ERROR);
                    throw e;
                } finally {
                    dbSpan.end();
                }
                
                return Behaviors.same();
            })
            .build();
    }
}
```

### 3. Span Hierarchy

**Message flow tracing:**
```
HTTP Request (root span)
  └─> OrderActor.processOrder (child span)
       ├─> InventoryActor.checkStock (child span)
       ├─> PaymentActor.processPayment (child span)
       └─> NotificationActor.sendConfirmation (child span)
```

---

## Implementation Requirements

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/tracing/AutomaticSpanInterceptor.java`**
2. **`core/src/main/java/io/github/seonwkim/core/tracing/SpanAttributeExtractor.java`**
3. **`core/src/main/java/io/github/seonwkim/core/tracing/TracingConfig.java`**
4. **`core/src/main/java/io/github/seonwkim/core/behavior/TracingBehaviorDecorator.java`**

### Tests
- `AutomaticSpanCreationTest.java`
- Test span creation and completion
- Test span attributes
- Test span hierarchy
- Test error recording

---

## Configuration

```yaml
spring:
  actor:
    tracing:
      automatic-spans:
        enabled: true
        include-message-size: true
        include-dispatcher: true
        span-name-format: "actor.{messageType}"  # or: custom format
      attributes:
        include-defaults: true
        custom:
          service.name: "order-service"
          environment: "${ENV:production}"
```

---

## Span Naming Strategies

### Default Format
```
actor.process-message
```

### Message-Type Format
```
actor.CreateOrder
actor.ProcessPayment
```

### Actor-Class Format
```
OrderActor.process-message
```

### Custom Format
```java
@Bean
public SpanNameStrategy customSpanNameStrategy() {
    return (actorClass, messageType) -> 
        String.format("%s.%s", actorClass.getSimpleName(), messageType);
}
```

---

## Error Handling

### Automatic Exception Recording
```java
public void onMessageError(Object message, Throwable error) {
    Span span = getCurrentSpan();
    
    // Record exception with stack trace
    span.recordException(error);
    
    // Add error attributes
    span.setAttribute("error.type", error.getClass().getName());
    span.setAttribute("error.message", error.getMessage());
    span.setAttribute("error.stack", getStackTrace(error));
    
    // Mark span as error
    span.setStatus(StatusCode.ERROR, error.getMessage());
    
    span.end();
}
```

---

## Performance Considerations

### Sampling
```yaml
spring:
  actor:
    tracing:
      sampler: probability
      probability: 0.1  # Only trace 10% of messages
```

### Attribute Extraction
```yaml
spring:
  actor:
    tracing:
      attributes:
        max-message-size: 1024  # Limit message size recording
        extract-message-fields: false  # Disable expensive extraction
```

---

## Acceptance Criteria

- [ ] Spans automatically created for all actor messages
- [ ] Span attributes include actor and message details
- [ ] Span hierarchy preserved across actor boundaries
- [ ] Exceptions recorded with stack traces
- [ ] Configuration for span naming and attributes
- [ ] Support for custom span attributes
- [ ] Comprehensive tests
- [ ] Documentation with examples
- [ ] Performance overhead < 2%

---

## Notes

- Span creation should be lightweight
- Use sampling to reduce overhead in production
- Consider async span completion for long-running operations
- Test with high message throughput
