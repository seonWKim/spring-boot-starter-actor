# Production Configuration Guide for Persistence

This guide covers production-ready configuration for persistence in actor systems, including connection pooling, retry strategies, health checks, and monitoring.

## Overview

Production actor systems require robust persistence configuration to handle:
- High concurrency and throughput
- Transient failures and retries
- Connection pool management
- Health monitoring
- Performance optimization

## Connection Pool Configuration

### HikariCP (JPA/JDBC)

HikariCP is the default connection pool for Spring Boot and provides excellent performance.

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/actordb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    
    hikari:
      # Pool sizing
      maximum-pool-size: 20          # Max connections (should be tuned based on load)
      minimum-idle: 5                # Min idle connections
      
      # Connection lifecycle
      max-lifetime: 1800000          # 30 minutes - max connection lifetime
      idle-timeout: 600000           # 10 minutes - max idle time
      connection-timeout: 30000      # 30 seconds - max wait for connection
      
      # Validation
      connection-test-query: SELECT 1  # Health check query
      validation-timeout: 5000         # 5 seconds - validation timeout
      
      # Leak detection (useful for debugging)
      leak-detection-threshold: 60000  # 60 seconds - warn if connection held too long
      
      # Pool behavior
      auto-commit: false              # Manual transaction control
      allow-pool-suspension: false    # Don't allow pool suspension
      
      # Metrics
      register-mbeans: true           # Enable JMX monitoring
      
      # Pool name for logging
      pool-name: ActorDB-Pool
```

**Sizing Guidelines:**
- **maximum-pool-size**: CPU cores * 2 to 4 (e.g., 8 cores → 16-32 connections)
- **minimum-idle**: 20-50% of maximum-pool-size
- Adjust based on actual load testing results

### R2DBC Connection Pool

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/actordb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    
    pool:
      # Pool sizing
      initial-size: 10               # Initial connections
      max-size: 50                   # Maximum connections
      
      # Connection lifecycle
      max-lifetime: 30m              # Max connection lifetime
      max-idle-time: 30m             # Max idle time
      max-acquire-time: 3s           # Max time to acquire connection
      max-create-connection-time: 5s # Max time to create connection
      
      # Validation
      validation-query: SELECT 1
      validation-depth: LOCAL        # LOCAL or REMOTE
      
      # Metrics
      metrics-enabled: true
```

### MongoDB Connection Pool

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/actordb
      # Connection pool settings in URI
      uri: mongodb://localhost:27017/actordb?maxPoolSize=50&minPoolSize=10&maxIdleTimeMS=60000&waitQueueTimeoutMS=30000
      
      # Or as separate properties:
      # maxPoolSize: 50
      # minPoolSize: 10
      # maxIdleTimeMS: 60000
      # waitQueueTimeoutMS: 30000
      # maxConnectionLifeTimeMS: 1800000
```

## Retry Configuration

### Using Spring Retry

Add dependency:
```gradle
implementation 'org.springframework.retry:spring-retry'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

#### Method-Level Retries

```java
@Configuration
@EnableRetry
public class RetryConfiguration {
    
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Retry policy - 3 attempts
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Backoff policy - exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);   // 1 second
        backOffPolicy.setMultiplier(2.0);         // Double each time
        backOffPolicy.setMaxInterval(10000);      // Max 10 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }
}

@Service
public class OrderPersistenceService {
    
    private final OrderRepository orderRepository;
    private final RetryTemplate retryTemplate;
    
    @Retryable(
        value = { DataAccessException.class, TransientDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public Order saveOrderWithRetry(Order order) {
        return orderRepository.save(order);
    }
    
    @Recover
    public Order recoverFromSaveFailure(DataAccessException e, Order order) {
        // Called after all retry attempts fail
        log.error("Failed to save order after retries: {}", order.getId(), e);
        throw new PersistenceException("Unable to save order", e);
    }
}
```

#### Configuration-Based Retries

```yaml
# application.yml
spring:
  retry:
    max-attempts: 3
    backoff:
      initial-interval: 1000
      multiplier: 2
      max-interval: 10000
```

### Using Resilience4j

Add dependency:
```gradle
implementation 'io.github.resilience4j:resilience4j-spring-boot3'
implementation 'io.github.resilience4j:resilience4j-reactor'
```

```yaml
# application.yml
resilience4j:
  retry:
    instances:
      orderPersistence:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.dao.TransientDataAccessException
          - org.springframework.dao.RecoverableDataAccessException
        ignore-exceptions:
          - org.springframework.dao.DataIntegrityViolationException
          
  circuitbreaker:
    instances:
      orderPersistence:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

```java
@Component
public class ResilientOrderService {
    
    private final OrderRepository orderRepository;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    
    public ResilientOrderService(
            OrderRepository orderRepository,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderRepository = orderRepository;
        this.retry = retryRegistry.retry("orderPersistence");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("orderPersistence");
    }
    
    public Order saveOrderResilient(Order order) {
        return Retry.decorateSupplier(retry, () ->
            CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                orderRepository.save(order)
            ).get()
        ).get();
    }
    
    // Reactive version
    public Mono<Order> saveOrderReactive(Order order) {
        return Mono.fromSupplier(() -> orderRepository.save(order))
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
```

## Health Checks

### Spring Boot Actuator

Add dependency:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
      probes:
        enabled: true  # Enable liveness and readiness probes
  
  health:
    # Database health check
    db:
      enabled: true
    
    # MongoDB health check  
    mongo:
      enabled: true
    
    # Disk space health check
    diskspace:
      enabled: true
      threshold: 10GB
  
  metrics:
    enable:
      jdbc: true
      hikaricp: true
      mongodb: true
```

### Custom Health Indicators

```java
@Component
public class ActorPersistenceHealthIndicator implements HealthIndicator {
    
    private final OrderRepository orderRepository;
    private final ActorSnapshotRepository snapshotRepository;
    
    @Override
    public Health health() {
        try {
            // Check database connectivity
            long orderCount = orderRepository.count();
            long snapshotCount = snapshotRepository.count();
            
            Map<String, Object> details = new HashMap<>();
            details.put("orderCount", orderCount);
            details.put("snapshotCount", snapshotCount);
            details.put("timestamp", Instant.now());
            
            return Health.up()
                .withDetails(details)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}

@Component
public class ConnectionPoolHealthIndicator implements HealthIndicator {
    
    private final HikariDataSource dataSource;
    
    @Override
    public Health health() {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            
            int activeConnections = poolMXBean.getActiveConnections();
            int idleConnections = poolMXBean.getIdleConnections();
            int totalConnections = poolMXBean.getTotalConnections();
            int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();
            
            Map<String, Object> details = new HashMap<>();
            details.put("active", activeConnections);
            details.put("idle", idleConnections);
            details.put("total", totalConnections);
            details.put("waiting", threadsAwaitingConnection);
            details.put("maxPoolSize", dataSource.getMaximumPoolSize());
            
            Health.Builder builder = Health.up();
            
            // Warning if pool is nearly exhausted
            double utilization = (double) activeConnections / dataSource.getMaximumPoolSize();
            if (utilization > 0.9) {
                builder = Health.status("DEGRADED");
                details.put("warning", "Connection pool utilization above 90%");
            }
            
            // Critical if threads are waiting
            if (threadsAwaitingConnection > 0) {
                builder = Health.down();
                details.put("error", "Threads waiting for connections");
            }
            
            return builder.withDetails(details).build();
            
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

### Kubernetes Probes

```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: actor-service
spec:
  template:
    spec:
      containers:
      - name: actor-service
        image: actor-service:latest
        ports:
        - containerPort: 8080
        
        # Liveness probe - restart if fails
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        # Readiness probe - remove from service if fails
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
```

## Monitoring and Metrics

### Micrometer Metrics

```java
@Configuration
public class PersistenceMetricsConfiguration {
    
    @Bean
    public MeterBinder databaseMetrics(OrderRepository orderRepository) {
        return registry -> {
            // Custom metrics
            Gauge.builder("actor.orders.total", orderRepository, OrderRepository::count)
                .description("Total number of orders")
                .register(registry);
            
            // Timer for persistence operations
            Timer.builder("actor.persistence.save")
                .description("Time to save actor state")
                .register(registry);
        };
    }
}

@Component
public class OrderMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Timer saveTimer;
    private final Counter saveSuccessCounter;
    private final Counter saveFailureCounter;
    
    public OrderMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.saveTimer = Timer.builder("actor.order.save.time")
            .description("Time to save order")
            .tag("entity", "order")
            .register(meterRegistry);
        
        this.saveSuccessCounter = Counter.builder("actor.order.save.success")
            .description("Successful order saves")
            .register(meterRegistry);
        
        this.saveFailureCounter = Counter.builder("actor.order.save.failure")
            .description("Failed order saves")
            .register(meterRegistry);
    }
    
    public Order saveWithMetrics(Order order, OrderRepository repository) {
        return saveTimer.record(() -> {
            try {
                Order saved = repository.save(order);
                saveSuccessCounter.increment();
                return saved;
            } catch (Exception e) {
                saveFailureCounter.increment();
                throw e;
            }
        });
    }
}
```

### Prometheus Integration

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  
  metrics:
    export:
      prometheus:
        enabled: true
    
    tags:
      application: actor-service
      environment: ${ENVIRONMENT:dev}
    
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 10ms,50ms,100ms,200ms,500ms
```

Add dependency:
```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### Logging Configuration

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.example.order: DEBUG
    org.springframework.jdbc.core: DEBUG  # SQL logging
    org.hibernate.SQL: DEBUG              # Hibernate SQL
    org.hibernate.type.descriptor.sql: TRACE  # SQL parameters
    com.zaxxer.hikari: DEBUG             # HikariCP logging
    io.r2dbc.postgresql.QUERY: DEBUG     # R2DBC query logging
    org.springframework.data.mongodb.core: DEBUG  # MongoDB logging
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: logs/actor-service.log
    max-size: 10MB
    max-history: 30
```

### Distributed Tracing

```gradle
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-zipkin'
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% in dev, lower in prod (e.g., 0.1)
  
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

## Performance Optimization

### Query Optimization

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        # Enable batch operations
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
        
        # Query caching
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
        
        # Statistics for monitoring
        generate_statistics: true
```

### Caching Configuration

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=600s
    cache-names:
      - orders
      - order-snapshots
```

```java
@Configuration
@EnableCaching
public class CacheConfiguration {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("orders", "order-snapshots");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}

@Service
public class CachedOrderService {
    
    @Cacheable(value = "orders", key = "#orderId")
    public Order findOrder(String orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }
    
    @CacheEvict(value = "orders", key = "#order.id")
    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }
}
```

## Security Configuration

### Database Credentials

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:actordb}
    username: ${DB_USERNAME}  # From environment variable
    password: ${DB_PASSWORD}  # From environment variable or secrets manager
```

### SSL/TLS Connection

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/actordb?ssl=true&sslmode=require
  
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/actordb?sslMode=require
  
  data:
    mongodb:
      uri: mongodb://localhost:27017/actordb?ssl=true&tls=true
```

### Encrypted Properties

```yaml
# application.yml
spring:
  datasource:
    username: ENC(encrypted_username)
    password: ENC(encrypted_password)
```

## Complete Production Configuration Example

```yaml
# application-prod.yml
spring:
  # Database configuration
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?ssl=true&sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      max-lifetime: 1800000
      idle-timeout: 600000
      connection-timeout: 30000
      leak-detection-threshold: 60000
      pool-name: ActorDB-Pool
  
  # JPA configuration
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
        generate_statistics: false
  
  # Caching
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=600s

# Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10GB
  
  metrics:
    enable:
      hikaricp: true
    export:
      prometheus:
        enabled: true

# Retry configuration
resilience4j:
  retry:
    instances:
      persistence:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
  
  circuitbreaker:
    instances:
      persistence:
        sliding-window-size: 100
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s

# Logging
logging:
  level:
    root: INFO
    com.example: DEBUG
  file:
    name: /var/log/actor-service/application.log
    max-size: 10MB
    max-history: 30
```

## Deployment Checklist

- [ ] Connection pool sized appropriately
- [ ] Retry logic configured
- [ ] Circuit breakers enabled
- [ ] Health checks implemented
- [ ] Metrics exported to monitoring system
- [ ] Logging configured for production
- [ ] Database credentials secured
- [ ] SSL/TLS enabled for database connections
- [ ] Connection leak detection enabled
- [ ] Backup and recovery tested
- [ ] Load testing completed
- [ ] Alerting rules configured
- [ ] Runbooks created for common issues

## Summary

Production persistence configuration requires:
- ✅ Properly sized connection pools
- ✅ Robust retry and circuit breaker strategies  
- ✅ Comprehensive health checks
- ✅ Detailed metrics and monitoring
- ✅ Secure credential management
- ✅ Optimized query performance
- ✅ Reliable backup and recovery

Follow this guide to build resilient, production-ready actor systems with reliable persistence.
