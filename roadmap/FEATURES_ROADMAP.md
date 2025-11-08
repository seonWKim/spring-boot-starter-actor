# Production-Ready Features Roadmap for Spring Boot Starter Actor

This document outlines recommended features to bridge the gap between Pekko and Spring Boot, enabling developers to fully leverage this library for production applications.

## 📋 Table of Contents

1. [Persistence and Event Sourcing](#1-persistence-and-event-sourcing)
2. [Streams and Backpressure](#2-streams-and-backpressure)
3. [Routing Patterns](#3-routing-patterns)
4. [Testing Utilities](#4-testing-utilities)
5. [Advanced Clustering Features](#5-advanced-clustering-features)
6. [Enhanced Observability](#6-enhanced-observability)
7. [Performance and Resilience](#7-performance-and-resilience)
8. [Developer Experience](#8-developer-experience)
9. [Integration Features](#9-integration-features)
10. [Security and Compliance](#10-security-and-compliance)

---

## 1. Persistence and Event Sourcing

Pekko provides powerful persistence capabilities that are essential for production systems requiring durable state management.

### 1.1 Event Sourcing Support (`@SpringEventSourcedActor`)

**Priority:** HIGH  
**Complexity:** High  
**Pekko Module:** `pekko-persistence-typed`

**Description:**  
Enable actors to persist their state as a sequence of events. This allows state recovery after crashes and provides a complete audit trail.

**Implementation:**

```java
@Component
public class OrderActor implements SpringEventSourcedActor<OrderActor.Command, OrderActor.Event, OrderActor.State> {
    
    public interface Command {}
    public interface Event extends JsonSerializable {}
    
    public record CreateOrder(String orderId, double amount) implements Command {}
    public record OrderCreated(String orderId, double amount) implements Event {}
    
    public record State(String orderId, double amount, OrderStatus status) {}
    
    @Override
    public String persistenceId() {
        return "order-" + entityId;
    }
    
    @Override
    public SpringEventSourcedBehavior<Command, Event, State> create(EventSourcedContext<Command> ctx) {
        return SpringEventSourcedBehavior.<Command, Event, State>builder()
            .withEmptyState(new State(null, 0.0, OrderStatus.PENDING))
            .withCommandHandler((state, cmd) -> {
                if (cmd instanceof CreateOrder create) {
                    return Effect().persist(new OrderCreated(create.orderId(), create.amount()));
                }
                return Effect().none();
            })
            .withEventHandler((state, event) -> {
                if (event instanceof OrderCreated created) {
                    return new State(created.orderId(), created.amount(), OrderStatus.CREATED);
                }
                return state;
            })
            .build();
    }
}
```

**Benefits:**
- State survives actor restarts and system crashes
- Complete audit trail of all state changes
- Time-travel debugging capabilities
- Event replay for analytics and testing

**Configuration:**

```yaml
spring:
  actor:
    persistence:
      journal:
        plugin: jdbc-journal
      snapshot-store:
        plugin: jdbc-snapshot-store
      jdbc:
        url: jdbc:postgresql://localhost:5432/actordb
        username: ${DB_USER}
        password: ${DB_PASSWORD}
```

### 1.2 Snapshot Support

**Priority:** HIGH  
**Complexity:** Medium

**Description:**  
Periodic snapshots to optimize event replay performance for long-lived actors.

```java
@Override
public SpringEventSourcedBehavior<Command, Event, State> create(EventSourcedContext<Command> ctx) {
    return SpringEventSourcedBehavior.<Command, Event, State>builder()
        .withEmptyState(new State())
        .withSnapshotStrategy(SnapshotStrategy.afterNEvents(100)) // Snapshot every 100 events
        .withCommandHandler(this::onCommand)
        .withEventHandler(this::onEvent)
        .build();
}
```

=> TODO: I don't want the persistence feature to be implicitly handled. Instead I want the users(mostly spring boot users) to manually save the state.

### 1.3 Persistence Query Support

**Priority:** MEDIUM  
**Complexity:** Medium  
**Pekko Module:** `pekko-persistence-query`

**Description:**  
Query persisted events for read models, projections, and analytics.

```java
@Service
public class OrderQueryService {
    private final PersistenceQuery persistenceQuery;
    
    public Source<EventEnvelope, NotUsed> getOrderEvents(String orderId) {
        return persistenceQuery
            .eventsByPersistenceId("order-" + orderId, 0L, Long.MAX_VALUE);
    }
    
    public Source<EventEnvelope, NotUsed> getAllOrderEvents() {
        return persistenceQuery
            .eventsByTag("order", NoOffset.getInstance());
    }
}
```

=> TODO: I don't want the persistence feature to be implicitly handled. Instead I want the users(mostly spring boot users) to manually save the state. Let's provide some kind of adapter patterns for it so that users can wire in their own databases easily. You should also consider non-blocking for blocking db operations 
---

## 2. Streams and Backpressure

Pekko Streams provides powerful stream processing capabilities essential for handling high-throughput scenarios.

### 2.1 Pekko Streams Integration (`@SpringActorStream`)

**Priority:** HIGH  
**Complexity:** High  
**Pekko Module:** `pekko-stream-typed`

**Description:**  
Integrate Pekko Streams for processing data pipelines with actors.

```java
@Service
public class DataProcessingService {
    private final SpringActorSystem actorSystem;
    
    public CompletionStage<Done> processLargeDataset(List<String> data) {
        return Source.from(data)
            .via(Flow.fromFunction(this::transform))
            .mapAsync(10, item -> {
                return actorSystem.getOrSpawn(ProcessorActor.class, "processor-" + item.hashCode())
                    .thenCompose(actor -> actor.ask(new Process(item))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
            })
            .runWith(Sink.ignore(), actorSystem.getMaterializer());
    }
}
```

**Features:**
- Automatic backpressure handling
- Stream composition and transformation
- Integration with actors for stateful processing
- Built-in error handling and recovery

### 2.2 ActorSource and ActorSink

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Expose actors as stream sources and sinks.

```java
@Service
public class StreamActorService {
    
    public Source<Message, ActorRef> createActorSource() {
        return Source.actorRef(
            completionMatcher -> {/* completion logic */},
            failureMatcher -> {/* failure logic */},
            100, // buffer size
            OverflowStrategy.dropHead()
        );
    }
    
    public Sink<Message, NotUsed> createActorSink(SpringActorRef<Command> actor) {
        return Sink.foreach(msg -> actor.tell(new Process(msg)));
    }
}
```

=> TODO: we are using fluent builder patterns for public APIs for this library. Let's add support for streams using the fluent builder 

### 2.3 Throttling and Rate Limiting

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Built-in support for rate limiting actor message processing.

```java
@Component
public class RateLimitedActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withThrottling(
                maxThroughput: 100,
                duration: Duration.ofSeconds(1)
            )
            .onMessage(ProcessData.class, this::handleProcessData)
            .build();
    }
}
```

=> TODO: this is great, but let's implement throttling directly not using the pekko's supported features. 

---

## 3. Routing Patterns

Routing enables load balancing and parallel processing across multiple actor instances.
=> TODO: I love this idea. I want you to check whether we could provide library native support for routers or should simply wrap pekko's built in router support

### 3.1 Router Support (`@SpringRouterActor`)

**Priority:** HIGH  
**Complexity:** Medium  
**Pekko Module:** `pekko-actor-typed`

**Description:**  
Create router actors that distribute messages across multiple worker actors.

```java
@Component
public class WorkerPoolRouter implements SpringRouterActor<WorkerActor.Command> {
    
    @Override
    public SpringRouterBehavior<WorkerActor.Command> create(SpringActorContext ctx) {
        return SpringRouterBehavior.<WorkerActor.Command>builder()
            .withRoutingStrategy(RoutingStrategy.roundRobin())
            .withPoolSize(10)
            .withWorkerClass(WorkerActor.class)
            .withSupervisionStrategy(SupervisorStrategy.restart())
            .build();
    }
}

// Usage
actorSystem.getOrSpawn(WorkerPoolRouter.class, "worker-pool")
    .thenAccept(router -> router.tell(new ProcessTask("task-1")));
```

**Routing Strategies:**
- **Round Robin:** Distribute messages evenly
- **Random:** Random distribution
- **Smallest Mailbox:** Send to actor with smallest mailbox
- **Broadcast:** Send to all routees
- **Consistent Hashing:** Route based on message content
- **Scatter-Gather-First:** Send to all, use first response

### 3.2 Dynamic Router Resizing

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Automatically resize router pools based on load.

```java
@Override
public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
    return SpringRouterBehavior.<Command>builder()
        .withRoutingStrategy(RoutingStrategy.roundRobin())
        .withResizer(Resizer.builder()
            .withLowerBound(2)
            .withUpperBound(20)
            .withPressureThreshold(0.8)
            .withBackoffThreshold(0.3)
            .build())
        .build();
}
```

### 3.3 Consistent Hashing Router

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Route messages to the same actor based on message content (e.g., user ID).

```java
@Override
public SpringRouterBehavior<Command> create(SpringActorContext ctx) {
    return SpringRouterBehavior.<Command>builder()
        .withRoutingStrategy(RoutingStrategy.consistentHashing(
            msg -> ((UserCommand) msg).userId()
        ))
        .withPoolSize(10)
        .build();
}
```

---

## 4. Testing Utilities

Comprehensive testing support is essential for production-ready systems.
=> I want you to scan through the tests so extract common patterns. Let's extract those behaviors and support using test utilities 

### 4.1 TestKit Integration (`@ActorTest`)

**Priority:** HIGH  
**Complexity:** Medium  
**Pekko Module:** `pekko-actor-testkit-typed`

**Description:**  
Spring Boot test utilities for actor testing.

```java
@SpringBootTest
@ActorTest
public class OrderActorTest {
    
    @Autowired
    private ActorTestKit testKit;
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    public void testOrderCreation() {
        TestProbe<OrderResponse> probe = testKit.createTestProbe();
        
        SpringActorRef<OrderActor.Command> orderActor = 
            actorSystem.spawn(OrderActor.class, "test-order").toCompletableFuture().join();
        
        orderActor.tell(new CreateOrder("order-1", 100.0, probe.ref()));
        
        OrderResponse response = probe.expectMessageClass(OrderResponse.class);
        assertEquals("order-1", response.orderId());
    }
    
    @Test
    public void testSupervision() {
        BehaviorTestKit<Command> testKit = BehaviorTestKit.create(
            OrderActor.create(mockContext)
        );
        
        testKit.run(new FailingCommand());
        
        testKit.expectEffectType(Effect.Type.RESTARTED);
    }
}
```

### 4.2 Mock Actor Support

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Mock actors for unit testing services that use actors.

```java
@TestConfiguration
public class MockActorConfiguration {
    
    @Bean
    @Primary
    public SpringActorSystem mockActorSystem() {
        return new MockSpringActorSystem();
    }
}

@Test
public void testServiceWithMockActors() {
    MockSpringActorRef<Command> mockActor = 
        mockActorSystem.createMock(OrderActor.class);
    
    when(mockActor.ask(any())).thenReturn(
        CompletableFuture.completedFuture(new Success())
    );
    
    service.processOrder("order-1");
    
    verify(mockActor).tell(argThat(cmd -> 
        cmd instanceof CreateOrder create && 
        create.orderId().equals("order-1")
    ));
}
```

### 4.3 Performance Testing Utilities

**Priority:** LOW  
**Complexity:** Medium

**Description:**  
Built-in utilities for load testing and benchmarking actors.

```java
@Test
public void benchmarkActorThroughput() {
    ActorBenchmark benchmark = ActorBenchmark.create(actorSystem);
    
    BenchmarkResult result = benchmark
        .forActor(OrderActor.class)
        .withConcurrency(100)
        .withDuration(Duration.ofMinutes(1))
        .withMessageSupplier(() -> new CreateOrder(UUID.randomUUID().toString(), 100.0))
        .run();
    
    System.out.println("Throughput: " + result.messagesPerSecond());
    System.out.println("P95 Latency: " + result.p95Latency());
}
```

---

## 5. Advanced Clustering Features

Enhanced clustering capabilities for production-grade distributed systems.
=> TODO: We already support cluster singleton. 

### 5.1 Cluster Singleton Support

**Priority:** HIGH  
**Complexity:** Medium  
**Pekko Module:** `pekko-cluster-singleton`

**Description:**  
Ensure exactly one instance of an actor runs across the entire cluster.

```java
@Component
@ClusterSingleton
public class ClusterMasterActor implements SpringActor<ClusterMasterActor.Command> {
    
    public interface Command {}
    public record CoordinateTask(String taskId) implements Command {}
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(CoordinateTask.class, this::handleCoordination)
            .build();
    }
}

// Usage
SpringActorRef<Command> singleton = actorSystem
    .singleton(ClusterMasterActor.class)
    .get();
```

**Use Cases:**
- Distributed job schedulers
- Cluster-wide coordinators
- Single writer for consistent state

### 5.2 Distributed Data (CRDTs)

**Priority:** MEDIUM  
**Complexity:** High  
**Pekko Module:** `pekko-cluster-typed`, `pekko-distributed-data`

**Description:**  
Share eventually consistent data across cluster nodes using CRDTs.
=> TODO: can we support this feature using the simply ask methods provided by our libray? 

```java
@Service
public class DistributedCacheService {
    private final Replicator<LWWMap<String, String>> replicator;
    
    public CompletionStage<String> get(String key) {
        return replicator.askGet(
            askReplyTo -> new Replicator.Get<>(
                cacheKey,
                Replicator.readLocal(),
                askReplyTo
            ),
            timeout
        ).thenApply(response -> {
            if (response instanceof GetSuccess<LWWMap<String, String>> success) {
                return success.dataValue().get(key).orElse(null);
            }
            return null;
        });
    }
    
    public CompletionStage<Done> put(String key, String value) {
        return replicator.askUpdate(
            askReplyTo -> new Replicator.Update<>(
                cacheKey,
                LWWMap.empty(),
                Replicator.writeLocal(),
                askReplyTo,
                map -> map.put(selfUniqueAddress, key, value)
            ),
            timeout
        ).thenApply(response -> Done.getInstance());
    }
}
```

**Supported CRDTs:**
- LWWMap (Last-Write-Wins Map)
- ORSet (Observed-Remove Set)
- GCounter (Grow-Only Counter)
- PNCounter (Positive-Negative Counter)
- GSet (Grow-Only Set)

### 5.3 Cluster Pub-Sub

**Priority:** MEDIUM  
**Complexity:** Medium  
**Pekko Module:** `pekko-cluster-typed`

**Description:**  
Publish-subscribe messaging across cluster nodes.
=> Provide wrappers just like other features from our library so that users can more easily use them, yet we can extend with other features if needed 

```java
@Service
public class EventBusService {
    private final SpringActorSystem actorSystem;
    
    public void subscribe(String topic, SpringActorRef<Event> subscriber) {
        actorSystem.receptionist()
            .register(ServiceKey.create(Event.class, topic), subscriber);
    }
    
    public void publish(String topic, Event event) {
        actorSystem.receptionist()
            .find(ServiceKey.create(Event.class, topic))
            .thenAccept(listing -> {
                listing.getServiceInstances(ServiceKey.create(Event.class, topic))
                    .forEach(actor -> actor.tell(event));
            });
    }
}
```

### 5.4 Split Brain Resolver

**Priority:** HIGH  
**Complexity:** High

**Description:**  
Automatic cluster partition recovery with configurable strategies.
=> TODO: We need this support and also add explicit test so that it's working properly

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
```

**Strategies:**
- **keep-majority:** Keep the partition with majority of nodes
- **keep-oldest:** Keep the partition with oldest node
- **static-quorum:** Require minimum number of nodes
- **keep-referee:** Designated node breaks ties

### 5.5 Cluster Sharding Rebalancing

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Advanced shard rebalancing strategies and configuration.

```yaml
spring:
  actor:
    pekko:
      cluster:
        sharding:
          rebalance-interval: 10s
          least-shard-allocation-strategy:
            rebalance-threshold: 3
            max-simultaneous-rebalance: 5
```

---

## 6. Enhanced Observability

Production systems require comprehensive observability.

### 6.1 Distributed Tracing Integration

**Priority:** HIGH  
**Complexity:** Medium

**Description:**  
Integration with OpenTelemetry/Zipkin for tracing message flows across actors.

```java
@Component
public class TracedActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withTracing(enabled: true)
            .onMessage(ProcessData.class, (context, msg) -> {
                Span span = context.currentSpan();
                span.setAttribute("data.size", msg.data().length());
                
                // Processing logic
                
                return Behaviors.same();
            })
            .build();
    }
}
```

**Configuration:**

```yaml
spring:
  actor:
    tracing:
      enabled: true
      sampler: probability
      probability: 0.1
      exporter:
        type: zipkin
        endpoint: http://localhost:9411/api/v2/spans
```

### 6.3 Health Checks and Readiness Probes

**Priority:** HIGH  
**Complexity:** Low

**Description:**  
Spring Boot Actuator integration for actor system health.
=> TODO: this is wonderful 

```java
@Component
public class ActorSystemHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        return Health.up()
            .withDetail("actorSystem", actorSystem.name())
            .withDetail("activeActors", actorSystem.activeActorCount())
            .withDetail("clusterStatus", clusterStatus)
            .withDetail("unreachableNodes", unreachableNodes)
            .build();
    }
}
```

**Endpoints:**
- `/actuator/health/actors` - Actor system health
- `/actuator/metrics/actors` - Actor metrics
- `/actuator/actors` - List active actors
- `/actuator/cluster` - Cluster status

### 6.4 Enhanced Metrics (Completing TODO.md)

**Priority:** HIGH  
**Complexity:** Medium

Implement all metrics from `metrics/TODO.md`:

- **Actor Metrics:** processing-time, time-in-mailbox, mailbox-size, errors, messages.processed
- **System Metrics:** active-actors, dead-letters, unhandled-messages
- **Dispatcher Metrics:** threads.active, queue.size, utilization
- **Cluster Metrics:** hosted-shards, hosted-entities, processed-messages
- **Scheduler Metrics:** tasks.scheduled, tasks.completed

### 6.5 Grafana Dashboard Templates

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Pre-built Grafana dashboards for actor system visualization.

**Dashboards:**
- Actor System Overview
- Cluster Health and Topology
- Message Flow Analysis
- Performance Metrics
- Error Rate and Supervision

---

## 7. Performance and Resilience

Features to ensure high performance and reliability.

### 7.1 Circuit Breaker Pattern

**Priority:** HIGH  
**Complexity:** Medium

**Description:**  
Automatic circuit breaker for actor communication.
=> TODO: should we support this as a library native feature or use pekko's provided lib? 

```java
@Component
public class ResilientActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withCircuitBreaker(CircuitBreakerConfig.builder()
                .maxFailures(5)
                .callTimeout(Duration.ofSeconds(10))
                .resetTimeout(Duration.ofSeconds(30))
                .exponentialBackoffFactor(2.0)
                .build())
            .onMessage(CallExternalService.class, this::handleExternalCall)
            .build();
    }
}
```

### 7.2 Bulkhead Pattern

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Isolate actor pools to prevent cascading failures.
=> TODO: we already have dispatcher configuration support 

```java
@Configuration
public class DispatcherConfiguration {
    
    @Bean
    public DispatcherConfig externalServiceDispatcher() {
        return DispatcherConfig.builder()
            .withType(DispatcherType.FIXED_POOL)
            .withCorePoolSize(10)
            .withMaxPoolSize(10)
            .withQueueSize(100)
            .withRejectionPolicy(RejectionPolicy.CALLER_RUNS)
            .build();
    }
}
```

### 7.3 Retry Mechanisms with Backoff

**Priority:** HIGH  
**Complexity:** Low

**Description:**  
Configurable retry policies for actor operations.

```java
SpringActorRef<Command> actor = actorSystem.getOrSpawn(ServiceActor.class, "service-1")
    .toCompletableFuture().join();

CompletionStage<Result> result = actor
    .ask(new ProcessRequest(data))
    .withTimeout(Duration.ofSeconds(5))
    .withRetry(RetryConfig.builder()
        .maxAttempts(3)
        .backoff(Backoff.exponential(
            minBackoff: Duration.ofMillis(100),
            maxBackoff: Duration.ofSeconds(10),
            randomFactor: 0.2
        ))
        .retryOn(TimeoutException.class, IOException.class)
        .build())
    .execute();
```

### 7.4 Message Deduplication

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Automatic message deduplication based on message ID.
=> TODO: we absolutely need this 

```java
@Component
public class DeduplicatingActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withDeduplication(DeduplicationConfig.builder()
                .enabled(true)
                .cacheSize(10000)
                .ttl(Duration.ofMinutes(5))
                .idExtractor(msg -> ((IdentifiableMessage) msg).messageId())
                .build())
            .onMessage(ProcessMessage.class, this::handleMessage)
            .build();
    }
}
```

### 7.5 Adaptive Concurrency Control

**Priority:** LOW  
**Complexity:** High

**Description:**  
Dynamically adjust actor concurrency based on system load.
=> TODO: we will need a thorough testing for this  

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .withAdaptiveConcurrency(AdaptiveConcurrencyConfig.builder()
            .minConcurrency(1)
            .maxConcurrency(100)
            .targetLatency(Duration.ofMillis(100))
            .smoothingFactor(0.3)
            .build())
        .onMessage(ProcessData.class, this::handleData)
        .build();
}
```

---

## 8. Developer Experience

Features that improve developer productivity.

### 8.2 Actor Visualization Tool

**Priority:** LOW  
**Complexity:** Medium

**Description:**  
Web UI for visualizing actor hierarchy and message flow.

**Features:**
- Real-time actor hierarchy tree
- Message flow visualization
- Actor state inspection
- Performance metrics per actor
- Interactive debugging

**Access:** `http://localhost:8080/actuator/actors/ui`

### 8.4 Spring Boot Starter Templates

**Priority:** LOW  
**Complexity:** Low

**Description:**  
Project templates for common use cases.

```bash
spring init --dependencies=web,actor \
  --type=maven-project \
  --template=chat-application \
  my-chat-app
```

**Templates:**
- Distributed chat application
- Event-driven microservice
- IoT data processing
- Real-time analytics
- CQRS with event sourcing

### 8.5 Enhanced Error Messages

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Detailed error messages with troubleshooting hints.

```java
ActorSpawnException: Failed to spawn actor 'order-actor'
Cause: ActorNotFoundException: Bean 'OrderRepository' not found

💡 Troubleshooting hints:
  1. Ensure OrderRepository is annotated with @Repository
  2. Check if component scanning includes the repository package
  3. Verify database configuration in application.yml
  
📖 Documentation: https://docs.spring-actor.io/troubleshooting#bean-not-found
```

---

## 9. Integration Features

Seamless integration with Spring ecosystem and external systems.

### 9.1 Spring Events Bridge

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Bridge between Spring Application Events and Actor messages.
=> TODO: wonderful, we should support this but in a wrapped way 

```java
@Component
public class SpringEventListener {
    
    @EventListener
    @SendToActor(OrderActor.class)
    public OrderActor.CreateOrder onOrderPlacedEvent(OrderPlacedEvent event) {
        return new OrderActor.CreateOrder(event.getOrderId(), event.getAmount());
    }
}

@Component
public class OrderActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .onMessage(OrderCreated.class, (context, msg) -> {
                // Publish Spring event
                context.publishApplicationEvent(new OrderCreatedEvent(msg.orderId()));
                return Behaviors.same();
            })
            .build();
    }
}
```

### 9.4 Kafka Integration

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
Direct Kafka-to-Actor message routing with backpressure.
=> TODO: adding an example module for kafka integration should be enough 

```java
@Configuration
public class KafkaActorConfiguration {
    
    @Bean
    public ActorKafkaConsumer<String, OrderEvent> kafkaConsumer(
            SpringActorSystem actorSystem) {
        return ActorKafkaConsumer.<String, OrderEvent>builder()
            .withTopic("orders")
            .withGroupId("order-processor")
            .withActorClass(OrderActor.class)
            .withActorIdExtractor(event -> event.getOrderId())
            .withBackpressure(BackpressureConfig.defaultConfig())
            .build();
    }
}
```

### 9.5 gRPC Integration

**Priority:** LOW  
**Complexity:** High

**Description:**  
Expose actors as gRPC services.
=> TODO: adding an example module for grpc integration should be enough

```java
@GrpcService
public class OrderActorService extends OrderServiceGrpc.OrderServiceImplBase {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Override
    public void createOrder(CreateOrderRequest request, 
                           StreamObserver<CreateOrderResponse> responseObserver) {
        actorSystem.getOrSpawn(OrderActor.class, request.getOrderId())
            .thenCompose(actor -> actor.ask(
                new CreateOrder(request.getOrderId(), request.getAmount())
            ).withTimeout(Duration.ofSeconds(5)).execute())
            .whenComplete((result, error) -> {
                if (error != null) {
                    responseObserver.onError(error);
                } else {
                    responseObserver.onNext(toResponse(result));
                    responseObserver.onCompleted();
                }
            });
    }
}
```

---

## 10. Security and Compliance

Security features for production deployments.

### 10.1 TLS/SSL for Remote Actors

**Priority:** HIGH  
**Complexity:** Medium

**Description:**  
Secure actor-to-actor communication in cluster mode.

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          transport: tls-tcp
          ssl:
            enabled: true
            key-store: classpath:keystore.jks
            key-store-password: ${KEYSTORE_PASSWORD}
            trust-store: classpath:truststore.jks
            trust-store-password: ${TRUSTSTORE_PASSWORD}
            protocol: TLSv1.3
```

### 10.2 Authentication and Authorization

**Priority:** HIGH  
**Complexity:** High

**Description:**  
Role-based access control for actor operations.

```java
@Component
@Secured("ROLE_ADMIN")
public class AdminActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withAuthorization(AuthorizationConfig.builder()
                .requirePermission("admin:write")
                .build())
            .onMessage(AdminCommand.class, this::handleAdminCommand)
            .build();
    }
}

// Usage with authentication context
actorSystem.getOrSpawn(AdminActor.class, "admin")
    .withSecurityContext(SecurityContextHolder.getContext())
    .thenAccept(actor -> actor.tell(new AdminCommand()));
```

### 10.3 Audit Logging

**Priority:** MEDIUM  
**Complexity:** Low

**Description:**  
Automatic audit trail for sensitive operations.

```java
@Component
@Audited
public class PaymentActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withAuditing(AuditConfig.builder()
                .includeMessagePayload(true)
                .maskSensitiveFields("creditCardNumber", "cvv")
                .auditLevel(AuditLevel.INFO)
                .build())
            .onMessage(ProcessPayment.class, this::handlePayment)
            .build();
    }
}
```

### 10.4 Message Encryption

**Priority:** MEDIUM  
**Complexity:** High

**Description:**  
End-to-end encryption for sensitive messages.

```java
@Component
public class SecureActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withEncryption(EncryptionConfig.builder()
                .algorithm("AES-256-GCM")
                .keyProvider(keyManagementService)
                .encryptFields("sensitiveData", "personalInfo")
                .build())
            .onMessage(SecureCommand.class, this::handleSecureCommand)
            .build();
    }
}
```

### 10.5 Rate Limiting per User

**Priority:** MEDIUM  
**Complexity:** Medium

**Description:**  
User-specific rate limiting to prevent abuse.

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext ctx) {
    return SpringActorBehavior.builder(Command.class, ctx)
        .withRateLimiting(RateLimitConfig.builder()
            .strategy(RateLimitStrategy.PER_USER)
            .maxRequestsPerMinute(100)
            .keyExtractor(cmd -> ((UserCommand) cmd).userId())
            .onLimitExceeded(LimitExceededAction.THROTTLE)
            .build())
        .onMessage(UserCommand.class, this::handleCommand)
        .build();
}
```
