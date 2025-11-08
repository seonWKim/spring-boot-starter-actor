# Task 6.1: MDC (Mapped Diagnostic Context) Logging

**Priority:** MEDIUM
**Estimated Effort:** 3-4 days
**Dependencies:** Phase 5 complete
**Assignee:** AI Agent

---

## Objective

Implement automatic MDC propagation across actor boundaries for structured logging.

---

## Features to Implement

### 1. Automatic MDC Propagation

**Fields to Include:**
- `actorPath`: Full actor path
- `actorClass`: Actor class name
- `messageType`: Message class name
- `correlationId`: Request correlation ID
- `traceId`: Distributed trace ID (if available)
- `spanId`: Current span ID (if available)

### 2. MDC Context Preservation

**Challenge:** MDC context lost across async boundaries

**Solution:**
```java
// Capture MDC before message send
Map<String, String> mdcContext = MDC.getCopyOfContextMap();

// Restore MDC before message processing
if (mdcContext != null) {
    MDC.setContextMap(mdcContext);
}
```

### 3. Configuration

**Allow users to configure which fields to include:**
```yaml
spring:
  actor:
    logging:
      mdc:
        enabled: true
        fields:
          - actorPath
          - actorClass
          - messageType
          - correlationId
          - traceId
        extract-correlation-id: true
        correlation-id-header: "X-Correlation-ID"
```

---

## Implementation Requirements

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/logging/MdcConfig.java`**
   - Configuration for MDC fields
   - Extract MDC from messages

2. **`core/src/main/java/io/github/seonwkim/core/logging/MdcPropagationInterceptor.java`**
   - Intercept message sends
   - Capture and restore MDC context

3. **`core/src/main/java/io/github/seonwkim/core/logging/CorrelationIdSupport.java`**
   - Interface for messages with correlation ID
   - Automatic extraction

4. **Tests**
   - `MdcPropagationTest.java`
   - Verify MDC across actor boundaries
   - Test correlation ID propagation

---

## Usage Example

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    private static final Logger log = LoggerFactory.getLogger(OrderActor.class);
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withMDC(MDCConfig.builder()
                .put("service", "order-service")
                .extractFromMessage(msg -> Map.of(
                    "orderId", ((OrderCommand) msg).orderId(),
                    "correlationId", ((OrderCommand) msg).correlationId()
                ))
                .build())
            .onMessage(CreateOrder.class, (context, msg) -> {
                // MDC automatically includes: actorPath, messageType, correlationId
                log.info("Processing order");
                // Output: [actorPath=/user/order-actor] [messageType=CreateOrder] 
                //         [correlationId=abc123] [orderId=12345] Processing order
                return Behaviors.same();
            })
            .build();
    }
}
```

---

## Correlation ID Propagation

### Automatic Extraction

```java
public interface CorrelationIdAware {
    String getCorrelationId();
}

public class OrderCommand implements CorrelationIdAware {
    private final String correlationId;
    private final String orderId;
    
    @Override
    public String getCorrelationId() {
        return correlationId;
    }
}
```

### Cross-Actor Propagation

```java
// Actor A sends to Actor B with correlation ID
context.tell(actorB, new ProcessOrder(
    MDC.get("correlationId"),
    orderId
));

// Actor B automatically has correlation ID in MDC
log.info("Processing in actor B");  // Includes correlationId
```

---

## Log Output Format

**With MDC:**
```
2025-11-08 10:15:30 INFO [actorPath=/user/order-actor] [messageType=CreateOrder] 
[correlationId=abc-123] [orderId=12345] Processing order
```

**Logback Configuration:**
```xml
<encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [actorPath=%X{actorPath}] 
    [messageType=%X{messageType}] [correlationId=%X{correlationId}] 
    %logger{36} - %msg%n</pattern>
</encoder>
```

---

## Acceptance Criteria

- [ ] MDC automatically populated for actor message processing
- [ ] MDC context survives async boundaries
- [ ] Correlation ID propagation works across actors
- [ ] Configuration allows customizing MDC fields
- [ ] Integration with distributed tracing (traceId/spanId)
- [ ] Comprehensive tests
- [ ] Documentation with examples
- [ ] Best practices guide

---

## Notes

- MDC storage is ThreadLocal - must be propagated explicitly
- Consider performance impact of MDC operations
- Ensure MDC cleanup to avoid memory leaks
- Test with async dispatchers
