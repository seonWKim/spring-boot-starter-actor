# Testing Cluster Singletons

## Overview

Testing cluster singletons requires special considerations because they only work in cluster mode. This guide covers different testing approaches and best practices.

## Testing Approaches

### 1. Local Mode Tests (Negative Tests)

Test that cluster singleton API fails gracefully in local mode:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.actor.provider=local"  // Local mode
})
public class ClusterSingletonLocalModeTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testClusterSingletonFailsInLocalMode() {
        // When: Attempting to spawn a cluster singleton in local mode
        CompletionStage<SpringActorRef<Command>> result = actorSystem
            .actor(MySingletonActor.class)
            .withId("singleton-test")
            .asClusterSingleton()
            .spawn();
        
        // Then: Should fail with IllegalStateException
        assertThatThrownBy(() -> result.toCompletableFuture().join())
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cluster singleton requested but cluster mode is not enabled");
    }
    
    @Test
    public void testRegularActorWorksInLocalMode() throws Exception {
        // Given: A regular (non-singleton) actor
        SpringActorRef<Command> actor = actorSystem
            .actor(MyRegularActor.class)
            .withId("regular-actor")
            .spawn()
            .toCompletableFuture()
            .get();
        
        // When: Sending messages to the actor
        actor.tell(new IncrementCommand());
        
        // Then: Actor should work normally
        Integer count = actor.ask(new GetCountCommand())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertEquals(1, count);
    }
}
```

### 2. Single-Node Cluster Tests

Test singleton behavior with a single-node cluster:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.actor.provider=cluster",
    "spring.actor.pekko.remote.artery.canonical.port=0",  // Random port
    "spring.actor.pekko.cluster.seed-nodes[0]=pekko://ActorSystem@127.0.0.1:2551"
})
public class ClusterSingletonSingleNodeTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testSingletonSpawnsSuccessfully() throws Exception {
        // When: Spawning a cluster singleton
        SpringActorRef<Command> singleton = actorSystem
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawn()
            .toCompletableFuture()
            .get();
        
        // Then: Singleton should be created
        assertNotNull(singleton);
    }
    
    @Test
    public void testSingletonReceivesMessages() throws Exception {
        // Given: A cluster singleton
        SpringActorRef<MySingletonActor.Command> singleton = actorSystem
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // When: Sending a message
        String result = singleton
            .ask(new MySingletonActor.ProcessTask("task-1"))
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        // Then: Singleton should process the message
        assertEquals("Processed: task-1", result);
    }
    
    @Test
    public void testMultipleSpawnsReturnSameProxy() throws Exception {
        // When: Spawning singleton multiple times
        SpringActorRef<Command> ref1 = actorSystem
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        SpringActorRef<Command> ref2 = actorSystem
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // Then: Should return the same proxy
        // (Note: actual equality depends on implementation)
        assertNotNull(ref1);
        assertNotNull(ref2);
    }
}
```

### 3. Multi-Node Cluster Tests

Test singleton behavior across multiple nodes:

```java
@SpringBootTest(classes = ClusterTestConfiguration.class)
public class ClusterSingletonMultiNodeTest {
    
    private ClusterTestKit testKit;
    
    @BeforeEach
    public void setup() {
        testKit = new ClusterTestKit();
    }
    
    @AfterEach
    public void teardown() {
        testKit.shutdown();
    }
    
    @Test
    public void testSingletonRunsOnOneNode() throws Exception {
        // Given: 3-node cluster
        ClusterTestScenario cluster = testKit.createCluster(3);
        cluster.awaitAllNodesUp();
        
        // When: Spawning singleton from all nodes
        SpringActorRef<Command> ref1 = cluster.getActorSystem(0)
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        SpringActorRef<Command> ref2 = cluster.getActorSystem(1)
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // Then: All nodes get a proxy to the same singleton
        // Verify by sending messages and checking they're processed by same actor
        String result1 = ref1.ask(new GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        String result2 = ref2.ask(new GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        // Both should return the same node address
        assertEquals(result1, result2);
    }
    
    @Test
    public void testSingletonMigratesOnNodeFailure() throws Exception {
        // Given: 3-node cluster with singleton on node 0
        ClusterTestScenario cluster = testKit.createCluster(3);
        cluster.awaitAllNodesUp();
        
        SpringActorRef<Command> singleton = cluster.getActorSystem(1)
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // Record which node hosts the singleton initially
        String initialHost = singleton.ask(new GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        // When: Node hosting singleton fails
        int failedNodeIndex = findNodeByAddress(cluster, initialHost);
        cluster.killNode(failedNodeIndex);
        
        // Wait for migration
        Thread.sleep(5000);
        
        // Then: Singleton should be running on a different node
        String newHost = singleton.ask(new GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertNotEquals(initialHost, newHost);
    }
    
    @Test
    public void testSingletonHandlesGracefulShutdown() throws Exception {
        // Given: 3-node cluster
        ClusterTestScenario cluster = testKit.createCluster(3);
        cluster.awaitAllNodesUp();
        
        SpringActorRef<Command> singleton = cluster.getActorSystem(0)
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // When: Gracefully shutting down node hosting singleton
        cluster.gracefulShutdownNode(0);
        
        // Wait for handover
        Thread.sleep(5000);
        
        // Then: Singleton should be accessible from remaining nodes
        String result = cluster.getActorSystem(1)
            .actor(MySingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait()
            .ask(new PingCommand())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertEquals("pong", result);
    }
}
```

## Test Infrastructure

### ClusterTestKit

Create a test kit for multi-node cluster testing:

```java
public class ClusterTestKit {
    
    private final List<SpringActorSystem> actorSystems = new ArrayList<>();
    private final List<ApplicationContext> contexts = new ArrayList<>();
    
    public ClusterTestScenario createCluster(int nodeCount) {
        return createCluster(nodeCount, new HashMap<>());
    }
    
    public ClusterTestScenario createCluster(int nodeCount, Map<String, Object> config) {
        List<Integer> ports = allocatePorts(nodeCount);
        String seedNodes = buildSeedNodes(ports);
        
        for (int i = 0; i < nodeCount; i++) {
            int port = ports.get(i);
            
            Map<String, Object> nodeConfig = new HashMap<>(config);
            nodeConfig.put("spring.actor.pekko.actor.provider", "cluster");
            nodeConfig.put("spring.actor.pekko.remote.artery.canonical.port", port);
            nodeConfig.put("spring.actor.pekko.cluster.seed-nodes[0]", seedNodes);
            
            SpringApplication app = new SpringApplication(ClusterTestConfiguration.class);
            app.setDefaultProperties(nodeConfig);
            
            ApplicationContext context = app.run();
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);
            
            actorSystems.add(actorSystem);
            contexts.add(context);
        }
        
        return new ClusterTestScenario(actorSystems, contexts);
    }
    
    private List<Integer> allocatePorts(int count) {
        List<Integer> ports = new ArrayList<>();
        int basePort = 25520;
        for (int i = 0; i < count; i++) {
            ports.add(basePort + i);
        }
        return ports;
    }
    
    private String buildSeedNodes(List<Integer> ports) {
        return "pekko://ActorSystem@127.0.0.1:" + ports.get(0);
    }
    
    public void shutdown() {
        contexts.forEach(ctx -> {
            if (ctx instanceof ConfigurableApplicationContext) {
                ((ConfigurableApplicationContext) ctx).close();
            }
        });
        actorSystems.clear();
        contexts.clear();
    }
}
```

### ClusterTestScenario

```java
public class ClusterTestScenario {
    
    private final List<SpringActorSystem> actorSystems;
    private final List<ApplicationContext> contexts;
    
    public ClusterTestScenario(List<SpringActorSystem> actorSystems, List<ApplicationContext> contexts) {
        this.actorSystems = actorSystems;
        this.contexts = contexts;
    }
    
    public SpringActorSystem getActorSystem(int index) {
        return actorSystems.get(index);
    }
    
    public void awaitAllNodesUp() {
        awaitAllNodesUp(Duration.ofSeconds(30));
    }
    
    public void awaitAllNodesUp(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        
        while (System.currentTimeMillis() < deadline) {
            if (allNodesUp()) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for cluster", e);
            }
        }
        
        throw new RuntimeException("Timeout waiting for all nodes to be up");
    }
    
    private boolean allNodesUp() {
        int expectedSize = actorSystems.size();
        
        for (SpringActorSystem system : actorSystems) {
            Cluster cluster = Cluster.get(system.getRaw());
            long upMembers = cluster.state().getMembers().stream()
                .filter(m -> m.status() == MemberStatus.up())
                .count();
            
            if (upMembers != expectedSize) {
                return false;
            }
        }
        
        return true;
    }
    
    public void killNode(int index) {
        ApplicationContext context = contexts.get(index);
        if (context instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) context).close();
        }
    }
    
    public void gracefulShutdownNode(int index) {
        SpringActorSystem system = actorSystems.get(index);
        Cluster cluster = Cluster.get(system.getRaw());
        
        // Leave cluster gracefully
        cluster.leave(cluster.selfMember().address());
        
        // Wait a bit for handover
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Close context
        killNode(index);
    }
}
```

## Example: Complete Test Suite

```java
@SpringBootTest
public class ClusterSingletonIntegrationTest {
    
    private ClusterTestKit testKit;
    
    @BeforeEach
    public void setup() {
        testKit = new ClusterTestKit();
    }
    
    @AfterEach
    public void teardown() {
        testKit.shutdown();
    }
    
    @Component
    public static class TestSingletonActor implements SpringActor<Command> {
        
        public interface Command extends JsonSerializable {}
        
        public record GetNodeAddress() extends AskCommand<String> implements Command {}
        
        public record Increment() implements Command {}
        
        public record GetCount() extends AskCommand<Integer> implements Command {}
        
        private int count = 0;
        
        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                .onMessage(GetNodeAddress.class, (context, msg) -> {
                    String address = context.getRaw().system().address().toString();
                    msg.reply(address);
                    return Behaviors.same();
                })
                .onMessage(Increment.class, (context, msg) -> {
                    count++;
                    return Behaviors.same();
                })
                .onMessage(GetCount.class, (context, msg) -> {
                    msg.reply(count);
                    return Behaviors.same();
                })
                .build();
        }
    }
    
    @Test
    public void testBasicSingletonFunctionality() throws Exception {
        // Given: 2-node cluster
        ClusterTestScenario cluster = testKit.createCluster(2);
        cluster.awaitAllNodesUp();
        
        // When: Both nodes spawn the same singleton
        SpringActorRef<TestSingletonActor.Command> ref1 = cluster.getActorSystem(0)
            .actor(TestSingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        SpringActorRef<TestSingletonActor.Command> ref2 = cluster.getActorSystem(1)
            .actor(TestSingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // Then: Messages from both proxies go to the same singleton
        ref1.tell(new TestSingletonActor.Increment());
        ref2.tell(new TestSingletonActor.Increment());
        
        Thread.sleep(1000); // Wait for messages to be processed
        
        Integer count = ref1.ask(new TestSingletonActor.GetCount())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertEquals(2, count, "Both increments should be counted by the same singleton");
    }
    
    @Test
    public void testSingletonMigration() throws Exception {
        // Given: 3-node cluster with singleton
        ClusterTestScenario cluster = testKit.createCluster(3);
        cluster.awaitAllNodesUp();
        
        SpringActorRef<TestSingletonActor.Command> singleton = cluster.getActorSystem(1)
            .actor(TestSingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        // Increment counter
        singleton.tell(new TestSingletonActor.Increment());
        Thread.sleep(500);
        
        // Record initial host
        String initialHost = singleton.ask(new TestSingletonActor.GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        // When: Kill the node hosting the singleton
        int failedNodeIndex = findNodeIndexByAddress(cluster, initialHost);
        cluster.killNode(failedNodeIndex);
        
        // Wait for migration
        Thread.sleep(5000);
        
        // Then: Singleton should be accessible from another node
        SpringActorRef<TestSingletonActor.Command> newRef = cluster.getActorSystem(
                failedNodeIndex == 0 ? 1 : 0
            )
            .actor(TestSingletonActor.class)
            .withId("test-singleton")
            .asClusterSingleton()
            .spawnAndWait();
        
        String newHost = newRef.ask(new TestSingletonActor.GetNodeAddress())
            .withTimeout(Duration.ofSeconds(3))
            .execute()
            .toCompletableFuture()
            .get();
        
        assertNotEquals(initialHost, newHost, "Singleton should have migrated");
        
        // Note: State (count) is lost during migration unless persisted
    }
    
    private int findNodeIndexByAddress(ClusterTestScenario cluster, String address) {
        for (int i = 0; i < 3; i++) {
            try {
                SpringActorSystem system = cluster.getActorSystem(i);
                if (system.getRaw().address().toString().equals(address)) {
                    return i;
                }
            } catch (Exception e) {
                // Node may be dead
            }
        }
        throw new IllegalArgumentException("Node not found: " + address);
    }
}
```

## Best Practices for Testing

### ✅ DO

- **Test in cluster mode** when testing singleton-specific behavior
- **Use test kits** to simplify multi-node cluster setup
- **Test failover scenarios** to ensure migration works
- **Wait for stabilization** after cluster state changes
- **Clean up resources** in @AfterEach to prevent test interference
- **Use unique IDs** for singletons to avoid conflicts between tests

### ❌ DON'T

- **Don't test singletons in local mode** (except negative tests)
- **Don't assume immediate migration** - allow time for failover
- **Don't rely on state preservation** during migration without implementing it
- **Don't forget timeouts** - cluster operations can be slow in tests
- **Don't run many multi-node tests in parallel** - port conflicts

## Troubleshooting Test Issues

### Test Timeout

**Problem**: Test hangs waiting for cluster to form

**Solution**: Check seed node configuration and firewall rules

```java
@Test(timeout = 60000)  // Add timeout to fail fast
public void testWithTimeout() {
    // Test code
}
```

### Port Already in Use

**Problem**: Tests fail with "address already in use"

**Solution**: Use port 0 for random port allocation

```properties
spring.actor.pekko.remote.artery.canonical.port=0
```

### Messages Not Delivered

**Problem**: Messages sent to singleton are not processed

**Solution**: Wait for cluster to stabilize before sending messages

```java
cluster.awaitAllNodesUp();
Thread.sleep(1000);  // Additional stabilization time
```

## See Also

- [Cluster Singleton Guide](cluster-singleton.md)
- [Cluster Configuration](../configuration/cluster.md)
- [Pekko Multi-Node Testing](https://pekko.apache.org/docs/pekko/current/multi-node-testing.html)
