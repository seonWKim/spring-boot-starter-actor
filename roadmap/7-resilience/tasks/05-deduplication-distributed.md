# Task 2.2: Distributed Message Deduplication with Redis

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** Task 2.1 (Local Deduplication)
**Assignee:** AI Agent

---

## Objective

Implement distributed message deduplication using Redis to prevent duplicate message processing across cluster nodes.

---

## Requirements

### 1. Redis-Based Deduplication

```java
@Component
public class ClusteredOrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDeduplication(DeduplicationConfig.builder()
                .backend(DeduplicationBackend.REDIS)
                .ttl(Duration.ofMinutes(5))
                .idExtractor(msg -> ((OrderMessage) msg).orderId())
                .build())
            .onMessage(ProcessOrder.class, this::handleOrder)
            .build();
    }
}
```

### 2. Redis Implementation

```java
public class RedisDeduplicationCache implements DeduplicationCache {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    private final Duration ttl;
    
    @Override
    public CompletionStage<Boolean> isDuplicate(String messageId) {
        String key = keyPrefix + messageId;
        
        // Use SET NX (set if not exists) with expiration
        return redisTemplate.opsForValue()
            .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), ttl)
            .map(success -> !success)  // If set failed, it's a duplicate
            .defaultIfEmpty(true)
            .toFuture();
    }
    
    @Override
    public CompletionStage<Void> markAsProcessed(String messageId) {
        String key = keyPrefix + messageId;
        return redisTemplate.opsForValue()
            .set(key, String.valueOf(System.currentTimeMillis()), ttl)
            .then()
            .toFuture();
    }
}
```

### 3. Async Deduplication Check

Since Redis operations are async, support non-blocking deduplication:

```java
// Option 1: Async check before processing
.withDeduplication(DeduplicationConfig.builder()
    .backend(DeduplicationBackend.REDIS)
    .async(true)  // Check asynchronously
    .build())

// The behavior wrapper handles async:
public Behavior<C> onMessage(C msg) {
    String messageId = idExtractor.extract(msg);
    if (messageId != null) {
        cache.isDuplicate(messageId).thenAccept(isDupe -> {
            if (!isDupe) {
                // Process message
                userHandler.apply(msg);
            } else {
                // Handle duplicate
                handleDuplicate(msg);
            }
        });
    }
    return Behaviors.same();
}
```

### 4. Fallback to Local Cache

For resilience, support fallback to local cache if Redis is unavailable:

```java
public class HybridDeduplicationCache implements DeduplicationCache {
    
    private final RedisDeduplicationCache redisCache;
    private final CaffeineDeduplicationCache localCache;
    private final boolean fallbackToLocal;
    
    @Override
    public CompletionStage<Boolean> isDuplicate(String messageId) {
        return redisCache.isDuplicate(messageId)
            .exceptionally(ex -> {
                if (fallbackToLocal) {
                    log.warn("Redis unavailable, falling back to local cache", ex);
                    return localCache.isDuplicate(messageId);
                }
                throw new CompletionException(ex);
            });
    }
}
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/deduplication/RedisDeduplicationCache.java`**
   - Redis-based implementation
   - Async operations with CompletionStage

2. **`core/src/main/java/io/github/seonwkim/core/deduplication/HybridDeduplicationCache.java`**
   - Combines Redis and local cache
   - Fallback logic

3. **`core/src/main/java/io/github/seonwkim/core/deduplication/AsyncDeduplicationBehavior.java`**
   - Async behavior wrapper for Redis
   - Non-blocking message processing

4. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationBackend.java`**
   - Enum for backend types
   - CAFFEINE, REDIS, HYBRID

### Files to Modify

1. **`core/build.gradle.kts`**
   - Add Spring Data Redis dependencies:
   ```kotlin
   compileOnly("org.springframework.boot:spring-boot-starter-data-redis-reactive")
   compileOnly("io.lettuce:lettuce-core")
   ```

2. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationConfig.java`**
   - Add backend selection
   - Add async configuration
   - Add fallback configuration

---

## Configuration

### Spring Boot YAML

```yaml
spring:
  actor:
    deduplication:
      enabled: true
      backend: redis  # or caffeine, hybrid
      redis:
        enabled: true
        key-prefix: "actor:dedup:"
        ttl: 5m
      local:
        fallback-enabled: true  # Use local cache if Redis fails
        cache-size: 1000
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
```

### Auto-Configuration

```java
@Configuration
@ConditionalOnClass(ReactiveRedisTemplate.class)
@EnableConfigurationProperties(ActorDeduplicationProperties.class)
public class RedisDeduplicationAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "spring.actor.deduplication.backend", havingValue = "redis")
    public DeduplicationCache redisDeduplicationCache(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ActorDeduplicationProperties properties) {
        
        if (properties.getLocal().isFallbackEnabled()) {
            // Create hybrid cache with fallback
            CaffeineDeduplicationCache localCache = new CaffeineDeduplicationCache(properties);
            RedisDeduplicationCache redisCache = new RedisDeduplicationCache(
                redisTemplate, properties.getRedis());
            
            return new HybridDeduplicationCache(redisCache, localCache, true);
        } else {
            // Redis only
            return new RedisDeduplicationCache(redisTemplate, properties.getRedis());
        }
    }
}
```

---

## Distributed Deduplication Guarantees

### Atomicity
Use Redis SET NX with expiration for atomic check-and-set:
```
SET actor:dedup:{messageId} {timestamp} NX EX {ttl_seconds}
```

### Consistency
- Redis provides strong consistency within a single instance
- For Redis Cluster, use hash tags to ensure related keys go to same slot

### Partition Tolerance
- Use fallback to local cache if Redis is unreachable
- Configure appropriate timeouts

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
@Testcontainers
public class RedisDeduplicationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);
    
    @Test
    public void testDistributedDeduplication() {
        // Send same message to different nodes
        // Verify only one processes it
    }
    
    @Test
    public void testRedisTTLExpiration() {
        // Verify keys expire correctly in Redis
    }
    
    @Test
    public void testFallbackToLocal() {
        // Stop Redis
        // Verify fallback to local cache
    }
    
    @Test
    public void testAsyncDeduplication() {
        // Test non-blocking deduplication
    }
}
```

### Integration Tests

```java
@SpringBootTest
public class ClusteredDeduplicationTest {
    
    @Test
    public void testClusterWideDeduplication() {
        // Start multiple actor systems
        // Send same message to different nodes
        // Verify only one processes it
    }
}
```

---

## Acceptance Criteria

- [ ] Redis-based deduplication implemented
- [ ] Async deduplication support
- [ ] Fallback to local cache when Redis unavailable
- [ ] Atomic check-and-set operations using Redis SET NX
- [ ] TTL-based expiration in Redis
- [ ] Spring Boot auto-configuration
- [ ] YAML configuration support
- [ ] Comprehensive tests including Testcontainers
- [ ] Documentation with Redis setup
- [ ] Consistent behavior with local deduplication

---

## Performance Considerations

### Optimization Strategies

1. **Connection Pooling**
   - Use Lettuce connection pooling
   - Configure appropriate pool size

2. **Pipeline Operations**
   - Batch Redis operations when possible
   - Use pipelining for multiple checks

3. **Local Cache Layer**
   - Hybrid approach: Check local cache first, then Redis
   - Reduces Redis load for recently seen messages

```java
public class OptimizedHybridCache implements DeduplicationCache {
    
    @Override
    public CompletionStage<Boolean> isDuplicate(String messageId) {
        // Check local cache first (fast)
        if (localCache.isDuplicate(messageId)) {
            return CompletableFuture.completedFuture(true);
        }
        
        // Check Redis (slower but distributed)
        return redisCache.isDuplicate(messageId).thenApply(isDupe -> {
            if (isDupe) {
                // Update local cache
                localCache.markAsProcessed(messageId);
            }
            return isDupe;
        });
    }
}
```

---

## Documentation

Update:
- Distributed deduplication guide
- Redis setup instructions
- Configuration reference
- Performance tuning guide
- Troubleshooting section
