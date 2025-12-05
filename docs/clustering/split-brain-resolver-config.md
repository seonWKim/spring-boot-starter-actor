# Split Brain Resolver Configuration

## Overview

The **Split Brain Resolver** is a critical component for production cluster deployments. It automatically handles network partitions (split brain scenarios) by deciding which partition should survive and which nodes should be shut down.

Without proper split brain resolution, a network partition can result in multiple independent clusters, leading to:
- Data inconsistency
- Duplicate singleton actors
- Conflicting cluster state
- System instability

## What is a Split Brain?

A **split brain** occurs when a cluster is divided by a network failure into multiple partitions that cannot communicate with each other. Each partition may believe it's the valid cluster and continue operating independently.

```
Before Partition:
┌────────────────────────────────────┐
│    Healthy Cluster (5 nodes)      │
│  Node1 ← → Node2 ← → Node3         │
│    ↕         ↕         ↕           │
│  Node4 ← ← ← ← ← → →  Node5       │
└────────────────────────────────────┘

After Network Partition:
┌──────────────────┐       ┌──────────────────┐
│  Partition A     │       │  Partition B     │
│  (3 nodes)       │   X   │  (2 nodes)       │
│                  │       │                  │
│  Node1 ← → Node2 │       │  Node4 ← → Node5 │
│    ↕             │       │                  │
│  Node3           │       │                  │
└──────────────────┘       └──────────────────┘
       Survives                  Shut down
```

## Configuration

### Basic Setup

Add split brain resolver to your `application.yml`:

```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster  # Required for clustering
      
      cluster:
        # Enable Split Brain Resolver
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        
        split-brain-resolver:
          # Which strategy to use (see below)
          active-strategy: keep-majority
          
          # Time to wait for cluster stability before taking action
          stable-after: 20s
          
          # Down all nodes if no clear decision can be made
          down-all-when-unstable: on
```

## Strategies

### 1. Keep-Majority (Recommended)

**Best for**: Most production deployments with 3+ nodes

**How it works**: The partition with more than half of the nodes survives. All other partitions are shut down.

```yaml
split-brain-resolver:
  active-strategy: keep-majority
  
  keep-majority:
    # Optional: only consider nodes with specific role
    role: ""
```

**Example Scenarios**:

| Cluster Size | Partition Split | Surviving Side |
|--------------|-----------------|----------------|
| 5 nodes | 3 vs 2 | 3-node side survives |
| 5 nodes | 4 vs 1 | 4-node side survives |
| 6 nodes | 3 vs 3 | All nodes down (no majority) |
| 7 nodes | 4 vs 3 | 4-node side survives |

**Pros**:
- Simple and predictable
- Works well for most scenarios
- Prevents minority partitions from operating

**Cons**:
- Doesn't work well with even number of nodes (e.g., 4 or 6)
- Both sides might shut down if no clear majority (3 vs 3)

**When to use**:
- Odd number of nodes (3, 5, 7, etc.)
- Equal importance of all nodes
- Need simple, predictable behavior

### 2. Keep-Oldest

**Best for**: When you need deterministic behavior regardless of partition size

**How it works**: The partition containing the oldest node (first to join) survives. All other partitions shut down.

```yaml
split-brain-resolver:
  active-strategy: keep-oldest
  
  keep-oldest:
    # Should oldest be downed if completely alone?
    down-if-alone: on
    
    # Optional: role filter
    role: ""
```

**Example Scenarios**:

| Scenario | Oldest Node Location | Surviving Side |
|----------|---------------------|----------------|
| 5 nodes split 2 vs 3 | On 2-node side | 2-node side survives |
| 5 nodes split 1 vs 4 | Isolated | Depends on `down-if-alone` setting |

**`down-if-alone` behavior**:

```yaml
# down-if-alone: on (default)
# If oldest node is completely isolated, it shuts down
# Other partition also shuts down (no oldest node)
# Result: All nodes shut down

# down-if-alone: off
# Oldest node stays up even if isolated
# Other partition shuts down
# Result: Only oldest node survives
```

**Pros**:
- Works with any partition size (even or odd)
- Deterministic - you always know which side survives
- Good when oldest node has critical state

**Cons**:
- Oldest node becomes a single point of failure
- Small partition with oldest node beats larger partition
- May lose capacity (2-node side survives vs 3-node side)

**When to use**:
- Need deterministic behavior
- Oldest node has important state
- Cannot guarantee odd number of nodes

### 3. Static Quorum

**Best for**: Mission-critical systems requiring minimum capacity

**How it works**: Only partitions meeting or exceeding a static quorum size survive. All others shut down.

```yaml
split-brain-resolver:
  active-strategy: static-quorum
  
  static-quorum:
    # Minimum number of nodes required
    quorum-size: 3
    
    # Optional: role filter
    role: ""
```

**Example Scenarios (quorum-size: 3)**:

| Cluster Size | Partition Split | Surviving Side |
|--------------|-----------------|----------------|
| 5 nodes | 3 vs 2 | 3-node side survives |
| 5 nodes | 2 vs 3 | 3-node side survives |
| 5 nodes | 2 vs 2 vs 1 | All down (no partition ≥ 3) |
| 6 nodes | 3 vs 3 | Both sides down (tie-breaker may apply) |
| 7 nodes | 4 vs 3 | 4-node side survives |

**Pros**:
- Guarantees minimum cluster capacity
- Good for mission-critical systems
- Prevents under-capacity clusters

**Cons**:
- May down entire cluster if quorum not met
- Requires careful quorum-size selection
- Less flexible than keep-majority

**When to use**:
- Need guaranteed minimum capacity
- System requires N nodes to function
- Mission-critical applications

**Choosing quorum-size**:
- **Too low**: Doesn't provide enough protection
- **Too high**: May down entire cluster frequently
- **Rule of thumb**: `quorum-size = (total_nodes / 2) + 1`

Examples:
- 5 nodes → quorum-size: 3
- 7 nodes → quorum-size: 4
- 9 nodes → quorum-size: 5

### 4. Keep-Referee (Advanced)

**Best for**: Deployments with a designated decision-maker node

```yaml
split-brain-resolver:
  active-strategy: keep-referee
  
  keep-referee:
    # Address of the referee node
    address: "pekko://ActorSystem@10.0.0.1:2551"
    
    # Should referee be downed if alone?
    down-if-alone: on
```

**When to use**:
- Have a designated "master" or "coordinator" node
- Need explicit control over which side survives
- Referee node has special hardware/location

### 5. Down-All (Extreme)

**Best for**: Safety-first approach or during testing

```yaml
split-brain-resolver:
  active-strategy: down-all
  
  # No additional configuration needed
```

**What it does**: Shuts down ALL nodes when a split brain is detected

**When to use**:
- Extreme safety requirements
- Testing split brain detection
- Prefer complete shutdown over potential inconsistency

## Configuration Parameters

### stable-after

**Description**: Time to wait for cluster stabilization before making decisions

```yaml
split-brain-resolver:
  stable-after: 20s  # Default
```

**Purpose**:
- Prevents hasty decisions during temporary network issues
- Allows time for nodes to reconnect
- Reduces unnecessary shutdowns

**Tuning**:
- **Too short** (< 10s): May trigger on transient network issues
- **Too long** (> 60s): Increases time in split brain state
- **Recommended**: 15-30 seconds

### down-all-when-unstable

**Description**: What to do when no clear decision can be made

```yaml
split-brain-resolver:
  down-all-when-unstable: on  # Default: on
```

**Values**:
- `on`: Down all nodes when unstable (recommended for safety)
- `off`: Keep nodes up even when unstable (risky)

**Unstable scenarios**:
- Keep-majority with equal split (3 vs 3)
- Static quorum with no partition meeting quorum
- Multiple simultaneous partitions

**Recommendation**: Keep this `on` for production systems

## Role-Based Configuration

All strategies support role-based filtering:

```yaml
split-brain-resolver:
  active-strategy: keep-majority
  
  keep-majority:
    role: "worker"  # Only consider "worker" nodes
```

**Use case**: Different strategies for different node types

```yaml
spring:
  actor:
    pekko:
      cluster:
        roles:
          - "worker"  # This node's role
        
        split-brain-resolver:
          active-strategy: keep-majority
          keep-majority:
            role: "worker"  # Only count worker nodes for majority
```

## Production Best Practices

### 1. Choose the Right Strategy

| Deployment | Recommended Strategy | Reason |
|-----------|---------------------|---------|
| 3-7 nodes, equal importance | keep-majority | Simple, predictable |
| Any size, need determinism | keep-oldest | Always know which survives |
| Need guaranteed capacity | static-quorum | Ensures minimum nodes |
| Testing/Safety-first | down-all | Prevents any inconsistency |

### 2. Plan for Network Partitions

**Deploy across multiple availability zones**:
```yaml
# Zone A: 2 nodes
# Zone B: 2 nodes  
# Zone C: 1 node (tie-breaker)
# Total: 5 nodes
```

**Avoid symmetric deployments**:
- ❌ 2 zones with 2 nodes each (4 total) → Equal split
- ✅ 3 zones with 2+2+1 nodes (5 total) → Clear majority

### 3. Test Split Brain Scenarios

**Before production**:
- Simulate network partitions
- Verify correct nodes shut down
- Test recovery procedures
- Document expected behavior

### 4. Monitor Split Brain Events

- Track unreachable members
- Alert on partition events
- Monitor split brain resolver actions
- Log node down decisions

See: [Split Brain Monitoring](split-brain-monitoring.md)

### 5. Configure Appropriate Timeouts

```yaml
split-brain-resolver:
  stable-after: 20s  # Adjust based on your network
  
  keep-majority:
    role: ""
```

Consider your environment:
- **Fast network**: 15-20s
- **Slow/WAN network**: 30-60s
- **Kubernetes**: 20-30s (faster pod replacement)

## Troubleshooting

### All Nodes Shut Down

**Symptom**: Entire cluster shuts down after network partition

**Possible causes**:
1. **Keep-majority with equal split**:
   ```
   6 nodes split into 3 vs 3 → No majority → All down
   ```
   Solution: Use odd number of nodes or switch to keep-oldest

2. **Static quorum not met**:
   ```
   5 nodes, quorum-size: 4, split 3 vs 2 → Neither meets quorum → All down
   ```
   Solution: Lower quorum-size or add more nodes

3. **down-all-when-unstable: on** (working as designed):
   Solution: If intentional, ensure monitoring and recovery procedures are in place

### Wrong Partition Survives

**Symptom**: Smaller partition survives instead of larger one

**Possible causes**:
1. **Using keep-oldest** with oldest on smaller side:
   ```
   5 nodes split 2 vs 3, oldest on 2-node side → 2 nodes survive
   ```
   Solution: Intentional behavior with keep-oldest. Switch to keep-majority if unwanted.

2. **Role-based filtering**:
   ```yaml
   keep-majority:
     role: "worker"
   ```
   Only "worker" nodes are counted. Ensure roles are set correctly.

### Delayed Resolution

**Symptom**: Takes too long to resolve split brain

**Cause**: `stable-after` is too long

**Solution**:
```yaml
split-brain-resolver:
  stable-after: 15s  # Reduce from default 20s
```

Balance between:
- Quick resolution (lower value)
- Avoiding false positives (higher value)

### Split Brain Not Detected

**Symptom**: Cluster continues operating in split state

**Possible causes**:
1. **Split Brain Resolver not enabled**:
   ```yaml
   # Missing or incorrect
   downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
   ```

2. **Network allows some connectivity** (not a complete partition):
   Split Brain Resolver requires complete partition detection

## Example Configurations

### Small Cluster (3-5 nodes)

```yaml
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
```

### Large Cluster (7+ nodes) with Quorum

```yaml
spring:
  actor:
    pekko:
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          active-strategy: static-quorum
          stable-after: 20s
          down-all-when-unstable: on
          static-quorum:
            quorum-size: 5  # For 9-node cluster
            role: ""
```

### Deterministic Behavior

```yaml
spring:
  actor:
    pekko:
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          active-strategy: keep-oldest
          stable-after: 20s
          down-all-when-unstable: on
          keep-oldest:
            down-if-alone: on
            role: ""
```

## See Also

- [Split Brain Testing](testing-split-brain-resolver.md) - Comprehensive test suite
- [Split Brain Monitoring](split-brain-monitoring.md) - Production monitoring and alerting
- [Cluster Configuration](../configuration/cluster.md) - Full cluster configuration reference
- [Pekko Split Brain Resolver](https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html) - Official documentation
