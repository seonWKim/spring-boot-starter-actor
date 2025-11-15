# Task 3.1: LWWMap CRDT Actor Wrapper

**Priority:** MEDIUM  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Wrap Pekko's Last-Write-Wins Map (LWWMap) CRDT in actor commands, using the existing `ask()` patterns to provide a Spring Boot-friendly API for distributed key-value storage.

## Background

CRDTs (Conflict-free Replicated Data Types) enable distributed data structures that automatically resolve conflicts. Rather than exposing Pekko's CRDT APIs directly, we wrap them in actor commands so developers can use familiar actor patterns.

### LWWMap Overview
- **Last-Write-Wins**: Conflicts resolved by timestamp
- **Eventually Consistent**: All replicas converge to same state
- **Available**: Works during network partitions
- **Use Cases**: Distributed caches, configuration, user preferences

## Requirements

### 1. Actor Implementation

```java
package io.github.seonwkim.core.crdt;

@Component
public class LWWMapActor<K, V> implements SpringActorWithContext<LWWMapActor.Command<K, V>, SpringActorContext> {
    
    // CRDT key for replication
    private final Key<LWWMap<K, V>> dataKey = 
        LWWMapKey.create("lwwmap-" + UUID.randomUUID());
    
    // Pekko Distributed Data replicator
    private ActorRef<Replicator.Command> replicator;
    
    /**
     * Command interface for LWWMap operations
     */
    public interface Command<K, V> extends JsonSerializable {}
    
    /**
     * Put a key-value pair in the map
     */
    public static class Put<K, V> extends AskCommand<Done> implements Command<K, V> {
        public final K key;
        public final V value;
        
        public Put(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    /**
     * Get a value by key
     */
    public static class Get<K, V> extends AskCommand<Optional<V>> implements Command<K, V> {
        public final K key;
        
        public Get(K key) {
            this.key = key;
        }
    }
    
    /**
     * Remove a key from the map
     */
    public static class Remove<K, V> extends AskCommand<Done> implements Command<K, V> {
        public final K key;
        
        public Remove(K key) {
            this.key = key;
        }
    }
    
    /**
     * Get all entries
     */
    public static class GetAll<K, V> extends AskCommand<Map<K, V>> implements Command<K, V> {
        public GetAll() {}
    }
    
    /**
     * Get size of the map
     */
    public static class Size<K, V> extends AskCommand<Integer> implements Command<K, V> {
        public Size() {}
    }
    
    /**
     * Check if key exists
     */
    public static class Contains<K, V> extends AskCommand<Boolean> implements Command<K, V> {
        public final K key;
        
        public Contains(K key) {
            this.key = key;
        }
    }
    
    @Override
    public SpringActorBehavior<Command<K, V>> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            .onStart(ctx -> {
                // Get replicator reference
                replicator = DistributedData.get(ctx.getRaw()).replicator();
                
                // Subscribe to changes
                replicator.tell(new Replicator.Subscribe<>(dataKey, 
                    ctx.getRaw().self().narrow()));
                
                ctx.getLog().info("LWWMap actor started with key: {}", dataKey);
            })
            .onMessage(Put.class, this::handlePut)
            .onMessage(Get.class, this::handleGet)
            .onMessage(Remove.class, this::handleRemove)
            .onMessage(GetAll.class, this::handleGetAll)
            .onMessage(Size.class, this::handleSize)
            .onMessage(Contains.class, this::handleContains)
            // Handle replicator responses
            .onMessage(Replicator.UpdateSuccess.class, this::handleUpdateSuccess)
            .onMessage(Replicator.UpdateFailure.class, this::handleUpdateFailure)
            .onMessage(Replicator.GetSuccess.class, this::handleGetSuccess)
            .onMessage(Replicator.GetFailure.class, this::handleGetFailure)
            .build();
    }
    
    private Behavior<Command<K, V>> handlePut(ActorContext<Command<K, V>> ctx, Put<K, V> cmd) {
        // Update the CRDT
        replicator.tell(new Replicator.Update<>(
            dataKey,
            LWWMap.create(),
            Replicator.writeLocal(),
            cmd, // Pass command as correlation
            curr -> curr.put(ctx.getRaw(), cmd.key, cmd.value)
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleGet(ActorContext<Command<K, V>> ctx, Get<K, V> cmd) {
        // Read from CRDT
        replicator.tell(new Replicator.Get<>(
            dataKey,
            Replicator.readLocal(),
            cmd // Pass command as correlation
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleRemove(ActorContext<Command<K, V>> ctx, Remove<K, V> cmd) {
        replicator.tell(new Replicator.Update<>(
            dataKey,
            LWWMap.create(),
            Replicator.writeLocal(),
            cmd,
            curr -> curr.remove(ctx.getRaw(), cmd.key)
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleGetAll(ActorContext<Command<K, V>> ctx, GetAll<K, V> cmd) {
        replicator.tell(new Replicator.Get<>(
            dataKey,
            Replicator.readLocal(),
            cmd
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleSize(ActorContext<Command<K, V>> ctx, Size<K, V> cmd) {
        replicator.tell(new Replicator.Get<>(
            dataKey,
            Replicator.readLocal(),
            cmd
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleContains(ActorContext<Command<K, V>> ctx, Contains<K, V> cmd) {
        replicator.tell(new Replicator.Get<>(
            dataKey,
            Replicator.readLocal(),
            cmd
        ));
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleUpdateSuccess(
            ActorContext<Command<K, V>> ctx, 
            Replicator.UpdateSuccess<LWWMap<K, V>> response) {
        
        // Reply to original command
        Object correlation = response.getRequest().orElse(null);
        if (correlation instanceof Put) {
            ((Put<K, V>) correlation).reply(Done.getInstance());
        } else if (correlation instanceof Remove) {
            ((Remove<K, V>) correlation).reply(Done.getInstance());
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleUpdateFailure(
            ActorContext<Command<K, V>> ctx, 
            Replicator.UpdateFailure<LWWMap<K, V>> response) {
        
        ctx.getLog().error("Update failed: {}", response);
        // Could reply with error
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleGetSuccess(
            ActorContext<Command<K, V>> ctx, 
            Replicator.GetSuccess<LWWMap<K, V>> response) {
        
        LWWMap<K, V> data = response.get(dataKey);
        Object correlation = response.getRequest().orElse(null);
        
        if (correlation instanceof Get) {
            Get<K, V> cmd = (Get<K, V>) correlation;
            cmd.reply(data.get(cmd.key));
        } else if (correlation instanceof GetAll) {
            GetAll<K, V> cmd = (GetAll<K, V>) correlation;
            cmd.reply(data.getEntries());
        } else if (correlation instanceof Size) {
            Size<K, V> cmd = (Size<K, V>) correlation;
            cmd.reply(data.size());
        } else if (correlation instanceof Contains) {
            Contains<K, V> cmd = (Contains<K, V>) correlation;
            cmd.reply(data.contains(cmd.key));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command<K, V>> handleGetFailure(
            ActorContext<Command<K, V>> ctx, 
            Replicator.GetFailure<LWWMap<K, V>> response) {
        
        ctx.getLog().error("Get failed: {}", response);
        // Reply with empty/error
        
        return Behaviors.same();
    }
}
```

### 2. Service Wrapper

```java
package io.github.seonwkim.core.crdt;

@Service
public class DistributedMapService<K, V> {
    
    private final SpringActorSystem actorSystem;
    private SpringActorRef<LWWMapActor.Command<K, V>> mapActor;
    
    public DistributedMapService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }
    
    @PostConstruct
    public void init() {
        // Spawn the LWWMap actor
        mapActor = actorSystem.actor(LWWMapActor.class)
            .withId("distributed-map")
            .spawnAndWait();
    }
    
    /**
     * Put a key-value pair
     */
    public CompletionStage<Done> put(K key, V value) {
        return mapActor.ask(new LWWMapActor.Put<>(key, value))
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
    
    /**
     * Get a value by key
     */
    public CompletionStage<Optional<V>> get(K key) {
        return mapActor.ask(new LWWMapActor.Get<>(key))
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
    
    /**
     * Remove a key
     */
    public CompletionStage<Done> remove(K key) {
        return mapActor.ask(new LWWMapActor.Remove<>(key))
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
    
    /**
     * Get all entries
     */
    public CompletionStage<Map<K, V>> getAll() {
        return mapActor.ask(new LWWMapActor.GetAll<>())
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
    
    /**
     * Get size
     */
    public CompletionStage<Integer> size() {
        return mapActor.ask(new LWWMapActor.Size<>())
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
    
    /**
     * Check if key exists
     */
    public CompletionStage<Boolean> contains(K key) {
        return mapActor.ask(new LWWMapActor.Contains<>(key))
            .withTimeout(Duration.ofSeconds(5))
            .execute();
    }
}
```

### 3. Configuration

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster
      cluster:
        seed-nodes:
          - "pekko://ActorSystem@127.0.0.1:2551"
        
        # Distributed Data configuration
        distributed-data:
          # Replication settings
          gossip-interval: 2s
          notify-subscribers-interval: 500ms
          
          # Durable storage (optional)
          durable:
            keys: []
```

## Testing Requirements

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.actor.provider=cluster"
})
public class LWWMapActorTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testPutAndGet() throws Exception {
        // Given: LWWMap actor
        SpringActorRef<LWWMapActor.Command<String, String>> actor = 
            actorSystem.actor(LWWMapActor.class)
                .withId("test-map")
                .spawnAndWait();
        
        // When: Put a value
        Done putResult = actor.ask(new LWWMapActor.Put<>("key1", "value1"))
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        // Then: Get returns the value
        Optional<String> value = actor.ask(new LWWMapActor.Get<>("key1"))
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertTrue(value.isPresent());
        assertEquals("value1", value.get());
    }
    
    @Test
    public void testRemove() throws Exception {
        // Test remove operation
    }
    
    @Test
    public void testGetAll() throws Exception {
        // Test getting all entries
    }
    
    @Test
    public void testLastWriteWins() throws Exception {
        // Test that last write wins on concurrent updates
    }
}

@SpringBootTest
public class DistributedMapServiceTest {
    
    @Autowired
    private DistributedMapService<String, String> mapService;
    
    @Test
    public void testServiceAPI() throws Exception {
        // Test the service wrapper
        mapService.put("key", "value").toCompletableFuture().get();
        
        Optional<String> result = mapService.get("key").toCompletableFuture().get();
        
        assertTrue(result.isPresent());
        assertEquals("value", result.get());
    }
}
```

## Deliverables

1. **Actor Implementation**: `core/src/main/java/io/github/seonwkim/core/crdt/LWWMapActor.java`
2. **Service Wrapper**: `core/src/main/java/io/github/seonwkim/core/crdt/DistributedMapService.java`
3. **Tests**: `core/src/test/java/io/github/seonwkim/core/crdt/LWWMapActorTest.java`
4. **Documentation**: `docs/clustering/crdt-lwwmap.md`
5. **Example**: `example/cluster/` with LWWMap usage

## Success Criteria

- ✅ LWWMap operations work via actor commands
- ✅ Service wrapper provides simple API
- ✅ Last-write-wins conflict resolution works correctly
- ✅ Data replicates across cluster nodes
- ✅ Tests validate all operations
- ✅ Documentation explains usage and trade-offs

## References

- Pekko Distributed Data: https://pekko.apache.org/docs/pekko/current/typed/distributed-data.html
- LWWMap: https://pekko.apache.org/api/pekko/current/org/apache/pekko/cluster/ddata/LWWMap.html
