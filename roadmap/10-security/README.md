# 10. Security and Compliance

Security features for production deployments of actor systems.

---

## 10.1 TLS/SSL for Remote Actors

**Priority:** HIGH  
**Complexity:** Medium

### Overview

Secure actor-to-actor communication in cluster mode with TLS/SSL.

### Configuration

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          transport: tls-tcp
          ssl:
            enabled: true
            key-store: classpath:keystore.jks
            key-store-password: ${KEYSTORE_PASSWORD}
            trust-store: classpath:truststore.jks
            trust-store-password: ${TRUSTSTORE_PASSWORD}
            protocol: TLSv1.3
            enabled-algorithms:
              - TLS_AES_256_GCM_SHA384
              - TLS_AES_128_GCM_SHA256
```

### Certificate Management

```java
@Configuration
public class TLSConfiguration {
    
    @Bean
    public SSLContext actorSystemSSLContext() {
        // Custom SSL context configuration
        // Support cert rotation
        // Integration with cert managers (Let's Encrypt, etc.)
    }
}
```

---

## 10.2 Authentication and Authorization

**Priority:** HIGH  
**Complexity:** High

### Overview

Role-based access control for actor operations integrated with Spring Security.

### Implementation

```java
@Component
@Secured("ROLE_ADMIN")
public class AdminActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withAuthorization(AuthorizationConfig.builder()
                .requireRole("ADMIN")
                .requirePermission("admin:write")
                .build())
            .onMessage(AdminCommand.class, this::handleAdminCommand)
            .build();
    }
}

// Usage with security context
actorSystem.getOrSpawn(AdminActor.class, "admin")
    .withSecurityContext(SecurityContextHolder.getContext())
    .thenAccept(actor -> actor.tell(new AdminCommand()));
```

---

## 10.3 Audit Logging

**Priority:** MEDIUM  
**Complexity:** Low

### Overview

Automatic audit trail for sensitive operations with configurable masking.

### Implementation

```java
@Component
@Audited  // Enable audit logging
public class PaymentActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withAuditing(AuditConfig.builder()
                .includeMessagePayload(true)
                .maskSensitiveFields("creditCardNumber", "cvv", "ssn")
                .auditLevel(AuditLevel.INFO)
                .destination(AuditDestination.DATABASE)  // or KAFKA, FILE
                .build())
            .onMessage(ProcessPayment.class, this::handlePayment)
            .build();
    }
}
```

### Audit Log Format

```json
{
  "timestamp": "2024-11-08T08:00:00Z",
  "actorPath": "/user/payment-actor",
  "messageType": "ProcessPayment",
  "user": "admin@example.com",
  "payload": {
    "orderId": "order-123",
    "amount": 100.00,
    "creditCardNumber": "****-****-****-1234"  // Masked
  },
  "result": "SUCCESS",
  "duration": "150ms"
}
```

---

## 10.4 Message Encryption

**Priority:** MEDIUM  
**Complexity:** High

### Overview

End-to-end encryption for sensitive messages in transit.

### Implementation

```java
@Component
public class SecureActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withEncryption(EncryptionConfig.builder()
                .algorithm("AES-256-GCM")
                .keyProvider(keyManagementService)
                .encryptFields("sensitiveData", "personalInfo", "credentials")
                .build())
            .onMessage(SecureCommand.class, this::handleSecureCommand)
            .build();
    }
}
```

---

## 10.5 Rate Limiting per User

**Priority:** MEDIUM  
**Complexity:** Medium

### Overview

User-specific rate limiting to prevent abuse and ensure fair resource allocation.

### Implementation

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .withRateLimiting(RateLimitConfig.builder()
            .strategy(RateLimitStrategy.PER_USER)
            .maxRequestsPerMinute(100)
            .keyExtractor(cmd -> ((UserCommand) cmd).userId())
            .onLimitExceeded(LimitExceededAction.THROTTLE)
            .build())
        .onMessage(UserCommand.class, this::handleCommand)
        .build();
}
```

---

## Summary

Security features for production:

1. **TLS/SSL**: Secure cluster communication
2. **Authentication/Authorization**: Spring Security integration
3. **Audit Logging**: Compliance-ready audit trails
4. **Encryption**: End-to-end message encryption
5. **Rate Limiting**: Per-user resource protection

All designed for **enterprise security** requirements with **Spring Boot** integration.
