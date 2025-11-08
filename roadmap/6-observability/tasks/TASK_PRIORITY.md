# Observability Implementation Tasks

**Overall Priority:** 🚨 CRITICAL - HIGHEST PRIORITY
**Current State:** Only 1/50+ metrics implemented

---

## Task Breakdown (In Priority Order)

### Phase 1: Core Actor Metrics (Week 1-2)
**Priority:** CRITICAL
**Estimated Effort:** 2 weeks

- [ ] **Task 1.1:** Implement actor mailbox metrics (2-3 days)
  - File: `tasks/01-actor-mailbox-metrics.md`
  - Metrics: `actor.time-in-mailbox`, `actor.mailbox-size`

- [ ] **Task 1.2:** Implement actor lifecycle metrics (2-3 days)
  - File: `tasks/02-actor-lifecycle-metrics.md`
  - Metrics: `actor.lifecycle.restarts`, `actor.lifecycle.stops`

- [ ] **Task 1.3:** Implement actor error metrics (1-2 days)
  - File: `tasks/03-actor-error-metrics.md`
  - Metrics: `actor.errors`, `actor.messages.processed`

### Phase 2: System-Level Metrics (Week 3)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 2.1:** Implement actor system metrics (3-4 days)
  - File: `tasks/04-system-metrics.md`
  - Metrics: `system.active-actors`, `system.processed-messages`, `system.dead-letters`, `system.unhandled-messages`

### Phase 3: Dispatcher Metrics (Week 4)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 3.1:** Implement dispatcher/thread pool metrics (4-5 days)
  - File: `tasks/05-dispatcher-metrics.md`
  - Metrics: All dispatcher metrics from TODO.md

### Phase 4: Health Checks (Week 5)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 4.1:** Implement Spring Boot Actuator health indicators (3-4 days)
  - File: `tasks/06-health-checks.md`
  - Components: `ActorSystemHealthIndicator`, `ClusterHealthIndicator`

- [ ] **Task 4.2:** Add Kubernetes readiness/liveness probes (1-2 days)
  - File: `tasks/07-kubernetes-probes.md`

### Phase 5: Cluster Sharding Metrics (Week 6) - If using sharding
**Priority:** MEDIUM
**Estimated Effort:** 1 week

- [ ] **Task 5.1:** Implement sharding metrics (4-5 days)
  - File: `tasks/08-sharding-metrics.md`
  - Metrics: All sharding metrics from TODO.md

### Phase 6: MDC & Structured Logging (Week 7)
**Priority:** MEDIUM
**Estimated Effort:** 3-4 days

- [ ] **Task 6.1:** Implement MDC context propagation (3-4 days)
  - File: `tasks/09-mdc-logging.md`

### Phase 7: Distributed Tracing (Week 8-9)
**Priority:** MEDIUM (after metrics complete)
**Estimated Effort:** 2 weeks

- [ ] **Task 7.1:** OpenTelemetry integration (1 week)
  - File: `tasks/10-distributed-tracing.md`

- [ ] **Task 7.2:** Automatic span creation (3-4 days)
  - File: `tasks/11-automatic-spans.md`

### Phase 8: Grafana Dashboards (Week 10)
**Priority:** MEDIUM
**Estimated Effort:** 1 week

- [ ] **Task 8.1:** Create Grafana dashboard templates (4-5 days)
  - File: `tasks/12-grafana-dashboards.md`

---

## Success Criteria

After completion:
- ✅ All metrics from `metrics/TODO.md` implemented and tested
- ✅ Comprehensive health checks for Kubernetes deployments
- ✅ Production-ready observability stack
- ✅ Grafana dashboards for common monitoring scenarios
