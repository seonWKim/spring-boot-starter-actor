# Task 2.3: Deduplication Configuration and Monitoring

**Priority:** HIGH
**Estimated Effort:** 2-3 days
**Dependencies:** Task 2.1, Task 2.2
**Assignee:** AI Agent

---

## Objective

Implement Spring Boot YAML configuration, auto-configuration, and metrics for message deduplication.

---

## Requirements

### 1. Spring Boot Configuration

```yaml
spring:
  actor:
    deduplication:
      enabled: true
      backend: caffeine  # or redis, hybrid
      
      # Default configuration for all actors
      defaults:
        cache-size: 10000
        ttl: 5m
        on-duplicate: IGNORE  # or LOG, REJECT, CALL_HANDLER
      
      # Local cache (Caffeine) configuration
      local:
        enabled: true
        cache-size: 10000
        ttl: 5m
        fallback-enabled: true  # Use as fallback when Redis fails
      
      # Distributed cache (Redis) configuration
      redis:
        enabled: false
        key-prefix: "actor:dedup:"
        ttl: 5m
        connection-timeout: 1s
        operation-timeout: 500ms
      
      # Per-actor overrides
      instances:
        order-actor:
          cache-size: 50000
          ttl: 10m
        payment-actor:
          backend: redis
          ttl: 15m
```

### 2. Configuration Properties

```java
@ConfigurationProperties(prefix = "spring.actor.deduplication")
public class ActorDeduplicationProperties {
    
    private boolean enabled = true;
    private DeduplicationBackend backend = DeduplicationBackend.CAFFEINE;
    private DefaultConfig defaults = new DefaultConfig();
    private LocalConfig local = new LocalConfig();
    private RedisConfig redis = new RedisConfig();
    private Map<String, InstanceConfig> instances = new HashMap<>();
    
    public static class DefaultConfig {
        private int cacheSize = 10000;
        private Duration ttl = Duration.ofMinutes(5);
        private DuplicateAction onDuplicate = DuplicateAction.IGNORE;
    }
    
    public static class LocalConfig {
        private boolean enabled = true;
        private int cacheSize = 10000;
        private Duration ttl = Duration.ofMinutes(5);
        private boolean fallbackEnabled = true;
    }
    
    public static class RedisConfig {
        private boolean enabled = false;
        private String keyPrefix = "actor:dedup:";
        private Duration ttl = Duration.ofMinutes(5);
        private Duration connectionTimeout = Duration.ofSeconds(1);
        private Duration operationTimeout = Duration.ofMillis(500);
    }
    
    public static class InstanceConfig {
        private DeduplicationBackend backend;
        private Integer cacheSize;
        private Duration ttl;
        private DuplicateAction onDuplicate;
    }
}
```

### 3. Auto-Configuration

```java
@Configuration
@EnableConfigurationProperties(ActorDeduplicationProperties.class)
@ConditionalOnProperty(name = "spring.actor.deduplication.enabled", havingValue = "true", matchIfMissing = true)
public class ActorDeduplicationAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public DeduplicationCacheProvider deduplicationCacheProvider(
            ActorDeduplicationProperties properties,
            @Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
        
        return new DeduplicationCacheProvider(properties, redisTemplate);
    }
    
    @Bean
    public DeduplicationMetrics deduplicationMetrics(MeterRegistry meterRegistry) {
        return new DeduplicationMetrics(meterRegistry);
    }
}
```

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/deduplication/ActorDeduplicationProperties.java`**
   - Configuration properties class
   - Nested classes for local, Redis, instance config

2. **`core/src/main/java/io/github/seonwkim/core/deduplication/ActorDeduplicationAutoConfiguration.java`**
   - Auto-configuration for deduplication
   - Bean registration based on configuration

3. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationCacheProvider.java`**
   - Factory for creating deduplication caches
   - Supports backend selection and configuration

4. **`core/src/main/java/io/github/seonwkim/core/deduplication/DeduplicationMetrics.java`**
   - Metrics collection for deduplication
   - Integration with Micrometer

5. **`core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**
   - Register auto-configuration

---

## DeduplicationCacheProvider Implementation

```java
public class DeduplicationCacheProvider {
    
    private final ActorDeduplicationProperties properties;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Map<String, DeduplicationCache> caches = new ConcurrentHashMap<>();
    
    public DeduplicationCache getCache(String actorName) {
        return caches.computeIfAbsent(actorName, name -> createCache(name));
    }
    
    private DeduplicationCache createCache(String actorName) {
        // Get instance-specific config or use defaults
        InstanceConfig instanceConfig = properties.getInstances().get(actorName);
        DeduplicationBackend backend = instanceConfig != null && instanceConfig.getBackend() != null
            ? instanceConfig.getBackend()
            : properties.getBackend();
        
        switch (backend) {
            case CAFFEINE:
                return createCaffeineCache(actorName, instanceConfig);
            case REDIS:
                return createRedisCache(actorName, instanceConfig);
            case HYBRID:
                return createHybridCache(actorName, instanceConfig);
            default:
                throw new IllegalArgumentException("Unknown backend: " + backend);
        }
    }
    
    private DeduplicationCache createCaffeineCache(String actorName, InstanceConfig config) {
        int cacheSize = config != null && config.getCacheSize() != null
            ? config.getCacheSize()
            : properties.getDefaults().getCacheSize();
        Duration ttl = config != null && config.getTtl() != null
            ? config.getTtl()
            : properties.getDefaults().getTtl();
        
        return new CaffeineDeduplicationCache(cacheSize, ttl);
    }
    
    private DeduplicationCache createRedisCache(String actorName, InstanceConfig config) {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                "Redis backend configured but ReactiveRedisTemplate not available. " +
                "Add spring-boot-starter-data-redis-reactive dependency.");
        }
        
        Duration ttl = config != null && config.getTtl() != null
            ? config.getTtl()
            : properties.getRedis().getTtl();
        
        return new RedisDeduplicationCache(
            redisTemplate,
            properties.getRedis().getKeyPrefix() + actorName + ":",
            ttl
        );
    }
    
    private DeduplicationCache createHybridCache(String actorName, InstanceConfig config) {
        CaffeineDeduplicationCache localCache = 
            (CaffeineDeduplicationCache) createCaffeineCache(actorName, config);
        RedisDeduplicationCache redisCache = 
            (RedisDeduplicationCache) createRedisCache(actorName, config);
        
        return new HybridDeduplicationCache(
            redisCache,
            localCache,
            properties.getLocal().isFallbackEnabled()
        );
    }
}
```

---

## Metrics Implementation

```java
public class DeduplicationMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordCheck(String actorName, boolean isDuplicate) {
        Counter.builder("actor.deduplication.messages.total")
            .tag("actor", actorName)
            .tag("duplicate", String.valueOf(isDuplicate))
            .register(meterRegistry)
            .increment();
    }
    
    public void recordCacheStats(String actorName, CacheStats stats) {
        Gauge.builder("actor.deduplication.cache.size", stats, CacheStats::estimatedSize)
            .tag("actor", actorName)
            .register(meterRegistry);
        
        Gauge.builder("actor.deduplication.cache.hit_rate", stats, CacheStats::hitRate)
            .tag("actor", actorName)
            .register(meterRegistry);
    }
}
```

### Exposed Metrics

- `actor.deduplication.messages.total` (Counter)
  - Tags: `actor`, `duplicate` (true/false)
  
- `actor.deduplication.cache.size` (Gauge)
  - Tags: `actor`
  
- `actor.deduplication.cache.hit_rate` (Gauge)
  - Tags: `actor`
  
- `actor.deduplication.cache.evictions` (Counter)
  - Tags: `actor`

---

## Integration with SpringActorBehavior

```java
// In SpringActorBehavior.Builder

private DeduplicationConfig deduplicationConfig = null;

public Builder<C, S> withDeduplication(DeduplicationConfig config) {
    this.deduplicationConfig = config;
    return this;
}

// Or use automatic configuration from properties
public Builder<C, S> withDeduplication(String actorName) {
    DeduplicationCacheProvider provider = 
        actorContext.applicationContext().getBean(DeduplicationCacheProvider.class);
    DeduplicationCache cache = provider.getCache(actorName);
    
    this.deduplicationConfig = DeduplicationConfig.builder()
        .cache(cache)
        .build();
    
    return this;
}
```

---

## Testing Requirements

### Unit Tests

```java
@SpringBootTest
public class DeduplicationConfigTest {
    
    @Test
    public void testDefaultConfiguration() {
        // Verify defaults applied
    }
    
    @Test
    public void testInstanceConfiguration() {
        // Verify per-instance overrides
    }
    
    @Test
    public void testBackendSelection() {
        // Test Caffeine, Redis, Hybrid backends
    }
    
    @Test
    public void testMetrics() {
        // Verify metrics are recorded
    }
    
    @Test
    public void testAutoConfiguration() {
        // Verify beans created correctly
    }
}
```

### Integration Tests

```java
@SpringBootTest(properties = {
    "spring.actor.deduplication.enabled=true",
    "spring.actor.deduplication.backend=caffeine"
})
public class DeduplicationIntegrationTest {
    
    @Autowired
    private DeduplicationCacheProvider cacheProvider;
    
    @Test
    public void testConfigurationLoaded() {
        assertNotNull(cacheProvider);
    }
}
```

---

## Acceptance Criteria

- [ ] YAML configuration for deduplication works
- [ ] Default configuration applied to all actors
- [ ] Per-instance configuration overrides defaults
- [ ] Auto-configuration activates based on properties
- [ ] Backend selection works (Caffeine, Redis, Hybrid)
- [ ] Metrics exposed via Micrometer
- [ ] DeduplicationCacheProvider manages cache instances
- [ ] Configuration can be disabled via `enabled: false`
- [ ] Comprehensive tests for all configuration scenarios
- [ ] Documentation with configuration examples

---

## Configuration Examples

### Single Node (Caffeine)
```yaml
spring:
  actor:
    deduplication:
      enabled: true
      backend: caffeine
      defaults:
        cache-size: 10000
        ttl: 5m
```

### Clustered (Redis)
```yaml
spring:
  actor:
    deduplication:
      enabled: true
      backend: redis
      redis:
        enabled: true
        key-prefix: "myapp:dedup:"
        ttl: 10m
  redis:
    host: redis.example.com
    port: 6379
```

### Hybrid (Redis with Fallback)
```yaml
spring:
  actor:
    deduplication:
      enabled: true
      backend: hybrid
      local:
        fallback-enabled: true
        cache-size: 1000
      redis:
        enabled: true
        ttl: 10m
```

---

## Documentation

Update:
- Configuration reference
- Metrics documentation
- Auto-configuration guide
- Backend selection guide
