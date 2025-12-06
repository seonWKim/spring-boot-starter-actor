# 5. Advanced Clustering Features

Enhanced clustering capabilities for production-grade distributed systems, noting existing support and suggesting improvements.

---

## 5.1 Cluster Singleton Support

**Priority:** HIGH
**Status:** ⚠️ **VERIFY IF ALREADY SUPPORTED**
**Action:** Check if cluster singleton exists in current codebase

### Note

The roadmap claims "The library already supports cluster singleton!" - **VERIFY THIS CLAIM** by checking:
1. Search codebase for `ClusterSingleton` annotation/class
2. Check if Pekko Cluster Singleton is wrapped
3. If exists: Document it thoroughly with examples
4. If doesn't exist: Implement wrapper around Pekko's cluster singleton

### Usage Example

```java
// Document how to use existing singleton support
@Component
@ClusterSingleton  // If this annotation exists
public class ClusterMasterActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(CoordinateTask.class, this::handleCoordination)
            .build();
    }
}
```

### Enhancement: Better Documentation and Examples

Provide clear documentation showing:
- How to configure cluster singleton in application.yml
- How to handle singleton failover
- How to test singleton behavior
- Production considerations (lease timeouts, handover)

---

## 5.2 Distributed Data (CRDTs) - Use Existing Ask Methods

**Priority:** MEDIUM  
**Approach:** Leverage existing `ask()` methods instead of direct CRDT access

### Design Philosophy

Rather than exposing Pekko's CRDT APIs directly, provide Spring Boot-friendly wrappers that use existing actor `ask()` patterns.

### Implementation Example

```java
// Wrap CRDT operations in actor commands
@Component
public class DistributedCacheActor implements SpringActor<Command> {
    
    private final Replicator<LWWMap<String, String>> replicator;
    
    public interface Command {}
    
    public record Get(String key) extends AskCommand<Optional<String>> implements Command {}
    public record Put(String key, String value) extends AskCommand<Done> implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(Get.class, this::handleGet)
            .onMessage(Put.class, this::handlePut)
            .build();
    }
    
    private Behavior<Command> handleGet(Get cmd) {
        // Use existing ask() infrastructure
        replicator.ask(/* CRDT get */)
            .thenAccept(value -> cmd.reply(value));
        return Behaviors.same();
    }
}

// Users interact with familiar actor API
@Service
public class CacheService {
    
    private final SpringActorSystem actorSystem;
    
    public CompletionStage<Optional<String>> get(String key) {
        return actorSystem.getOrSpawn(DistributedCacheActor.class, "cache")
            .thenCompose(actor -> actor
                .ask(new Get(key))
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }
}
```

**Benefits:**
- Users don't need to learn CRDT APIs
- Uses familiar actor `ask()` patterns
- Spring Boot friendly
- Easy to test

---

## 5.3 Split Brain Resolver with Explicit Tests

**Priority:** HIGH  
**Testing:** CRITICAL - Explicit tests required

### Overview

Provide robust split brain resolution with comprehensive test coverage to ensure reliability in production.

### Implementation

```yaml
# Spring Boot configuration
spring:
  actor:
    pekko:
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          active-strategy: keep-majority
          stable-after: 20s
          down-all-when-unstable: on
          keep-majority:
            role: ""
          keep-oldest:
            down-if-alone: on
```

### Explicit Test Requirements

```java
@SpringBootTest
@ClusterTest  // Custom annotation for cluster testing
public class SplitBrainResolverTest {
    
    @Test
    public void testKeepMajorityStrategy() {
        // Start 5-node cluster
        ClusterTestKit cluster = testKit.createCluster(5);
        
        // Simulate network partition (3 vs 2)
        cluster.partition(List.of(0, 1, 2), List.of(3, 4));
        
        // Wait for split brain resolution
        cluster.awaitStable(Duration.ofSeconds(30));
        
        // Verify: majority side (3 nodes) stays up
        assertTrue(cluster.isUp(0));
        assertTrue(cluster.isUp(1));
        assertTrue(cluster.isUp(2));
        
        // Verify: minority side (2 nodes) is down
        assertTrue(cluster.isDown(3));
        assertTrue(cluster.isDown(4));
    }
    
    @Test
    public void testKeepOldestStrategy() {
        ClusterTestKit cluster = testKit.createCluster(3);
        
        // Identify oldest node
        int oldestNode = cluster.getOldestNode();
        
        // Simulate partition
        cluster.partition(List.of(oldestNode), List.of(/* others */));
        
        cluster.awaitStable(Duration.ofSeconds(30));
        
        // Verify oldest node's partition stays up
        assertTrue(cluster.isUp(oldestNode));
    }
    
    @Test
    public void testStaticQuorumStrategy() {
        ClusterTestKit cluster = testKit.createCluster(5);
        
        // Configure: require quorum of 3
        cluster.setQuorum(3);
        
        // Partition into 2-node and 3-node sides
        cluster.partition(List.of(0, 1), List.of(2, 3, 4));
        
        cluster.awaitStable(Duration.ofSeconds(30));
        
        // Verify: 3-node side (meets quorum) stays up
        assertTrue(cluster.isUp(2));
        assertTrue(cluster.isUp(3));
        assertTrue(cluster.isUp(4));
        
        // Verify: 2-node side (below quorum) goes down
        assertTrue(cluster.isDown(0));
        assertTrue(cluster.isDown(1));
    }
}
```

### Production Monitoring

```java
@Component
public class SplitBrainHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        ClusterState state = getClusterState();
        
        if (state.hasUnreachableMembers()) {
            return Health.degraded()
                .withDetail("unreachableMembers", state.getUnreachableMembers())
                .withDetail("splitBrainRisk", "HIGH")
                .build();
        }
        
        return Health.up()
            .withDetail("clusterSize", state.getMembers().size())
            .withDetail("splitBrainRisk", "LOW")
            .build();
    }
}
```

---

## 5.4 Cluster Pub-Sub

**Priority:** MEDIUM  
**Complexity:** Medium

### Overview

Simple publish-subscribe messaging across cluster nodes using Spring Boot-friendly API.

### Implementation

```java
@Service
public class ClusterEventBus {
    
    private final SpringActorSystem actorSystem;
    
    // Subscribe to topic
    public void subscribe(String topic, SpringActorHandle<Event> subscriber) {
        actorSystem.receptionist()
            .register(ServiceKey.create(Event.class, topic), subscriber);
    }
    
    // Publish to topic
    public void publish(String topic, Event event) {
        actorSystem.receptionist()
            .find(ServiceKey.create(Event.class, topic))
            .thenAccept(listing -> {
                listing.getServiceInstances(ServiceKey.create(Event.class, topic))
                    .forEach(actor -> actor.tell(event));
            });
    }
}

// Usage
@Service
public class OrderService {
    
    private final ClusterEventBus eventBus;
    
    public void createOrder(Order order) {
        // Process order...
        
        // Publish event to all subscribers
        eventBus.publish("orders", new OrderCreated(order.getId()));
    }
}
```

---

## Summary

1. **Cluster Singleton**: Document existing support, add examples
2. **CRDTs**: Wrap in actor commands using existing ask() methods
3. **Split Brain Resolver**: Provide explicit, comprehensive tests
4. **Pub-Sub**: Simple Spring Boot-friendly API

Focus on leveraging existing infrastructure while providing excellent documentation and testing.
