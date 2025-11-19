# Task 3.2: Audit Destinations

**Priority:** MEDIUM
**Estimated Effort:** 1 week
**Dependencies:** Task 3.1 (Audit Logging)

---

## Overview

Implement multiple audit destinations (Database, Kafka, File) with configurable routing and query API.

---

## Requirements

### 1. Audit Destination Interface

```java
public interface AuditDestination {
    void write(AuditEvent event);
    CompletableFuture<Void> writeAsync(AuditEvent event);
}
```

### 2. Database Destination

```java
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findByActorType(String actorType);
    List<AuditEventEntity> findByTimestampBetween(Instant start, Instant end);
}
```

### 3. Kafka Destination

```java
public class KafkaAuditDestination implements AuditDestination {
    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    
    @Override
    public CompletableFuture<Void> writeAsync(AuditEvent event) {
        return kafkaTemplate.send("audit-events", event.getId(), event)
            .completable()
            .thenApply(result -> null);
    }
}
```

### 4. File Destination

```java
public class FileAuditDestination implements AuditDestination {
    private final Path auditLogPath;
    
    @Override
    public void write(AuditEvent event) {
        String json = objectMapper.writeValueAsString(event);
        Files.write(auditLogPath, (json + "\n").getBytes(), 
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
```

### 5. Query API

```java
@RestController
@RequestMapping("/api/audit")
public class AuditQueryController {
    
    @GetMapping
    public Page<AuditEvent> queryAudits(
        @RequestParam(required = false) String actorType,
        @RequestParam(required = false) String messageType,
        @RequestParam(required = false) Instant startTime,
        @RequestParam(required = false) Instant endTime,
        Pageable pageable
    ) {
        // Query audit events
    }
    
    @GetMapping("/export")
    public void exportAudits(
        @RequestParam String format, // CSV, JSON
        HttpServletResponse response
    ) {
        // Export audit events
    }
}
```

---

## Configuration

```yaml
spring:
  actor:
    audit:
      destinations:
        database:
          enabled: true
        kafka:
          enabled: true
          topic: audit-events
        file:
          enabled: false
          path: /var/log/actor-audit.log
      routing:
        PaymentActor:
          - database
          - kafka
        default:
          - database
```

---

## Deliverables

1. ✅ AuditDestination interface
2. ✅ Database destination
3. ✅ Kafka destination
4. ✅ File destination
5. ✅ Query API
6. ✅ Export functionality
7. ✅ Tests

---

## Success Criteria

- [ ] Multiple destinations work
- [ ] Routing configuration works
- [ ] Query API works
- [ ] Export functionality works
- [ ] All tests pass

