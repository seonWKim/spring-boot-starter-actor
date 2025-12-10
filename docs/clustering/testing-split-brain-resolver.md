# Testing Split Brain Resolver

## Overview

Testing split brain resolution is CRITICAL because network partitions are inevitable in production. These tests must explicitly verify which nodes shut down and which stay up under various partition scenarios.

## Testing Approaches

### 1. Configuration Verification Tests

Simplest tests to verify configuration is applied correctly:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.actor.provider=cluster",
    "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
    "spring.actor.pekko.cluster.split-brain-resolver.active-strategy=keep-majority",
    "spring.actor.pekko.cluster.split-brain-resolver.stable-after=20s"
})
public class SplitBrainResolverConfigTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testSplitBrainResolverConfigured() {
        // Verify that split brain resolver is configured
        Cluster cluster = Cluster.get(actorSystem.getRaw());
        
        // Check downing provider
        Config config = actorSystem.getRaw().settings().config();
        String downingProvider = config.getString("pekko.cluster.downing-provider-class");
        
        assertEquals("org.apache.pekko.cluster.sbr.SplitBrainResolverProvider", downingProvider);
    }
    
    @Test
    public void testKeepMajorityStrategyConfigured() {
        Config config = actorSystem.getRaw().settings().config();
        String strategy = config.getString("pekko.cluster.split-brain-resolver.active-strategy");
        
        assertEquals("keep-majority", strategy);
    }
    
    @Test
    public void testStableAfterConfigured() {
        Config config = actorSystem.getRaw().settings().config();
        Duration stableAfter = config.getDuration("pekko.cluster.split-brain-resolver.stable-after");
        
        assertEquals(Duration.ofSeconds(20), stableAfter);
    }
}
```

### 2. Simulated Multi-Node Tests

Test split brain scenarios using Pekko's testing tools with simulated network partitions:

```java
package io.github.seonwkim.core.cluster.splitbrain;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.*;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for split brain resolver tests.
 * Provides utilities for creating multi-node test scenarios.
 */
public abstract class SplitBrainTestBase {
    
    protected ClusterTestKit testKit;
    
    @BeforeEach
    public void setup() {
        testKit = new ClusterTestKit();
    }
    
    @AfterEach
    public void teardown() {
        if (testKit != null) {
            testKit.shutdown();
        }
    }
    
    /**
     * Waits for all nodes in the cluster to reach "Up" status.
     */
    protected void awaitAllNodesUp(List<ActorSystem<?>> systems, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        
        while (System.currentTimeMillis() < deadline) {
            boolean allUp = true;
            
            for (ActorSystem<?> system : systems) {
                Cluster cluster = Cluster.get(system);
                long upCount = cluster.state().getMembers().stream()
                    .filter(m -> m.status() == MemberStatus.up())
                    .count();
                
                if (upCount != systems.size()) {
                    allUp = false;
                    break;
                }
            }
            
            if (allUp) {
                return;
            }
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        }
        
        throw new RuntimeException("Timeout waiting for cluster convergence");
    }
    
    /**
     * Counts how many nodes are still "Up" in the cluster.
     */
    protected int countUpNodes(List<ActorSystem<?>> systems) {
        return (int) systems.stream()
            .map(Cluster::get)
            .map(c -> c.state())
            .filter(state -> state.getMembers().stream()
                .anyMatch(m -> m.status() == MemberStatus.up()))
            .count();
    }
    
    /**
     * Simulates network partition by marking nodes as unreachable.
     */
    protected void simulatePartition(ActorSystem<?> system, List<Member> unreachableMembers) {
        Cluster cluster = Cluster.get(system);
        
        for (Member member : unreachableMembers) {
            // Note: Actual unreachability simulation requires test-specific infrastructure
            // This is a placeholder for the concept
            cluster.down(member.address());
        }
    }
}
```

### 3. Keep-Majority Strategy Tests

```java
package io.github.seonwkim.core.cluster.splitbrain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for Keep-Majority split brain resolver strategy.
 * 
 * CRITICAL: These tests verify that the majority partition survives
 * and minority partitions are shut down.
 */
@TestPropertySource(properties = {
    "spring.actor.pekko.actor.provider=cluster",
    "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
    "spring.actor.pekko.cluster.split-brain-resolver.active-strategy=keep-majority",
    "spring.actor.pekko.cluster.split-brain-resolver.stable-after=10s",
    "spring.actor.pekko.cluster.split-brain-resolver.down-all-when-unstable=on"
})
public class KeepMajorityStrategyTest extends SplitBrainTestBase {
    
    @Test
    public void test5Nodes_3vs2_MajoritySurvives() throws Exception {
        // CRITICAL TEST: 5-node cluster partitioned into 3 vs 2
        // Expected: 3-node side stays up, 2-node side shuts down
        
        // Given: 5-node cluster with keep-majority
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Simulate network partition (3 vs 2)
        cluster.partition(
            List.of(0, 1, 2),  // Majority side (3 nodes)
            List.of(3, 4)       // Minority side (2 nodes)
        );
        
        // Wait for split brain resolution
        Thread.sleep(15000); // Wait longer than stable-after
        
        // Then: Verify majority side (3 nodes) stays up
        assertTrue(cluster.isUp(0), "Node 0 (majority) should be UP");
        assertTrue(cluster.isUp(1), "Node 1 (majority) should be UP");
        assertTrue(cluster.isUp(2), "Node 2 (majority) should be UP");
        
        // And: Verify minority side (2 nodes) is down
        assertTrue(cluster.isDown(3), "Node 3 (minority) should be DOWN");
        assertTrue(cluster.isDown(4), "Node 4 (minority) should be DOWN");
        
        // Additional verification: Count remaining cluster size from surviving nodes
        int clusterSize = cluster.getClusterSize(0); // Query from node 0
        assertEquals(3, clusterSize, "Cluster should have exactly 3 members");
    }
    
    @Test
    public void test6Nodes_3vs3_AllDown() throws Exception {
        // CRITICAL TEST: 6-node cluster with equal partition
        // Expected: All nodes shut down (no clear majority)
        
        // Given: 6-node cluster
        ClusterTestScenario cluster = testKit.createCluster(6, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority",
            "spring.actor.pekko.cluster.split-brain-resolver.down-all-when-unstable", "on"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Equal partition (3 vs 3)
        cluster.partition(
            List.of(0, 1, 2),
            List.of(3, 4, 5)
        );
        
        Thread.sleep(15000);
        
        // Then: All nodes should be down (no majority exists)
        for (int i = 0; i < 6; i++) {
            assertTrue(cluster.isDown(i), 
                "Node " + i + " should be DOWN (no majority possible)");
        }
    }
    
    @Test
    public void test5Nodes_1vs4_MajoritySurvives() throws Exception {
        // CRITICAL TEST: Single node separated from majority
        // Expected: 4-node side survives, single node shuts down
        
        // Given: 5-node cluster
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Single node isolated
        cluster.partition(
            List.of(0),           // Single isolated node
            List.of(1, 2, 3, 4)   // Majority (4 nodes)
        );
        
        Thread.sleep(15000);
        
        // Then: Single node is down
        assertTrue(cluster.isDown(0), "Isolated node should be DOWN");
        
        // And: Majority stays up
        assertTrue(cluster.isUp(1), "Node 1 (majority) should be UP");
        assertTrue(cluster.isUp(2), "Node 2 (majority) should be UP");
        assertTrue(cluster.isUp(3), "Node 3 (majority) should be UP");
        assertTrue(cluster.isUp(4), "Node 4 (majority) should be UP");
        
        // Verify cluster size from any surviving node
        int clusterSize = cluster.getClusterSize(1);
        assertEquals(4, clusterSize, "Cluster should have exactly 4 members");
    }
    
    @Test
    public void test7Nodes_MultiplePartitions_LargestSurvives() throws Exception {
        // CRITICAL TEST: Multiple simultaneous partitions
        // Expected: Largest partition survives
        
        // Given: 7-node cluster
        ClusterTestScenario cluster = testKit.createCluster(7, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Three-way partition (3 vs 2 vs 2)
        cluster.multiPartition(
            List.of(0, 1, 2),     // Largest partition (3 nodes)
            List.of(3, 4),        // Minority 1 (2 nodes)
            List.of(5, 6)         // Minority 2 (2 nodes)
        );
        
        Thread.sleep(15000);
        
        // Then: Largest partition survives
        assertTrue(cluster.isUp(0), "Node 0 (largest partition) should be UP");
        assertTrue(cluster.isUp(1), "Node 1 (largest partition) should be UP");
        assertTrue(cluster.isUp(2), "Node 2 (largest partition) should be UP");
        
        // All other nodes are down
        assertTrue(cluster.isDown(3), "Node 3 (minority) should be DOWN");
        assertTrue(cluster.isDown(4), "Node 4 (minority) should be DOWN");
        assertTrue(cluster.isDown(5), "Node 5 (minority) should be DOWN");
        assertTrue(cluster.isDown(6), "Node 6 (minority) should be DOWN");
    }
}
```

### 4. Keep-Oldest Strategy Tests

```java
package io.github.seonwkim.core.cluster.splitbrain;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for Keep-Oldest split brain resolver strategy.
 * 
 * CRITICAL: These tests verify that the partition containing
 * the oldest node survives, regardless of partition size.
 */
@TestPropertySource(properties = {
    "spring.actor.pekko.cluster.split-brain-resolver.active-strategy=keep-oldest",
    "spring.actor.pekko.cluster.split-brain-resolver.stable-after=10s",
    "spring.actor.pekko.cluster.split-brain-resolver.keep-oldest.down-if-alone=on"
})
public class KeepOldestStrategyTest extends SplitBrainTestBase {
    
    @Test
    public void test5Nodes_OldestOn2NodeSide_2NodesSurvive() throws Exception {
        // CRITICAL TEST: Oldest node on smaller partition
        // Expected: 2-node side with oldest survives, 3-node side shuts down
        
        // Given: 5-node cluster where node 0 is oldest (first to join)
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-oldest"
        ));
        
        cluster.awaitAllNodesUp();
        
        // Node 0 is the oldest (joined first)
        int oldestNode = 0;
        
        // When: Partition with oldest on smaller side
        cluster.partition(
            List.of(0, 1),        // Oldest is here (2 nodes)
            List.of(2, 3, 4)      // No oldest (3 nodes)
        );
        
        Thread.sleep(15000);
        
        // Then: Side with oldest survives (even though smaller)
        assertTrue(cluster.isUp(0), "Node 0 (oldest) should be UP");
        assertTrue(cluster.isUp(1), "Node 1 (with oldest) should be UP");
        
        // Side without oldest shuts down
        assertTrue(cluster.isDown(2), "Node 2 (no oldest) should be DOWN");
        assertTrue(cluster.isDown(3), "Node 3 (no oldest) should be DOWN");
        assertTrue(cluster.isDown(4), "Node 4 (no oldest) should be DOWN");
        
        // Verify cluster size
        int clusterSize = cluster.getClusterSize(0);
        assertEquals(2, clusterSize, "Cluster should have exactly 2 members");
    }
    
    @Test
    public void testOldestAlone_WithDownIfAlone_AllDown() throws Exception {
        // CRITICAL TEST: Oldest node completely isolated with down-if-alone=on
        // Expected: All nodes shut down
        
        // Given: 3-node cluster with down-if-alone=on
        ClusterTestScenario cluster = testKit.createCluster(3, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-oldest",
            "spring.actor.pekko.cluster.split-brain-resolver.keep-oldest.down-if-alone", "on"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Oldest node (node 0) is completely isolated
        cluster.partition(
            List.of(0),          // Oldest, alone
            List.of(1, 2)        // Others
        );
        
        Thread.sleep(15000);
        
        // Then: All nodes shut down
        // - Oldest is alone and down-if-alone=on, so it shuts down
        // - Others don't have oldest, so they shut down too
        assertTrue(cluster.isDown(0), "Node 0 (oldest, alone) should be DOWN");
        assertTrue(cluster.isDown(1), "Node 1 (no oldest) should be DOWN");
        assertTrue(cluster.isDown(2), "Node 2 (no oldest) should be DOWN");
    }
    
    @Test
    public void testOldestAlone_WithoutDownIfAlone_OldestSurvives() throws Exception {
        // CRITICAL TEST: Oldest node isolated with down-if-alone=off
        // Expected: Only oldest node survives
        
        // Given: 3-node cluster with down-if-alone=off
        ClusterTestScenario cluster = testKit.createCluster(3, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-oldest",
            "spring.actor.pekko.cluster.split-brain-resolver.keep-oldest.down-if-alone", "off"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Oldest node isolated
        cluster.partition(
            List.of(0),          // Oldest, alone
            List.of(1, 2)        // Others
        );
        
        Thread.sleep(15000);
        
        // Then: Oldest stays up even when alone
        assertTrue(cluster.isUp(0), "Node 0 (oldest, alone) should be UP");
        
        // Others shut down
        assertTrue(cluster.isDown(1), "Node 1 (no oldest) should be DOWN");
        assertTrue(cluster.isDown(2), "Node 2 (no oldest) should be DOWN");
        
        // Verify cluster size (just the oldest node)
        int clusterSize = cluster.getClusterSize(0);
        assertEquals(1, clusterSize, "Cluster should have exactly 1 member");
    }
}
```

### 5. Static Quorum Strategy Tests

```java
package io.github.seonwkim.core.cluster.splitbrain;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for Static Quorum split brain resolver strategy.
 * 
 * CRITICAL: These tests verify that only partitions meeting
 * or exceeding the quorum size survive.
 */
public class StaticQuorumStrategyTest extends SplitBrainTestBase {
    
    @Test
    public void test5Nodes_Quorum3_3vs2_QuorumSideSurvives() throws Exception {
        // CRITICAL TEST: Static quorum with one side meeting quorum
        // Expected: 3-node side (meets quorum) survives, 2-node side shuts down
        
        // Given: 5-node cluster with quorum-size=3
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "static-quorum",
            "spring.actor.pekko.cluster.split-brain-resolver.static-quorum.quorum-size", "3"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Partition into 3 vs 2
        cluster.partition(
            List.of(0, 1, 2),    // Meets quorum (3 >= 3)
            List.of(3, 4)        // Below quorum (2 < 3)
        );
        
        Thread.sleep(15000);
        
        // Then: Quorum side survives
        assertTrue(cluster.isUp(0), "Node 0 (quorum met) should be UP");
        assertTrue(cluster.isUp(1), "Node 1 (quorum met) should be UP");
        assertTrue(cluster.isUp(2), "Node 2 (quorum met) should be UP");
        
        // Below-quorum side shuts down
        assertTrue(cluster.isDown(3), "Node 3 (below quorum) should be DOWN");
        assertTrue(cluster.isDown(4), "Node 4 (below quorum) should be DOWN");
    }
    
    @Test
    public void test5Nodes_Quorum4_3vs2_AllDown() throws Exception {
        // CRITICAL TEST: Neither partition meets quorum
        // Expected: All nodes shut down
        
        // Given: 5-node cluster with quorum-size=4
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "static-quorum",
            "spring.actor.pekko.cluster.split-brain-resolver.static-quorum.quorum-size", "4",
            "spring.actor.pekko.cluster.split-brain-resolver.down-all-when-unstable", "on"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Partition into 3 vs 2 (both below quorum of 4)
        cluster.partition(
            List.of(0, 1, 2),    // Below quorum (3 < 4)
            List.of(3, 4)        // Below quorum (2 < 4)
        );
        
        Thread.sleep(15000);
        
        // Then: All nodes shut down (no partition meets quorum)
        for (int i = 0; i < 5; i++) {
            assertTrue(cluster.isDown(i), 
                "Node " + i + " should be DOWN (quorum not met)");
        }
    }
    
    @Test
    public void test6Nodes_Quorum3_3vs3_BothMeetQuorum() throws Exception {
        // CRITICAL TEST: Both partitions exactly meet quorum
        // Expected: Tie-breaker applies (typically address-based)
        
        // Given: 6-node cluster with quorum-size=3
        ClusterTestScenario cluster = testKit.createCluster(6, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "static-quorum",
            "spring.actor.pekko.cluster.split-brain-resolver.static-quorum.quorum-size", "3"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Partition into 3 vs 3 (both meet quorum)
        cluster.partition(
            List.of(0, 1, 2),    // Meets quorum (3 >= 3)
            List.of(3, 4, 5)     // Meets quorum (3 >= 3)
        );
        
        Thread.sleep(15000);
        
        // Then: One partition survives (tie-breaker determines which)
        int upCount = 0;
        for (int i = 0; i < 6; i++) {
            if (cluster.isUp(i)) {
                upCount++;
            }
        }
        
        assertEquals(3, upCount, "Exactly one partition (3 nodes) should survive");
        
        // Note: The specific partition that survives depends on tie-breaker
        // (typically the one with the lowest address)
    }
}
```

### 6. Edge Cases and Recovery Tests

```java
package io.github.seonwkim.core.cluster.splitbrain;

import org.junit.jupiter.api.Test;

/**
 * Tests for edge cases and recovery scenarios.
 */
public class SplitBrainEdgeCaseTests extends SplitBrainTestBase {
    
    @Test
    public void testRapidHealing_NoDowning() throws Exception {
        // CRITICAL TEST: Partition heals before stable-after timeout
        // Expected: No nodes are downed
        
        // Given: 5-node cluster with stable-after=20s
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority",
            "spring.actor.pekko.cluster.split-brain-resolver.stable-after", "20s"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: Partition occurs
        cluster.partition(List.of(0, 1, 2), List.of(3, 4));
        
        // But heals quickly (before stable-after)
        Thread.sleep(10000); // 10 seconds < 20 seconds stable-after
        cluster.heal();
        
        // Wait for cluster to stabilize
        Thread.sleep(5000);
        
        // Then: All nodes should still be up (partition healed too quickly)
        for (int i = 0; i < 5; i++) {
            assertTrue(cluster.isUp(i), 
                "Node " + i + " should be UP (quick heal)");
        }
    }
    
    @Test
    public void testCascadingFailures() throws Exception {
        // CRITICAL TEST: Sequential failures in surviving partition
        // Expected: Cluster adapts to each failure
        
        // Given: 5-node cluster
        ClusterTestScenario cluster = testKit.createCluster(5, Map.of(
            "spring.actor.pekko.cluster.split-brain-resolver.active-strategy", "keep-majority"
        ));
        
        cluster.awaitAllNodesUp();
        
        // When: First partition (3 vs 2)
        cluster.partition(List.of(0, 1, 2), List.of(3, 4));
        Thread.sleep(15000);
        
        // Verify majority survived
        assertTrue(cluster.isUp(0));
        assertTrue(cluster.isUp(1));
        assertTrue(cluster.isUp(2));
        
        // Then: Another node fails in surviving partition
        cluster.killNode(2);
        Thread.sleep(5000);
        
        // Should still have majority (2 out of original 5)
        assertTrue(cluster.isUp(0), "Node 0 should stay UP");
        assertTrue(cluster.isUp(1), "Node 1 should stay UP");
        
        int clusterSize = cluster.getClusterSize(0);
        assertEquals(2, clusterSize, "Cluster should have 2 members");
    }
}
```

## Test Infrastructure

### ClusterTestKit

The test infrastructure for creating and managing multi-node test clusters:

```java
package io.github.seonwkim.core.cluster.splitbrain;

import io.github.seonwkim.core.SpringActorSystem;
import java.util.*;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Test kit for creating and managing multi-node cluster test scenarios.
 * 
 * This is a simplified version for documentation purposes.
 * Full implementation would require Pekko's multi-node testing infrastructure.
 */
public class ClusterTestKit {
    
    private final List<ApplicationContext> contexts = new ArrayList<>();
    private final List<SpringActorSystem> actorSystems = new ArrayList<>();
    
    /**
     * Creates a cluster with the specified number of nodes.
     */
    public ClusterTestScenario createCluster(int nodeCount, Map<String, Object> config) {
        // Implementation would:
        // 1. Allocate ports for each node
        // 2. Configure seed nodes
        // 3. Start Spring Boot applications for each node
        // 4. Return ClusterTestScenario for test manipulation
        
        // Simplified placeholder
        return new ClusterTestScenario(actorSystems);
    }
    
    /**
     * Shuts down all test nodes.
     */
    public void shutdown() {
        contexts.forEach(ctx -> {
            if (ctx instanceof ConfigurableApplicationContext) {
                ((ConfigurableApplicationContext) ctx).close();
            }
        });
        
        contexts.clear();
        actorSystems.clear();
    }
}
```

### ClusterTestScenario

```java
package io.github.seonwkim.core.cluster.splitbrain;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.*;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.MemberStatus;

/**
 * Represents a specific multi-node cluster test scenario.
 * Provides methods to simulate network partitions and verify node states.
 */
public class ClusterTestScenario {
    
    private final List<SpringActorSystem> actorSystems;
    
    public ClusterTestScenario(List<SpringActorSystem> actorSystems) {
        this.actorSystems = actorSystems;
    }
    
    /**
     * Waits for all nodes to reach "Up" status.
     */
    public void awaitAllNodesUp() {
        awaitAllNodesUp(Duration.ofSeconds(30));
    }
    
    public void awaitAllNodesUp(Duration timeout) {
        // Implementation: Wait for cluster convergence
    }
    
    /**
     * Simulates network partition between two groups of nodes.
     */
    public void partition(List<Integer> side1, List<Integer> side2) {
        // Implementation: Use Pekko's TestConductor or similar
        // to simulate unreachability between sides
    }
    
    /**
     * Simulates multiple simultaneous partitions.
     */
    public void multiPartition(List<Integer>... partitions) {
        // Implementation: Simulate multiple isolated partitions
    }
    
    /**
     * Heals the network partition (all nodes can communicate again).
     */
    public void heal() {
        // Implementation: Restore connectivity between all nodes
    }
    
    /**
     * Checks if a node is still "Up" in the cluster.
     */
    public boolean isUp(int nodeIndex) {
        SpringActorSystem system = actorSystems.get(nodeIndex);
        Cluster cluster = Cluster.get(system.getRaw());
        
        // Check if this node's self member is "Up"
        return cluster.selfMember().status() == MemberStatus.up();
    }
    
    /**
     * Checks if a node has been downed or shut down.
     */
    public boolean isDown(int nodeIndex) {
        return !isUp(nodeIndex);
    }
    
    /**
     * Gets the current cluster size from a specific node's perspective.
     */
    public int getClusterSize(int nodeIndex) {
        SpringActorSystem system = actorSystems.get(nodeIndex);
        Cluster cluster = Cluster.get(system.getRaw());
        
        return (int) cluster.state().getMembers().stream()
            .filter(m -> m.status() == MemberStatus.up())
            .count();
    }
    
    /**
     * Kills a specific node (simulates hard failure).
     */
    public void killNode(int nodeIndex) {
        // Implementation: Terminate the actor system for this node
    }
    
    /**
     * Gracefully shuts down a node (allows handover).
     */
    public void gracefulShutdownNode(int nodeIndex) {
        // Implementation: Leave cluster gracefully, then terminate
    }
}
```

## Important Notes

### Limitations of Testing

1. **True Network Partitions**: Difficult to simulate in unit tests
   - Real network behavior is complex
   - OS-level network isolation required for accurate testing

2. **Timing Sensitivity**: Tests may be flaky due to:
   - Race conditions in cluster membership
   - Variable network/CPU performance
   - GC pauses affecting timing

3. **Multi-JVM Testing**: Full tests require:
   - Pekko Multi-JVM TestKit
   - Separate JVM processes
   - More complex setup

### Recommendations

For production validation:

1. **Integration Tests**: Use containerized environments (Docker, Kubernetes)
2. **Chaos Engineering**: Use tools like Chaos Monkey to simulate partitions in staging
3. **Monitoring**: Implement comprehensive monitoring in production
4. **Runbooks**: Document expected behavior and recovery procedures

## Running the Tests

### Single Test
```bash
./gradlew :core:test --tests "KeepMajorityStrategyTest.test5Nodes_3vs2_MajoritySurvives"
```

### All Split Brain Tests
```bash
./gradlew :core:test --tests "*SplitBrain*"
```

### With Detailed Logging
```bash
./gradlew :core:test --tests "*SplitBrain*" -Dspring.actor.pekko.loglevel=DEBUG
```

## See Also

- [Split Brain Resolver Configuration](split-brain-resolver-config.md)
- [Split Brain Monitoring](split-brain-monitoring.md)
- [Cluster Singleton Testing](testing-cluster-singleton.md)
- [Pekko Multi-Node Testing](https://pekko.apache.org/docs/pekko/current/multi-node-testing.html)
