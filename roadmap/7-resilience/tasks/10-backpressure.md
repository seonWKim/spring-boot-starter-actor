# Task 5.1: Backpressure Handling

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Implement mailbox backpressure configuration and overflow strategies to prevent system overload when actors cannot keep up with incoming message rate.

---

## Requirements

### 1. Bounded Mailbox Configuration

Configure bounded mailboxes with overflow strategies:

```java
@Component
public class HighThroughputActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withMailbox(MailboxConfig.builder()
                .capacity(1000)
                .onOverflow(OverflowStrategy.DROP_OLDEST)
                .build())
            .onMessage(ProcessData.class, this::handleData)
            .build();
    }
}
```

### 2. Overflow Strategies

Support multiple strategies for handling mailbox overflow:

```java
public enum OverflowStrategy {
    DROP_OLDEST,      // Drop oldest message in mailbox
    DROP_NEWEST,      // Drop incoming message
    DROP_BUFFER,      // Drop entire buffer
    FAIL,             // Throw exception
    BACKPRESSURE      // Signal backpressure to sender
}

// Example configurations
.withMailbox(MailboxConfig.builder()
    .capacity(1000)
    .onOverflow(OverflowStrategy.DROP_OLDEST)
    .build())

.withMailbox(MailboxConfig.builder()
    .capacity(500)
    .onOverflow(OverflowStrategy.FAIL)
    .onOverflowCallback((msg) -> {
        log.error("Mailbox full, dropping message: {}", msg);
    })
    .build())

.withMailbox(MailboxConfig.builder()
    .capacity(2000)
    .onOverflow(OverflowStrategy.BACKPRESSURE)
    .backpressureTimeout(Duration.ofSeconds(5))
    .build())
```

### 3. Backpressure Signaling

When using BACKPRESSURE strategy, signal back to sender:

```java
public class BackpressureMailbox extends BoundedMailbox {
    
    @Override
    public void enqueue(ActorRef receiver, Envelope envelope) {
        if (isFull()) {
            // Send backpressure signal
            if (envelope.sender() != ActorRef.noSender()) {
                envelope.sender().tell(new BackpressureSignal(receiver), ActorRef.noSender());
            }
            
            // Wait or fail based on timeout
            if (!tryEnqueueWithWait(envelope)) {
                handleOverflow(envelope);
            }
        } else {
            super.enqueue(receiver, envelope);
        }
    }
    
    private boolean tryEnqueueWithWait(Envelope envelope) {
        try {
            return queue.offer(envelope, backpressureTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

### 4. Spring Boot Configuration

```yaml
spring:
  actor:
    mailbox:
      # Default mailbox configuration
      default:
        type: bounded
        capacity: 1000
        overflow-strategy: DROP_OLDEST
      
      # Per-actor mailbox configuration
      instances:
        high-throughput-actor:
          capacity: 5000
          overflow-strategy: DROP_OLDEST
        
        critical-actor:
          capacity: 100
          overflow-strategy: FAIL
        
        async-processor:
          capacity: 10000
          overflow-strategy: DROP_NEWEST
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/mailbox/BoundedMailboxConfig.java`**
   - Configuration for bounded mailboxes
   - Capacity and overflow settings

2. **`core/src/main/java/io/github/seonwkim/core/mailbox/OverflowStrategy.java`**
   - Enum for overflow strategies
   - DROP_OLDEST, DROP_NEWEST, FAIL, BACKPRESSURE

3. **`core/src/main/java/io/github/seonwkim/core/mailbox/BackpressureSignal.java`**
   - Signal sent to sender when backpressure detected
   - Contains backpressure information

4. **`core/src/main/java/io/github/seonwkim/core/mailbox/BackpressureMailbox.java`**
   - Custom mailbox implementation with backpressure
   - Handles overflow strategies

5. **`core/src/main/java/io/github/seonwkim/core/mailbox/MailboxMetrics.java`**
   - Metrics for mailbox operations
   - Overflow events, queue depth, etc.

6. **`core/src/main/java/io/github/seonwkim/core/SpringActorBehavior.java`** (modify)
   - Add `.withMailbox()` method to builder

7. **`core/src/main/java/io/github/seonwkim/core/MailboxConfig.java`** (modify)
   - Extend existing MailboxConfig with backpressure support

---

## MailboxConfig Extension

```java
public class MailboxConfig {
    
    // Existing fields
    private final String mailboxType;
    
    // New fields for backpressure
    private final Integer capacity;
    private final OverflowStrategy overflowStrategy;
    private final Duration backpressureTimeout;
    private final Consumer<Object> overflowCallback;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String mailboxType = "unbounded";
        private Integer capacity = null;
        private OverflowStrategy overflowStrategy = OverflowStrategy.DROP_OLDEST;
        private Duration backpressureTimeout = Duration.ofSeconds(5);
        private Consumer<Object> overflowCallback = null;
        
        public Builder capacity(int capacity) {
            this.capacity = capacity;
            this.mailboxType = "bounded";
            return this;
        }
        
        public Builder onOverflow(OverflowStrategy strategy) {
            this.overflowStrategy = strategy;
            return this;
        }
        
        public Builder backpressureTimeout(Duration timeout) {
            this.backpressureTimeout = timeout;
            return this;
        }
        
        public Builder onOverflowCallback(Consumer<Object> callback) {
            this.overflowCallback = callback;
            return this;
        }
        
        public MailboxConfig build() {
            return new MailboxConfig(this);
        }
    }
}
```

---

## Backpressure Implementation

```java
public class BackpressureMailbox extends AbstractBoundedNodeQueue<Envelope> {
    
    private final int capacity;
    private final OverflowStrategy strategy;
    private final Duration backpressureTimeout;
    private final MailboxMetrics metrics;
    
    @Override
    public void enqueue(ActorRef receiver, Envelope envelope) {
        if (isFull()) {
            metrics.recordOverflow(receiver.path().name());
            handleOverflow(receiver, envelope);
        } else {
            super.enqueue(receiver, envelope);
            metrics.recordEnqueue(receiver.path().name());
        }
    }
    
    private void handleOverflow(ActorRef receiver, Envelope envelope) {
        switch (strategy) {
            case DROP_OLDEST:
                // Remove oldest message and add new one
                dequeue();
                super.enqueue(receiver, envelope);
                metrics.recordDrop(receiver.path().name(), "oldest");
                break;
                
            case DROP_NEWEST:
                // Drop incoming message
                metrics.recordDrop(receiver.path().name(), "newest");
                // Optionally notify sender
                notifySenderOfDrop(envelope);
                break;
                
            case DROP_BUFFER:
                // Clear mailbox and add new message
                clear();
                super.enqueue(receiver, envelope);
                metrics.recordDrop(receiver.path().name(), "buffer");
                break;
                
            case FAIL:
                // Throw exception
                throw new MailboxOverflowException(
                    "Mailbox capacity exceeded: " + capacity);
                
            case BACKPRESSURE:
                // Try to wait for space
                if (!tryEnqueueWithWait(receiver, envelope)) {
                    // Timeout, send backpressure signal
                    sendBackpressureSignal(receiver, envelope);
                    throw new BackpressureException(
                        "Mailbox full and backpressure timeout exceeded");
                }
                break;
        }
    }
    
    private boolean tryEnqueueWithWait(ActorRef receiver, Envelope envelope) {
        // Send backpressure signal immediately
        sendBackpressureSignal(receiver, envelope);
        
        try {
            // Wait for space in mailbox
            long timeoutMs = backpressureTimeout.toMillis();
            long start = System.currentTimeMillis();
            
            while (isFull() && (System.currentTimeMillis() - start) < timeoutMs) {
                Thread.sleep(10);
            }
            
            if (!isFull()) {
                super.enqueue(receiver, envelope);
                return true;
            }
            
            return false;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    private void sendBackpressureSignal(ActorRef receiver, Envelope envelope) {
        if (envelope.sender() != ActorRef.noSender()) {
            BackpressureSignal signal = new BackpressureSignal(
                receiver.path().name(),
                numberOfMessages(),
                capacity
            );
            envelope.sender().tell(signal, ActorRef.noSender());
            metrics.recordBackpressureSignal(receiver.path().name());
        }
    }
    
    private void notifySenderOfDrop(Envelope envelope) {
        if (envelope.sender() != ActorRef.noSender()) {
            MessageDropped signal = new MessageDropped(envelope.message());
            envelope.sender().tell(signal, ActorRef.noSender());
        }
    }
}
```

---

## Handling Backpressure Signals

Actors can handle backpressure signals from downstream:

```java
@Component
public class ProducerActor implements SpringActor<Command> {
    
    private boolean backpressureActive = false;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(ProduceData.class, this::handleProduce)
            .onMessage(BackpressureSignal.class, this::handleBackpressure)
            .build();
    }
    
    private Behavior<Command> handleProduce(ActorContext<Command> ctx, ProduceData msg) {
        if (backpressureActive) {
            // Slow down production
            ctx.getLog().warn("Backpressure active, slowing down production");
            // Schedule retry later
            ctx.scheduleOnce(Duration.ofSeconds(1), ctx.getSelf(), msg);
            return Behaviors.same();
        }
        
        // Normal production
        downstreamActor.tell(new ProcessData(msg.data()));
        return Behaviors.same();
    }
    
    private Behavior<Command> handleBackpressure(
            ActorContext<Command> ctx, BackpressureSignal signal) {
        
        ctx.getLog().warn("Received backpressure signal from {}: queue {}/{}",
            signal.actorName(), signal.currentSize(), signal.capacity());
        
        backpressureActive = true;
        
        // Schedule to clear backpressure flag
        ctx.scheduleOnce(Duration.ofSeconds(5), ctx.getSelf(), 
            new ClearBackpressure());
        
        return Behaviors.same();
    }
}
```

---

## Spring Boot Configuration

### Configuration Properties

```java
@ConfigurationProperties(prefix = "spring.actor.mailbox")
public class ActorMailboxProperties {
    
    private DefaultConfig defaultConfig = new DefaultConfig();
    private Map<String, InstanceConfig> instances = new HashMap<>();
    
    public static class DefaultConfig {
        private String type = "unbounded";
        private Integer capacity = null;
        private OverflowStrategy overflowStrategy = OverflowStrategy.DROP_OLDEST;
        private Duration backpressureTimeout = Duration.ofSeconds(5);
    }
    
    public static class InstanceConfig {
        private Integer capacity;
        private OverflowStrategy overflowStrategy;
        private Duration backpressureTimeout;
    }
}
```

### Auto-Configuration

```java
@Configuration
@EnableConfigurationProperties(ActorMailboxProperties.class)
public class ActorMailboxAutoConfiguration {
    
    @Bean
    public MailboxConfigProvider mailboxConfigProvider(
            ActorMailboxProperties properties) {
        return new MailboxConfigProvider(properties);
    }
    
    @Bean
    public MailboxMetrics mailboxMetrics(MeterRegistry meterRegistry) {
        return new MailboxMetrics(meterRegistry);
    }
}
```

---

## Metrics

Expose mailbox metrics:

- `actor.mailbox.size` (Gauge) - Current mailbox size
  - Tags: `actor`
  
- `actor.mailbox.capacity` (Gauge) - Mailbox capacity
  - Tags: `actor`
  
- `actor.mailbox.utilization` (Gauge) - Size / Capacity ratio
  - Tags: `actor`
  
- `actor.mailbox.overflow` (Counter) - Overflow events
  - Tags: `actor`, `strategy`
  
- `actor.mailbox.drops` (Counter) - Dropped messages
  - Tags: `actor`, `drop_type` (oldest, newest, buffer)
  
- `actor.mailbox.backpressure` (Counter) - Backpressure signals sent
  - Tags: `actor`

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class BackpressureTest {
    
    @Test
    public void testDropOldest() {
        // Fill mailbox to capacity
        // Send additional messages
        // Verify oldest messages dropped
    }
    
    @Test
    public void testDropNewest() {
        // Fill mailbox
        // Verify new messages dropped
    }
    
    @Test
    public void testBackpressureSignal() {
        // Fill mailbox
        // Verify backpressure signal sent to sender
    }
    
    @Test
    public void testOverflowFail() {
        // Configure FAIL strategy
        // Fill mailbox
        // Verify exception thrown
    }
    
    @Test
    public void testMetrics() {
        // Verify overflow metrics recorded
    }
}
```

---

## Acceptance Criteria

- [ ] Bounded mailbox configuration implemented
- [ ] Multiple overflow strategies supported
- [ ] Backpressure signaling works
- [ ] `.withMailbox()` API added to behavior builder
- [ ] Spring Boot YAML configuration support
- [ ] Mailbox metrics exposed
- [ ] Auto-configuration for mailbox settings
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation with examples
- [ ] No breaking changes to existing API

---

## Example Configurations

### Drop Oldest (Default)
```yaml
spring:
  actor:
    mailbox:
      instances:
        data-processor:
          capacity: 1000
          overflow-strategy: DROP_OLDEST
```

### Fail Fast
```yaml
spring:
  actor:
    mailbox:
      instances:
        critical-actor:
          capacity: 100
          overflow-strategy: FAIL
```

### Backpressure
```yaml
spring:
  actor:
    mailbox:
      instances:
        stream-processor:
          capacity: 5000
          overflow-strategy: BACKPRESSURE
          backpressure-timeout: 10s
```

---

## Documentation

Update:
- Backpressure handling guide
- Mailbox configuration reference
- Overflow strategies documentation
- Best practices for capacity sizing
- Metrics and monitoring guide
