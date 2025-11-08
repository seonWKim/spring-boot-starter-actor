# Spring Boot Starter Actor - Implementation Roadmap

**Version:** 2.0 (Revised 2025-01-08)
**Status:** Ready for Implementation
**Timeline:** 14-16 months (optimized from 18-21 months)

---

## 🎯 Executive Summary

This roadmap transforms spring-boot-starter-actor into a production-ready, enterprise-grade actor framework. Based on comprehensive analysis, we've optimized the implementation plan by:

- **Simplifying over-engineered features** (persistence, streams, throttling)
- **Removing unnecessary features** (adaptive concurrency, hot reload, websocket)
- **Prioritizing production readiness** (metrics completion is CRITICAL)
- **Leveraging existing infrastructure** (Spring Data, Resilience4j, Pekko features)

**Key Finding:** Only 1 of 50+ planned metrics is implemented. **Metrics completion must be the top priority.**

---

## 🚨 Critical Implementation Order

### Phase 0: CRITICAL FOUNDATION (4-6 weeks) - DO THIS FIRST
**Directory:** `6-observability/`

**Current State:** Only `actor.processing-time` is implemented (1/50+ metrics)

**Must Complete:**
- Actor metrics (mailbox, lifecycle, errors)
- System metrics (active actors, dead letters, unhandled messages)
- Dispatcher metrics (threads, queue, utilization)
- Health checks (Actuator integration, Kubernetes probes)

**Why Critical:** Production readiness depends on comprehensive observability.

---

### Phase 1: Core Resilience & Production (8-10 weeks)

#### 1. Circuit Breaker (2-3 weeks) - `7-resilience/`
- **Approach:** Use Resilience4j (don't reimplement)
- **Features:** Per-actor circuit breakers, YAML config, Actuator endpoints
- **Priority:** HIGH

#### 2. Message Deduplication (2-3 weeks) - `7-resilience/`
- **Approach:** Caffeine (local) + Redis (distributed)
- **Features:** Configurable TTL, message ID extraction, metrics
- **Priority:** HIGH - Critical for idempotent distributed systems

#### 3. Retry Mechanisms (1-2 weeks) - `7-resilience/`
- **Approach:** Exponential backoff with jitter
- **Features:** Integration with actor.ask(), configurable conditions
- **Priority:** HIGH

#### 4. Enhanced Error Messages (1 week) - `8-developer-experience/`
- **Approach:** Context-aware errors with troubleshooting hints
- **Features:** Common error scenarios, documentation links, pretty formatting
- **Priority:** HIGH - Massive DX impact

#### 5. TLS/SSL (2-3 weeks) - `10-security/`
- **Approach:** Spring Boot YAML configuration
- **Features:** Certificate management, cluster encryption
- **Priority:** HIGH for production clusters

---

### Phase 2: Advanced Features (10-12 weeks)

#### 6. Router Support (6-7 weeks) - `3-routing/`
- **Approach:** ✅ EXCELLENT DESIGN - Wrap Pekko routers with Spring Boot API
- **Strategies:** Round Robin, Smallest Mailbox, Consistent Hashing, Broadcast
- **Features:** Dynamic resizing, supervision, metrics, health checks
- **Priority:** HIGH

#### 7. Testing Utilities (4 weeks) - `4-testing/`
- **Approach:** Thin wrapper over Pekko TestKit (don't reinvent)
- **Features:** @EnableActorTesting, fluent API, mock support
- **Priority:** HIGH

#### 8. Split Brain Resolver (4 weeks) - `5-clustering/`
- **Approach:** Spring Boot config for Pekko's SBR
- **Critical:** Explicit comprehensive tests for all scenarios
- **Strategies:** Keep-majority, keep-oldest, static quorum
- **Priority:** CRITICAL for cluster deployments

#### 9. MDC Logging (1 week) - `6-observability/`
- **Approach:** Automatic context propagation
- **Features:** Actor path, message type, correlation ID in logs
- **Priority:** MEDIUM-HIGH

---

### Phase 3: Integration & Documentation (6-8 weeks)

#### 10. Persistence Patterns Documentation (1-2 weeks) - `1-persistence/`
- **⚠️ CHANGED APPROACH:** Documentation only, NOT library features
- **Rationale:** Spring Data already provides everything needed
- **Content:** Best practices, examples (JPA, MongoDB, R2DBC), patterns
- **Priority:** MEDIUM

#### 11. Pekko Streams Examples (2-3 weeks) - `2-streams/`
- **⚠️ CHANGED APPROACH:** Examples only, NOT custom stream builder
- **Rationale:** Don't reimplement Pekko Streams (6-8 weeks saved)
- **Content:** Integration patterns, backpressure handling, documentation
- **Priority:** MEDIUM

#### 12. Spring Events Bridge (1-2 weeks) - `9-integration/`
- **Approach:** Library support (wrapped implementation)
- **Features:** @SendToActor annotation, event publishing
- **Priority:** MEDIUM

#### 13. Kafka/gRPC Examples (2-3 weeks) - `9-integration/`
- **Approach:** Example modules only (NOT library features)
- **Content:** Complete example applications
- **Priority:** MEDIUM

#### 14. Distributed Tracing (3 weeks) - `6-observability/`
- **Approach:** OpenTelemetry integration
- **Features:** Automatic span creation, context propagation
- **Priority:** MEDIUM (after metrics complete)

---

### Phase 4: Advanced Clustering & Security (8-10 weeks)

#### 15. Cluster Singleton Documentation (1 week) - `5-clustering/`
- **Status:** ✅ Already exists (isClusterSingleton method)
- **Task:** Document usage, failover, testing
- **Priority:** MEDIUM

#### 16. CRDTs Wrapped in Actors (4-5 weeks) - `5-clustering/`
- **Approach:** Wrap Pekko CRDTs, use existing ask() methods
- **Types:** LWWMap, ORSet, Counter
- **Priority:** MEDIUM

#### 17. Cluster Pub-Sub (3-4 weeks) - `5-clustering/`
- **Approach:** ClusterEventBus with Spring Boot API
- **Features:** Subscribe/publish, topic management
- **Priority:** MEDIUM

#### 18. Auth/AuthZ (6-8 weeks) - `10-security/`
- **Approach:** Spring Security integration
- **Features:** @Secured annotation, RBAC, security context propagation
- **Priority:** MEDIUM-HIGH

#### 19. Audit Logging (2-3 weeks) - `10-security/`
- **Approach:** @Audited annotation
- **Features:** Field masking, multiple destinations (DB, Kafka, File)
- **Priority:** MEDIUM

---

## 🗂️ Feature Directory Reference

Each feature has its own directory with detailed tasks:

| Directory | Priority | Effort | Status |
|-----------|----------|--------|--------|
| **6-observability/** | 🚨 CRITICAL | 10 weeks | Only 1/50+ metrics done |
| **7-resilience/** | ⚡ HIGH | 7 weeks | Circuit breaker, deduplication, retry |
| **3-routing/** | ⚡ HIGH | 7 weeks | Excellent design, proceed as-is |
| **4-testing/** | ⚡ HIGH | 4 weeks | Wrap Pekko TestKit |
| **5-clustering/** | ⚡ HIGH | 11 weeks | Split brain testing critical |
| **8-developer-experience/** | ⚡ HIGH | 2 weeks | Error messages = high DX |
| **10-security/** | ⚡ HIGH | 12 weeks | TLS first, then auth |
| **1-persistence/** | 📚 MEDIUM | 2 weeks | Documentation only |
| **2-streams/** | 📚 MEDIUM | 3 weeks | Examples only |
| **9-integration/** | 📚 MEDIUM | 4 weeks | Spring Events + examples |

**Navigate:** Each directory contains `README.md` and `tasks/TASK_PRIORITY.md`

---

## ❌ Removed Features

Based on analysis, these features were removed:

1. **Adaptive Concurrency Control** - Too complex, unclear value, high risk
2. **Hot Reload Support** - Complex to implement, unclear value vs. effort
3. **WebSocket Integration** - Unclear value per user feedback
4. **ActorStateAdapter Pattern** - Unnecessary abstraction (use Spring Data directly)
5. **Custom Stream Builder API** - Would reimplement Pekko Streams (use theirs)
6. **Library-Native Throttling** - Reimplement Pekko's proven throttling (wrap instead)

**Effort Saved:** 14-18 weeks redirected to high-value features

---

## 🎨 Design Principles

All features follow these principles:

1. **Spring Boot Native** - Leverage Spring Data, Security, Actuator, configuration
2. **Explicit over Implicit** - Users control when and how features are used
3. **Production Ready** - Built-in metrics, health checks, retry logic
4. **Developer Friendly** - Fluent APIs, clear errors, comprehensive docs
5. **Pragmatic** - Library support where valuable, examples where flexible
6. **Don't Reinvent** - Wrap battle-tested libraries (Resilience4j, Pekko)

---

## 📊 Key Design Decisions

| Feature | Decision | Rationale |
|---------|----------|-----------|
| **Persistence** | Manual patterns, no library features | Spring Data already provides everything |
| **Streams** | Examples only, not custom API | Don't reimplement Pekko Streams |
| **Throttling** | Wrap Pekko's, not library-native | Proven implementation, just add config |
| **Routers** | Wrap Pekko routers | Leverage battle-tested routing logic |
| **Circuit Breaker** | Use Resilience4j | Better Spring Boot integration |
| **Testing** | Wrap Pekko TestKit | Reuse proven infrastructure |
| **Kafka/gRPC** | Example modules only | Integration patterns vary by use case |

---

## 📈 Success Metrics

After full implementation, users can:

1. ✅ Monitor production systems (comprehensive metrics)
2. ✅ Build resilient distributed systems (circuit breaker, retry, deduplication)
3. ✅ Distribute load efficiently (routers with multiple strategies)
4. ✅ Test actors comprehensively (TestKit with Spring Boot integration)
5. ✅ Persist state using familiar patterns (Spring Data integration)
6. ✅ Process streams with backpressure (Pekko Streams examples)
7. ✅ Secure cluster communication (TLS/SSL)
8. ✅ Handle cluster failures (split brain resolver)
9. ✅ Debug easily (enhanced error messages)
10. ✅ Deploy to Kubernetes (health checks, readiness probes)

---

## 🚀 Getting Started

### For Implementers

1. **Read this file** - Understand overall roadmap
2. **Start with Phase 0** - Navigate to `6-observability/`
3. **Check task breakdown** - Read `tasks/TASK_PRIORITY.md`
4. **Implement tasks** - Follow detailed task specifications
5. **Track progress** - Update checkboxes in TASK_PRIORITY.md

### For Maintainers

1. **Verify priorities** - Confirm Phase 0-1 order
2. **Review metrics** - Check `metrics/TODO.md` for complete list
3. **Plan sprints** - Use task estimates for planning
4. **Monitor progress** - Track completion in task files

---

## 📝 Task Organization

```
feature-directory/
├── README.md              # Feature overview with review notes
└── tasks/
    ├── TASK_PRIORITY.md   # Priority order, estimates, phases
    └── XX-task-name.md    # Detailed specifications (created as needed)
```

**Example detailed tasks created:**
- `6-observability/tasks/01-actor-mailbox-metrics.md`
- `7-resilience/tasks/01-circuit-breaker-integration.md`
- `8-developer-experience/tasks/01-error-message-framework.md`

---

## ⏱️ Timeline Summary

| Phase | Duration | Focus |
|-------|----------|-------|
| **Phase 0** | 4-6 weeks | Metrics completion (CRITICAL) |
| **Phase 1** | 8-10 weeks | Circuit breaker, dedup, retry, errors, TLS |
| **Phase 2** | 10-12 weeks | Routers, testing, split brain, MDC |
| **Phase 3** | 6-8 weeks | Documentation, examples, tracing |
| **Phase 4** | 8-10 weeks | CRDTs, pub-sub, auth, audit |

**Total:** 36-46 weeks (14-16 months)
**Original:** 18-21 months
**Improvement:** 20% faster

---

## 🔍 Implementation Status

Track completion in individual `tasks/TASK_PRIORITY.md` files:

```markdown
- [x] Completed task
- [ ] Pending task
```

---

## 💡 Key Recommendations

1. **Complete metrics FIRST** - Production readiness depends on observability
2. **Use existing infrastructure** - Don't reinvent Spring Data, Resilience4j, Pekko features
3. **Document patterns, not features** - Persistence needs docs, not library code
4. **Examples before abstractions** - Streams needs examples, not custom API
5. **Test split brain extensively** - Cluster reliability is critical
6. **Wrap, don't reimplement** - Leverage battle-tested libraries

---

## 📚 Additional Resources

- **Review Analysis:** See original `ROADMAP_REVIEW_2025-01-08.md` for detailed rationale
- **Metrics TODO:** `../metrics/TODO.md` - Complete list of 50+ metrics to implement
- **Current Implementation:** Check `../core/`, `../metrics/` for existing code

---

## 🤝 Contributing

When implementing a feature:

1. Navigate to feature directory (e.g., `6-observability/`)
2. Read `README.md` for context and review notes
3. Check `tasks/TASK_PRIORITY.md` for task breakdown
4. Implement tasks in priority order
5. Update checkboxes as you complete tasks
6. Create detailed task specs as needed (follow examples)

---

**Last Updated:** 2025-01-08
**Maintainer:** Review and approve
**Status:** Ready for Phase 0 implementation
