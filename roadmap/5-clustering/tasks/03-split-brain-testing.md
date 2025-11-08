# Task 2.2: Split Brain Resolver Testing - CRITICAL

**Priority:** 🚨 CRITICAL  
**Estimated Effort:** 2-3 weeks  
**Status:** TODO

## Objective

Create a comprehensive test suite that explicitly validates split brain resolution behavior across all strategies and partition scenarios. Each test must verify which nodes shut down and which stay up.

## Background

Split brain testing is critical because:
- Network partitions are inevitable in production
- Incorrect resolution can lead to data loss or corruption
- Default behaviors must be explicitly verified
- Edge cases must be handled correctly

## Test Framework Requirements

### 1. Cluster Test Infrastructure

Create a test framework that can:
- Start multi-node clusters programmatically
- Simulate network partitions
- Verify cluster state after resolution
- Assert which nodes are up/down

```java
@SpringBootTest
public abstract class ClusterTestBase {
    
    protected ClusterTestKit testKit;
    
    @BeforeEach
    public void setupCluster() {
        testKit = new ClusterTestKit();
    }
    
    @AfterEach
    public void teardownCluster() {
        testKit.shutdown();
    }
}

public class ClusterTestKit {
    
    /**
     * Creates a cluster with the specified number of nodes.
     */
    public ClusterTestScenario createCluster(int nodeCount);
    
    /**
     * Simulates a network partition.
     */
    public void partition(List<Integer> side1, List<Integer> side2);
    
    /**
     * Waits for cluster to stabilize after partition.
     */
    public void awaitStable(Duration timeout);
    
    /**
     * Checks if a node is up.
     */
    public boolean isUp(int nodeIndex);
    
    /**
     * Checks if a node is down.
     */
    public boolean isDown(int nodeIndex);
}
```

### 2. Configuration for Each Test

Each test needs its own configuration:
```java
public class ClusterTestScenario {
    
    public void withSplitBrainResolver(String strategy, Consumer<Config> configurer);
    
    public void withQuorumSize(int quorum);
    
    public SpringActorSystem getActorSystem(int nodeIndex);
}
```

## Required Test Scenarios

### Test Suite 1: Keep-Majority Strategy

#### Test 1.1: Majority Partition Survives (5-node: 3 vs 2)
```java
@Test
public void testKeepMajority_5Nodes_3v2_MajorityStaysUp() {
    // Given: 5-node cluster with keep-majority strategy
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("keep-majority", config -> {
            config.withStableAfter(Duration.ofSeconds(20));
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Network partition creates 3 vs 2 split
    cluster.partition(
        List.of(0, 1, 2),  // Majority side
        List.of(3, 4)       // Minority side
    );
    
    // Wait for split brain resolution
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Majority side (3 nodes) stays up
    assertTrue(cluster.isUp(0), "Node 0 (majority) should stay up");
    assertTrue(cluster.isUp(1), "Node 1 (majority) should stay up");
    assertTrue(cluster.isUp(2), "Node 2 (majority) should stay up");
    
    // And: Minority side (2 nodes) is downed
    assertTrue(cluster.isDown(3), "Node 3 (minority) should be down");
    assertTrue(cluster.isDown(4), "Node 4 (minority) should be down");
}
```

#### Test 1.2: Equal Partition (3 vs 3)
```java
@Test
public void testKeepMajority_6Nodes_3v3_AllDown() {
    // Given: 6-node cluster
    ClusterTestScenario cluster = testKit.createCluster(6)
        .withSplitBrainResolver("keep-majority", config -> {
            config.withDownAllWhenUnstable(true);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Equal partition (3 vs 3)
    cluster.partition(
        List.of(0, 1, 2),
        List.of(3, 4, 5)
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: All nodes should be downed (no majority exists)
    for (int i = 0; i < 6; i++) {
        assertTrue(cluster.isDown(i), 
            "Node " + i + " should be down (no majority)");
    }
}
```

#### Test 1.3: Single Node Separated
```java
@Test
public void testKeepMajority_5Nodes_1v4_MajorityStaysUp() {
    // Given: 5-node cluster
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("keep-majority", config -> {});
    
    cluster.awaitAllNodesUp();
    
    // When: Single node separated from rest
    cluster.partition(
        List.of(0),           // Single node
        List.of(1, 2, 3, 4)   // Majority
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Single node is downed
    assertTrue(cluster.isDown(0), "Isolated node should be down");
    
    // And: Majority (4 nodes) stays up
    assertTrue(cluster.isUp(1), "Node 1 (majority) should stay up");
    assertTrue(cluster.isUp(2), "Node 2 (majority) should stay up");
    assertTrue(cluster.isUp(3), "Node 3 (majority) should stay up");
    assertTrue(cluster.isUp(4), "Node 4 (majority) should stay up");
}
```

#### Test 1.4: Multiple Simultaneous Partitions
```java
@Test
public void testKeepMajority_MultiplePartitions() {
    // Given: 7-node cluster
    ClusterTestScenario cluster = testKit.createCluster(7)
        .withSplitBrainResolver("keep-majority", config -> {});
    
    cluster.awaitAllNodesUp();
    
    // When: Three-way partition: 3 vs 2 vs 2
    cluster.multiPartition(
        List.of(0, 1, 2),     // Majority
        List.of(3, 4),        // Minority 1
        List.of(5, 6)         // Minority 2
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Only the largest partition (3 nodes) survives
    assertTrue(cluster.isUp(0), "Node 0 (majority) should stay up");
    assertTrue(cluster.isUp(1), "Node 1 (majority) should stay up");
    assertTrue(cluster.isUp(2), "Node 2 (majority) should stay up");
    
    // All other nodes are downed
    assertTrue(cluster.isDown(3), "Node 3 (minority) should be down");
    assertTrue(cluster.isDown(4), "Node 4 (minority) should be down");
    assertTrue(cluster.isDown(5), "Node 5 (minority) should be down");
    assertTrue(cluster.isDown(6), "Node 6 (minority) should be down");
}
```

### Test Suite 2: Keep-Oldest Strategy

#### Test 2.1: Partition with Oldest Node Survives
```java
@Test
public void testKeepOldest_PartitionWithOldestSurvives() {
    // Given: 5-node cluster with keep-oldest strategy
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("keep-oldest", config -> {
            config.withDownIfAlone(true);
        });
    
    cluster.awaitAllNodesUp();
    
    // Node 0 is the oldest (first to join)
    int oldestNode = 0;
    
    // When: Partition separates oldest from some nodes
    cluster.partition(
        List.of(0, 1),        // Side with oldest
        List.of(2, 3, 4)      // Side without oldest
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Partition with oldest node survives
    assertTrue(cluster.isUp(0), "Node 0 (oldest) should stay up");
    assertTrue(cluster.isUp(1), "Node 1 (with oldest) should stay up");
    
    // Other partition is downed
    assertTrue(cluster.isDown(2), "Node 2 (without oldest) should be down");
    assertTrue(cluster.isDown(3), "Node 3 (without oldest) should be down");
    assertTrue(cluster.isDown(4), "Node 4 (without oldest) should be down");
}
```

#### Test 2.2: Oldest Node Alone (down-if-alone = on)
```java
@Test
public void testKeepOldest_OldestAloneDown() {
    // Given: 3-node cluster with keep-oldest and down-if-alone
    ClusterTestScenario cluster = testKit.createCluster(3)
        .withSplitBrainResolver("keep-oldest", config -> {
            config.withDownIfAlone(true);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Oldest node is completely isolated
    cluster.partition(
        List.of(0),          // Oldest, alone
        List.of(1, 2)        // Rest
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: All nodes are downed (oldest alone, down-if-alone=true)
    assertTrue(cluster.isDown(0), "Node 0 (oldest, alone) should be down");
    assertTrue(cluster.isDown(1), "Node 1 (without oldest) should be down");
    assertTrue(cluster.isDown(2), "Node 2 (without oldest) should be down");
}
```

#### Test 2.3: Oldest Node Alone (down-if-alone = off)
```java
@Test
public void testKeepOldest_OldestAloneStaysUp() {
    // Given: 3-node cluster with keep-oldest and down-if-alone=false
    ClusterTestScenario cluster = testKit.createCluster(3)
        .withSplitBrainResolver("keep-oldest", config -> {
            config.withDownIfAlone(false);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Oldest node is isolated
    cluster.partition(
        List.of(0),          // Oldest, alone
        List.of(1, 2)        // Rest
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Oldest stays up even when alone
    assertTrue(cluster.isUp(0), "Node 0 (oldest, alone) should stay up");
    
    // Other nodes are downed
    assertTrue(cluster.isDown(1), "Node 1 (without oldest) should be down");
    assertTrue(cluster.isDown(2), "Node 2 (without oldest) should be down");
}
```

### Test Suite 3: Static Quorum Strategy

#### Test 3.1: Partition Above Quorum Survives
```java
@Test
public void testStaticQuorum_AboveQuorumSurvives() {
    // Given: 5-node cluster with quorum=3
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("static-quorum", config -> {
            config.withQuorumSize(3);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Partition creates 3 vs 2 split
    cluster.partition(
        List.of(0, 1, 2),    // Meets quorum (3)
        List.of(3, 4)        // Below quorum (2)
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Partition meeting quorum survives
    assertTrue(cluster.isUp(0), "Node 0 (quorum met) should stay up");
    assertTrue(cluster.isUp(1), "Node 1 (quorum met) should stay up");
    assertTrue(cluster.isUp(2), "Node 2 (quorum met) should stay up");
    
    // Partition below quorum is downed
    assertTrue(cluster.isDown(3), "Node 3 (below quorum) should be down");
    assertTrue(cluster.isDown(4), "Node 4 (below quorum) should be down");
}
```

#### Test 3.2: Both Partitions Below Quorum
```java
@Test
public void testStaticQuorum_BothBelowQuorum_AllDown() {
    // Given: 5-node cluster with quorum=4
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("static-quorum", config -> {
            config.withQuorumSize(4);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Partition creates 3 vs 2 split (both below quorum of 4)
    cluster.partition(
        List.of(0, 1, 2),    // Below quorum (3 < 4)
        List.of(3, 4)        // Below quorum (2 < 4)
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: All nodes are downed (no partition meets quorum)
    for (int i = 0; i < 5; i++) {
        assertTrue(cluster.isDown(i), 
            "Node " + i + " should be down (quorum not met)");
    }
}
```

#### Test 3.3: Exact Quorum Size
```java
@Test
public void testStaticQuorum_ExactQuorumSurvives() {
    // Given: 6-node cluster with quorum=3
    ClusterTestScenario cluster = testKit.createCluster(6)
        .withSplitBrainResolver("static-quorum", config -> {
            config.withQuorumSize(3);
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Partition creates 3 vs 3 split (both exactly meet quorum)
    cluster.partition(
        List.of(0, 1, 2),    // Exactly meets quorum
        List.of(3, 4, 5)     // Exactly meets quorum
    );
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: Both partitions meet quorum - behavior depends on tie-breaker
    // Document the tie-breaker behavior (typically address-based)
    int upCount = 0;
    for (int i = 0; i < 6; i++) {
        if (cluster.isUp(i)) upCount++;
    }
    
    // One partition should survive
    assertEquals(3, upCount, "Exactly one partition (3 nodes) should survive");
}
```

### Test Suite 4: Edge Cases and Recovery

#### Test 4.1: Rapid Healing (Partition Resolves Quickly)
```java
@Test
public void testRapidHealing_NoDowning() {
    // Given: 5-node cluster with stable-after=20s
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("keep-majority", config -> {
            config.withStableAfter(Duration.ofSeconds(20));
        });
    
    cluster.awaitAllNodesUp();
    
    // When: Partition occurs but heals within stable-after period
    cluster.partition(
        List.of(0, 1, 2),
        List.of(3, 4)
    );
    
    // Heal after 10 seconds (before stable-after)
    Thread.sleep(10000);
    cluster.heal();
    
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Then: No nodes should be downed (partition healed too quickly)
    for (int i = 0; i < 5; i++) {
        assertTrue(cluster.isUp(i), 
            "Node " + i + " should still be up (quick heal)");
    }
}
```

#### Test 4.2: Cascading Failures
```java
@Test
public void testCascadingFailures() {
    // Given: 5-node cluster
    ClusterTestScenario cluster = testKit.createCluster(5)
        .withSplitBrainResolver("keep-majority", config -> {});
    
    cluster.awaitAllNodesUp();
    
    // When: First partition
    cluster.partition(List.of(0, 1, 2), List.of(3, 4));
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Verify majority survived (0, 1, 2)
    assertTrue(cluster.isUp(0));
    assertTrue(cluster.isUp(1));
    assertTrue(cluster.isUp(2));
    
    // Then: Another node fails in the surviving partition
    cluster.killNode(2);
    cluster.awaitStable(Duration.ofSeconds(30));
    
    // Should still have majority (2 out of original 5)
    assertTrue(cluster.isUp(0), "Node 0 should stay up");
    assertTrue(cluster.isUp(1), "Node 1 should stay up");
}
```

## Test Infrastructure Implementation

### Key Components

1. **ClusterTestKit**: Main test harness
2. **ClusterTestScenario**: Represents a specific cluster configuration
3. **NetworkPartitionSimulator**: Simulates network failures using Pekko TestKit
4. **ClusterStateVerifier**: Verifies cluster membership state

### Implementation Location

```
core/src/test/java/io/github/seonwkim/core/cluster/
├── ClusterTestKit.java
├── ClusterTestScenario.java
├── NetworkPartitionSimulator.java
├── ClusterStateVerifier.java
└── splitbrain/
    ├── KeepMajorityStrategyTest.java
    ├── KeepOldestStrategyTest.java
    ├── StaticQuorumStrategyTest.java
    └── EdgeCaseTests.java
```

## Success Criteria

- ✅ All 15+ test scenarios pass consistently
- ✅ Each test explicitly verifies which nodes are up/down
- ✅ Tests run in reasonable time (<5 minutes total)
- ✅ Tests are deterministic (no flakiness)
- ✅ Clear failure messages when assertions fail
- ✅ Test infrastructure is reusable for custom scenarios

## Documentation Requirements

Create `docs/clustering/testing-split-brain-resolver.md` with:
- How to run the tests
- How to write custom scenarios
- Interpreting test results
- Troubleshooting test failures

## References

- Pekko Multi-Node Testing: https://pekko.apache.org/docs/pekko/current/multi-node-testing.html
- Split Brain Resolver: https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html
