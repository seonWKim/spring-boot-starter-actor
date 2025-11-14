# Task 3.1: Audit Logging Implementation

**Priority:** MEDIUM
**Estimated Effort:** 1 week
**Dependencies:** None (can be implemented independently)

---

## Overview

Implement automatic audit trail for actor operations with @Audited annotation and field masking for sensitive data.

---

## Requirements

### 1. @Audited Annotation

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /**
     * Audit level
     */
    AuditLevel level() default AuditLevel.INFO;
    
    /**
     * Whether to include message payload
     */
    boolean includePayload() default true;
    
    /**
     * Fields to mask in payload
     */
    String[] maskFields() default {};
}
```

### 2. Field Masking

```java
public class FieldMasker {
    private static final Map<String, Pattern> SENSITIVE_PATTERNS = Map.of(
        "password", Pattern.compile("password", Pattern.CASE_INSENSITIVE),
        "creditCard", Pattern.compile("(credit.*card|card.*number)", Pattern.CASE_INSENSITIVE),
        "ssn", Pattern.compile("(ssn|social.*security)", Pattern.CASE_INSENSITIVE)
    );
    
    public Object maskSensitiveFields(Object payload, String[] additionalFields) {
        // Mask predefined sensitive fields
        // Mask additional configured fields
        // Return masked copy
    }
}
```

### 3. Audit Event Model

```java
@Data
public class AuditEvent {
    private String id;
    private Instant timestamp;
    private String actorPath;
    private String actorType;
    private String messageType;
    private String user;
    private Object payload;
    private String result;
    private Long durationMs;
    private Map<String, String> metadata;
}
```

### 4. Audit Interceptor

```java
public class AuditInterceptor {
    public <T> T intercept(
        Object actor,
        Method method,
        Object message,
        Supplier<T> proceed
    ) {
        Instant start = Instant.now();
        Audited annotation = method.getAnnotation(Audited.class);
        
        try {
            T result = proceed.get();
            logAuditEvent(actor, method, message, result, start, "SUCCESS");
            return result;
        } catch (Exception e) {
            logAuditEvent(actor, method, message, null, start, "FAILURE");
            throw e;
        }
    }
}
```

---

## Configuration

```yaml
spring:
  actor:
    audit:
      enabled: true
      mask-sensitive-data: true
      sensitive-field-patterns:
        - password
        - creditCard
        - ssn
        - apiKey
      include-payload: true
      include-result: false
```

---

## Deliverables

1. ✅ @Audited annotation
2. ✅ FieldMasker implementation
3. ✅ AuditEvent model
4. ✅ AuditInterceptor
5. ✅ Configuration support
6. ✅ Tests for masking
7. ✅ Documentation

---

## Success Criteria

- [ ] @Audited annotation works
- [ ] Sensitive fields are masked
- [ ] Audit events captured correctly
- [ ] Performance impact is minimal
- [ ] All tests pass

