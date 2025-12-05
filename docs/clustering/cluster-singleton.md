# Cluster Singleton

## Overview

A **cluster singleton** is an actor that runs on exactly one node in the cluster at any given time. If the node hosting the singleton fails, it automatically migrates to another node. This is useful for:

- **Cluster coordinators** that manage distributed work
- **Aggregators** that collect data from all nodes
- **Singleton services** that must have only one instance (e.g., schedulers, leaders)

The spring-boot-starter-actor library provides built-in support for cluster singletons through Pekko's ClusterSingleton feature.

## How It Works

When you spawn an actor as a cluster singleton:

1. The actor is created on one node (typically the oldest node in the cluster)
2. You receive a **proxy reference** that routes messages to the actual singleton
3. The proxy automatically handles:
   - **Location transparency**: You don't need to know which node hosts the singleton
   - **Automatic failover**: If the singleton node fails, it migrates to another node
   - **Message routing**: All messages are automatically forwarded to the current singleton

## Basic Usage

### 1. Define Your Singleton Actor

```java
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.springframework.stereotype.Component;

@Component
public class ClusterCoordinatorActor implements SpringActor<Command> {
    
    public interface Command extends JsonSerializable {}
    
    public record CoordinateTask(String taskId) extends AskCommand<String> implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onStart(context -> {
                context.getLog().info("Cluster coordinator singleton started on this node");
            })
            .onMessage(CoordinateTask.class, this::handleCoordination)
            .build();
    }
    
    private Behavior<Command> handleCoordination(ActorContext<Command> ctx, CoordinateTask cmd) {
        ctx.getLog().info("Coordinating task: {}", cmd.taskId());
        cmd.reply("Task coordinated by singleton on " + ctx.getSelf().path());
        return Behaviors.same();
    }
}
```

### 2. Spawn as Cluster Singleton

```java
import io.github.seonwkim.core.SpringActorSystem;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;

@Service
public class CoordinatorService {
    
    private final SpringActorSystem actorSystem;
    private SpringActorRef<ClusterCoordinatorActor.Command> coordinator;
    
    public CoordinatorService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    @PostConstruct
    public void init() {
        // Spawn as cluster singleton
        coordinator = actorSystem
            .actor(ClusterCoordinatorActor.class)
            .withId("cluster-coordinator")
            .asClusterSingleton()  // ⬅️ This makes it a singleton
            .spawnAndWait();
        
        System.out.println("Cluster singleton proxy initialized");
    }
    
    public CompletionStage<String> coordinateTask(String taskId) {
        // Send message to the singleton (wherever it is)
        return coordinator
            .ask(new ClusterCoordinatorActor.CoordinateTask(taskId))
            .withTimeout(Duration.ofSeconds(3))
            .execute();
    }
}
```

### 3. Configure Your Application

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster  # ⬅️ Required for cluster singletons
      
      cluster:
        seed-nodes:
          - "pekko://ActorSystem@127.0.0.1:2551"
          - "pekko://ActorSystem@127.0.0.1:2552"
        
        # Optional: Configure singleton settings
        singleton:
          # Time to wait for graceful handover
          hand-over-retry-interval: 1s
          min-number-of-hand-over-retries: 15
```

## Key Concepts

### Proxy vs Actual Singleton

- **Proxy**: The `SpringActorRef` you get when spawning is a lightweight proxy
- **Actual Singleton**: The real actor instance running on one node
- **Message Flow**: Messages sent to proxy → routed through cluster → delivered to actual singleton

```
┌────────────┐         ┌────────────┐         ┌────────────┐
│  Node 1    │         │  Node 2    │         │  Node 3    │
│            │         │            │         │            │
│  Proxy ────┼────────▶│  Proxy ────┼────────▶│ SINGLETON  │
│            │         │            │         │  (actual)  │
└────────────┘         └────────────┘         └────────────┘
```

### Spawning Multiple Times is Safe

```java
// First spawn creates the singleton
SpringActorRef<Command> ref1 = actorSystem.actor(MySingletonActor.class)
    .withId("my-singleton")
    .asClusterSingleton()
    .spawnAndWait();

// Subsequent spawns return the same proxy
SpringActorRef<Command> ref2 = actorSystem.actor(MySingletonActor.class)
    .withId("my-singleton")
    .asClusterSingleton()
    .spawnAndWait();

// ref1 and ref2 point to the same singleton
```

## Failover Behavior

### Automatic Migration

When the node hosting the singleton fails:

1. **Detection**: Cluster detects the node is unreachable (via heartbeats)
2. **Down Decision**: Cluster downing provider decides to remove the node
3. **Migration**: Singleton is started on another node (typically the next oldest)
4. **Reconnection**: Proxy automatically reconnects to the new singleton

```
Before Failure:
┌────────────┐         ┌────────────┐
│  Node 1    │         │  Node 2    │
│            │         │            │
│  Proxy     │────────▶│ SINGLETON  │
│            │         │  (active)  │
└────────────┘         └────────────┘

After Node 2 Fails:
┌────────────┐         ┌────────────┐
│  Node 1    │         │  Node 3    │
│            │         │            │
│  Proxy ────┼────────▶│ SINGLETON  │
│            │         │  (migrated)│
└────────────┘         └────────────┘
```

### Graceful Handover

During rolling updates or planned shutdowns:

1. **Handover Started**: Old singleton prepares to terminate
2. **New Singleton Started**: On another node
3. **State Transfer**: Optional state can be transferred
4. **Old Singleton Stopped**: After handover completes

```yaml
# Configure handover timeout
spring:
  actor:
    pekko:
      cluster:
        singleton:
          hand-over-retry-interval: 1s
          min-number-of-hand-over-retries: 15
```

### Message Delivery During Failover

- **In-flight messages**: May be lost if singleton fails
- **Message buffer**: Proxy buffers messages during migration
- **Retry**: Application should implement retry logic for critical operations

```java
// Implement retry logic
public CompletionStage<String> coordinateTaskWithRetry(String taskId) {
    return coordinator
        .ask(new CoordinateTask(taskId))
        .withTimeout(Duration.ofSeconds(3))
        .onTimeout(() -> {
            // Retry on timeout (may be due to failover)
            return coordinator
                .ask(new CoordinateTask(taskId))
                .withTimeout(Duration.ofSeconds(3))
                .execute()
                .toCompletableFuture()
                .join();
        })
        .execute();
}
```

## Common Patterns

### 1. Metrics Aggregator

Collect metrics from all nodes in the cluster:

```java
@Component
public class MetricsAggregatorActor implements SpringActor<Command> {
    
    private final Map<String, Map<String, Long>> metrics = new ConcurrentHashMap<>();
    
    public record RecordMetric(String nodeAddress, String metricName, long value, long timestamp) 
        implements Command {}
    
    public record GetMetrics() extends AskCommand<Map<String, Map<String, Long>>> implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(RecordMetric.class, (context, msg) -> {
                metrics
                    .computeIfAbsent(msg.nodeAddress(), k -> new HashMap<>())
                    .put(msg.metricName(), msg.value());
                return Behaviors.same();
            })
            .onMessage(GetMetrics.class, (context, msg) -> {
                msg.reply(new HashMap<>(metrics));
                return Behaviors.same();
            })
            .build();
    }
}
```

### 2. Work Distributor

Distribute work across cluster nodes:

```java
@Component
public class WorkDistributorActor implements SpringActor<Command> {
    
    private final Queue<WorkItem> workQueue = new LinkedList<>();
    private final Set<ActorRef<WorkResult>> workers = new HashSet<>();
    
    public record RegisterWorker(ActorRef<WorkResult> worker) implements Command {}
    public record SubmitWork(WorkItem item) implements Command {}
    public record RequestWork() extends AskCommand<Optional<WorkItem>> implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(RegisterWorker.class, this::handleRegisterWorker)
            .onMessage(SubmitWork.class, this::handleSubmitWork)
            .onMessage(RequestWork.class, this::handleRequestWork)
            .build();
    }
    
    private Behavior<Command> handleRegisterWorker(ActorContext<Command> ctx, RegisterWorker msg) {
        workers.add(msg.worker());
        ctx.getLog().info("Worker registered: {}", msg.worker());
        return Behaviors.same();
    }
    
    private Behavior<Command> handleSubmitWork(ActorContext<Command> ctx, SubmitWork msg) {
        workQueue.offer(msg.item());
        ctx.getLog().info("Work submitted: {}", msg.item());
        return Behaviors.same();
    }
    
    private Behavior<Command> handleRequestWork(ActorContext<Command> ctx, RequestWork msg) {
        WorkItem work = workQueue.poll();
        msg.reply(Optional.ofNullable(work));
        return Behaviors.same();
    }
}
```

### 3. Cluster Scheduler

Schedule tasks to run periodically across the cluster:

```java
@Component
public class ClusterSchedulerActor implements SpringActor<Command> {
    
    public interface Command {}
    
    public record ScheduleTask(String taskId, Duration interval, Runnable task) implements Command {}
    public record CancelTask(String taskId) implements Command {}
    
    private static class Tick implements Command {
        final String taskId;
        Tick(String taskId) { this.taskId = taskId; }
    }
    
    private final Map<String, Cancellable> scheduledTasks = new HashMap<>();
    private final Map<String, Runnable> tasks = new HashMap<>();
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(ScheduleTask.class, this::handleScheduleTask)
            .onMessage(CancelTask.class, this::handleCancelTask)
            .onMessage(Tick.class, this::handleTick)
            .build();
    }
    
    private Behavior<Command> handleScheduleTask(ActorContext<Command> ctx, ScheduleTask msg) {
        // Cancel existing if present
        Cancellable existing = scheduledTasks.remove(msg.taskId());
        if (existing != null) {
            existing.cancel();
        }
        
        // Store task
        tasks.put(msg.taskId(), msg.task());
        
        // Schedule recurring tick
        Cancellable scheduled = ctx.scheduleAtFixedRate(
            msg.interval(),
            msg.interval(),
            ctx.getSelf(),
            new Tick(msg.taskId())
        );
        
        scheduledTasks.put(msg.taskId(), scheduled);
        ctx.getLog().info("Task scheduled: {}", msg.taskId());
        
        return Behaviors.same();
    }
    
    private Behavior<Command> handleCancelTask(ActorContext<Command> ctx, CancelTask msg) {
        Cancellable scheduled = scheduledTasks.remove(msg.taskId());
        if (scheduled != null) {
            scheduled.cancel();
            tasks.remove(msg.taskId());
            ctx.getLog().info("Task cancelled: {}", msg.taskId());
        }
        return Behaviors.same();
    }
    
    private Behavior<Command> handleTick(ActorContext<Command> ctx, Tick msg) {
        Runnable task = tasks.get(msg.taskId());
        if (task != null) {
            try {
                task.run();
            } catch (Exception e) {
                ctx.getLog().error("Task execution failed: {}", msg.taskId(), e);
            }
        }
        return Behaviors.same();
    }
}
```

## Production Considerations

### 1. State Management

**Problem**: Singleton state is lost during migration

**Solutions**:
- Use event sourcing to rebuild state
- Use distributed data (CRDTs) for shared state
- Implement state snapshot/restore
- Use external storage (database) for critical state

```java
@Component
public class StatefulSingletonActor implements SpringActor<Command> {
    
    private final StateRepository stateRepository;
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onStart(context -> {
                // Restore state from external storage
                State state = stateRepository.load();
                context.getLog().info("State restored: {}", state);
            })
            .onStop(context -> {
                // Persist state before stopping
                stateRepository.save(currentState);
                context.getLog().info("State persisted");
            })
            .build();
    }
}
```

### 2. Monitoring

Track singleton location and migrations:

```java
@Component
public class SingletonMonitor {
    
    @Scheduled(fixedDelay = 10000)
    public void checkSingletonLocation() {
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        
        // Log which node hosts the singleton
        cluster.state().getMembers().forEach(member -> {
            if (isSingletonHost(member)) {
                log.info("Singleton hosted on: {}", member.address());
            }
        });
    }
}
```

### 3. Lease Configuration

Use leases to prevent split brain scenarios:

```yaml
spring:
  actor:
    pekko:
      cluster:
        singleton:
          # Use lease for split brain protection
          use-lease: "singleton-lease"
          
      coordination:
        lease:
          singleton-lease:
            lease-class: org.apache.pekko.coordination.lease.kubernetes.KubernetesLease
```

### 4. Handover Timeout

Configure timeouts for graceful handover:

```yaml
spring:
  actor:
    pekko:
      cluster:
        singleton:
          # Retry handover every 1 second
          hand-over-retry-interval: 1s
          
          # Try at least 15 times (15 seconds total)
          min-number-of-hand-over-retries: 15
```

## Best Practices

### ✅ DO

- **Use for coordination tasks** that require exactly one instance
- **Keep singleton stateless** or use external storage for state
- **Implement idempotent operations** to handle retries
- **Monitor singleton location** and migrations
- **Test failover behavior** in development
- **Cache the proxy reference** - it's lightweight and valid for the cluster lifetime

### ❌ DON'T

- **Don't use for high-throughput operations** - singleton is a bottleneck
- **Don't assume immediate failover** - there's a delay during migration
- **Don't rely on message delivery guarantees** during failover
- **Don't store critical state only in memory** - use external storage
- **Don't spawn multiple singletons with different IDs** for the same purpose

## Troubleshooting

### Singleton Not Starting

**Symptom**: `IllegalStateException: Cluster singleton requested but cluster mode is not enabled`

**Cause**: Actor provider is not set to "cluster"

**Solution**:
```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster  # Must be "cluster", not "local"
```

### Singleton Migration Timeout

**Symptom**: Singleton takes too long to migrate to new node

**Cause**: Handover timeout is too short

**Solution**: Increase handover retry interval and count:
```yaml
spring:
  actor:
    pekko:
      cluster:
        singleton:
          hand-over-retry-interval: 2s
          min-number-of-hand-over-retries: 30
```

### Messages Lost During Failover

**Symptom**: Some messages never receive responses during failover

**Cause**: In-flight messages are lost when singleton node fails

**Solution**: Implement application-level retry:
```java
public <T> CompletionStage<T> askWithRetry(Command cmd, int maxRetries) {
    return askWithRetryHelper(cmd, maxRetries, 0);
}

private <T> CompletionStage<T> askWithRetryHelper(Command cmd, int maxRetries, int attempt) {
    return singleton.ask(cmd)
        .withTimeout(Duration.ofSeconds(3))
        .execute()
        .exceptionally(ex -> {
            if (attempt < maxRetries) {
                return askWithRetryHelper(cmd, maxRetries, attempt + 1)
                    .toCompletableFuture().join();
            }
            throw new RuntimeException("Failed after " + maxRetries + " retries", ex);
        });
}
```

## See Also

- [Testing Cluster Singletons](testing-cluster-singleton.md)
- [Split Brain Resolver](split-brain-resolver-config.md)
- [Cluster Configuration](../configuration/cluster.md)
- [Pekko Cluster Singleton Documentation](https://pekko.apache.org/docs/pekko/current/typed/cluster-singleton.html)
