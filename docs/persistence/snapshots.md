# Snapshot Strategies for Actor State

This guide covers snapshot strategies for efficiently persisting and recovering actor state in the spring-boot-starter-actor framework.

## Overview

Snapshots are point-in-time captures of actor state that enable fast recovery without replaying all events or rebuilding state from scratch. Snapshots are particularly useful for:
- Actors with long event histories
- Quick recovery after restarts
- Reducing memory pressure
- Optimizing cold starts

## Basic Snapshot Pattern

### 1. Define Snapshot Entity

```java
package com.example.order;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "actor_snapshots")
public class ActorSnapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "actor_id", nullable = false)
    private String actorId;
    
    @Column(name = "actor_type", nullable = false)
    private String actorType;
    
    @Column(name = "state_data", columnDefinition = "jsonb")
    private String stateData;
    
    @Column(name = "sequence_number")
    private Long sequenceNumber;  // For event-sourced actors
    
    @Column(name = "version")
    private Integer version;  // Snapshot schema version
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    // Constructors
    protected ActorSnapshot() {}
    
    public ActorSnapshot(String actorId, String actorType, String stateData) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.stateData = stateData;
        this.createdAt = Instant.now();
        this.version = 1;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public String getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getStateData() { return stateData; }
    public void setStateData(String stateData) { this.stateData = stateData; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
```

### 2. Create Snapshot Repository

```java
package com.example.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActorSnapshotRepository extends JpaRepository<ActorSnapshot, Long> {
    
    Optional<ActorSnapshot> findTopByActorIdAndActorTypeOrderByCreatedAtDesc(
        String actorId, String actorType);
    
    List<ActorSnapshot> findByActorIdAndActorTypeOrderByCreatedAtDesc(
        String actorId, String actorType);
    
    @Modifying
    @Query("DELETE FROM ActorSnapshot s WHERE s.actorId = :actorId " +
           "AND s.actorType = :actorType AND s.createdAt < :cutoff")
    int deleteOldSnapshots(
        @Param("actorId") String actorId,
        @Param("actorType") String actorType,
        @Param("cutoff") Instant cutoff);
    
    @Query("SELECT COUNT(s) FROM ActorSnapshot s WHERE s.actorId = :actorId " +
           "AND s.actorType = :actorType")
    long countSnapshots(@Param("actorId") String actorId, @Param("actorType") String actorType);
}
```

### 3. Implement Snapshot Strategy

```java
package com.example.order;

public interface SnapshotStrategy {
    boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot);
    boolean shouldLoadSnapshot();
}

public class TimeBasedSnapshotStrategy implements SnapshotStrategy {
    private final long intervalMillis;
    
    public TimeBasedSnapshotStrategy(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }
    
    @Override
    public boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot) {
        return timeSinceLastSnapshot >= intervalMillis;
    }
    
    @Override
    public boolean shouldLoadSnapshot() {
        return true;
    }
}

public class OperationCountSnapshotStrategy implements SnapshotStrategy {
    private final long operationInterval;
    
    public OperationCountSnapshotStrategy(long operationInterval) {
        this.operationInterval = operationInterval;
    }
    
    @Override
    public boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot) {
        return operationCount >= operationInterval;
    }
    
    @Override
    public boolean shouldLoadSnapshot() {
        return true;
    }
}

public class HybridSnapshotStrategy implements SnapshotStrategy {
    private final long operationInterval;
    private final long timeIntervalMillis;
    
    public HybridSnapshotStrategy(long operationInterval, long timeIntervalMillis) {
        this.operationInterval = operationInterval;
        this.timeIntervalMillis = timeIntervalMillis;
    }
    
    @Override
    public boolean shouldCreateSnapshot(long operationCount, long timeSinceLastSnapshot) {
        return operationCount >= operationInterval || 
               timeSinceLastSnapshot >= timeIntervalMillis;
    }
    
    @Override
    public boolean shouldLoadSnapshot() {
        return true;
    }
}
```

### 4. Actor with Snapshots

```java
package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SnapshotOrderActor implements SpringActor<SnapshotOrderActor.Command> {
    
    private final OrderRepository orderRepository;
    private final ActorSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    
    public SnapshotOrderActor(
            OrderRepository orderRepository,
            ActorSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }
    
    public interface Command {}
    
    public static class CreateOrder extends AskCommand<OrderResponse> implements Command {
        private final String customerId;
        private final double amount;
        
        public CreateOrder(String customerId, double amount) {
            this.customerId = customerId;
            this.amount = amount;
        }
        
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
    }
    
    public static class UpdateOrder extends AskCommand<OrderResponse> implements Command {
        private final double newAmount;
        
        public UpdateOrder(double newAmount) {
            this.newAmount = newAmount;
        }
        
        public double getNewAmount() { return newAmount; }
    }
    
    public static class SaveSnapshot implements Command {}
    
    public record OrderResponse(boolean success, Order order, String message) {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                // Load from snapshot if available
                Order order = loadFromSnapshot(actorContext.actorId())
                    .orElseGet(() -> 
                        orderRepository.findById(actorContext.actorId()).orElse(null));
                
                SnapshotStrategy strategy = new HybridSnapshotStrategy(
                    100,  // Every 100 operations
                    60000 // Or every 60 seconds
                );
                
                return new SnapshotOrderBehavior(
                    ctx, actorContext, orderRepository, snapshotRepository,
                    objectMapper, order, strategy);
            })
            .onMessage(CreateOrder.class, SnapshotOrderBehavior::handleCreateOrder)
            .onMessage(UpdateOrder.class, SnapshotOrderBehavior::handleUpdateOrder)
            .onMessage(SaveSnapshot.class, SnapshotOrderBehavior::handleSaveSnapshot)
            .build();
    }
    
    private Optional<Order> loadFromSnapshot(String actorId) {
        try {
            return snapshotRepository
                .findTopByActorIdAndActorTypeOrderByCreatedAtDesc(
                    actorId, "OrderActor")
                .map(snapshot -> {
                    try {
                        return objectMapper.readValue(
                            snapshot.getStateData(), Order.class);
                    } catch (Exception e) {
                        return null;
                    }
                });
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    private static class SnapshotOrderBehavior {
        private final ActorContext<Command> ctx;
        private final SpringActorContext actorContext;
        private final OrderRepository orderRepository;
        private final ActorSnapshotRepository snapshotRepository;
        private final ObjectMapper objectMapper;
        private final SnapshotStrategy strategy;
        
        private Order currentOrder;
        private long operationCount;
        private Instant lastSnapshotTime;
        
        SnapshotOrderBehavior(
                ActorContext<Command> ctx,
                SpringActorContext actorContext,
                OrderRepository orderRepository,
                ActorSnapshotRepository snapshotRepository,
                ObjectMapper objectMapper,
                Order currentOrder,
                SnapshotStrategy strategy) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            this.orderRepository = orderRepository;
            this.snapshotRepository = snapshotRepository;
            this.objectMapper = objectMapper;
            this.currentOrder = currentOrder;
            this.strategy = strategy;
            this.operationCount = 0;
            this.lastSnapshotTime = Instant.now();
        }
        
        private Behavior<Command> handleCreateOrder(CreateOrder cmd) {
            if (currentOrder != null) {
                cmd.reply(new OrderResponse(false, null, "Order already exists"));
                return Behaviors.same();
            }
            
            try {
                currentOrder = new Order();
                currentOrder.setOrderId(actorContext.actorId());
                currentOrder.setCustomerId(cmd.getCustomerId());
                currentOrder.setAmount(cmd.getAmount());
                currentOrder.setStatus(OrderStatus.PENDING);
                
                currentOrder = orderRepository.save(currentOrder);
                operationCount++;
                
                saveSnapshotIfNeeded();
                
                cmd.reply(new OrderResponse(true, currentOrder, "Order created"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to create order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleUpdateOrder(UpdateOrder cmd) {
            if (currentOrder == null) {
                cmd.reply(new OrderResponse(false, null, "Order not found"));
                return Behaviors.same();
            }
            
            try {
                currentOrder.setAmount(cmd.getNewAmount());
                currentOrder = orderRepository.save(currentOrder);
                operationCount++;
                
                saveSnapshotIfNeeded();
                
                cmd.reply(new OrderResponse(true, currentOrder, "Order updated"));
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to update order", e);
                cmd.reply(new OrderResponse(false, null, e.getMessage()));
            }
            
            return Behaviors.same();
        }
        
        private Behavior<Command> handleSaveSnapshot(SaveSnapshot cmd) {
            saveSnapshot();
            return Behaviors.same();
        }
        
        private void saveSnapshotIfNeeded() {
            long timeSinceLastSnapshot = 
                Instant.now().toEpochMilli() - lastSnapshotTime.toEpochMilli();
            
            if (strategy.shouldCreateSnapshot(operationCount, timeSinceLastSnapshot)) {
                saveSnapshot();
                operationCount = 0;
                lastSnapshotTime = Instant.now();
            }
        }
        
        private void saveSnapshot() {
            if (currentOrder == null) {
                return;
            }
            
            try {
                String stateData = objectMapper.writeValueAsString(currentOrder);
                
                ActorSnapshot snapshot = new ActorSnapshot(
                    actorContext.actorId(),
                    "OrderActor",
                    stateData
                );
                
                snapshotRepository.save(snapshot);
                
                ctx.getLog().info("Snapshot saved for order {}", actorContext.actorId());
                
                // Clean up old snapshots (keep only last 5)
                cleanupOldSnapshots();
                
            } catch (Exception e) {
                ctx.getLog().error("Failed to save snapshot", e);
            }
        }
        
        private void cleanupOldSnapshots() {
            try {
                long count = snapshotRepository.countSnapshots(
                    actorContext.actorId(), "OrderActor");
                
                if (count > 5) {
                    // Keep only recent 5 snapshots, delete older ones
                    Instant cutoff = Instant.now().minusSeconds(3600); // 1 hour old
                    snapshotRepository.deleteOldSnapshots(
                        actorContext.actorId(), "OrderActor", cutoff);
                }
            } catch (Exception e) {
                ctx.getLog().warn("Failed to cleanup old snapshots", e);
            }
        }
    }
}
```

## Advanced Snapshot Patterns

### Pattern 1: Snapshot Versioning

```java
@Entity
@Table(name = "actor_snapshots")
public class VersionedSnapshot {
    
    @Id
    @GeneratedValue
    private Long id;
    
    private String actorId;
    private String stateData;
    
    @Column(name = "schema_version")
    private Integer schemaVersion;
    
    // Migration from v1 to v2
    public static class SnapshotMigrator {
        
        public String migrateV1ToV2(String v1Data) {
            // Parse v1 format
            OrderStateV1 v1 = parseV1(v1Data);
            
            // Convert to v2 format
            OrderStateV2 v2 = new OrderStateV2();
            v2.setOrderId(v1.getId());
            v2.setCustomerId(v1.getCustomer());
            v2.setAmount(v1.getTotal());
            v2.setStatus(v1.getState());
            v2.setCurrency("USD"); // New field with default
            
            return serializeV2(v2);
        }
    }
}
```

### Pattern 2: Compressed Snapshots

```java
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.Base64;

public class CompressedSnapshotService {
    
    private final ActorSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    
    public void saveCompressedSnapshot(String actorId, Object state) throws Exception {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(state);
        
        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        // Encode to Base64
        String compressed = Base64.getEncoder().encodeToString(baos.toByteArray());
        
        // Save
        ActorSnapshot snapshot = new ActorSnapshot(actorId, "OrderActor", compressed);
        snapshotRepository.save(snapshot);
    }
    
    public <T> T loadCompressedSnapshot(String actorId, Class<T> stateClass) 
            throws Exception {
        
        ActorSnapshot snapshot = snapshotRepository
            .findTopByActorIdAndActorTypeOrderByCreatedAtDesc(actorId, "OrderActor")
            .orElseThrow();
        
        // Decode from Base64
        byte[] compressed = Base64.getDecoder().decode(snapshot.getStateData());
        
        // Decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            String json = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, stateClass);
        }
    }
}
```

### Pattern 3: Incremental Snapshots

```java
@Entity
public class IncrementalSnapshot {
    @Id
    private String actorId;
    
    @Column(name = "base_snapshot")
    private String baseSnapshot;
    
    @Column(name = "delta_snapshot")
    private String deltaSnapshot;
    
    private Instant baseTimestamp;
    private Instant deltaTimestamp;
}

public class IncrementalSnapshotStrategy {
    
    private Order previousState;
    private Order currentState;
    
    public String createDelta() throws Exception {
        if (previousState == null) {
            // First snapshot - full state
            return objectMapper.writeValueAsString(currentState);
        }
        
        // Create delta object with only changed fields
        OrderDelta delta = new OrderDelta();
        
        if (!Objects.equals(previousState.getAmount(), currentState.getAmount())) {
            delta.setAmount(currentState.getAmount());
        }
        
        if (!Objects.equals(previousState.getStatus(), currentState.getStatus())) {
            delta.setStatus(currentState.getStatus());
        }
        
        return objectMapper.writeValueAsString(delta);
    }
    
    public Order applyDelta(Order base, String deltaJson) throws Exception {
        OrderDelta delta = objectMapper.readValue(deltaJson, OrderDelta.class);
        
        Order result = base.clone();
        
        if (delta.getAmount() != null) {
            result.setAmount(delta.getAmount());
        }
        
        if (delta.getStatus() != null) {
            result.setStatus(delta.getStatus());
        }
        
        return result;
    }
}
```

### Pattern 4: Distributed Snapshots

```java
@Component
public class DistributedSnapshotService {
    
    private final ActorSnapshotRepository localRepository;
    private final S3Client s3Client;  // Or other object storage
    
    public void saveDistributedSnapshot(String actorId, Object state) throws Exception {
        String json = objectMapper.writeValueAsString(state);
        
        // Save locally for fast access
        ActorSnapshot localSnapshot = new ActorSnapshot(actorId, "OrderActor", json);
        localRepository.save(localSnapshot);
        
        // Also save to distributed storage for durability
        String s3Key = String.format("snapshots/%s/%s.json", 
            actorId, Instant.now().toEpochMilli());
        
        s3Client.putObject(PutObjectRequest.builder()
            .bucket("actor-snapshots")
            .key(s3Key)
            .build(),
            RequestBody.fromString(json));
    }
    
    public <T> Optional<T> loadDistributedSnapshot(String actorId, Class<T> stateClass) {
        // Try local first
        Optional<ActorSnapshot> local = localRepository
            .findTopByActorIdAndActorTypeOrderByCreatedAtDesc(actorId, "OrderActor");
        
        if (local.isPresent()) {
            try {
                return Optional.of(objectMapper.readValue(
                    local.get().getStateData(), stateClass));
            } catch (Exception e) {
                // Fall through to S3
            }
        }
        
        // Fallback to S3
        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket("actor-snapshots")
                    .prefix("snapshots/" + actorId + "/")
                    .maxKeys(1)
                    .build());
            
            if (!response.contents().isEmpty()) {
                String key = response.contents().get(0).key();
                ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                        .bucket("actor-snapshots")
                        .key(key)
                        .build());
                
                return Optional.of(objectMapper.readValue(
                    object.asUtf8String(), stateClass));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        
        return Optional.empty();
    }
}
```

## Snapshot Management

### Cleanup Strategy

```java
@Component
public class SnapshotCleanupService {
    
    private final ActorSnapshotRepository snapshotRepository;
    
    @Scheduled(cron = "0 0 2 * * *")  // Run at 2 AM daily
    public void cleanupOldSnapshots() {
        Instant cutoff = Instant.now().minusDays(30);
        
        // Keep only last 10 snapshots per actor
        List<String> actorIds = snapshotRepository.findDistinctActorIds();
        
        for (String actorId : actorIds) {
            List<ActorSnapshot> snapshots = snapshotRepository
                .findByActorIdAndActorTypeOrderByCreatedAtDesc(actorId, "OrderActor");
            
            if (snapshots.size() > 10) {
                List<ActorSnapshot> toDelete = snapshots.subList(10, snapshots.size());
                snapshotRepository.deleteAll(toDelete);
            }
        }
        
        // Delete very old snapshots regardless
        snapshotRepository.deleteOldSnapshots(cutoff);
    }
}
```

### Monitoring

```java
@Component
public class SnapshotMetrics {
    
    private final MeterRegistry meterRegistry;
    private final ActorSnapshotRepository snapshotRepository;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void recordMetrics() {
        // Snapshot count
        long count = snapshotRepository.count();
        meterRegistry.gauge("actor.snapshots.total", count);
        
        // Average snapshot size
        double avgSize = snapshotRepository.getAverageSnapshotSize();
        meterRegistry.gauge("actor.snapshots.avg_size", avgSize);
        
        // Oldest snapshot age
        Optional<ActorSnapshot> oldest = snapshotRepository.findOldest();
        oldest.ifPresent(snapshot -> {
            long age = Duration.between(snapshot.getCreatedAt(), Instant.now()).toHours();
            meterRegistry.gauge("actor.snapshots.oldest_age_hours", age);
        });
    }
}
```

## Configuration

```yaml
# application.yml
actor:
  snapshot:
    enabled: true
    strategy: hybrid  # time, count, or hybrid
    operation-interval: 100
    time-interval-seconds: 60
    compression:
      enabled: true
      algorithm: gzip
    retention:
      max-snapshots-per-actor: 10
      max-age-days: 30
    storage:
      type: database  # database, s3, redis
      s3:
        bucket: actor-snapshots
        region: us-east-1
```

## Best Practices

1. **Choose appropriate snapshot frequency**
   - Too frequent: Wastes storage and I/O
   - Too infrequent: Slow recovery

2. **Use compression for large states**
   - Reduces storage costs
   - Faster network transfers

3. **Implement snapshot versioning**
   - Handle schema evolution
   - Backward compatibility

4. **Monitor snapshot sizes**
   - Alert on unusually large snapshots
   - Investigate state bloat

5. **Test recovery from snapshots**
   - Verify snapshots are valid
   - Test migration paths

6. **Consider distributed storage**
   - Local cache for speed
   - Remote storage for durability

7. **Implement cleanup policies**
   - Prevent unbounded growth
   - Balance storage vs. recovery time

## Summary

Snapshots provide:
- ✅ Fast actor recovery
- ✅ Reduced memory pressure
- ✅ Better cold start performance
- ✅ Efficient state persistence

Choose snapshot strategies based on:
- Actor state size
- Change frequency
- Recovery time requirements
- Storage constraints
