# Task 2.1: Local Message Deduplication with Caffeine

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Implement in-memory message deduplication using Caffeine cache to prevent duplicate message processing in single-node deployments.

---

## Requirements

### 1. Core Deduplication

Detect and prevent duplicate messages based on message IDs:

```java
@Component
public class OrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDeduplication(DeduplicationConfig.builder()
                .enabled(true)
                .backend(DeduplicationBackend.CAFFEINE)
                .cacheSize(10000)
                .ttl(Duration.ofMinutes(5))
                .idExtractor(msg -> ((IdentifiableMessage) msg).messageId())
                .onDuplicate(DuplicateAction.IGNORE)
                .build())
            .onMessage(ProcessOrder.class, this::handleOrder)
            .build();
    }
}
```

### 2. Message ID Extraction Strategies

Support multiple ways to extract message IDs:

```java
// 1. Interface-based
public interface IdentifiableMessage {
    String messageId();
}

// 2. Custom extractor function
.idExtractor(msg -> {
    if (msg instanceof OrderCommand) {
        return ((OrderCommand) msg).getOrderId();
    }
    return null;  // No deduplication for this message
})

// 3. Hash-based (automatic)
.idExtractor(MessageIdExtractors.hashBased())  // Uses message hashCode()

// 4. Field-based (reflection)
.idExtractor(MessageIdExtractors.field("orderId"))
```

### 3. Duplicate Handling Actions

Configure what happens when duplicate is detected:

```java
public enum DuplicateAction {
    IGNORE,           // Silently ignore duplicate
    LOG,              // Log and ignore
    REJECT,           // Reply with error
    CALL_HANDLER      // Call special duplicate handler
}

// Example with custom handler
.withDeduplication(DeduplicationConfig.builder()
    .onDuplicate(DuplicateAction.CALL_HANDLER)
    .duplicateHandler((ctx, msg) -> {
        ctx.getLog().warn("Duplicate detected: {}", msg);
        if (msg instanceof AskCommand) {
            ((AskCommand<?>) msg).replyError(new DuplicateMessageException());
        }
        return Behaviors.same();
    })
    .build())
```

### 4. Caffeine Cache Configuration

```java
public class CaffeineDeduplicationCache implements DeduplicationCache {
    
    private final Cache<String, Long> cache;
    
    public CaffeineDeduplicationCache(DeduplicationConfig config) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(config.getCacheSize())
            .expireAfterWrite(config.getTtl())
            .recordStats()  // For metrics
            .build();
    }
    
    @Override
    public boolean isDuplicate(String messageId) {
        Long timestamp = cache.getIfPresent(messageId);
        if (timestamp != null) {
            return true;
        }
        cache.put(messageId, System.currentTimeMillis());
        return false;
    }
}
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationConfig.java`**
   - Configuration class for deduplication
   - Builder pattern support

2. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationCache.java`**
   - Interface for deduplication cache
   - Support for multiple backends

3. **`core/src/main/java/io/github/seonwkim/core/deduplication/CaffeineDeduplicationCache.java`**
   - Caffeine-based implementation
   - In-memory cache with TTL

4. **`core/src/main/java/io/github/seonwkim/core/deduplication/MessageIdExtractor.java`**
   - Interface for extracting message IDs
   - Various implementations

5. **`core/src/main/java/io/github/seonwkim/core/deduplication/MessageIdExtractors.java`**
   - Utility class with common extractors
   - Hash-based, field-based, etc.

6. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationBehavior.java`**
   - Behavior wrapper for deduplication
   - Intercepts messages and checks for duplicates

7. **`core/src/main/java/io/github/seonwkim/core/SpringActorBehavior.java`** (modify)
   - Add `.withDeduplication()` method to builder

### Files to Modify

1. **`core/build.gradle.kts`**
   - Add Caffeine dependency:
   ```kotlin
   implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
   ```

---

## Deduplication Config Structure

```java
public class DeduplicationConfig {
    private final boolean enabled;
    private final DeduplicationBackend backend;
    private final int cacheSize;
    private final Duration ttl;
    private final MessageIdExtractor idExtractor;
    private final DuplicateAction onDuplicate;
    private final BiFunction<ActorContext<?>, Object, Behavior<?>> duplicateHandler;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private boolean enabled = true;
        private DeduplicationBackend backend = DeduplicationBackend.CAFFEINE;
        private int cacheSize = 10000;
        private Duration ttl = Duration.ofMinutes(5);
        private MessageIdExtractor idExtractor;
        private DuplicateAction onDuplicate = DuplicateAction.IGNORE;
        
        public Builder enabled(boolean enabled) { ... }
        public Builder backend(DeduplicationBackend backend) { ... }
        public Builder cacheSize(int cacheSize) { ... }
        public Builder ttl(Duration ttl) { ... }
        public Builder idExtractor(MessageIdExtractor extractor) { ... }
        public Builder onDuplicate(DuplicateAction action) { ... }
        
        public DeduplicationConfig build() { ... }
    }
}
```

---

## Example Usage

### Basic Deduplication
```java
@Component
public class PaymentActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDeduplication(DeduplicationConfig.builder()
                .cacheSize(5000)
                .ttl(Duration.ofMinutes(10))
                .idExtractor(msg -> {
                    if (msg instanceof ProcessPayment) {
                        return ((ProcessPayment) msg).transactionId();
                    }
                    return null;
                })
                .build())
            .onMessage(ProcessPayment.class, this::processPayment)
            .build();
    }
}
```

### With Custom Duplicate Handler
```java
.withDeduplication(DeduplicationConfig.builder()
    .idExtractor(msg -> ((OrderMessage) msg).orderId())
    .onDuplicate(DuplicateAction.CALL_HANDLER)
    .duplicateHandler((ctx, msg) -> {
        ctx.getLog().warn("Duplicate order: {}", msg);
        // Send acknowledgment even for duplicates
        if (msg instanceof AskCommand) {
            ((AskCommand<?>) msg).reply(new OrderAlreadyProcessed());
        }
        return Behaviors.same();
    })
    .build())
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class DeduplicationTest {
    
    @Test
    public void testDuplicateMessagesIgnored() {
        // Send same message twice
        // Verify only processed once
    }
    
    @Test
    public void testTTLExpiration() {
        // Send message
        // Wait for TTL to expire
        // Send same message again
        // Verify it's processed
    }
    
    @Test
    public void testCacheSizeLimit() {
        // Send more messages than cache size
        // Verify old entries evicted
    }
    
    @Test
    public void testCustomIdExtractor() {
        // Test custom ID extraction logic
    }
    
    @Test
    public void testDuplicateHandler() {
        // Test custom duplicate handling
    }
}
```

---

## Acceptance Criteria

- [ ] Caffeine cache integrated for deduplication
- [ ] Message ID extraction strategies implemented
- [ ] TTL-based expiration works correctly
- [ ] Cache size limits enforced
- [ ] Multiple duplicate actions supported (IGNORE, LOG, REJECT, CALL_HANDLER)
- [ ] `.withDeduplication()` API added to behavior builder
- [ ] Comprehensive tests (>80% coverage)
- [ ] Documentation with examples
- [ ] No breaking changes to existing API

---

## Metrics

Expose deduplication metrics:
- `deduplication.messages.total` (Counter) - Total messages checked
- `deduplication.messages.duplicate` (Counter) - Duplicates detected
- `deduplication.cache.size` (Gauge) - Current cache size
- `deduplication.cache.hit_rate` (Gauge) - Cache hit rate

---

## Documentation

Update:
- README.md with deduplication examples
- mkdocs/docs/ with comprehensive guide
- JavaDoc on all public APIs
