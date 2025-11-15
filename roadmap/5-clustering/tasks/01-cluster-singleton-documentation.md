# Task 1.1: Cluster Singleton Documentation

**Priority:** HIGH  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Document the existing cluster singleton functionality in the spring-boot-starter-actor project, providing comprehensive guidance on usage patterns, failover behavior, and testing strategies.

## Background

The codebase already supports cluster singletons through:
- `SpringActorSpawnBuilder.asClusterSingleton()` method
- `SpringActorSpawnBuilder.asClusterSingleton(boolean)` method
- Integration with Pekko's ClusterSingleton

Example from codebase (SpringActorSpawnBuilder.java):
```java
public SpringActorSpawnBuilder<A, C> asClusterSingleton() {
    return asClusterSingleton(true);
}
```

## Requirements

### 1. Verify Existing Implementation

- [x] Confirm `isClusterSingleton` flag exists in SpringActorSpawnBuilder
- [x] Verify integration with Pekko's ClusterSingleton
- [x] Check existing example in `example/cluster` module
- [ ] Review existing tests in `core/src/test/java/io/github/seonwkim/core/ClusterSingletonTest.java`

### 2. Document Usage Patterns

Create comprehensive documentation covering:

#### Basic Usage
```java
@Service
public class MyService {
    private final SpringActorSystem actorSystem;
    
    public void initializeSingleton() {
        SpringActorRef<Command> singleton = actorSystem
            .actor(MySingletonActor.class)
            .withId("my-singleton")
            .asClusterSingleton()  // Makes it a cluster singleton
            .spawnAndWait();
    }
}
```

#### Configuration Requirements
Document required application.yml settings:
```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster  # Required for cluster singletons
      cluster:
        seed-nodes:
          - "pekko://ActorSystem@127.0.0.1:2551"
```

#### Proxy Pattern
Explain how the proxy works:
- The returned reference is a proxy that routes messages
- Messages are automatically forwarded to the current singleton instance
- Location transparency - no need to track which node hosts the singleton

### 3. Document Failover Behavior

#### Automatic Failover
- When the node hosting the singleton fails
- How quickly a new singleton is started
- Message handling during failover
- Guarantees provided (at-most-once vs at-least-once)

#### Graceful Handover
- What happens during rolling updates
- How to ensure zero downtime
- Configuration for handover timeouts

### 4. Testing Guide

#### Local Mode Testing
Document the limitation that cluster singletons cannot be tested in local mode:
```java
@Test
public void testClusterSingletonInLocalModeFails() {
    // This will fail with IllegalStateException
    actorSystem.actor(SingletonActor.class)
        .withId("singleton")
        .asClusterSingleton()
        .spawn();
}
```

#### Cluster Mode Testing
Provide examples for testing in cluster mode:
- Setting up multi-node test clusters
- Simulating node failures
- Verifying singleton migration
- Testing message delivery during failover

### 5. Production Considerations

Document best practices:
- Lease configuration for split brain scenarios
- Monitoring singleton location
- Metrics for singleton migrations
- Health checks for singleton availability
- Considerations for stateful singletons

### 6. Common Patterns

#### Cluster Coordinator
```java
@Component
public class ClusterCoordinatorActor implements SpringActor<Command> {
    // Singleton that coordinates work across the cluster
}
```

#### Aggregator Pattern
```java
@Component
public class MetricsAggregatorActor implements SpringActor<Command> {
    // Singleton that collects metrics from all nodes
}
```

## Deliverables

1. **Documentation File**: `docs/clustering/cluster-singleton.md`
   - Complete usage guide
   - API reference
   - Configuration reference
   - Examples

2. **Testing Guide**: `docs/clustering/testing-cluster-singleton.md`
   - Setup instructions
   - Test examples
   - Common pitfalls

3. **Example Enhancement**: Enhance `example/cluster` with more documentation
   - Add inline comments
   - Add README explaining the example
   - Add troubleshooting section

## Success Criteria

- ✅ Comprehensive documentation covers all aspects of cluster singleton usage
- ✅ Developers can implement cluster singletons following the guide
- ✅ Testing guide enables writing reliable tests
- ✅ Production considerations help avoid common pitfalls
- ✅ Examples are clear and well-documented

## References

- Existing code: `core/src/main/java/io/github/seonwkim/core/SpringActorSpawnBuilder.java`
- Existing test: `core/src/test/java/io/github/seonwkim/core/ClusterSingletonTest.java`
- Existing example: `example/cluster/src/main/java/io/github/seonwkim/example/ClusterSingletonService.java`
- Pekko Cluster Singleton: https://pekko.apache.org/docs/pekko/current/typed/cluster-singleton.html
