# Task 3.4: CRDT Spring Boot Integration

**Priority:** MEDIUM  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Create Spring Boot auto-configuration and comprehensive documentation for CRDT usage.

## Configuration Properties

```java
@ConfigurationProperties(prefix = "spring.actor.pekko.cluster.distributed-data")
public class DistributedDataProperties {
    private Duration gossipInterval = Duration.ofSeconds(2);
    private Duration notifySubscribersInterval = Duration.ofMillis(500);
    private List<String> durableKeys = new ArrayList<>();
    // Getters/setters
}
```

## Auto-Configuration

```java
@Configuration
@ConditionalOnProperty(prefix = "spring.actor.pekko.cluster", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DistributedDataProperties.class)
public class DistributedDataAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public DistributedMapService<String, String> distributedMapService(SpringActorSystem actorSystem) {
        return new DistributedMapService<>(actorSystem);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DistributedSetService<String> distributedSetService(SpringActorSystem actorSystem) {
        return new DistributedSetService<>(actorSystem);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DistributedCounterService distributedCounterService(SpringActorSystem actorSystem) {
        return new DistributedCounterService(actorSystem);
    }
}
```

## YAML Configuration

```yaml
spring:
  actor:
    pekko:
      cluster:
        distributed-data:
          gossip-interval: 2s
          notify-subscribers-interval: 500ms
          durable:
            keys: ["my-important-data"]
```

## Deliverables

1. Configuration classes in `core/src/main/java/io/github/seonwkim/core/config/`
2. Auto-configuration in `core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
3. Comprehensive guide: `docs/clustering/crdts-guide.md`
4. Usage examples in `example/cluster/`

## Documentation Topics

- What are CRDTs and when to use them
- LWWMap usage and trade-offs
- ORSet usage patterns
- Counter usage examples
- Configuration guide
- Best practices
- Troubleshooting

## Success Criteria

- ✅ Auto-configuration works out of the box
- ✅ Configuration properties map correctly
- ✅ Documentation is comprehensive
- ✅ Examples demonstrate common patterns
