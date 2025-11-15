# Task 4.2: Cluster Pub-Sub Spring Integration

**Priority:** MEDIUM  
**Estimated Effort:** 1 week  
**Status:** TODO

## Objective

Create Spring Boot auto-configuration and comprehensive documentation for cluster pub-sub.

## Auto-Configuration

```java
@Configuration
@ConditionalOnProperty(prefix = "spring.actor.pekko.cluster", name = "enabled", matchIfMissing = true)
public class ClusterPubSubAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ClusterEventBus clusterEventBus(SpringActorSystem actorSystem) {
        return new ClusterEventBus(actorSystem);
    }
}
```

## Annotation-Based Subscription

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClusterSubscriber {
    String[] topics();
}

@Component
@ClusterSubscriber(topics = {"orders", "payments"})
public class OrderEventHandler implements SpringActor<ClusterEvent> {
    // Automatically subscribes to topics on startup
}
```

## Configuration Properties

```yaml
spring:
  actor:
    pekko:
      cluster:
        pub-sub:
          # Routing strategy: broadcast, random, round-robin
          routing-strategy: broadcast
          # Maximum number of subscribers per topic
          max-subscribers-per-topic: 1000
```

## Deliverables

1. Auto-configuration class
2. `@ClusterSubscriber` annotation and processor
3. Configuration properties
4. Comprehensive guide: `docs/clustering/pub-sub-guide.md`
5. Complete example in `example/cluster/`

## Documentation Topics

- When to use cluster pub-sub
- Publishing events
- Subscribing to topics
- Delivery guarantees
- Performance considerations
- Best practices
- Troubleshooting

## Success Criteria

- ✅ Auto-configuration works out of the box
- ✅ Annotation-based subscription is convenient
- ✅ Documentation covers all use cases
- ✅ Examples are clear and complete
