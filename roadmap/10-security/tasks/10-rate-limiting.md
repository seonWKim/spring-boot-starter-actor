# Task 5.1: Per-User Rate Limiting

**Priority:** MEDIUM (Optional - Week 12)
**Estimated Effort:** 2-3 weeks
**Dependencies:** Task 2.1 (Spring Security Integration) recommended

---

## Overview

Implement per-user rate limiting to prevent abuse and ensure fair resource allocation. This integrates with the existing throttling infrastructure and Spring Security context.

---

## Requirements

### 1. Rate Limiter

```java
public interface RateLimiter {
    /**
     * Check if request is allowed for user
     */
    boolean tryAcquire(String userId);
    
    /**
     * Get remaining quota for user
     */
    long getRemainingQuota(String userId);
    
    /**
     * Reset quota for user
     */
    void reset(String userId);
}
```

### 2. @RateLimited Annotation

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    int maxRequests() default 100;
    TimeUnit timeUnit() default TimeUnit.MINUTES;
    String keyExtractor() default "userId";
}
```

### 3. User Identity Extraction

```java
public class UserIdentityExtractor {
    public String extractUserId(SecurityContext context) {
        Authentication auth = context.getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }
}
```

### 4. Rate Limit Interceptor

```java
public class RateLimitInterceptor {
    public <T> T intercept(
        Object message,
        Supplier<T> proceed
    ) {
        String userId = extractUserId(message);
        
        if (!rateLimiter.tryAcquire(userId)) {
            throw new RateLimitExceededException(
                "Rate limit exceeded for user: " + userId
            );
        }
        
        return proceed.get();
    }
}
```

---

## Configuration

```yaml
spring:
  actor:
    rate-limiting:
      enabled: true
      default-limit: 100
      default-window: 1m
      
      # Per-actor limits
      actors:
        PaymentActor:
          limit: 10
          window: 1m
        AdminActor:
          limit: 1000
          window: 1h
          
      # Per-role limits
      roles:
        ROLE_USER:
          limit: 100
          window: 1m
        ROLE_PREMIUM:
          limit: 1000
          window: 1m
```

---

## Metrics

```java
@Component
public class RateLimitMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordRateLimitHit(String userId, String actorType) {
        meterRegistry.counter("actor.rate_limit.hit",
            "user", userId,
            "actor_type", actorType
        ).increment();
    }
    
    public void recordRateLimitExceeded(String userId, String actorType) {
        meterRegistry.counter("actor.rate_limit.exceeded",
            "user", userId,
            "actor_type", actorType
        ).increment();
    }
}
```

---

## Deliverables

1. ✅ RateLimiter interface and implementation
2. ✅ @RateLimited annotation
3. ✅ UserIdentityExtractor
4. ✅ RateLimitInterceptor
5. ✅ Configuration support
6. ✅ Metrics integration
7. ✅ Tests

---

## Success Criteria

- [ ] Per-user rate limiting works
- [ ] Configuration supports per-actor limits
- [ ] Rate limit metrics are collected
- [ ] Clear errors when limit exceeded
- [ ] All tests pass

---

## Notes

- This phase is OPTIONAL (Week 12)
- Only implement if time permits
- Should integrate with existing throttling infrastructure
- Consider distributed rate limiting for cluster deployments

