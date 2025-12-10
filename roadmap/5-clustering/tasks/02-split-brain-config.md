# Task 2.1: Split Brain Resolver Configuration

**Priority:** CRITICAL  
**Estimated Effort:** 3-4 days  
**Status:** TODO

## Objective

Create Spring Boot YAML configuration support for Pekko's Split Brain Resolver, providing a production-ready solution for handling network partitions in clustered environments.

## Background

Split brain scenarios occur when a cluster is partitioned by network failures, resulting in multiple separate clusters that each believe they are the valid cluster. Without proper resolution, this can lead to data inconsistency and system instability.

Pekko provides a Split Brain Resolver with multiple strategies:
- **Keep-Majority**: Keep the partition with the majority of nodes
- **Keep-Oldest**: Keep the partition containing the oldest node
- **Static Quorum**: Keep the partition meeting a static quorum threshold
- **Keep-Referee**: Keep the partition containing a designated referee node
- **Down-All**: Down all nodes when split brain is detected

## Requirements

### 1. Spring Boot Configuration Mapping

Create configuration properties that map to Pekko's Split Brain Resolver settings.

#### Configuration Class Structure
```java
package io.github.seonwkim.core.config;

@ConfigurationProperties(prefix = "spring.actor.pekko.cluster.split-brain-resolver")
public class SplitBrainResolverProperties {
    
    private String activeStrategy = "keep-majority";
    private Duration stableAfter = Duration.ofSeconds(20);
    private DownAllWhenUnstable downAllWhenUnstable = DownAllWhenUnstable.ON;
    
    private KeepMajorityProperties keepMajority = new KeepMajorityProperties();
    private KeepOldestProperties keepOldest = new KeepOldestProperties();
    private StaticQuorumProperties staticQuorum = new StaticQuorumProperties();
    
    // Getters and setters
    
    public static class KeepMajorityProperties {
        private String role = "";
        // Additional properties
    }
    
    public static class KeepOldestProperties {
        private boolean downIfAlone = true;
        private String role = "";
    }
    
    public static class StaticQuorumProperties {
        private int quorumSize = 0;
        private String role = "";
    }
    
    public enum DownAllWhenUnstable {
        ON, OFF
    }
}
```

### 2. Application YAML Examples

#### Keep-Majority Strategy (Default)
```yaml
spring:
  actor:
    pekko:
      actor:
        provider: cluster
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          # Active strategy - use keep-majority
          active-strategy: keep-majority
          
          # Time margin for cluster state changes before split brain resolution
          stable-after: 20s
          
          # Should down all nodes if cluster becomes unstable
          down-all-when-unstable: on
          
          # Keep-majority specific configuration
          keep-majority:
            # Optional: only consider nodes with specific role
            role: ""
```

#### Keep-Oldest Strategy
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
            # Down the oldest node if it's alone (separated from all other nodes)
            down-if-alone: on
            # Optional: role filter
            role: ""
```

#### Static Quorum Strategy
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
            # Minimum number of nodes required to keep the partition up
            quorum-size: 3
            # Optional: role filter
            role: ""
```

### 3. Configuration Auto-Configuration

Create an auto-configuration class to automatically apply these settings:

```java
@Configuration
@ConditionalOnProperty(prefix = "spring.actor.pekko.cluster", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SplitBrainResolverProperties.class)
public class SplitBrainResolverAutoConfiguration {
    
    @Bean
    public Config splitBrainResolverConfig(SplitBrainResolverProperties properties) {
        // Convert Spring Boot properties to Pekko Config
        return ConfigFactory.parseString(buildConfigString(properties));
    }
    
    private String buildConfigString(SplitBrainResolverProperties properties) {
        // Build Pekko configuration from Spring properties
    }
}
```

### 4. Configuration Validation

Add validation to ensure configuration is correct:

```java
@Configuration
public class SplitBrainResolverConfigValidator {
    
    @Autowired
    public void validateConfiguration(SplitBrainResolverProperties properties) {
        if ("static-quorum".equals(properties.getActiveStrategy())) {
            if (properties.getStaticQuorum().getQuorumSize() <= 0) {
                throw new IllegalStateException(
                    "static-quorum strategy requires quorum-size > 0");
            }
        }
        
        // Additional validation
    }
}
```

### 5. Strategy Selection Guide

Document when to use each strategy:

#### Keep-Majority (Recommended for most cases)
- **Use when**: Cluster has 3+ nodes and can tolerate losing minority
- **Pros**: Simple, handles most scenarios well
- **Cons**: Issues with even-sized partitions (e.g., 3 vs 3)
- **Example**: 5-node cluster split into 3 vs 2 - the 3-node side survives

#### Keep-Oldest
- **Use when**: Need deterministic behavior regardless of partition size
- **Pros**: Predictable, works with any partition size
- **Cons**: Oldest node becomes a single point of failure
- **Example**: Useful when oldest node has critical state

#### Static Quorum
- **Use when**: Need guaranteed minimum cluster size
- **Pros**: Ensures minimum capacity, good for mission-critical systems
- **Cons**: May down entire cluster if quorum not met
- **Example**: 5-node cluster with quorum=3; any partition with <3 nodes shuts down

## Deliverables

1. **Configuration Classes**: In `core/src/main/java/io/github/seonwkim/core/config/`
   - `SplitBrainResolverProperties.java`
   - `SplitBrainResolverAutoConfiguration.java`
   - `SplitBrainResolverConfigValidator.java`

2. **Documentation**: `docs/clustering/split-brain-resolver-config.md`
   - Complete configuration reference
   - Strategy selection guide
   - Example configurations
   - Troubleshooting

3. **Example Application**: `example/cluster/src/main/resources/application.yml`
   - Updated with split brain resolver configuration
   - Comments explaining each setting

## Success Criteria

- ✅ Configuration properties correctly map to Pekko settings
- ✅ All three main strategies (keep-majority, keep-oldest, static-quorum) are configurable
- ✅ Validation prevents invalid configurations
- ✅ Documentation clearly explains when to use each strategy
- ✅ Example application demonstrates configuration

## Testing Requirements

- Unit tests for configuration property mapping
- Validation tests for invalid configurations
- Integration tests that configurations are applied correctly

## References

- Pekko Split Brain Resolver: https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html
- Pekko Configuration: https://pekko.apache.org/docs/pekko/current/general/configuration.html
