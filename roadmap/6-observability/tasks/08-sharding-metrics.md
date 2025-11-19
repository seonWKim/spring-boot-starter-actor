# Task 5.1: Cluster Sharding Metrics

**Priority:** MEDIUM
**Estimated Effort:** 4-5 days
**Dependencies:** Phase 4 complete
**Assignee:** AI Agent

---

## Objective

Implement metrics for cluster sharding to monitor entity distribution and shard health.

**Note:** Only implement if cluster sharding is being used.

---

## Metrics to Implement

### 1. `sharding.region.hosted-shards` (Gauge)
**Description:** Number of shards per region
**Use Case:** Monitor shard distribution

### 2. `sharding.region.hosted-entities` (Gauge)
**Description:** Number of entities per region
**Use Case:** Monitor entity distribution and load balancing

### 3. `sharding.region.processed-messages` (Counter)
**Description:** Messages processed per region
**Use Case:** Track regional throughput

### 4. `sharding.shard.hosted-entities` (Histogram)
**Description:** Entity distribution across shards
**Use Case:** Identify hot shards

### 5. `sharding.shard.processed-messages` (Histogram)
**Description:** Message distribution across shards
**Use Case:** Identify unbalanced message routing

---

## Implementation Requirements

### Files to Create

1. **`metrics/src/main/java/io/github/seonwkim/metrics/ShardingMetrics.java`**
   - Integrate with Pekko Cluster Sharding
   - Track shard allocation events
   - Monitor entity lifecycle within shards

2. **Instrumentation**
   - Instrument ShardRegion
   - Track shard coordinator events
   - Monitor entity passivation

3. **Tests**
   - `ShardingMetricsTest.java`
   - Multi-node cluster tests

---

## Configuration

```yaml
spring:
  actor:
    cluster:
      sharding:
        enabled: true
        metrics:
          enabled: true
          track-distribution: true
```

---

## Tags/Labels

Each metric should include:
- `system`: Actor system name
- `region`: Shard region name
- `shard-id`: Shard identifier (for shard-level metrics)

---

## Acceptance Criteria

- [ ] All 5 sharding metrics implemented
- [ ] Metrics track shard rebalancing
- [ ] Entity distribution visible
- [ ] Metrics properly tagged
- [ ] Multi-node cluster tests
- [ ] Documentation updated
- [ ] Performance overhead < 2%

---

## Notes

- Only implement if sharding is used
- Test with shard rebalancing scenarios
- Monitor shard handoff performance
