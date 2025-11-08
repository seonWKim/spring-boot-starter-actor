# Spring Boot Starter Actor - Feature Roadmap

This roadmap outlines recommended features to transform spring-boot-starter-actor into a production-ready, enterprise-grade actor framework for Spring Boot applications.

---

## Overview

Our goal is to bridge the full capabilities of Apache Pekko with Spring Boot, prioritizing **Developer Experience (DX)** for Spring Boot users and **Production Readiness** for enterprise deployments.

### Design Principles

1. **Spring Boot Native**: Leverage Spring Data, Spring Security, Spring Boot configuration
2. **Explicit over Implicit**: Users control when and how features are used
3. **Production Ready**: Built-in metrics, health checks, retry logic, connection pooling
4. **Developer Friendly**: Fluent APIs, clear error messages, comprehensive documentation
5. **Pragmatic**: Library support where it adds value, examples where flexibility is needed

---

## Feature Categories

### [1. Persistence and Event Sourcing](1_PERSISTENCE_AND_EVENT_SOURCING.md)

Manual state management with Spring Data adapter patterns for database-agnostic persistence.

**Key Features:**
- Manual state control via Spring Data repositories (JPA, MongoDB, R2DBC)
- ActorStateAdapter pattern for any database
- Non-blocking support with reactive databases
- Production-ready: connection pooling, retries, health checks

**Priority:** HIGH  
**Approach:** Manual, explicit state management (not implicit event sourcing)

---

### [2. Streams and Backpressure](2_STREAMS_AND_BACKPRESSURE.md)

Fluent builder API for stream processing with library-native throttling.

**Key Features:**
- Fluent builder pattern: `.stream().source().map().mapAsyncToActor().run()`
- Library-native throttling (not Pekko's) with full monitoring
- Automatic backpressure handling
- Spring Boot YAML configuration

**Priority:** HIGH  
**Approach:** Fluent builders with library-native throttling

---

### [3. Routing Patterns](3_ROUTING_PATTERNS.md)

Load balancing and parallel processing with hybrid Pekko wrapper approach.

**Key Features:**
- Wrap Pekko's battle-tested routers with Spring Boot API
- Multiple strategies: Round Robin, Smallest Mailbox, Consistent Hashing, Broadcast
- Dynamic resizing based on load
- Built-in metrics and health checks

**Priority:** HIGH  
**Approach:** Hybrid - Wrap Pekko routers with Spring Boot-friendly API

---

### [4. Testing Utilities](4_TESTING_UTILITIES.md)

Extract common test patterns and provide Spring Boot-friendly test utilities.

**Key Features:**
- Fluent test API: `testKit.forActor().spawn().send().expectReply()`
- Mock actor support for unit testing
- Performance testing utilities
- Extract patterns from existing tests

**Priority:** HIGH  
**Approach:** Extract common patterns, provide reusable utilities

---

### [5. Advanced Clustering](5_ADVANCED_CLUSTERING.md)

Enterprise-grade distributed features with explicit testing.

**Key Features:**
- Cluster Singleton (already supported - document it!)
- CRDTs wrapped in actor commands using existing ask() methods
- Split Brain Resolver with **explicit comprehensive tests** (CRITICAL)
- Cluster Pub-Sub with Spring Boot-friendly API

**Priority:** HIGH (Split Brain Resolver)  
**Approach:** Document existing features, wrap CRDTs, explicit tests for split brain

---

### [6. Enhanced Observability](6_ENHANCED_OBSERVABILITY.md)

Production-grade observability with metrics, health checks, and tracing.

**Key Features:**
- Deep Spring Boot Actuator integration
- Complete metrics/TODO.md checklist
- OpenTelemetry distributed tracing
- Structured logging with MDC
- Kubernetes readiness/liveness probes
- Grafana dashboard templates

**Priority:** HIGH  
**Approach:** Wonderful features - expand implementation

---

### [7. Performance and Resilience](7_PERFORMANCE_AND_RESILIENCE.md)

Essential resilience patterns with library-native implementations where appropriate.

**Key Features:**
- **Circuit Breaker**: Library-native with Resilience4j integration
- **Message Deduplication**: Absolutely need this! (HIGH priority)
- **Retry Mechanisms**: Exponential backoff with jitter
- **Bulkhead Pattern**: Document existing dispatcher configuration
- **Adaptive Concurrency**: Needs thorough testing

**Priority:** HIGH (Circuit Breaker, Deduplication, Retry)  
**Approach:** Library-native for better Spring Boot integration

---

### [8. Developer Experience](8_DEVELOPER_EXPERIENCE.md)

Features that improve developer productivity.

**Key Features:**
- Enhanced error messages with troubleshooting hints
- Hot reload support with Spring Boot DevTools
- Actor visualization tool (optional)

**Priority:** MEDIUM (Error messages HIGH)  
**Approach:** Focus on clear, actionable feedback

---

### [9. Integration Features](9_INTEGRATION_FEATURES.md)

Seamless integration with Spring ecosystem and external systems.

**Key Features:**
- **Spring Events Bridge**: Wrapped implementation
- **WebSocket Integration**: Library support
- **Kafka Integration**: Example module only
- **gRPC Integration**: Example module only

**Priority:** MEDIUM  
**Approach:** Library support for Spring, examples for external systems

---

### [10. Security and Compliance](10_SECURITY_AND_COMPLIANCE.md)

Enterprise security features for production deployments.

**Key Features:**
- TLS/SSL for cluster communication
- Authentication/Authorization with Spring Security
- Audit logging with field masking
- Message encryption
- Per-user rate limiting

**Priority:** HIGH (TLS/SSL, Auth)  
**Approach:** Spring Security integration

---

## Development Phases

See [TODO.md](TODO.md) for detailed development tracking and timeline.

### Quick Summary

| Phase | Duration | Focus | Key Features |
|-------|----------|-------|--------------|
| **Phase 1** | 3-4 months | Core Resilience | Circuit Breaker, Retry, Metrics, Health Checks, TLS, Split Brain |
| **Phase 2** | 4-5 months | Advanced Features | State Persistence, Routers, TestKit, Tracing, Deduplication |
| **Phase 3** | 3-4 months | Streams & Integration | Stream Builder, Throttling, Spring Events, WebSocket, Examples |
| **Phase 4** | 2-3 months | Developer Experience | Error Messages, Logging, Hot Reload, Dashboards |
| **Phase 5** | 3-4 months | Advanced Clustering | Singleton Docs, CRDTs, Pub-Sub, Dynamic Resizing |
| **Phase 6** | 2-3 months | Security | Auth, Audit Logging, Encryption, Rate Limiting |
| **Phase 7** | 2-3 months | Ecosystem (Optional) | Spring Integration, Templates, Code Gen |

**Total Timeline:** 18-21 months (excluding Phase 7)

---

## Implementation Priorities

### Must Have (Phases 1-2)
✅ Circuit Breaker  
✅ Retry Mechanisms  
✅ Enhanced Metrics  
✅ Health Checks  
✅ TLS/SSL  
✅ Split Brain Resolver  
✅ Manual State Persistence  
✅ Router Support  
✅ TestKit Integration  
✅ Message Deduplication  

### Should Have (Phases 3-4)
- Stream Builder API
- Library-Native Throttling
- Spring Events Bridge
- WebSocket Integration
- Enhanced Error Messages
- Grafana Dashboards

### Nice to Have (Phases 5-7)
- CRDTs
- Cluster Pub-Sub
- Authentication/Authorization
- Audit Logging
- Message Encryption
- Hot Reload
- Visualization Tool

---

## Key Design Decisions

### 1. Persistence: Manual vs Automatic
**Decision:** Manual state management with adapter patterns  
**Rationale:** Spring Boot users expect explicit control, familiar Spring Data patterns

### 2. Streams: API Design
**Decision:** Fluent builder API  
**Rationale:** Consistent with library's existing patterns, intuitive for Spring Boot users

### 3. Throttling: Pekko vs Library-Native
**Decision:** Library-native implementation  
**Rationale:** Better Spring Boot integration, full control over monitoring

### 4. Routers: Native vs Wrapper
**Decision:** Hybrid - wrap Pekko routers  
**Rationale:** Leverage battle-tested routing logic, provide Spring Boot-friendly API

### 5. Circuit Breaker: Pekko vs Library-Native
**Decision:** Library-native with Resilience4j integration  
**Rationale:** Better Spring Boot integration, leverage existing Spring circuit breaker ecosystem

### 6. Kafka/gRPC: Library vs Examples
**Decision:** Example modules only  
**Rationale:** Integration patterns vary by use case, examples provide flexibility

### 7. Testing: Custom vs Pekko TestKit
**Decision:** Extract common patterns, build on Pekko TestKit  
**Rationale:** Reuse proven testing infrastructure, add Spring Boot-friendly wrappers

---

## Success Metrics

After implementation, users should be able to:

1. ✅ Build production apps with durable state (persistence)
2. ✅ Process high-throughput streams with backpressure
3. ✅ Distribute load efficiently (routers)
4. ✅ Test actor behavior comprehensively (test kit)
5. ✅ Handle failures gracefully (circuit breaker, retry)
6. ✅ Monitor system health (metrics, health checks, tracing)
7. ✅ Secure cluster communication (TLS/SSL, auth)
8. ✅ Deploy to Kubernetes (readiness/liveness probes)
9. ✅ Debug easily (error messages, visualization)
10. ✅ Scale horizontally (cluster sharding, split brain resolver)

---

## Contributing

See [TODO.md](TODO.md) for tracking development progress.

When implementing a feature:
1. Review the detailed spec in the corresponding category file
2. Create feature branch
3. Implement with tests
4. Update TODO.md
5. Submit PR

---

## Community Input

We need your feedback on:

1. **Feature Priorities** - Which features are most critical for your use case?
2. **API Design** - Review proposed APIs in category files
3. **Use Cases** - Share your production requirements
4. **Design Decisions** - Agree/disagree with our approaches?

**Open an issue to discuss!**

---

**Last Updated:** 2025-11-08  
**Version:** 2.0  
**Status:** Enhanced based on maintainer feedback
