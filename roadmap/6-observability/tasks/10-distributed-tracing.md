# Task 7.1: OpenTelemetry Integration

**Priority:** MEDIUM
**Estimated Effort:** 1 week
**Dependencies:** Phase 6 complete
**Assignee:** AI Agent

---

## Objective

Integrate OpenTelemetry for distributed tracing across actor systems and services.

---

## Features to Implement

### 1. OpenTelemetry SDK Integration

**Dependencies:**
```gradle
implementation("io.opentelemetry:opentelemetry-api:1.32.0")
implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:1.32.0")
```

### 2. Trace Context Propagation

**Challenge:** Propagate trace context across actor message boundaries

**Solution:**
- Extract trace context from current message
- Inject trace context into outgoing messages
- Support W3C Trace Context format

### 3. Auto-Configuration

```java
@Configuration
@ConditionalOnProperty(name = "spring.actor.tracing.enabled", havingValue = "true")
public class ActorTracingAutoConfiguration {
    
    @Bean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
    
    @Bean
    public ActorTracingInterceptor actorTracingInterceptor(OpenTelemetry openTelemetry) {
        return new ActorTracingInterceptor(openTelemetry);
    }
}
```

---

## Configuration

```yaml
spring:
  actor:
    tracing:
      enabled: true
      sampler: probability  # or: always_on, always_off, parent_based
      probability: 0.1  # Sample 10% of traces
      exporter:
        type: otlp  # or: jaeger, zipkin
        endpoint: http://localhost:4318/v1/traces
        timeout: 10s
      propagation:
        format: w3c  # W3C Trace Context
        extract-from-mdc: true
```

---

## Implementation Requirements

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/tracing/ActorTracingAutoConfiguration.java`**
2. **`core/src/main/java/io/github/seonwkim/core/tracing/ActorTracingInterceptor.java`**
3. **`core/src/main/java/io/github/seonwkim/core/tracing/TraceContext.java`**
4. **`core/src/main/java/io/github/seonwkim/core/tracing/TraceContextPropagator.java`**

### Tests
- `ActorTracingTest.java`
- Test trace context propagation
- Test sampling configuration
- Integration tests with OpenTelemetry

---

## Trace Context Propagation Example

```java
// Message envelope with trace context
public class TracedMessage<T> {
    private final T message;
    private final Map<String, String> traceContext;
    
    public TracedMessage(T message, Context context) {
        this.message = message;
        this.traceContext = extractTraceContext(context);
    }
}

// Automatic trace context injection
public class ActorTracingInterceptor {
    
    public void beforeMessageSend(Object message, ActorRef<?> target) {
        Context currentContext = Context.current();
        // Inject trace context into message
    }
    
    public void beforeMessageProcess(Object message) {
        // Extract trace context from message
        // Set as current context
    }
}
```

---

## Exporter Configuration

### OTLP Exporter (Recommended)
```yaml
spring:
  actor:
    tracing:
      exporter:
        type: otlp
        endpoint: http://otel-collector:4318/v1/traces
        headers:
          authorization: "Bearer ${OTEL_TOKEN}"
```

### Jaeger Exporter
```yaml
spring:
  actor:
    tracing:
      exporter:
        type: jaeger
        endpoint: http://jaeger:14250
```

### Zipkin Exporter
```yaml
spring:
  actor:
    tracing:
      exporter:
        type: zipkin
        endpoint: http://zipkin:9411/api/v2/spans
```

---

## Sampling Strategies

### Probability Sampler
```yaml
spring:
  actor:
    tracing:
      sampler: probability
      probability: 0.1  # 10%
```

### Parent-Based Sampler
```yaml
spring:
  actor:
    tracing:
      sampler: parent_based
      parent_sampler: probability
      parent_probability: 0.1
```

### Custom Sampler
```java
@Bean
public Sampler customSampler() {
    return Sampler.parentBased(
        Sampler.traceIdRatioBased(0.1)
    );
}
```

---

## Integration with Spring Boot

```java
@Component
public class TracedOrderController {
    
    @Autowired
    private ActorRef<OrderActor.Command> orderActor;
    
    @Autowired
    private Tracer tracer;
    
    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(@RequestBody OrderRequest request) {
        Span span = tracer.spanBuilder("create-order")
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Trace context automatically propagated to actor
            orderActor.tell(new CreateOrder(request.getOrderId()));
            
            span.setAttribute("order.id", request.getOrderId());
            return ResponseEntity.ok("Order created");
        } finally {
            span.end();
        }
    }
}
```

---

## Acceptance Criteria

- [ ] OpenTelemetry SDK integrated
- [ ] Trace context propagation works across actors
- [ ] Support for multiple exporters (OTLP, Jaeger, Zipkin)
- [ ] Sampling configuration works
- [ ] Auto-configuration for Spring Boot
- [ ] Integration with Spring Boot tracing
- [ ] Comprehensive tests
- [ ] Documentation with examples
- [ ] Performance overhead < 2%

---

## Notes

- Use GlobalOpenTelemetry if already configured
- Support both manual and automatic instrumentation
- Consider trace context extraction from HTTP headers
- Test with different sampling strategies
