# TODO - Development Tracking

This file tracks the development status of features outlined in the roadmap. Update this file as features are implemented.

---

## Development Phases

### Phase 1: Core Resilience & Production Readiness (3-4 months)

**Goal:** Make the library production-ready with essential resilience patterns

- [ ] **Circuit Breaker Pattern** (2-3 weeks)
  - [ ] Library-native implementation
  - [ ] Resilience4j integration
  - [ ] Spring Boot YAML configuration
  - [ ] Actuator endpoints
  - [ ] Metrics and health checks

- [ ] **Retry Mechanisms** (1-2 weeks)
  - [ ] Exponential backoff implementation
  - [ ] Jitter support
  - [ ] Configurable retry conditions
  - [ ] Integration with actor ask operations

- [ ] **Enhanced Metrics** (4-6 weeks)
  - [ ] Complete metrics/TODO.md checklist
  - [ ] Actor processing time metrics
  - [ ] Mailbox size metrics
  - [ ] Error counters
  - [ ] System-level metrics
  - [ ] Dispatcher metrics
  - [ ] Grafana dashboard templates

- [ ] **Health Checks** (1-2 weeks)
  - [ ] Actor system health indicator
  - [ ] Cluster health indicator
  - [ ] Kubernetes readiness/liveness probes
  - [ ] Actuator integration

- [ ] **TLS/SSL Support** (2-3 weeks)
  - [ ] Cluster communication encryption
  - [ ] Certificate management
  - [ ] Spring Boot configuration
  - [ ] Documentation and examples

- [ ] **Split Brain Resolver** (4-6 weeks)
  - [ ] Keep-majority strategy
  - [ ] Keep-oldest strategy
  - [ ] Static quorum strategy
  - [ ] **Explicit comprehensive tests** (CRITICAL)
  - [ ] Production monitoring
  - [ ] Health indicators

**Phase 1 Total:** ~15-22 weeks

---

### Phase 2: Advanced Features & Testing (4-5 months)

**Goal:** Add powerful features for complex use cases

- [ ] **Manual State Persistence** (6-8 weeks)
  - [ ] ActorStateAdapter interface
  - [ ] JPA adapter implementation
  - [ ] MongoDB adapter implementation
  - [ ] R2DBC adapter for non-blocking
  - [ ] Redis adapter for caching
  - [ ] Connection pooling
  - [ ] Retry configuration
  - [ ] Health checks
  - [ ] Documentation and examples

- [ ] **Router Support** (4-6 weeks)
  - [ ] Wrap Pekko routers with Spring Boot API
  - [ ] Fluent builder interface
  - [ ] Round Robin strategy
  - [ ] Smallest Mailbox strategy
  - [ ] Consistent Hashing strategy
  - [ ] Broadcast strategy
  - [ ] Dynamic resizing
  - [ ] Spring Boot YAML configuration
  - [ ] Metrics and health checks

- [ ] **TestKit Integration** (3-4 weeks)
  - [ ] @EnableActorTesting annotation
  - [ ] SpringActorTestKit
  - [ ] Fluent test API
  - [ ] Test probes
  - [ ] Mock actor support
  - [ ] Performance testing utilities
  - [ ] Extract common test patterns

- [ ] **Distributed Tracing** (3-4 weeks)
  - [ ] OpenTelemetry integration
  - [ ] Automatic span creation
  - [ ] Message context propagation
  - [ ] Spring Boot configuration
  - [ ] Sampling support
  - [ ] Documentation

- [ ] **Message Deduplication** (2-3 weeks)
  - [ ] In-memory cache (Caffeine)
  - [ ] Distributed cache (Redis)
  - [ ] Configurable TTL
  - [ ] Message ID extraction
  - [ ] Metrics
  - [ ] Spring Boot configuration

**Phase 2 Total:** ~18-25 weeks

---

### Phase 3: Streams & Integration (3-4 months)

**Goal:** Enable stream processing and external integrations

- [ ] **Fluent Stream Builder API** (6-8 weeks)
  - [ ] StreamBuilder interface
  - [ ] Source operations
  - [ ] Transformation operations
  - [ ] Actor integration (mapAsyncToActor)
  - [ ] Backpressure handling
  - [ ] Sink operations
  - [ ] Error handling and recovery
  - [ ] Documentation and examples

- [ ] **Library-Native Throttling** (2-3 weeks)
  - [ ] ThrottleConfig implementation
  - [ ] Per-actor throttling
  - [ ] Dynamic adjustment
  - [ ] Metrics integration
  - [ ] Health checks
  - [ ] Spring Boot configuration

- [ ] **Spring Events Bridge** (1-2 weeks)
  - [ ] @SendToActor annotation
  - [ ] Event-to-message conversion
  - [ ] Actor-to-event publishing
  - [ ] Documentation

- [ ] **WebSocket Integration** (2-3 weeks)
  - [ ] ActorWebSocketHandler
  - [ ] Message routing
  - [ ] Session management
  - [ ] Examples

- [ ] **Kafka Example Module** (1-2 weeks)
  - [ ] Consumer actor example
  - [ ] Producer actor example
  - [ ] Pipeline example
  - [ ] Documentation

- [ ] **gRPC Example Module** (1-2 weeks)
  - [ ] Service implementation example
  - [ ] Proto definitions
  - [ ] Documentation

**Phase 3 Total:** ~13-20 weeks

---

### Phase 4: Developer Experience (2-3 months)

**Goal:** Improve productivity and ease of use

- [ ] **Enhanced Error Messages** (1-2 weeks)
  - [ ] Troubleshooting hints
  - [ ] Common error scenarios
  - [ ] Documentation links
  - [ ] Stack trace formatting

- [ ] **Structured Logging with MDC** (1 week)
  - [ ] MDC configuration
  - [ ] Automatic context propagation
  - [ ] Message extraction
  - [ ] Documentation

- [ ] **Hot Reload Support** (4-5 weeks)
  - [ ] DevTools integration
  - [ ] State preservation
  - [ ] Graceful restart strategy
  - [ ] Configuration

- [ ] **Grafana Dashboards** (2-3 weeks)
  - [ ] Actor system overview dashboard
  - [ ] Cluster health dashboard
  - [ ] Performance metrics dashboard
  - [ ] Error rate dashboard
  - [ ] Export templates

- [ ] **Actor Visualization Tool** (4-6 weeks) - Optional
  - [ ] Web UI implementation
  - [ ] Actor hierarchy visualization
  - [ ] Message flow visualization
  - [ ] State inspection
  - [ ] Metrics display

**Phase 4 Total:** ~8-17 weeks

---

### Phase 5: Advanced Clustering (3-4 months)

**Goal:** Enterprise-grade distributed features

- [ ] **Cluster Singleton Documentation** (1 week)
  - [ ] Usage examples
  - [ ] Configuration guide
  - [ ] Failover handling
  - [ ] Testing guide

- [ ] **CRDTs with Ask Methods** (4-5 weeks)
  - [ ] Wrap CRDTs in actor commands
  - [ ] LWWMap support
  - [ ] ORSet support
  - [ ] Counter support
  - [ ] Spring Boot integration
  - [ ] Documentation

- [ ] **Cluster Pub-Sub** (3-4 weeks)
  - [ ] ClusterEventBus implementation
  - [ ] Subscribe API
  - [ ] Publish API
  - [ ] Topic management
  - [ ] Documentation

- [ ] **Dynamic Router Resizing** (2-3 weeks)
  - [ ] Auto-scaling configuration
  - [ ] Manual resize API
  - [ ] Metrics
  - [ ] Health checks

- [ ] **Snapshot Support** (2-3 weeks)
  - [ ] Periodic snapshot configuration
  - [ ] Manual snapshot API
  - [ ] Snapshot storage
  - [ ] Recovery

**Phase 5 Total:** ~12-18 weeks

---

### Phase 6: Security & Compliance (2-3 months)

**Goal:** Enterprise security features

- [ ] **Authentication/Authorization** (6-8 weeks)
  - [ ] Spring Security integration
  - [ ] @Secured annotation support
  - [ ] Role-based access control
  - [ ] Permission checking
  - [ ] Security context propagation

- [ ] **Audit Logging** (2-3 weeks)
  - [ ] @Audited annotation
  - [ ] Configurable masking
  - [ ] Multiple destinations (DB, Kafka, File)
  - [ ] Audit log format
  - [ ] Query API

- [ ] **Message Encryption** (4-5 weeks)
  - [ ] Field-level encryption
  - [ ] Key management integration
  - [ ] Algorithm support (AES-256-GCM)
  - [ ] Configuration

- [ ] **Rate Limiting per User** (2-3 weeks)
  - [ ] Per-user strategy
  - [ ] Key extraction
  - [ ] Limit exceeded actions
  - [ ] Metrics

**Phase 6 Total:** ~14-19 weeks

---

### Phase 7: Ecosystem Integration (2-3 months) - Optional

**Goal:** Deep Spring ecosystem integration

- [ ] **Spring Integration Support** (2-3 weeks)
  - [ ] Actor gateway
  - [ ] Channel integration
  - [ ] Flow integration

- [ ] **Code Generation** (3-4 weeks)
  - [ ] YAML/JSON definitions
  - [ ] Actor boilerplate generation
  - [ ] Gradle plugin

- [ ] **Spring Boot Templates** (2-3 weeks)
  - [ ] Project templates
  - [ ] Common use cases
  - [ ] Documentation

**Phase 7 Total:** ~7-10 weeks

---

## Total Timeline

- **Minimum:** 69 weeks (~16 months)
- **Maximum:** 109 weeks (~25 months)
- **Realistic (excluding Phase 7):** 80-93 weeks (~18-21 months)

---

## Priority Matrix

### Must Have (Phase 1-2)
- Circuit Breaker
- Retry Mechanisms
- Enhanced Metrics
- Health Checks
- TLS/SSL
- Split Brain Resolver
- Manual State Persistence
- Router Support
- TestKit Integration
- Message Deduplication

### Should Have (Phase 3-4)
- Stream Builder API
- Library-Native Throttling
- Spring Events Bridge
- WebSocket Integration
- Enhanced Error Messages
- Grafana Dashboards

### Nice to Have (Phase 5-7)
- CRDTs
- Cluster Pub-Sub
- Authentication/Authorization
- Audit Logging
- Message Encryption
- Hot Reload
- Visualization Tool

---

## Update Instructions

When implementing a feature:

1. Create a feature branch: `feature/circuit-breaker`
2. Implement the feature with tests
3. Update this TODO with ✅ checkmark
4. Add notes about implementation decisions
5. Update documentation
6. Create PR for review

---

**Last Updated:** 2025-11-08  
**Current Phase:** Planning / Phase 1 Preparation
